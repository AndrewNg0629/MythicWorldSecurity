package top.aenp.msec.network;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.s2c.login.LoginQueryRequestS2CPacket;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.text.Text;
import org.apache.commons.lang3.Validate;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.auth.MSecEd25519Identity;
import top.aenp.msec.network.interfaces.SignedPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginIdentityC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginModVersionC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginSignatureC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginX25519C2SPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginModVersionS2CPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginSignatureS2CPayload;
import top.aenp.mwl.network.v2.MythicNetwork;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginS2CPayload;

import java.io.ByteArrayOutputStream;
import java.security.*;
import java.util.UUID;

public class MSecLoginHandshakeHandler {
    private final ClientConnection clientConnection;
    private volatile MSecHandshakeStates state = MSecHandshakeStates.LOGIN_HELLO;
    private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    private final ServerLoginNetworkHandler serverLoginNetworkHandler;
    private PublicKey clientPublicKey = null;
    private String clientFingerprint = null;
    private String profileName = null;
    private UUID profileUuid = null;

    public MSecLoginHandshakeHandler(ClientConnection connection, ServerLoginNetworkHandler handler) {
        clientConnection = connection;
        serverLoginNetworkHandler = handler;
    }
    private enum MSecHandshakeStates {
        LOGIN_HELLO, VERSION_EXCHANGE, IDENTITY_EXCHANGE, KEY_EXCHANGE, SIGNATURE_EXCHANGE, AUTHENTICATING, TEXTURE, SUCCESS
    }

    public void onVanillaHello(LoginHelloC2SPacket packet) {
        Validate.validState(state == MSecHandshakeStates.LOGIN_HELLO, "Unexpected hello packet!");
        profileName = packet.name();
        profileUuid = packet.profileId();
        sendPayload(new LoginModVersionS2CPayload(MythicWorldSecurity.MOD_VERSION, MSecNetwork.PROTOCOL_VERSION));
        state = MSecHandshakeStates.VERSION_EXCHANGE;
    }

    public void onModVersion(LoginModVersionC2SPayload payload) {
        payload.writeMessageAsBytes(byteArrayOutputStream);
        //TODO
    }

    public void onClientIdentity(LoginIdentityC2SPayload payload) {
        payload.writeMessageAsBytes(byteArrayOutputStream);
        clientPublicKey = payload.clientPublicKey();
        clientFingerprint = MSecEd25519Identity.hashPublicKey(clientPublicKey);

        //TODO
    }

    public void onClientX25519(LoginX25519C2SPayload payload) {
        payload.writeMessageAsBytes(byteArrayOutputStream);
        //TODO
        sendPayload(new LoginSignatureS2CPayload(signTranscript()));
    }

    public void onClientSignature(LoginSignatureC2SPayload payload) {
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(clientPublicKey);
            verifier.update(byteArrayOutputStream.toByteArray());
            if (!verifier.verify(payload.clientSignature())) {
                serverLoginNetworkHandler.disconnect(Text.of("Your signature is INVALID!\nYou may be under MITM attack!"));
                MythicWorldSecurity.LOGGER.error("Client signature of {} is invalid!", profileName);
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException  e) {
            serverLoginNetworkHandler.disconnect(Text.of("Failed to validate your signature."));
            MythicWorldSecurity.LOGGER.error("Failed to validate client signature of {}: {}", profileName, e.toString());
        }
    }

    private void sendPayload(MythicLoginS2CPayload payload) {
        if (payload instanceof SignedPayload signedPayload) {
            signedPayload.writeMessageAsBytes(byteArrayOutputStream);
        }
        clientConnection.send(new LoginQueryRequestS2CPacket(MythicNetwork.QUERY_ID, payload));
    }

    private byte[] signTranscript() {
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(MSecEd25519Identity.getInstance().privateKey);
            signer.update(byteArrayOutputStream.toByteArray());
            return signer.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            serverLoginNetworkHandler.disconnect(Text.of("Failed to sign your transcript."));
            MythicWorldSecurity.LOGGER.error("Failed to sign login transcript: {}", e.toString());
            throw new RuntimeException(e);
        }
    }
}
