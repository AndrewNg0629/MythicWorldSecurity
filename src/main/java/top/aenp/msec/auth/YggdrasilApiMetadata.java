package top.aenp.msec.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.Utils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.List;

public record YggdrasilApiMetadata(String metaServerName, String injectedApiUrl, String homepageUrl, List<String> skinDomains) {

    public static YggdrasilApiMetadata fetchApiMetadataWithRetries(String originalApiUrlString, Proxy proxy, int maxRetries) {
        for (int i = 1; i <= maxRetries; i++) {
            try {
                return fetchApiMetadata(originalApiUrlString, proxy);
            } catch (IOException e) {
                MythicWorldSecurity.LOGGER.error("API metadata fetch attempt #{} failed!", i, e);
                try {
                    Thread.sleep(1000L * i);
                } catch (InterruptedException e1) {
                    throw new RuntimeException(e1);
                }
            }
        }
        throw new RuntimeException(String.format("Failed to fetch API metadata after %d attempts.", maxRetries));
    }

    private static YggdrasilApiMetadata fetchApiMetadata(String originalApiUrlString, Proxy proxy) throws IOException {
        originalApiUrlString = addHttpsAndSlash(originalApiUrlString);
        HttpURLConnection originalConnection = null;
        HttpURLConnection redirectedConnection = null;
        HttpURLConnection followedConnection;
        try {
            URL originalApiUrl = URL.of(new URI(originalApiUrlString), null);
            originalConnection = (HttpURLConnection) (proxy != null ? originalApiUrl.openConnection(proxy) : originalApiUrl.openConnection());
            originalConnection.setDoOutput(false);
            originalConnection.setDoInput(true);
            originalConnection.connect();
            followedConnection = originalConnection;
            String indicatedApiUrlString = originalConnection.getHeaderField("x-authlib-injector-api-location");
            if (indicatedApiUrlString != null) {
                indicatedApiUrlString = addHttpsAndSlash(indicatedApiUrlString);
                if (!originalApiUrlString.equals(indicatedApiUrlString)) {
                    originalConnection.disconnect();
                    URL indicatedApiUrl = URL.of(new URI(indicatedApiUrlString), null);
                    redirectedConnection = (HttpURLConnection) indicatedApiUrl.openConnection();
                    redirectedConnection.setDoOutput(false);
                    redirectedConnection.setDoInput(true);
                    redirectedConnection.connect();
                    followedConnection = redirectedConnection;
                }
            }
            followedConnection = Utils.followHttpRedirects(followedConnection, proxy);
            int responseCode = followedConnection.getResponseCode();
            if (!(responseCode >= 200 && responseCode < 400)) {
                throw new IOException(String.format("Failed to request Yggdrasil API: %s, %d", followedConnection.getURL(), followedConnection.getResponseCode()));
            }
            try(InputStreamReader reader = new InputStreamReader(followedConnection.getInputStream())) {
                String metaServerName = null;
                String homepageUrl = null;
                List<String> skinDomains = null;
                JsonObject metadataRootJson = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject metaJson = metadataRootJson.getAsJsonObject("meta");
                JsonElement metaServerNameJson = metaJson.get("serverName");
                if (metaServerNameJson != null) {
                    metaServerName = metaServerNameJson.getAsJsonPrimitive().getAsString();
                }
                JsonElement linksJson = metaJson.get("links");
                if (linksJson != null) {
                    JsonElement homepageUrlJson = linksJson.getAsJsonObject().get("homepage");
                    if (homepageUrlJson != null) {
                        homepageUrl = homepageUrlJson.getAsJsonPrimitive().getAsString();
                    }
                }
                JsonElement skinDomainsJson = metadataRootJson.get("skinDomains");
                if (skinDomainsJson != null) {
                    skinDomains = skinDomainsJson.getAsJsonArray()
                            .asList()
                            .stream()
                            .map(jsonElement -> jsonElement.getAsJsonPrimitive().getAsString())
                            .toList();
                }
                return new YggdrasilApiMetadata(metaServerName, originalApiUrlString, homepageUrl, skinDomains);
            } catch (Exception e) {
                throw new IOException("Failed to parse API metadata.", e);
            }
        } catch (URISyntaxException | JsonParseException e) {
            throw new IOException("Failed to fetch Yggdrasil API metadata.", e);
        } finally {
            if (originalConnection != null) {
                originalConnection.disconnect();
            }
            if (redirectedConnection != null) {
                redirectedConnection.disconnect();
            }
        }
    }

    private static String addHttpsAndSlash(String url) {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            url = "https://" + url;
        }
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        return url;
    }
}
