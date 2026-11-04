package top.aenp.msec.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.InsecurePublicKeyException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.TextureUrlChecker;
import com.mojang.authlib.yggdrasil.response.MinecraftTexturesPayload;
import com.mojang.util.UUIDTypeAdapter;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.network.MSecNetwork;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractMSecMinecraftSessionService implements MinecraftSessionService {
    public static final Gson PROFILE_GSON = new GsonBuilder()
            .registerTypeAdapter(GameProfile.class, new GameProfile.Serializer())
            .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
            .registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer())
            .create();

    @Override
    public Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> getTextures(GameProfile profile, boolean requireSecure) throws InsecurePublicKeyException {
        Property packedTextures = profile.getProperties().get("textures").stream().findFirst().orElse(null);
        if (packedTextures == null) {
            return Map.of();
        }
        MinecraftTexturesPayload texturesPayload;
        try {
            String jsonString = new String(Base64.getDecoder().decode(packedTextures.getValue()), StandardCharsets.UTF_8);
            texturesPayload = PROFILE_GSON.fromJson(jsonString, MinecraftTexturesPayload.class);
        } catch (Exception e) {
            MythicWorldSecurity.LOGGER.error("Failed to decode textures payload", e);
            return Map.of();
        }
        if (texturesPayload == null || texturesPayload.getTextures() == null || texturesPayload.getTextures().isEmpty()) {
            return Map.of();
        }
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> texturesByType = texturesPayload.getTextures();
        for (Map.Entry<MinecraftProfileTexture.Type, MinecraftProfileTexture> entry : texturesByType.entrySet()) {
            String url = entry.getValue().getUrl();
            if (!(TextureUrlChecker.isAllowedTextureDomain(url) || MSecNetwork.isAllowedTextureDomain(url))) {
                MythicWorldSecurity.LOGGER.error("Textures payload contains blocked domain: {}", url);
                return Map.of();
            }
        }
        return texturesByType;
    }

    @Override
    public String getSecurePropertyValue(Property property) throws InsecurePublicKeyException {
        return property.getValue();
    }
}
