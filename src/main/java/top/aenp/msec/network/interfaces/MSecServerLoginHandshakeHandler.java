package top.aenp.msec.network.interfaces;

import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import top.aenp.msec.network.payloads.auth.c2s.LoginAuthSourceResponseC2SPayload;
import top.aenp.msec.network.payloads.auth.c2s.LoginContactFinishC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginIdentityC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginModVersionC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginSignatureC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginX25519C2SPayload;
import top.aenp.msec.network.payloads.texture.c2s.LoginTextureMetadataC2SPayload;

public interface MSecServerLoginHandshakeHandler {
    void onVanillaHello(LoginHelloC2SPacket packet);

    void onModVersion(LoginModVersionC2SPayload payload);

    void onClientIdentity(LoginIdentityC2SPayload payload);

    void onClientX25519(LoginX25519C2SPayload payload);

    void onClientSignature(LoginSignatureC2SPayload payload);

    void onTextureMetadata(LoginTextureMetadataC2SPayload payload);

    void onAuthSourceMetadata(LoginAuthSourceResponseC2SPayload payload);

    void onContactFinish(LoginContactFinishC2SPayload payload);

    enum MSecHandshakeState {
        LOGIN_HELLO, VERSION_EXCHANGE, IDENTITY_EXCHANGE, KEY_EXCHANGE, SIGNATURE_EXCHANGE, SOURCE_DETERMINATION, AUTHENTICATING, TEXTURE_METADATA, TEXTURE_SUBMISSION, SUCCESS
    }
}
