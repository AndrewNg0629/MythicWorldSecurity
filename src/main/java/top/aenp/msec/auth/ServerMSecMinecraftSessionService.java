package top.aenp.msec.auth;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.response.MinecraftProfilePropertiesResponse;
import com.mojang.util.UUIDTypeAdapter;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.Utils;
import top.aenp.msec.config.ServerConfigManager;
import top.aenp.msec.network.MSecNetwork;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ServerMSecMinecraftSessionService extends AbstractMSecMinecraftSessionService {
    public static final ServerMSecMinecraftSessionService INSTANCE = new ServerMSecMinecraftSessionService();
    private static final int CACHE_EXPIRE_TIME = 3600;
    private final HashMap<UUID, ProfileResultCacheEntry> profileByUUIDCache = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private ServerMSecMinecraftSessionService() {
    }

    @Override
    public void joinServer(GameProfile profile, String authenticationToken, String serverId) {
        throw new UnsupportedOperationException("Join server shouldn't be called don a server!");
    }

    @Override
    public GameProfile hasJoinedServer(GameProfile user, String serverId, InetAddress address) {
        throw new UnsupportedOperationException("This method shouldn't be called.");
    }

    @Override
    public GameProfile fillProfileProperties(GameProfile profile, boolean requireSecure) {
        UUID profileId = profile.getId();
        if (profileId == null) {
            return profile;
        }
        try {
            lock.readLock().lock();
            ProfileResultCacheEntry cacheEntry = profileByUUIDCache.get(profileId);
            if (cacheEntry != null) {
                if (cacheEntry.cachedOn + 1000L * CACHE_EXPIRE_TIME >= System.currentTimeMillis()) {
                    return cacheEntry.gameProfile;
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        try {
            lock.writeLock().lock();
            ProfileResultCacheEntry cacheEntry = profileByUUIDCache.get(profileId);
            if (cacheEntry != null) {
                if (cacheEntry.cachedOn + 1000L * CACHE_EXPIRE_TIME >= System.currentTimeMillis()) {
                    return cacheEntry.gameProfile;
                }
            }
            GameProfile gameProfile = fetchProfileUncached(profileId);
            if (gameProfile != null) {
                ProfileResultCacheEntry newCacheEntry = new ProfileResultCacheEntry(gameProfile, System.currentTimeMillis());
                if (profileByUUIDCache.size() >= 1000) {
                    profileByUUIDCache.entrySet().removeIf(mapEntry -> mapEntry.getValue().cachedOn + 1000L * CACHE_EXPIRE_TIME < System.currentTimeMillis());
                }
                profileByUUIDCache.put(profileId, newCacheEntry);
                return gameProfile;
            } else {
                return profile;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public GameProfile fetchProfileUncached(UUID profileId) {
        UUIDBySourceRepository.UUIDSearchResult searchResult = UUIDBySourceRepository.getInstance().searchAndReverseMapUUID(profileId);
        UUID requestUUID;
        if (searchResult == null) {
            for (String trialConfigName : ServerConfigManager.getConfig().bootstrapConfig().unknownUUIDMatchSequence()) {
                GameProfile gameProfile = fetchProfileAndSuffix(trialConfigName, profileId);
                if (gameProfile != null) {
                    UUID uuid = UUIDBySourceRepository.getInstance().mapUUID(trialConfigName, gameProfile.getId());
                    if (uuid != null) {
                        GameProfile rewrittenProfile = new GameProfile(uuid, gameProfile.getName());
                        rewrittenProfile.getProperties().putAll(gameProfile.getProperties());
                        return rewrittenProfile;
                    } else {
                        return null;
                    }
                }
            }
            return null;
        } else {
            requestUUID = searchResult.reverseMappedUUID();
             GameProfile gameProfile = fetchProfileAndSuffix(searchResult.sourceConfigName(), requestUUID);
            if (gameProfile != null) {
                GameProfile rewrittenProfile = new GameProfile(profileId, gameProfile.getName());
                rewrittenProfile.getProperties().putAll(gameProfile.getProperties());
                return rewrittenProfile;
            } else {
                return null;
            }
        }
    }

    private GameProfile fetchProfileAndSuffix(String apiConfigName, UUID requestUUID) {
        String suffixExemptedName = ServerConfigManager.getInstance().getSuffixExemptedSourceName();
        apiConfigName = apiConfigName.toLowerCase(Locale.ROOT);
        if (apiConfigName.equals("offline")) {
            //TODO Add offline system support.
            return null;
        } else {
            String sessionHost;
            AuthSourceSuffixInfo suffixInfo = null;
            if (apiConfigName.equalsIgnoreCase("mojang")) {
                sessionHost = Utils.MOJANG_SESSION_SERVER + "/session/minecraft";
                if (!suffixExemptedName.equals("mojang")) {
                    suffixInfo = ServerConfigManager.getInstance().getMojangSuffixInfo();
                }
            } else {
                YggdrasilApiMetadata metadata = ServerConfigManager.getInstance().getYggdrasilConfigNameMap().inverse().get(apiConfigName);
                if (metadata == null) {
                    return null;
                }
                String apiRoot = metadata.apiRootUrl();
                if (!apiRoot.endsWith("/")) {
                    apiRoot = apiRoot + "/";
                }
                sessionHost = apiRoot + "sessionserver/session/minecraft";
                if (!suffixExemptedName.equalsIgnoreCase(apiConfigName)) {
                    suffixInfo = ServerConfigManager.getInstance().getYggdrasilSuffixInfoMap().get(metadata);
                }
            }
            MinecraftProfilePropertiesResponse profileResponse = fetchProfileFromHost(requestUUID, sessionHost);
            if (profileResponse == null) {
                return null;
            }
            String profileName = profileResponse.getName();
            UUID profileId = profileResponse.getId();
            if (suffixInfo != null) {
                profileName = profileName + suffixInfo.primarySuffix();
            }
            GameProfile rewrittenProfile = new GameProfile(profileId, profileName);
            rewrittenProfile.getProperties().putAll(profileResponse.getProperties());
            return rewrittenProfile;
        }
    }

    private MinecraftProfilePropertiesResponse fetchProfileFromHost(UUID uuid, String sessionHost) {
        try {
            String slashedSessionHost = sessionHost;
            if (!slashedSessionHost.endsWith("/")) {
                slashedSessionHost = slashedSessionHost + "/";
            }
            URI requestUri = new URI(slashedSessionHost + "profile/" + UUIDTypeAdapter.fromUUID(uuid) + "?unsigned=true");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(requestUri)
                    .GET()
                    .build();
            HttpResponse<String> httpResponse = MSecNetwork.getHttpClient(null).send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int responseCode = httpResponse.statusCode();
            if (responseCode == 200) {
                String responseString = httpResponse.body();
                MinecraftProfilePropertiesResponse profileResponse = AbstractMSecMinecraftSessionService.PROFILE_GSON.fromJson(responseString, MinecraftProfilePropertiesResponse.class);
                if (profileResponse == null) {
                    MythicWorldSecurity.LOGGER.error("Profile of UUID {} from {} doesn't exist!", uuid, sessionHost);
                    return null;
                } else {
                    return profileResponse;
                }
            } else if (responseCode == 204) {
                return null;
            } else {
                MythicWorldSecurity.LOGGER.error("Failed to fetch profile of UUID {} from {} : {}", uuid, sessionHost, responseCode);
                return null;
            }
        } catch (URISyntaxException | InterruptedException | IOException e) {
            MythicWorldSecurity.LOGGER.error("Failed to fetch profile of UUID {} from {} due to exception!", uuid, sessionHost, e);
            return null;
        }
    }

    public record ProfileResultCacheEntry(GameProfile gameProfile, long cachedOn) {
    }
}
