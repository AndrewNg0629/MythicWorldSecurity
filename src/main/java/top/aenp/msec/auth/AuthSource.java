package top.aenp.msec.auth;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.ProfileActionType;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.response.HasJoinedMinecraftServerResponse;
import com.mojang.authlib.yggdrasil.response.ProfileAction;
import com.mojang.util.UUIDTypeAdapter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public interface AuthSource {
     Gson GSON = new GsonBuilder()
            .registerTypeAdapter(GameProfile.class, new GameProfile.Serializer())
            .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
            .registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer())
            .create();

    UUID getUUIDByName(String name) throws IOException;
    String getNameByUUID(UUID uuid) throws IOException;
    ProfileResult checkJoined(String name, String apiNonce, InetAddress clientAddress) throws IOException;
    List<GameProfile> fetchProfiles(String[] names);

    default ProfileResult checkJoinedWithEndpoint(URI sessionEndpoint, String name, String apiNonce, InetAddress clientAddress) throws IOException {
        HttpURLConnection connection = null;
        try {
            StringBuilder builder = new StringBuilder("hasJoined?username=");
            builder.append(URLEncoder.encode(name, StandardCharsets.UTF_8));
            builder.append("&serverId=");
            builder.append(URLEncoder.encode(apiNonce, StandardCharsets.UTF_8));
            if (clientAddress != null) {
                builder.append("&ip=");
                builder.append(URLEncoder.encode(clientAddress.getHostAddress(), StandardCharsets.UTF_8));
            }
            URL requestURL = sessionEndpoint.resolve("session/minecraft/").resolve(builder.toString()).toURL();
            connection = (HttpURLConnection) requestURL.openConnection();
            connection.setDoInput(true);
            connection.setDoOutput(false);
            connection.connect();
            if (connection.getResponseCode() == 200) {
                try(InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                    JsonElement jsonElement = JsonParser.parseReader(reader);
                    HasJoinedMinecraftServerResponse response = AuthSource.GSON.fromJson(jsonElement, HasJoinedMinecraftServerResponse.class);
                    if (response != null && response.id() != null) {
                        GameProfile profile = new GameProfile(response.id(), name);
                        if (response.properties() != null) {
                            profile.getProperties().putAll(response.properties());
                        }
                        Set<ProfileActionType> profileActionTypes = response.profileActions().stream().map(ProfileAction::type).collect(Collectors.toSet());
                        return new ProfileResult(profile, profileActionTypes);
                    } else {
                        return null;
                    }
                } catch (JsonParseException e) {
                    throw new IOException("Failed to read profile.", e);
                }
            } else if (connection.getResponseCode() == 204) {
                return null;
            } else {
                throw new IOException("API error: " + connection.getResponseCode());
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
