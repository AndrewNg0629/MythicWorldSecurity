package top.aenp.msec.auth;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileActionType;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.response.MinecraftProfilePropertiesResponse;
import com.mojang.authlib.yggdrasil.response.ProfileAction;
import com.mojang.util.UndashedUuid;
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
import java.util.stream.Collectors;

public class ServerMSecMinecraftSessionService extends AbstractMSecMinecraftSessionService {
    public static final ServerMSecMinecraftSessionService INSTANCE = new ServerMSecMinecraftSessionService();
    private static final int CACHE_EXPIRE_TIME = 3600;
    private final HashMap<UUID, ProfileResultCacheEntry> profileByUUIDCache = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private ServerMSecMinecraftSessionService() {
    }

    @Override
    public void joinServer(UUID profileId, String authenticationToken, String serverId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProfileResult hasJoinedServer(String profileName, String apiNonce, InetAddress clientAddress) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProfileResult fetchProfile(UUID profileId, boolean requireSecure) {
        try {
            lock.readLock().lock();
            ProfileResultCacheEntry cacheEntry = profileByUUIDCache.get(profileId);
            if (cacheEntry != null) {
                if (cacheEntry.cachedOn + 1000L * CACHE_EXPIRE_TIME >= System.currentTimeMillis()) {
                    return cacheEntry.profileResult;
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
                    return cacheEntry.profileResult;
                }
            }
            ProfileResult profileResult = fetchProfileUncached(profileId);
            if (profileResult != null) {
                ProfileResultCacheEntry newCacheEntry = new ProfileResultCacheEntry(profileResult, System.currentTimeMillis());
                if (profileByUUIDCache.size() >= 1000) {
                    profileByUUIDCache.entrySet().removeIf(mapEntry -> mapEntry.getValue().cachedOn + 1000L * CACHE_EXPIRE_TIME < System.currentTimeMillis());
                }
                profileByUUIDCache.put(profileId, newCacheEntry);
                return profileResult;
            } else {
                return null;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ProfileResult fetchProfileUncached(UUID profileId) {
        UUIDBySourceRepository.UUIDSearchResult searchResult = UUIDBySourceRepository.getInstance().searchAndReverseMapUUID(profileId);
        UUID requestUUID;
        if (searchResult == null) {
            for (String trialConfigName : ServerConfigManager.getConfig().bootstrapConfig().unknownUUIDMatchSequence()) {
                ProfileResult profileResult = fetchProfileAndSuffix(trialConfigName, profileId);
                if (profileResult != null) {
                    UUID uuid = UUIDBySourceRepository.getInstance().mapUUID(trialConfigName, profileResult.profile().getId());
                    if (uuid != null) {
                        GameProfile rewrittenProfile = new GameProfile(uuid, profileResult.profile().getName());
                        rewrittenProfile.getProperties().putAll(profileResult.profile().getProperties());
                        return new ProfileResult(rewrittenProfile, profileResult.actions());
                    } else {
                        return null;
                    }
                }
            }
            return null;
        } else {
            requestUUID = searchResult.reverseMappedUUID();
            ProfileResult profileResult = fetchProfileAndSuffix(searchResult.sourceConfigName(), requestUUID);
            if (profileResult != null) {
                GameProfile rewrittenProfile = new GameProfile(profileId, profileResult.profile().getName());
                rewrittenProfile.getProperties().putAll(profileResult.profile().getProperties());
                return new ProfileResult(rewrittenProfile, profileResult.actions());
            } else {
                return null;
            }
        }
    }

    private ProfileResult fetchProfileAndSuffix(String apiConfigName, UUID requestUUID) {
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
            GameProfile gameProfile = profileResponse.toProfile();
            String profileName = gameProfile.getName();
            if (suffixInfo != null) {
                profileName = profileName + suffixInfo.primarySuffix();
            }
            GameProfile rewrittenProfile = new GameProfile(gameProfile.getId(), profileName);
            rewrittenProfile.getProperties().putAll(gameProfile.getProperties());
            Set<ProfileActionType> profileActions = profileResponse.profileActions().stream()
                    .map(ProfileAction::type)
                    .collect(Collectors.toSet());
            return new ProfileResult(rewrittenProfile, profileActions);
        }
    }

    private MinecraftProfilePropertiesResponse fetchProfileFromHost(UUID uuid, String sessionHost) {
        try {
            String slashedSessionHost = sessionHost;
            if (!slashedSessionHost.endsWith("/")) {
                slashedSessionHost = slashedSessionHost + "/";
            }
            URI requestUri = new URI(slashedSessionHost + "profile/" + UndashedUuid.toString(uuid) + "?unsigned=true");
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

    public record ProfileResultCacheEntry(ProfileResult profileResult, long cachedOn) {
    }
}
