package top.aenp.msec.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.response.HasJoinedMinecraftServerResponse;
import com.mojang.util.UUIDTypeAdapter;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.s2c.login.LoginQueryRequestS2CPacket;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.text.Text;
import org.apache.commons.lang3.Validate;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.Utils;
import top.aenp.msec.auth.AuthSourceSuffixInfo;
import top.aenp.msec.auth.UUIDBySourceRepository;
import top.aenp.msec.auth.YggdrasilApiMetadata;
import top.aenp.msec.config.ServerConfigManager;
import top.aenp.msec.network.interfaces.MSecServerLoginHandshakeHandler;
import top.aenp.msec.network.interfaces.SignedPayload;
import top.aenp.msec.network.payloads.auth.c2s.LoginAuthSourceResponseC2SPayload;
import top.aenp.msec.network.payloads.auth.c2s.LoginContactFinishC2SPayload;
import top.aenp.msec.network.payloads.auth.s2c.LoginAllowedSkinDomainsS2CPayload;
import top.aenp.msec.network.payloads.auth.s2c.LoginAuthSourceRequestS2CPayload;
import top.aenp.msec.network.payloads.auth.s2c.LoginContactEndpointS2CPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginIdentityC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginModVersionC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginSignatureC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginX25519C2SPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginIdentityS2CPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginModVersionS2CPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginSignatureS2CPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginX25519S2CPayload;
import top.aenp.msec.network.payloads.texture.c2s.LoginTextureMetadataC2SPayload;
import top.aenp.msec.trust.MSecEd25519Identity;
import top.aenp.mwl.network.v2.MythicNetwork;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginS2CPayload;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Locale;
import java.util.UUID;

