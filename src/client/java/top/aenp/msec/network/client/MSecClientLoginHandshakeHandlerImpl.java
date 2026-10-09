package top.aenp.msec.network.client;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.c2s.login.LoginQueryResponseC2SPacket;
import net.minecraft.text.Text;
import org.apache.commons.lang3.Validate;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.auth.MSecEd25519Identity;
import top.aenp.msec.network.MSecAESKeySet;
import top.aenp.msec.network.MSecNetwork;
import top.aenp.msec.network.interfaces.MSecClientLoginHandshakeHandler;
import top.aenp.msec.network.interfaces.SignedPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginIdentityC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginModVersionC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginSignatureC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginX25519C2SPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginIdentityS2CPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginModVersionS2CPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginSignatureS2CPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginX25519S2CPayload;
import top.aenp.mwl.network.v2.MythicNetwork;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginC2SPayload;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.security.*;
import java.util.function.Consumer;

import static top.aenp.msec.network.interfaces.MSecServerLoginHandshakeHandler.MSecHandshakeState;

public class MSecClientLoginHandshakeHandlerImpl implements MSecClientLoginHandshakeHandler {
    private final ClientConnection clientConnection;
    private final Consumer<Text> stateConsumer;
    private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    private volatile MSecHandshakeState state = MSecHandshakeState.VERSION_EXCHANGE;
    private MSecAESKeySet mSecAESKeySet = null;
    private PublicKey serverEd25519PublicKey = null;
    private String serverFingerprint = null;

    public MSecClientLoginHandshakeHandlerImpl(ClientConnection connection, Consumer<Text> stateConsumer) {
        clientConnection = connection;
        this.stateConsumer = stateConsumer;
    }

    @Override
    public void onModVersion(LoginModVersionS2CPayload payload) {
        Validate.validState(state == MSecHandshakeState.VERSION_EXCHANGE, "Unexpected mod version payload!");
        payload.writeMessageAsBytes(byteArrayOutputStream);
        if (payload.serverProtocolVersion() == MSecNetwork.PROTOCOL_VERSION) {
            state = MSecHandshakeState.IDENTITY_EXCHANGE;
            sendPayload(new LoginModVersionC2SPayload(MythicWorldSecurity.MOD_VERSION, MSecNetwork.PROTOCOL_VERSION));
        } else {
            clientConnection.disconnect(Text.of("Incompatible MSec client version: " + payload.serverModVersion()));
        }
    }

    @Override
    public void onServerIdentity(LoginIdentityS2CPayload payload) {
        Validate.validState(state == MSecHandshakeState.IDENTITY_EXCHANGE, "Unexpected server identity payload!");
        payload.writeMessageAsBytes(byteArrayOutputStream);
        InetSocketAddress connectionSocketAddress = (InetSocketAddress) clientConnection.getAddress();
        String serverHostName = connectionSocketAddress.getHostString();
        int serverPort = connectionSocketAddress.getPort();
        //TODO Trust logic.
        serverEd25519PublicKey = payload.serverPublicKey();
        serverFingerprint = MSecEd25519Identity.hashEd25519PublicKey(serverEd25519PublicKey);
        state = MSecHandshakeState.KEY_EXCHANGE;
        sendPayload(new LoginIdentityC2SPayload(MSecEd25519Identity.getInstance().publicKey));
    }

    @Override
    public void onServerX25519(LoginX25519S2CPayload payload) {
        Validate.validState(state == MSecHandshakeState.KEY_EXCHANGE, "Unexpected server key exchange payload!");
        payload.writeMessageAsBytes(byteArrayOutputStream);
        stateConsumer.accept(Text.translatable("connect.encrypting"));
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("X25519");
            keyPairGenerator.initialize(255);
            KeyPair clientX25519KeyPair = keyPairGenerator.generateKeyPair();
            KeyAgreement clientKeyAgreement = KeyAgreement.getInstance("X25519");
            clientKeyAgreement.init(clientX25519KeyPair.getPrivate());
            clientKeyAgreement.doPhase(payload.serverX25519PublicKey(), true);
            byte[] masterSecret = clientKeyAgreement.generateSecret();
            mSecAESKeySet = new MSecAESKeySet(masterSecret, payload.KDSalt());
            state = MSecHandshakeState.SIGNATURE_EXCHANGE;
            sendPayload(new LoginX25519C2SPayload(clientX25519KeyPair.getPublic()));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            clientConnection.disconnect(Text.of("Failed to exchange key."));
            MythicWorldSecurity.LOGGER.error("Key exchange with the server failed: {}", e.toString());
        }
    }

    @Override
    public void onServerSignature(LoginSignatureS2CPayload payload) {
        Validate.validState(state == MSecHandshakeState.SIGNATURE_EXCHANGE, "Unexpected server signature payload!");
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(serverEd25519PublicKey);
            verifier.update(byteArrayOutputStream.toByteArray());
            if (!verifier.verify(payload.serverSignature())) {
                clientConnection.disconnect(Text.of("The MSec signature of the server is INVALID! You may be under MITM attack!"));
                MythicWorldSecurity.LOGGER.error("Server signature is invalid!");
            } else {
                try {
                    MythicWorldSecurity.LOGGER.info("The signature of the server is valid. Fingerprint: {}", serverFingerprint);
                    Cipher decryptionCipher = Cipher.getInstance("AES/CTR/NoPadding");
                    decryptionCipher.init(Cipher.DECRYPT_MODE, mSecAESKeySet.s2cKey, mSecAESKeySet.s2cIV);
                    Cipher encryptionCipher = Cipher.getInstance("AES/CTR/NoPadding");
                    encryptionCipher.init(Cipher.ENCRYPT_MODE, mSecAESKeySet.c2sKey, mSecAESKeySet.c2sIV);
                    LoginSignatureC2SPayload signatureC2SPayload = new LoginSignatureC2SPayload(signTranscript());
                    state = MSecHandshakeState.AUTHENTICATING;
                    clientConnection.send(new LoginQueryResponseC2SPacket(MythicNetwork.QUERY_ID, signatureC2SPayload), PacketCallbacks.always(() -> clientConnection.setupEncryption(decryptionCipher, encryptionCipher)));
                } catch (NoSuchPaddingException | InvalidAlgorithmParameterException  e) {
                    clientConnection.disconnect(Text.of("Failed to setup encryption for you."));
                    MythicWorldSecurity.LOGGER.error("Failed to setup encryption: {}", e.toString());
                }
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            clientConnection.disconnect(Text.of("Failed to validate your signature."));
            MythicWorldSecurity.LOGGER.error("Failed to validate server signature: {}", e.toString());
        }
    }

    private void sendPayload(MythicLoginC2SPayload payload) {
        if (payload instanceof SignedPayload signedPayload) {
            signedPayload.writeMessageAsBytes(byteArrayOutputStream);
        }
        clientConnection.send(new LoginQueryResponseC2SPacket(MythicNetwork.QUERY_ID, payload));
    }

    private byte[] signTranscript() {
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(MSecEd25519Identity.getInstance().privateKey);
            signer.update(byteArrayOutputStream.toByteArray());
            return signer.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            clientConnection.disconnect(Text.of("Failed to sign transcript."));
            MythicWorldSecurity.LOGGER.error("Failed to sign login transcript: {}", e.toString());
            throw new RuntimeException("Failed to sign login transcript: " + e);
        }
    }
}
