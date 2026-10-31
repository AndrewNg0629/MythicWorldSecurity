package top.aenp.msec.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import top.aenp.msec.network.MSecNetwork;

import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record YggdrasilApiMetadata(
        String metaServerName,
        String apiRootUrl,
        String homepageUrl,
        Set<String> skinDomains
) {

    public static YggdrasilApiMetadata fetchApiMetadata(String originalApiUrlString, Proxy proxy) throws IOException {
        if (originalApiUrlString.endsWith("/")) {
            originalApiUrlString = originalApiUrlString.substring(0, originalApiUrlString.length() - 1);
        }
        originalApiUrlString = addHttps(originalApiUrlString);
        String apiRootUrl = originalApiUrlString;
        try {
            HttpRequest originalRequest = HttpRequest.newBuilder()
                    .uri(new URI(originalApiUrlString))
                    .GET()
                    .build();
            HttpResponse<String> httpResponse = MSecNetwork.getHttpClient(proxy).send(originalRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Optional<String> indicatedApiUrlOptional = httpResponse.headers().firstValue("x-authlib-injector-api-location");
            if (indicatedApiUrlOptional.isPresent()) {
                String indicatedApiUrlString = addHttps(indicatedApiUrlOptional.get());
                if (!addHttpsAndSlash(originalApiUrlString).equals(addHttpsAndSlash(indicatedApiUrlString))) {
                    apiRootUrl = indicatedApiUrlString;
                    HttpRequest redirectedRequest = HttpRequest.newBuilder()
                            .uri(new URI(indicatedApiUrlString))
                            .GET()
                            .build();
                    httpResponse = MSecNetwork.getHttpClient(proxy).send(redirectedRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                }
            }

            int responseCode = httpResponse.statusCode();
            if (!(responseCode >= 200 && responseCode < 400)) {
                throw new IOException(String.format("Failed to request Yggdrasil API: %s, %d", httpResponse.uri(), responseCode));
            }
            try {
                String metaServerName = null;
                String homepageUrl = null;
                Set<String> skinDomains = null;
                JsonObject metadataRootJson = JsonParser.parseString(httpResponse.body()).getAsJsonObject();
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
                            .collect(Collectors.toSet());
                }
                return new YggdrasilApiMetadata(metaServerName, apiRootUrl, homepageUrl, skinDomains);
            } catch (Exception e) {
                throw new IOException("Failed to parse API metadata.", e);
            }
        } catch (URISyntaxException | JsonParseException | InterruptedException e) {
            throw new IOException("Failed to fetch Yggdrasil API metadata.", e);
        }
    }

    private static String addHttps(String url) {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            url = "https://" + url;
        }
        return url;
    }

    private static String addHttpsAndSlash(String url) {
        url = addHttps(url);
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        return url;
    }

    public static boolean equals(YggdrasilApiMetadata meta1, YggdrasilApiMetadata meta2) {
        if (Objects.equals(meta1, meta2)) {
            return true;
        }
        if (meta1 == null || meta2 == null) {
            return false;
        }
        Set<String> akinDomains1 = meta1.skinDomains.stream().map(YggdrasilApiMetadata::addHttpsAndSlash).collect(Collectors.toSet());
        Set<String> akinDomains2 = meta2.skinDomains.stream().map(YggdrasilApiMetadata::addHttpsAndSlash).collect(Collectors.toSet());
        return meta1.metaServerName.equals(meta2.metaServerName)
                && addHttpsAndSlash(meta1.apiRootUrl).equals(addHttpsAndSlash(meta2.apiRootUrl))
                && addHttpsAndSlash(meta1.homepageUrl).equals(addHttpsAndSlash(meta2.homepageUrl))
                && akinDomains1.equals(akinDomains2);
    }
}
