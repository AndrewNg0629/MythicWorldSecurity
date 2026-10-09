package top.aenp.msec.network.interfaces;

import top.aenp.msec.network.payloads.crypto.s2c.LoginIdentityS2CPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginModVersionS2CPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginSignatureS2CPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginX25519S2CPayload;

public interface MSecClientLoginHandshakeHandler {
    void onModVersion(LoginModVersionS2CPayload payload);
    void onServerIdentity(LoginIdentityS2CPayload payload);
    void onServerX25519(LoginX25519S2CPayload payload);
    void onServerSignature(LoginSignatureS2CPayload payload);
}
