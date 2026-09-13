package top.aenp.msec.network.interfaces;

import top.aenp.msec.network.MSecLoginHandshakeHandler;
import top.aenp.msec.network.payloads.texture.LoginTextureDataC2SPayload;
import top.aenp.msec.network.payloads.texture.LoginTextureMetadataC2SPayload;
import top.aenp.mwl.network.v2.interfaces.MythicServerLoginNetworkHandler;

public interface MSecServerLoginNetworkHandler extends MythicServerLoginNetworkHandler {
    default void mythicworldsecurity$onTextureMetadata(LoginTextureMetadataC2SPayload payload) {
        throw new RuntimeException();
    }
    default void mythicworldsecurity$onTextureData(LoginTextureDataC2SPayload payload) {
        throw new RuntimeException();
    }
    default void mythicworldsecurity$textureFileWriteCallback(LoginTextureDataC2SPayload.TextureType type, boolean successful, String hash) {
        throw new RuntimeException();
    }
    default MSecLoginHandshakeHandler mythicworldsecurity$getMSecHandshakeHandler() {
        throw new RuntimeException();
    }
}
