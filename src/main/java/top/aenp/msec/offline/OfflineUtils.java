package top.aenp.msec.offline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.response.MinecraftTexturesPayload;
import com.mojang.util.UUIDTypeAdapter;
import top.aenp.msec.network.MSecNetwork;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OfflineUtils {
    private static final Gson PROFILE_GSON = new GsonBuilder()
            .registerTypeAdapter(GameProfile.class, new GameProfile.Serializer())
            .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
            .registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer())
            .create();

    public static GameProfile constructOfflineProfile(String playerName, UUID uuid, String skinHash, String skinModelType, String capeHash) {
        GameProfile gameProfile = new GameProfile(uuid, playerName);
        HashMap<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = new HashMap<>();
        if (skinHash != null) {
            MinecraftProfileTexture skinTexture = new MinecraftProfileTexture(
                    MSecNetwork.TEXTURE_DUMMY_URL_PREFIX + skinHash,
                    skinModelType.equalsIgnoreCase("slim") ? Map.of("model", "slim") : null
            );
            textures.put(MinecraftProfileTexture.Type.SKIN, skinTexture);
        }
        if (capeHash != null) {
            MinecraftProfileTexture capeTexture = new MinecraftProfileTexture(
                    MSecNetwork.TEXTURE_DUMMY_URL_PREFIX + capeHash,
                    null
            );
            textures.put(MinecraftProfileTexture.Type.CAPE, capeTexture);
        }
        MinecraftTexturesPayload texturesPayload = new MinecraftTexturesPayload(
                System.currentTimeMillis(),
                gameProfile.getId(),
                gameProfile.getName(),
                false,
                textures
        );
        String serializedPayload = PROFILE_GSON.toJson(texturesPayload);
        String encodedPayload = Base64.getEncoder().encodeToString(serializedPayload.getBytes(StandardCharsets.UTF_8));
        gameProfile.getProperties().put("textures", new Property("textures", encodedPayload));
        return gameProfile;
    }

}
