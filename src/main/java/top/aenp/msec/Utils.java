package top.aenp.msec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.util.UUIDTypeAdapter;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

public class Utils {
    public static final Gson PROFILE_GSON = new GsonBuilder()
            .registerTypeAdapter(GameProfile.class, new GameProfile.Serializer())
            .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
            .registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer())
            .create();

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss O");

    public static final Codec<ZonedDateTime> DATE_TIME_CODEC = Codec.STRING.comapFlatMap(
            string -> {
                try {
                    ZonedDateTime zonedDateTime = ZonedDateTime.parse(string, DATE_TIME_FORMATTER);
                    return DataResult.success(zonedDateTime);
                } catch (DateTimeParseException e) {
                    return DataResult.error(e::getMessage);
                }
            },
            zonedDateTime -> zonedDateTime.format(DATE_TIME_FORMATTER)
    );

    public static final Codec<String> VALIDATED_ADDR_STRING_CODEC;

    private static final MessageDigest MESSAGE_DIGEST_SHA1;

    static {
        try {
            MESSAGE_DIGEST_SHA1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        Function<String, DataResult<String>> hostStringNormalizer = string -> {
            String hostname;
            int port = 25565;
            String[] segments = string.split(":");
            switch (segments.length) {
                case 1 :
                    hostname = segments[0].toLowerCase(Locale.ROOT);
                    break;
                case 2 :
                    hostname = segments[0].toLowerCase(Locale.ROOT);
                    try {
                        port = Integer.parseInt(segments[1]);
                    } catch (NumberFormatException e) {
                        return DataResult.error(() -> String.format("Port number cannot be parsed: %s", segments[1]));
                    }
                    if (port < 0 || port > 65535) {
                        int illegalPort = port;
                        return DataResult.error(() -> String.format("Port number is out of range: %d", illegalPort));
                    }
                    break;
                default:
                    return DataResult.error(() -> String.format("Illegal host string: %s", string));
            }
            return DataResult.success(hostname + ":" + port);
        };
        VALIDATED_ADDR_STRING_CODEC = Codec.STRING.flatXmap(hostStringNormalizer, hostStringNormalizer);
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
