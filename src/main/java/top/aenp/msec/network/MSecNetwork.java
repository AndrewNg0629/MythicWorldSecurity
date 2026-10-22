package top.aenp.msec.network;

import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.network.payloads.LoginHeartbeatC2SPayload;
import top.aenp.msec.network.payloads.LoginHeartbeatS2CPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginIdentityC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginModVersionC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginSignatureC2SPayload;
import top.aenp.msec.network.payloads.crypto.c2s.LoginX25519C2SPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginIdentityS2CPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginModVersionS2CPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginSignatureS2CPayload;
import top.aenp.msec.network.payloads.crypto.s2c.LoginX25519S2CPayload;
import top.aenp.msec.network.payloads.texture.*;
import top.aenp.msec.trust.TrustUtils;
import top.aenp.mwl.network.v2.MythicNetwork;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.*;

public class MSecNetwork {
    public static final ArrayList<String> EXTRA_ALLOWED_DOMAINS = new ArrayList<>();
    public static final String MSEC_DUMMY_DOMAIN = "msec-placeholder-domain.invalid";
    public static final String TEXTURE_DUMMY_URL_PREFIX = "https://" + MSEC_DUMMY_DOMAIN + "/offline-player-texture/";
    public static final int PROTOCOL_VERSION = 0;
    private static final KeyFactory X25519_KEY_FACTORY;
    private static boolean initialized = false;

    static {
        try {
            X25519_KEY_FACTORY = KeyFactory.getInstance("X25519");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

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
            MythicNetwork.INSTANCE.LOGIN_S2C_CODECS.put(LoginHeartbeatS2CPayload.ID, LoginHeartbeatS2CPayload.CODEC);
            MythicNetwork.INSTANCE.LOGIN_C2S_CODECS.put(LoginHeartbeatC2SPayload.ID, LoginHeartbeatC2SPayload.CODEC);
            MSecNetwork.EXTRA_ALLOWED_DOMAINS.add(MSecNetwork.MSEC_DUMMY_DOMAIN);
            initialized = true;
        } else {
            throw new IllegalStateException("MSecNetwork has already been initialized!");
        }
    }

    public static PublicKey decodeX25519PublicKey(byte[] encodedPublicKey) {
        try {
            return X25519_KEY_FACTORY.generatePublic(new X509EncodedKeySpec(encodedPublicKey));
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException("Broken X25519 public key.", e);
        }
    }

    public static boolean isAllowedTextureDomain(String url) {
        URI uri;
        try {
            uri = new URI(url).normalize();
        } catch (URISyntaxException ignored) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            return false;
        }
        String domain = uri.getHost();
        if (domain == null) {
            return false;
        }
        String decodedDomain = IDN.toUnicode(domain);
        String lowerCaseDomain = decodedDomain.toLowerCase(Locale.ROOT);
        if (!lowerCaseDomain.equals(decodedDomain)) {
            return false;
        }
        for (String entry : EXTRA_ALLOWED_DOMAINS) {
            if (decodedDomain.endsWith(entry)) {
                return true;
            }
        }
        return false;
    }

    public static <R> R fetchIOWithRetries(IOFetchOperation<R> operation, String name, int maxRetries) throws IOException {
        for (int i = 1; i <= maxRetries; i++) {
            try {
                return operation.fetch();
            } catch (IOException e) {
                MythicWorldSecurity.LOGGER.error("Attempt #{} to fetch {} failed!", i, name, e);
                try {
                    Thread.sleep(1000L * i);
                } catch (InterruptedException e1) {
                    throw new RuntimeException("Thread terminated during fetch.", e);
                }
            }
        }
        throw new IOException(String.format("Failed to fetch %s after %d retries.", name, maxRetries));
    }

    @FunctionalInterface
    public interface IOFetchOperation<R> {
        R fetch() throws IOException;
    }
}