public class MSecServerLoginHandshakeHandlerImpl implements MSecServerLoginHandshakeHandler {
    public static final Gson PROFILE_GSON = new GsonBuilder()
            .registerTypeAdapter(GameProfile.class, new GameProfile.Serializer())
            .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
            .registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer())
            .create();
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
    private AuthSourceSuffixInfo sourceSuffixInfo = null;
    private String checkJoinHost = null;
    private String apiConfigName = null;

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
        Validate.validState(state == MSecHandshakeState.LOGIN_HELLO, "Unexpected login hello packet!");
        profileName = packet.name();
        if (ServerConfigManager.getConfig().bootstrapConfig().mojangAuthSourceConfig().enabled() && ServerConfigManager.getInstance().getMojangSuffixInfo().isSuffixed(profileName)) {
            serverLoginNetworkHandler.disconnect(Text.of("Your player name contains defined source suffix!"));
            return;
        }
        if (ServerConfigManager.getConfig().bootstrapConfig().offlineAuthSourceConfig().enabled() && ServerConfigManager.getInstance().getOfflineSuffixInfo().isSuffixed(profileName)) {
            serverLoginNetworkHandler.disconnect(Text.of("Your player name contains defined source suffix!"));
            return;
        }
        for (AuthSourceSuffixInfo info : ServerConfigManager.getInstance().getYggdrasilSuffixInfoMap().values()) {
            if (info.isSuffixed(profileName)) {
                serverLoginNetworkHandler.disconnect(Text.of("Your player name contains defined source suffix!"));
                return;
            }
        }
        state = MSecHandshakeState.VERSION_EXCHANGE;
        sendPayload(new LoginModVersionS2CPayload(MythicWorldSecurity.MOD_VERSION, MSecNetwork.PROTOCOL_VERSION));
    }

    @Override
    public void onModVersion(LoginModVersionC2SPayload payload) {
        Validate.validState(state == MSecHandshakeState.VERSION_EXCHANGE, "Unexpected mod version payload!");
        payload.writeMessageAsBytes(byteArrayOutputStream);
        if (payload.clientProtocolVersion() == MSecNetwork.PROTOCOL_VERSION) {
            state = MSecHandshakeState.IDENTITY_EXCHANGE;
            sendPayload(new LoginIdentityS2CPayload(MSecEd25519Identity.getInstance().publicKey,
                            ServerConfigManager.getConfig().mutableConfig().dnsHttpsConfig().applicableAddresses().stream().map(address -> address.split(":")[0]).toList(),
                            ServerConfigManager.getConfig().mutableConfig().directHttpsConfig().applicableAddresses().stream().map(address -> address.split(":")[0]).toList()
                    )
            );
            serverLoginNetworkHandler.mythicworldsecurity$setWaitingForTrust(true);
        } else {
            serverLoginNetworkHandler.disconnect(Text.of("Incompatible MSec client version: " + payload.clientModVersion()));
        }
    }

    @Override
    public void onClientIdentity(LoginIdentityC2SPayload payload) {
        Validate.validState(state == MSecHandshakeState.IDENTITY_EXCHANGE, "Unexpected client identity payload!");
        payload.writeMessageAsBytes(byteArrayOutputStream);
        serverLoginNetworkHandler.mythicworldsecurity$setWaitingForTrust(false);
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
                } catch (NoSuchPaddingException | InvalidAlgorithmParameterException e) {
                    serverLoginNetworkHandler.disconnect(Text.of("Failed to setup encryption for you."));
                    MythicWorldSecurity.LOGGER.error("Failed to setup encryption for {}: {}", profileName, e.toString());
                }
                state = MSecHandshakeState.SOURCE_DETERMINATION;
                sendPayload(new LoginAuthSourceRequestS2CPayload());
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            serverLoginNetworkHandler.disconnect(Text.of("Failed to validate your signature."));
            MythicWorldSecurity.LOGGER.error("Failed to validate client signature of {}: {}", profileName, e.toString());
        }
    }

    @SuppressWarnings("resource")
    @Override
    public void onAuthSourceMetadata(LoginAuthSourceResponseC2SPayload payload) {
        Validate.validState(state == MSecHandshakeState.SOURCE_DETERMINATION, "Unexpected auth source metadata payload!");
        if (payload.authSourceType() == LoginAuthSourceResponseC2SPayload.AuthSourceType.OFFLINE || !serverLoginNetworkHandler.mythicworldsecurity$getServer().isOnlineMode()) {
            loginViaOffline();
        } else {
            String suffixExemptedName = ServerConfigManager.getInstance().getSuffixExemptedSourceName();
            if (payload.authSourceType() == LoginAuthSourceResponseC2SPayload.AuthSourceType.MOJANG && ServerConfigManager.getConfig().bootstrapConfig().mojangAuthSourceConfig().enabled()) {
                apiConfigName = "mojang";
                checkJoinHost = Utils.MOJANG_SESSION_SERVER + "/session/minecraft/hasJoined";
                if (!suffixExemptedName.equalsIgnoreCase("mojang")) {
                    sourceSuffixInfo = ServerConfigManager.getInstance().getMojangSuffixInfo();
                }
            } else {
                YggdrasilApiMetadata metadata = payload.yggdrasilApiMetadata();
                YggdrasilApiMetadata matchingMetadata = null;
                for (YggdrasilApiMetadata metadataInKeys : ServerConfigManager.getInstance().getYggdrasilConfigNameMap().keySet()) {
                    if (YggdrasilApiMetadata.equals(metadata, metadataInKeys)) {
                        matchingMetadata = metadataInKeys;
                    }
                }
                if (matchingMetadata != null) {
                    apiConfigName = ServerConfigManager.getInstance().getYggdrasilConfigNameMap().get(matchingMetadata).toLowerCase(Locale.ROOT);
                    String apiRootUrl = matchingMetadata.apiRootUrl();
                    if (!apiRootUrl.endsWith("/")) {
                        apiRootUrl = apiRootUrl + "/";
                    }
                    checkJoinHost = apiRootUrl + "sessionserver/session/minecraft/hasJoined";
                    if (!suffixExemptedName.equalsIgnoreCase(apiConfigName)) {
                        sourceSuffixInfo = ServerConfigManager.getInstance().getYggdrasilSuffixInfoMap().get(matchingMetadata);
                    }
                } else {
                    loginViaOffline();
                    return;
                }
            }
            state = MSecHandshakeState.AUTHENTICATING;
            sendPayload(new LoginContactEndpointS2CPayload(loginApiNonce));
        }
    }

    private void loginViaOffline() {
        //TODO Offline logic.
        serverLoginNetworkHandler.disconnect(Text.of("Offline not implemented."));
    }

    @Override
    public void onContactFinish(LoginContactFinishC2SPayload payload) {
        Validate.validState(state == MSecHandshakeState.AUTHENTICATING, "Unexpected contact finish payload!");
        Runnable verifyTask = () -> {
            try {
                URI requestUri = new URI(checkJoinHost + "?username=" + profileName + "&serverId=" + loginApiNonce);
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(requestUri)
                        .GET()
                        .build();
                HttpResponse<String> httpResponse = MSecNetwork.getHttpClient(null).send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int responseCode = httpResponse.statusCode();
                if (!(responseCode >= 200 && responseCode < 400)) {
                    onAuthenticationFail();
                    return;
                }
                HasJoinedMinecraftServerResponse apiResponse = PROFILE_GSON.fromJson(httpResponse.body(), HasJoinedMinecraftServerResponse.class);
                if (apiResponse != null && apiResponse.id() != null && apiResponse.properties() != null) {
                    UUID fetchedUUID = apiResponse.id();
                    UUID mappedUUID = UUIDBySourceRepository.getInstance().mapUUID(apiConfigName, fetchedUUID);
                    if (mappedUUID != null) {
                        String suffixedName = profileName;
                        if (sourceSuffixInfo != null) {
                            suffixedName = suffixedName + sourceSuffixInfo.primarySuffix();
                            MythicWorldSecurity.LOGGER.info("Player {} has been suffixed as {}", profileName, suffixedName);
                        }
                        MythicWorldSecurity.LOGGER.info("UUID of player {} is {}", suffixedName, mappedUUID);
                        GameProfile rewrittenProfile = new GameProfile(mappedUUID, suffixedName);
                        rewrittenProfile.getProperties().putAll(apiResponse.properties());
                        state = MSecHandshakeState.SUCCESS;
                        sendPayload(new LoginAllowedSkinDomainsS2CPayload(ServerConfigManager.getInstance().getAllSkinDomains()));
                        serverLoginNetworkHandler.mythicworldsecurity$startVerify(rewrittenProfile);
                    } else {
                        onAuthenticationFail();
                    }
                } else {
                    onAuthenticationFail();
                }
            } catch (Exception e) {
                serverLoginNetworkHandler.disconnect(Text.translatable("multiplayer.disconnect.authservers_down"));
                MythicWorldSecurity.LOGGER.error("Couldn't verify username because servers are unavailable.", e);
            }
        };
        Thread.startVirtualThread(verifyTask).setName("MSec Player Authenticator");
    }

    @Override
    public void onTextureMetadata(LoginTextureMetadataC2SPayload payload) {
        Validate.validState(state == MSecHandshakeState.TEXTURE_METADATA, "Unexpected texture metadata payload!");
        //TODO
    }

    private void onAuthenticationFail() {
        serverLoginNetworkHandler.disconnect(Text.translatable("multiplayer.disconnect.unverified_username"));
        MythicWorldSecurity.LOGGER.error("Failed to authenticate player '{}'", profileName);
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
            throw new RuntimeException("Failed to sign login transcript: " + e);
        }
    }
}
