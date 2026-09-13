package top.aenp.msec.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.util.UndashedUuid;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class MojangAuthSource implements AuthSource {
    public static final MojangAuthSource INSTANCE = new MojangAuthSource();

    private static final URI MOJANG_SERVICES_HOST;
    private static final URI MOJANG_SESSION_HOST;

    static {
        try {
            MOJANG_SERVICES_HOST = URI.create("https://api.minecraftservices.com/");
            MOJANG_SESSION_HOST = URI.create("https://sessionserver.mojang.com/");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MojangAuthSource() {
    }

    @Override
    public UUID getUUIDByName(String name) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL requestUrl = MOJANG_SERVICES_HOST.resolve("minecraft/profile/lookup/name/").resolve(name).toURL();
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setDoInput(true);
            connection.setDoOutput(false);
            connection.connect();
            if (connection.getResponseCode() == 200) {
                try(InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                    JsonElement jsonElement = JsonParser.parseReader(reader);
                    String uuidString = jsonElement.getAsJsonObject().get("id").getAsString();
                    return UndashedUuid.fromStringLenient(uuidString);
                } catch (JsonParseException e) {
                    throw new IOException("Failed to read name query.", e);
                }
            } else {
                return null;
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public String getNameByUUID(UUID uuid) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL requestUrl = MOJANG_SERVICES_HOST.resolve("minecraft/profile/lookup/").resolve(URLEncoder.encode(uuid.toString(), StandardCharsets.UTF_8)).toURL();
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setDoInput(true);
            connection.setDoOutput(false);
            connection.connect();
            if (connection.getResponseCode() == 200) {
                try(InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                    JsonElement jsonElement = JsonParser.parseReader(reader);
                    return jsonElement.getAsJsonObject().get("name").getAsString();
                } catch (JsonParseException e) {
                    throw new IOException("Failed to read name query.", e);
                }
            } else {
                return null;
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public ProfileResult checkJoined(String name, String apiNonce, InetAddress clientAddress) throws IOException {
        return checkJoinedWithEndpoint(MOJANG_SESSION_HOST, name, apiNonce, clientAddress);
    }

    @Override
    public List<GameProfile> fetchProfiles(String[] names) {
        return List.of();
    }
}
