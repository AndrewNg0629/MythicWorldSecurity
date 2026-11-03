package top.aenp.msec.network.interfaces;

import top.aenp.msec.network.payloads.auth.c2s.PlayProfileBatchRequestC2SPayload;
import top.aenp.msec.network.payloads.auth.c2s.PlayProfileQueryC2SPayload;
import top.aenp.msec.network.payloads.texture.c2s.PlayTextureRequestC2SPayload;
import top.aenp.mwl.network.v2.interfaces.MythicServerPlayNetworkHandler;

public interface MSecServerPlayNetworkHandler extends MythicServerPlayNetworkHandler {
    default void mythicworldsecurity$onTextureRequest(PlayTextureRequestC2SPayload payload) {
        throw new RuntimeException();
    }

    default void mythicworldsecurity$onProfileQuery(PlayProfileQueryC2SPayload payload) {
        throw new RuntimeException();
    }

    default void mythicworldsecurity$onProfileBatchRequest(PlayProfileBatchRequestC2SPayload payload) {
        throw new RuntimeException();
    }
}
