package top.aenp.msec.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.network.payloads.LoginHeartbeatC2SPayload;
import top.aenp.msec.network.payloads.LoginHeartbeatS2CPayload;
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
import top.aenp.msec.network.payloads.texture.c2s.LoginTextureDataC2SPayload;
import top.aenp.msec.network.payloads.texture.c2s.LoginTextureMetadataC2SPayload;
import top.aenp.msec.network.payloads.texture.c2s.PlayTextureRequestC2SPayload;
import top.aenp.msec.network.payloads.texture.s2c.LoginTextureCommandS2CPayload;
import top.aenp.msec.network.payloads.texture.s2c.PlayTextureResponseS2CPayload;
import top.aenp.mwl.network.v2.MythicNetwork;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class MSecNetwork {
    public static final HashSet<String> EXTRA_ALLOWED_DOMAINS = new HashSet<>();
    public static final String MSEC_DUMMY_DOMAIN = "msec-placeholder-domain.invalid";
    public static final String TEXTURE_DUMMY_URL_PREFIX = "https://" + MSEC_DUMMY_DOMAIN + "/offline-player-texture/";
    public static final int PROTOCOL_VERSION = 0;
    public static final Codec<Character> SEPARATOR_CODEC;
    public static final Codec<String> PLAYER_NAME_COMPONENT_CODEC;
    public static final Codec<Set<String>> PLAYER_NAME_COMPONENT_SET_CODEC;
    public static final Pattern VALID_PLAYERNAME_PATTERN;
    private static final KeyFactory X25519_KEY_FACTORY;
    private static final HttpClient PROXYLESS_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15L))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ConcurrentHashMap<Proxy, HttpClient> HTTP_CLIENT_MAP = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    static {
        VALID_PLAYERNAME_PATTERN = Pattern.compile("^[\\p{L}\\p{Nd}._+\\-]+$");
        SEPARATOR_CODEC = Codec.STRING.comapFlatMap(
                string -> switch (string) {
                    case "." -> DataResult.success('.');
                    case "-" -> DataResult.success('-');
                    case "+" -> DataResult.success('+');
                    case "_" -> DataResult.success('_');
                    default -> DataResult.error(() -> "Illegal separator char: " + string);
                },
                String::valueOf
        );
        PLAYER_NAME_COMPONENT_CODEC = Codec.STRING.comapFlatMap(
                string -> {
                    if (VALID_PLAYERNAME_PATTERN.matcher(string).matches()) {
                        return DataResult.success(string);
                    } else {
                        return DataResult.error(() -> "Illegal player name component: " + string);
                    }
                },
                string -> string
        );
        PLAYER_NAME_COMPONENT_SET_CODEC = PLAYER_NAME_COMPONENT_CODEC.listOf().xmap(Set::copyOf, List::copyOf);
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
            MythicNetwork.INSTANCE.LOGIN_S2C_CODECS.put(LoginAuthSourceRequestS2CPayload.ID, LoginAuthSourceRequestS2CPayload.CODEC);
            MythicNetwork.INSTANCE.LOGIN_C2S_CODECS.put(LoginAuthSourceResponseC2SPayload.ID, LoginAuthSourceResponseC2SPayload.CODEC);
            MythicNetwork.INSTANCE.LOGIN_S2C_CODECS.put(LoginContactEndpointS2CPayload.ID, LoginContactEndpointS2CPayload.CODEC);
            MythicNetwork.INSTANCE.LOGIN_C2S_CODECS.put(LoginContactFinishC2SPayload.ID, LoginContactFinishC2SPayload.CODEC);
            MythicNetwork.INSTANCE.LOGIN_S2C_CODECS.put(LoginAllowedSkinDomainsS2CPayload.ID, LoginAllowedSkinDomainsS2CPayload.CODEC);
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
        IOException ioException = null;
        for (int i = 1; i <= maxRetries; i++) {
            try {
                return operation.fetch();
            } catch (IOException e) {
                ioException = e;
                MythicWorldSecurity.LOGGER.error("Attempt #{} to fetch {} failed!", i, name, e);
                if (i < maxRetries) {
                    try {
                        Thread.sleep(1000L * i);
                    } catch (InterruptedException e1) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Thread terminated during fetch.", e);
                    }
                }
            }
        }
        throw new IOException(String.format("Failed to fetch %s after %d retries.", name, maxRetries), ioException);
    }

    public synchronized static HttpClient getHttpClient(Proxy proxy) {
        if (proxy == null) {
            return PROXYLESS_HTTP_CLIENT;
        } else {
            return HTTP_CLIENT_MAP.computeIfAbsent(proxy,
                    p -> HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(15L))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .proxy(new ProxySelector() {
                                @Override
                                public List<Proxy> select(URI uri) {
                                    return List.of(p);
                                }

                                @Override
                                public void connectFailed(URI uri, SocketAddress socketAddress, IOException e) {
                                }
                            })
                            .build()
            );
        }
    }

    @FunctionalInterface
    public interface IOFetchOperation<R> {
        R fetch() throws IOException;
    }
}
