package top.aenp.msec.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.SignatureState;
import com.mojang.authlib.minecraft.InsecurePublicKeyException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
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
    public Property getPackedTextures(GameProfile profile) {
        return profile.getProperties().get("textures").stream().findFirst().orElse(null);
    }

    @Override
    public MinecraftProfileTextures unpackTextures(Property packedTextures) {
        MinecraftTexturesPayload texturesPayload;
        try {
            String jsonString = new String(Base64.getDecoder().decode(packedTextures.value()), StandardCharsets.UTF_8);
            texturesPayload = PROFILE_GSON.fromJson(jsonString, MinecraftTexturesPayload.class);
        } catch (Exception e) {
            MythicWorldSecurity.LOGGER.error("Failed to decode textures payload", e);
            return MinecraftProfileTextures.EMPTY;
        }
        if (texturesPayload == null || texturesPayload.textures() == null || texturesPayload.textures().isEmpty()) {
            return MinecraftProfileTextures.EMPTY;
        }
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> texturesByType = texturesPayload.textures();
        for (Map.Entry<MinecraftProfileTexture.Type, MinecraftProfileTexture> entry : texturesByType.entrySet()) {
            String url = entry.getValue().getUrl();
            if (!(TextureUrlChecker.isAllowedTextureDomain(url) || MSecNetwork.isAllowedTextureDomain(url))) {
                MythicWorldSecurity.LOGGER.error("Textures payload contains blocked domain: {}", url);
                return MinecraftProfileTextures.EMPTY;
            }
        }
        return new MinecraftProfileTextures(
                texturesByType.get(MinecraftProfileTexture.Type.SKIN),
                texturesByType.get(MinecraftProfileTexture.Type.CAPE),
                texturesByType.get(MinecraftProfileTexture.Type.ELYTRA),
                SignatureState.SIGNED
        );
    }

    @Override
    public String getSecurePropertyValue(Property property) throws InsecurePublicKeyException {
        return property.value();
    }
}
