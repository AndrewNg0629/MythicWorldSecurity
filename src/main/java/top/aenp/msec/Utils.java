package top.aenp.msec;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.util.UUIDTypeAdapter;
import top.aenp.mwl.misc.MythicReflectionUtils;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public class Utils {
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(GameProfile.class, new GameProfile.Serializer())
            .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
            .registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer())
            .create();
    private static final MessageDigest MESSAGE_DIGEST_SHA1;
    public static final ArrayList<String> EXTRA_DOMAINS = new ArrayList<>();

    static {
        try {
            MESSAGE_DIGEST_SHA1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String hashBytes1(byte[] data) {
        byte[] hash = MESSAGE_DIGEST_SHA1.digest(data);
        return new BigInteger(1, hash).toString(16);
    }

    public static void ensureParents(File file) throws IOException {
        File directory = file.getParentFile();
        if (directory != null && !directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException(String.format("Failed to create parent directories for %s.", file));
            }
        }
    }

    public static boolean isAllowedTextureDomain(final String url) {
        final URI uri;
        try {
            uri = new URI(url).normalize();
        } catch (final URISyntaxException ignored) {
            return false;
        }
        final String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            return false;
        }
        final String domain = uri.getHost();
        if (domain == null) {
            return false;
        }
        final String decodedDomain = IDN.toUnicode(domain);
        final String lowerCaseDomain = decodedDomain.toLowerCase(Locale.ROOT);
        if (!lowerCaseDomain.equals(decodedDomain)) {
            return false;
        }
        for (final String entry : EXTRA_DOMAINS) {
            if (decodedDomain.endsWith(entry)) {
                return true;
            }
        }
        return false;
    }
    public static HttpURLConnection followHttpRedirects(HttpURLConnection connection, Proxy proxy) throws IOException {
        HttpURLConnection newConnection = null;
        int redirectionCount = 0;
        try {
            while (isRedirect(connection.getResponseCode())) {
                if (redirectionCount > 10) {
                    throw new IOException("Too many redirects!");
                }
                URL newUrl = URL.of(new URI(connection.getHeaderField("Location")), null);
                connection.disconnect();
                newConnection = (HttpURLConnection) (proxy != null ? newUrl.openConnection(proxy) : newUrl.openConnection());
                newConnection.setDoOutput(false);
                newConnection.setDoInput(true);
                newConnection.connect();
                connection = newConnection;
                redirectionCount++;
            }
            return connection;
        } catch (URISyntaxException | IOException e) {
            if (newConnection != null) {
                newConnection.disconnect();
            }
            throw new IOException("Failed to follow redirects!", e);
        }
    }

    private static boolean isRedirect(int responseCode) {
        return responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_SEE_OTHER;
    }
}
