package top.aenp.msec.network.interfaces;

import top.aenp.msec.network.payloads.texture.PlayTextureRequestC2SPayload;
import top.aenp.mwl.network.v2.interfaces.MythicServerPlayNetworkHandler;

public interface MSecServerPlayNetworkHandler extends MythicServerPlayNetworkHandler {
    default void mythicworldsecurity$onTextureRequest(PlayTextureRequestC2SPayload payload) {
        throw new RuntimeException();
    }
}
