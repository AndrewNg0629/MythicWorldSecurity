package top.aenp.msec.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.ProfileNotFoundException;
import com.mojang.util.UUIDTypeAdapter;
import top.aenp.msec.Utils;
import top.aenp.msec.config.ServerConfigManager;
import top.aenp.msec.network.MSecNetwork;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@SuppressWarnings("SameParameterValue")
public class ServerMSecGameProfileRepository implements GameProfileRepository {
    public static final ServerMSecGameProfileRepository INSTANCE = new ServerMSecGameProfileRepository();
    private static final int CACHE_EXPIRE_TIME = 3600;
    private final HashMap<String, ProfileCacheEntry> profileByNameCache = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final Gson PROFILE_GSON = new GsonBuilder()
            .registerTypeAdapter(GameProfile.class, new GameProfile.Serializer())
            .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
            .registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer())
            .create();

    private ServerMSecGameProfileRepository() {
    }

    @Override
    public void findProfilesByNames(String[] names, ProfileLookupCallback callback) {
        Set<String> nameSet = Set.copyOf(List.of(names));
        Map<String, ProfileQueryResultEntry> lookupResult = findProfilesByNamesAsMap(nameSet);
        for (Map.Entry<String, ProfileQueryResultEntry> entry : lookupResult.entrySet()) {
            ProfileQueryResultEntry queryResult = entry.getValue();
            if (queryResult.profile != null) {
                callback.onProfileLookupSucceeded(queryResult.profile);
            } else {
                if (queryResult.isNotFound) {
                    callback.onProfileLookupFailed(entry.getKey(), new ProfileNotFoundException("Requested profile was not found."));
                } else {
                    callback.onProfileLookupFailed(entry.getKey(), new AuthenticationException("Unknown exception."));
                }
            }
        }
    }

    public Map<String, ProfileQueryResultEntry> findProfilesByNamesAsMap(Set<String> names) {
        HashMap<String, ProfileQueryResultEntry> resultMap = new HashMap<>();
        HashSet<String> mutableNames = new HashSet<>(names);
        try {
            lock.readLock().lock();
            Map<String, ProfileQueryResultEntry> cacheResult = findCacheHitsAsMap(mutableNames);
            resultMap.putAll(cacheResult);
        } finally {
            lock.readLock().unlock();
        }
        if (!mutableNames.isEmpty()) {
            try {
                lock.writeLock().lock();
                Map<String, ProfileQueryResultEntry> cacheResult = findCacheHitsAsMap(mutableNames);
                resultMap.putAll(cacheResult);
                if (!mutableNames.isEmpty()) {
                    Map<String, Map<String, List<String>>> requestNamesBySourceMap = sortAndStripNamesBySource(mutableNames);
                    Map<String, List<String>> mojangRequests = requestNamesBySourceMap.remove("mojang");
                    Map<String, List<String>> offlineRequests = requestNamesBySourceMap.remove("offline"); //TODO Offline.
                    if (mojangRequests != null) {
                        Map<String, ProfileQueryResultEntry> mojangResult = pageAndLookupOnlineFromSource(mojangRequests, "mojang", 2);
                        resultMap.putAll(mojangResult);
                    }
                    for (Map.Entry<String, Map<String, List<String>>> entry : requestNamesBySourceMap.entrySet()) {
                        String sourceName = entry.getKey();
                        Map<String, List<String>> requestNames = entry.getValue();
                        Map<String, ProfileQueryResultEntry> yggdrasilResult = pageAndLookupOnlineFromSource(requestNames, sourceName, 2);
                        resultMap.putAll(yggdrasilResult);
                    }
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
        return resultMap;
    }

    private Map<String, Map<String, List<String>>> sortAndStripNamesBySource(Set<String> parameterNames) {
        Map<String, Map<String, List<String>>> resultMap = new HashMap<>();
        nameLoop: for (String parameterName : parameterNames) {
            for (String suffix : ServerConfigManager.getInstance().getMojangSuffixInfo().allPossibleSuffixes()) {
                if (parameterName.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) {
                    Map<String, List<String>> parameterNameMap = resultMap.computeIfAbsent("mojang", name -> new HashMap<>());
                    String strippedName = parameterName.substring(0, parameterName.length() - suffix.length());
                    List<String> targetParameterNames = parameterNameMap.computeIfAbsent(strippedName, name -> new LinkedList<>());
                    targetParameterNames.add(parameterName);
                    continue nameLoop;
                }
            }
            //TODO Offline.
            for (Map.Entry<YggdrasilApiMetadata, AuthSourceSuffixInfo> suffixInfoEntry : ServerConfigManager.getInstance().getYggdrasilSuffixInfoMap().entrySet()) {
                for (String suffix : suffixInfoEntry.getValue().allPossibleSuffixes()) {
                    if (parameterName.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) {
                        String authSourceName = ServerConfigManager.getInstance().getYggdrasilConfigNameMap().get(suffixInfoEntry.getKey());
                        Map<String, List<String>> parameterNameMap = resultMap.computeIfAbsent(authSourceName, name -> new HashMap<>());
                        String strippedName = parameterName.substring(0, parameterName.length() - suffix.length());
                        List<String> targetParameterNames = parameterNameMap.computeIfAbsent(strippedName, name -> new LinkedList<>());
                        targetParameterNames.add(parameterName);
                        continue nameLoop;
                    }
                }
            }
            String suffixExemptedSource = ServerConfigManager.getInstance().getSuffixExemptedSourceName();
            if (!suffixExemptedSource.isEmpty()) {
                Map<String, List<String>> parameterNameMap = resultMap.computeIfAbsent(suffixExemptedSource, name -> new HashMap<>());
                List<String> targetParameterNames = parameterNameMap.computeIfAbsent(parameterName, name -> new LinkedList<>());
                targetParameterNames.add(parameterName);
            }
        }
        return resultMap;
    }

    private Map<String, ProfileQueryResultEntry> findCacheHitsAsMap(HashSet<String> names) {
        HashMap<String, ProfileQueryResultEntry> resultMap = new HashMap<>();
        Iterator<String> iterator = names.iterator();
        while (iterator.hasNext()) {
            String parameterName = iterator.next();
            String lowercaseName = parameterName.toLowerCase(Locale.ROOT);
            ProfileCacheEntry cacheEntry = profileByNameCache.get(lowercaseName);
            if (cacheEntry != null) {
                if (cacheEntry.cachedOn + 1000L * CACHE_EXPIRE_TIME < System.currentTimeMillis()) {
                    iterator.remove();
                    resultMap.put(parameterName, new ProfileQueryResultEntry(cacheEntry.profile, false));
                }
            }
        }
        return resultMap;
    }

    private Map<String, ProfileQueryResultEntry> pageAndLookupOnlineFromSource(Map<String, List<String>> names, String authSourceName, int maxPageSize) {
        String lookupUrl;
        AuthSourceSuffixInfo suffixInfo = null;
        if (authSourceName.equalsIgnoreCase("offline")) {
            return Map.of();
        } else if (authSourceName.equalsIgnoreCase("mojang")) {
            lookupUrl = Utils.MOJANG_SERVICES_HOST + "/minecraft/profile/lookup/bulk/byname";
            if (!ServerConfigManager.getInstance().getSuffixExemptedSourceName().equalsIgnoreCase("mojang")) {
                suffixInfo = ServerConfigManager.getInstance().getMojangSuffixInfo();
            }
        } else {
            YggdrasilApiMetadata metadata = ServerConfigManager.getInstance().getYggdrasilConfigNameMap().inverse().get(authSourceName);
            String apiRootUrl = metadata.apiRootUrl();
            if (!apiRootUrl.endsWith("/")) {
                apiRootUrl = apiRootUrl + "/";
            }
            lookupUrl = apiRootUrl + "api/profiles/minecraft";
            if (!ServerConfigManager.getInstance().getSuffixExemptedSourceName().equalsIgnoreCase(authSourceName)) {
                suffixInfo = ServerConfigManager.getInstance().getYggdrasilSuffixInfoMap().get(metadata);
            }
        }
        final String finalLookupUrl = lookupUrl;
        HashSet<HashMap<String, List<String>>> pageSet = new HashSet<>();
        HashMap<String, List<String>> currentPage = new HashMap<>();
        pageSet.add(currentPage);
        for (Map.Entry<String, List<String>> entry : names.entrySet()) {
            if (currentPage.size() >= maxPageSize) {
                currentPage = new HashMap<>();
                pageSet.add(currentPage);
            }
            currentPage.put(entry.getKey(), entry.getValue());
        }
        HashMap<String, ProfileQueryResultEntry> resultMap = new HashMap<>();
        for (Map<String, List<String>> requestPage : pageSet) {
            try {
                Set<String> namePage = requestPage.keySet();
                Map<String, GameProfile> fetchResult = MSecNetwork.fetchIOWithRetries(() -> fetchProfilePageFromHttp(namePage, finalLookupUrl), "bulk profile lookup", 3, 300L);
                for (Map.Entry<String, GameProfile> entry : fetchResult.entrySet()) {
                    String resultName = entry.getKey();
                    GameProfile resultProfile = entry.getValue();
                    for (String parameterName : requestPage.get(resultName)) {
                        if (resultProfile != null) {
                            UUID rewrittenUUID = UUIDBySourceRepository.getInstance().mapUUID(authSourceName, resultProfile.getId());
                            String suffixedName = resultProfile.getName();
                            if (suffixInfo != null) {
                                suffixedName = suffixedName + suffixInfo.primarySuffix();
                            }
                            GameProfile rewrittenProfile = new GameProfile(rewrittenUUID, suffixedName);
                            rewrittenProfile.getProperties().putAll(resultProfile.getProperties());
                            ProfileCacheEntry cacheEntry = new ProfileCacheEntry(rewrittenProfile, System.currentTimeMillis());
                            if (profileByNameCache.size() >= 1000) {
                                profileByNameCache.entrySet().removeIf(mapEntry -> mapEntry.getValue().cachedOn + 1000L * CACHE_EXPIRE_TIME < System.currentTimeMillis());
                            }
                            profileByNameCache.put(parameterName.toLowerCase(Locale.ROOT), cacheEntry);
                            resultMap.put(parameterName, new ProfileQueryResultEntry(rewrittenProfile, false));
                        } else {
                            resultMap.put(parameterName, new ProfileQueryResultEntry(null, true));
                        }
                    }
                }
            } catch (IOException e) {
                for (Map.Entry<String, List<String>> entry : requestPage.entrySet()) {
                    for (String parameterName : entry.getValue()) {
                        resultMap.put(parameterName, new ProfileQueryResultEntry(null, false));
                    }
                }
            }
        }
        return resultMap;
    }

    private Map<String, GameProfile> fetchProfilePageFromHttp(Set<String> strippedNames, String lookupUrl) throws IOException {
        try {
            URI requestUri = new URI(lookupUrl);
            JsonArray jsonArray = new JsonArray();
            strippedNames.forEach(name -> jsonArray.add(name.toLowerCase(Locale.ROOT)));
            String jsonArrayString = PROFILE_GSON.toJson(jsonArray);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(requestUri)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonArrayString, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> httpResponse = MSecNetwork.getHttpClient(null).send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int responseCode = httpResponse.statusCode();
            if (!(responseCode >= 200 && responseCode < 400)) {
                throw new IOException("Failed to fetch profiles from " + lookupUrl + " : " + responseCode);
            }
            try {
                JsonArray responseArray = JsonParser.parseString(httpResponse.body()).getAsJsonArray();
                List<GameProfile> responseProfiles = responseArray.asList().stream().map(profileJson -> PROFILE_GSON.fromJson(profileJson, GameProfile.class)).toList();
                HashMap<String, GameProfile> queryResultMap = new HashMap<>();
                responseProfiles.forEach(gameProfile -> queryResultMap.put(gameProfile.getName().toLowerCase(Locale.ROOT), gameProfile));
                HashMap<String, GameProfile> resultMap = new HashMap<>();
                for (String strippedName : strippedNames) {
                    resultMap.put(strippedName, queryResultMap.get(strippedName.toLowerCase(Locale.ROOT)));
                }
                return resultMap;
            } catch (Exception e) {
                throw new IOException("Failed to parse profile result from " + lookupUrl, e);
            }
        } catch (URISyntaxException e) {
            throw new IOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException();
        }
    }

    private record ProfileCacheEntry(GameProfile profile, long cachedOn) {
    }

    public record ProfileQueryResultEntry(GameProfile profile, boolean isNotFound) {
    }
}
