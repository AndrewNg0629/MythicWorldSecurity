package top.aenp.msec.network.interfaces;

import top.aenp.msec.network.payloads.auth.s2c.PlayProfileBatchResponseS2CPayload;
import top.aenp.msec.network.payloads.auth.s2c.PlayProfileResponseS2CPayload;
import top.aenp.msec.network.payloads.texture.s2c.PlayTextureResponseS2CPayload;
import top.aenp.mwl.network.v2.interfaces.MythicClientPlayNetworkHandler;

public interface MSecClientPlayNetworkHandler extends MythicClientPlayNetworkHandler {
    default void mythicworldsecurity$onTextureResponse(PlayTextureResponseS2CPayload payload) {
        throw new RuntimeException();
    }

    default void mythicworldsecurity$onProfileResponse(PlayProfileResponseS2CPayload payload) {
        throw new RuntimeException();
    }

    default void mythicworldsecurity$onProfileBatchResponse(PlayProfileBatchResponseS2CPayload payload) {
        throw new RuntimeException();
    }
}
