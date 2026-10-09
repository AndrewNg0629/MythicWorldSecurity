package top.aenp.msec.network.interfaces;

import top.aenp.msec.network.payloads.texture.LoginTextureCommandS2CPayload;
import top.aenp.mwl.network.v2.interfaces.MythicClientLoginNetworkHandler;

public interface MSecClientLoginNetworkHandler extends MythicClientLoginNetworkHandler {
    default void mythicworldsecurity$onTextureCommand(LoginTextureCommandS2CPayload payload) {
        throw new RuntimeException();
    }

    default MSecClientLoginHandshakeHandler mythicworldsecurity$getMSecHandshakeHandler() {
        throw new RuntimeException();
    }
}
