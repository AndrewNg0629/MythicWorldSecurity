package top.aenp.msec.network.interfaces;

import top.aenp.msec.network.payloads.texture.PlayTextureResponseS2CPayload;
import top.aenp.mwl.network.v2.interfaces.MythicClientPlayNetworkHandler;

public interface MSecClientPlayNetworkHandler extends MythicClientPlayNetworkHandler {
    default void mythicworldsecurity$onTextureResponse(PlayTextureResponseS2CPayload payload) {
        throw new RuntimeException();
    }
}
