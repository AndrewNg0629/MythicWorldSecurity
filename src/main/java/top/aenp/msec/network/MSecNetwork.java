package top.aenp.msec.network;

import top.aenp.msec.network.payloads.crypto.c2s.LoginIdentityC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginModVersionC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginSignatureC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginX25519C2SPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginIdentityS2CPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginModVersionS2CPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginSignatureS2CPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginX25519S2CPayload;
import top.aenp.msec.network.payloads.texture.*;
import top.aenp.mwl.network.v2.MythicNetwork;

public class MSecNetwork {
    public static final int PROTOCOL_VERSION = 0;
    private static boolean initialized = false;
    public static void init() {
        if (!initialized) {
            MythicNetwork.INSTANCE.CUSTOM_PAYLOAD_CODECS.put(PlayTextureRequestC2SPayload.ID.id(), PlayTextureRequestC2SPayload.CODEC);
            MythicNetwork.INSTANCE.CUSTOM_PAYLOAD_CODECS.put(PlayTextureResponseS2CPayload.ID.id(), PlayTextureResponseS2CPayload.CODEC);
            MythicNetwork.INSTANCE.LOGIN_S2C_CODECS.put(LoginTextureCommandS2CPayload.ID, LoginTextureCommandS2CPayload.CODEC);
            MythicNetwork.INSTANCE.LOGIN_C2S_CODECS.put(LoginTextureDataC2SPayload.ID, LoginTextureDataC2SPayload.CODEC);
            MythicNetwork.INSTANCE.LOGIN_C2S_CODECS.put(LoginTextureMetadataC2SPayload.ID, LoginTextureMetadataC2SPayload.CODEC);
            MythicNetwork.INSTANCE.LOGIN_S2C_CODECS.put(LoginModVersionS2CPayload.ID, LoginModVersionS2CPayload.CODEC);
            MythicNetwork.INSTANCE.LOGIN_C2S_CODECS.put(LoginModVersionC2SPayload.ID, LoginModVersionC2SPayload.CODEC);
            MythicNetwork.INSTANCE.LOGIN_S2C_CODECS.put(LoginIdentityS2CPayload.ID, LoginIdentityS2CPayload.CODEC);
            MythicNetwork.INSTANCE.LOGIN_C2S_CODECS.put(LoginIdentityC2SPayload.ID, LoginIdentityC2SPayload.CODEC);
            MythicNetwork.INSTANCE.LOGIN_S2C_CODECS.put(LoginX25519S2CPayload.ID, LoginX25519S2CPayload.CODEC);
            MythicNetwork.INSTANCE.LOGIN_C2S_CODECS.put(LoginX25519C2SPayload.ID, LoginX25519C2SPayload.CODEC);
            MythicNetwork.INSTANCE.LOGIN_S2C_CODECS.put(LoginSignatureS2CPayload.ID, LoginSignatureS2CPayload.CODEC);
            MythicNetwork.INSTANCE.LOGIN_C2S_CODECS.put(LoginSignatureC2SPayload.ID, LoginSignatureC2SPayload.CODEC);
            initialized = true;
        } else {
            throw new IllegalStateException("MSecNetwork has already been initialized!");
        }
    }
}
