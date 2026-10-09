package top.aenp.msec.network;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.response.MinecraftTexturesPayload;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.s2c.login.LoginQueryRequestS2CPacket;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Uuids;
import org.apache.commons.lang3.Validate;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.Utils;
import top.aenp.msec.auth.MSecEd25519Identity;
import top.aenp.msec.network.interfaces.MSecServerLoginHandshakeHandler;
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
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginS2CPayload;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public class MSecServerLoginHandshakeHandlerImpl implements MSecServerLoginHandshakeHandler {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final ClientConnection clientConnection;
    private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    private final ServerLoginNetworkHandler serverLoginNetworkHandler;
    private final byte[] encryptionSalt;
    private final String loginApiNonce;
    private volatile MSecHandshakeState state = MSecHandshakeState.LOGIN_HELLO;
    private PublicKey clientEd25519PublicKey = null;
    private String clientFingerprint = null;
    private KeyPair serverX25519Keypair = null;
    private MSecAESKeySet mSecAESKeySet = null;
    private String profileName = null;
    private UUID profileUuid = null;

    public MSecServerLoginHandshakeHandlerImpl(ClientConnection connection, ServerLoginNetworkHandler handler) {
        clientConnection = connection;
        serverLoginNetworkHandler = handler;
        encryptionSalt = new byte[32];
        SECURE_RANDOM.nextBytes(encryptionSalt);
        byte[] loginApiNonceBytes = new byte[20];
        SECURE_RANDOM.nextBytes(loginApiNonceBytes);
        loginApiNonce = new BigInteger(1, loginApiNonceBytes).toString(16);
    }

    @Override
    public void onVanillaHello(LoginHelloC2SPacket packet) {
        profileName = packet.name();
        profileUuid = packet.profileId();
        state = MSecHandshakeState.VERSION_EXCHANGE;
        sendPayload(new LoginModVersionS2CPayload(MythicWorldSecurity.MOD_VERSION, MSecNetwork.PROTOCOL_VERSION));
    }

    @Override
    public void onModVersion(LoginModVersionC2SPayload payload) {
        Validate.validState(state == MSecHandshakeState.VERSION_EXCHANGE, "Unexpected mod version payload!");
        payload.writeMessageAsBytes(byteArrayOutputStream);
        if (payload.clientProtocolVersion() == MSecNetwork.PROTOCOL_VERSION) {
            state = MSecHandshakeState.IDENTITY_EXCHANGE;
            sendPayload(new LoginIdentityS2CPayload(MSecEd25519Identity.getInstance().publicKey, Set.of()));
        } else {
            serverLoginNetworkHandler.disconnect(Text.of("Incompatible MSec client version: " + payload.clientModVersion()));
        }
    }

    @Override
    public void onClientIdentity(LoginIdentityC2SPayload payload) {
        Validate.validState(state == MSecHandshakeState.IDENTITY_EXCHANGE, "Unexpected client identity payload!");
        payload.writeMessageAsBytes(byteArrayOutputStream);
        clientEd25519PublicKey = payload.clientPublicKey();
        clientFingerprint = MSecEd25519Identity.hashEd25519PublicKey(clientEd25519PublicKey);
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("X25519");
            keyPairGenerator.initialize(255);
            serverX25519Keypair = keyPairGenerator.generateKeyPair();
            state = MSecHandshakeState.KEY_EXCHANGE;
            sendPayload(new LoginX25519S2CPayload(serverX25519Keypair.getPublic(), encryptionSalt));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onClientX25519(LoginX25519C2SPayload payload) {
        Validate.validState(state == MSecHandshakeState.KEY_EXCHANGE, "Unexpected client key exchange payload!");
        payload.writeMessageAsBytes(byteArrayOutputStream);
        try {
            KeyAgreement serverKeyAgreement = KeyAgreement.getInstance("X25519");
            serverKeyAgreement.init(serverX25519Keypair.getPrivate());
            serverKeyAgreement.doPhase(payload.clientX25519PublicKey(), true);
            byte[] masterSecret = serverKeyAgreement.generateSecret();
            mSecAESKeySet = new MSecAESKeySet(masterSecret, encryptionSalt);
            state = MSecHandshakeState.SIGNATURE_EXCHANGE;
            sendPayload(new LoginSignatureS2CPayload(signTranscript()));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            serverLoginNetworkHandler.disconnect(Text.of("Failed to exchange key."));
            MythicWorldSecurity.LOGGER.error("Key exchange with {} failed: {}", profileName, e.toString());
        }
    }

    @Override
    public void onClientSignature(LoginSignatureC2SPayload payload) {
        Validate.validState(state == MSecHandshakeState.SIGNATURE_EXCHANGE, "Unexpected client signature payload!");
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(clientEd25519PublicKey);
            verifier.update(byteArrayOutputStream.toByteArray());
            if (!verifier.verify(payload.clientSignature())) {
                clientConnection.disconnect(Text.of("Invalid signature"));
                MythicWorldSecurity.LOGGER.error("Client signature of {} is invalid!", profileName);
            } else {
                try {
                    MythicWorldSecurity.LOGGER.info("The client signature of {} is valid. Fingerprint: {}", profileName, clientFingerprint);
                    Cipher decryptionCipher = Cipher.getInstance("AES/CTR/NoPadding");
                    decryptionCipher.init(Cipher.DECRYPT_MODE, mSecAESKeySet.c2sKey, mSecAESKeySet.c2sIV);
                    Cipher encryptionCipher = Cipher.getInstance("AES/CTR/NoPadding");
                    encryptionCipher.init(Cipher.ENCRYPT_MODE, mSecAESKeySet.s2cKey, mSecAESKeySet.s2cIV);
                    clientConnection.setupEncryption(decryptionCipher, encryptionCipher);
                    state = MSecHandshakeState.AUTHENTICATING;
                } catch (NoSuchPaddingException | InvalidAlgorithmParameterException  e) {
                    serverLoginNetworkHandler.disconnect(Text.of("Failed to setup encryption for you."));
                    MythicWorldSecurity.LOGGER.error("Failed to setup encryption for {}: {}", profileName, e.toString());
                }

                serverLoginNetworkHandler.mythicworldsecurity$startVerify(constructTestProfile());
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            serverLoginNetworkHandler.disconnect(Text.of("Failed to validate your signature."));
            MythicWorldSecurity.LOGGER.error("Failed to validate client signature of {}: {}", profileName, e.toString());
        }
    }

    //TODO Auth logic.

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
            throw new RuntimeException("Failed to sign login transcript: " + e);
        }
    }

    //Test method
    private GameProfile constructTestProfile() {
        GameProfile gameProfile = new GameProfile(Uuids.getOfflinePlayerUuid(profileName), profileName + "_test");
        HashMap<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = new HashMap<>();
        MinecraftProfileTexture skinTexture = new MinecraftProfileTexture(MSecNetwork.TEXTURE_DUMMY_URL_PREFIX + "49d53d3894c4bf776518b384ced33f56e1d78a19", null);
        //MinecraftProfileTexture skinTexture = new MinecraftProfileTexture(MSecNetwork.TEXTURE_DUMMY_URL_PREFIX + "49d53d3894c4bf776518b384ced33f56e1d78a19", Map.of("model", "slim"));
        MinecraftProfileTexture capeTexture = new MinecraftProfileTexture(MSecNetwork.TEXTURE_DUMMY_URL_PREFIX + "ed1cb191817b61be89acebd65e94a98d651119b8", null);
        textures.put(MinecraftProfileTexture.Type.SKIN, skinTexture);
        textures.put(MinecraftProfileTexture.Type.CAPE, capeTexture);
        MinecraftTexturesPayload payload = new MinecraftTexturesPayload(
                System.currentTimeMillis(),
                gameProfile.getId(),
                gameProfile.getName(),
                false,
                textures
        );
        String rewrittenPayload = Utils.GSON.toJson(payload);
        String encodedRewrittenPayload = Base64.getEncoder().encodeToString(rewrittenPayload.getBytes(StandardCharsets.UTF_8));
        gameProfile.getProperties().put("textures", new Property("textures", encodedRewrittenPayload));
        return gameProfile;
    }
}
