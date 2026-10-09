package top.aenp.msec;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.util.UUIDTypeAdapter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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

    public static byte[] limitedRead(InputStream inputStream, int sizeLimit) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            long totalBytes = 0L;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if ((totalBytes += bytesRead) > sizeLimit) {
                    throw new IOException(String.format("Input is larger than limit: %d Bytes.", sizeLimit));
                }
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            return byteArrayOutputStream.toByteArray();
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

    public static int[] packByteArray(byte[] byteArray) {
        if (byteArray.length % 4 != 0) {
            return new int[0];
        }
        int intArrayLength = byteArray.length / 4;
        int[] intArray = new int[intArrayLength];
        for (int a = 0; a < intArrayLength; a++) {
            intArray[a] = ((byteArray[a * 4] & 0xff) << 24) | ((byteArray[a * 4 + 1] & 0xff) << 16) | ((byteArray[a * 4 + 2] & 0xff) << 8) | (byteArray[a * 4 + 3] & 0xff);
        }
        return intArray;
    }

    public static byte[] unpackIntArray(int[] intArray) {
        byte[] byteArray = new byte[intArray.length * 4];
        for (int a = 0; a < intArray.length; a++) {
            byteArray[a * 4] = (byte) (intArray[a] >>> 24 & 0xff);
            byteArray[a * 4 + 1] = (byte) (intArray[a] >>> 16 & 0xff);
            byteArray[a * 4 + 2] = (byte) (intArray[a] >>> 8 & 0xff);
            byteArray[a * 4 + 3] = (byte) (intArray[a] & 0xff);
        }
        return byteArray;
    }

    public static byte[] compressBufferedImage(BufferedImage bufferedImage) throws IOException {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        int[] intPixelData = bufferedImage.getRGB(0, 0, width, height, null, 0, width);
        byte[] bytePixelData = unpackIntArray(intPixelData);
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)
        ) {
            zipOutputStream.setMethod(ZipOutputStream.DEFLATED);
            zipOutputStream.setLevel(Deflater.DEFAULT_COMPRESSION);
            zipOutputStream.putNextEntry(new ZipEntry("pixels"));
            zipOutputStream.write(bytePixelData);
            zipOutputStream.closeEntry();
            return byteArrayOutputStream.toByteArray();
        }
    }

    public static BufferedImage decompressTexture(byte[] compressedPixels, int width, int height, int maxWidth, int maxHeight) throws IOException {
        if (width > maxWidth || height > maxHeight) {
            throw new IOException(String.format("Texture size %d*%d exceeds limit %d*%d.", width, height, maxWidth, maxHeight));
        }
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(compressedPixels))) {
            ZipEntry entry = zipInputStream.getNextEntry();
            if (entry == null || !entry.getName().equals("pixels")) {
                throw new IOException("Bad compressed texture data.");
            }
            byte[] bytePixels = limitedRead(zipInputStream, maxWidth * maxHeight * 4 + 1);
            zipInputStream.closeEntry();
            int[] intPixels = packByteArray(bytePixels);
            BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            try {
                bufferedImage.setRGB(0, 0, width, height, intPixels, 0, width);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new IOException(e);
            }
            return bufferedImage;
        }
    }

    public static byte[] encodeBufferedImage(BufferedImage bufferedImage) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            ImageIO.write(bufferedImage, "PNG", byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        }
    }

    public static HttpURLConnection followHttpRedirects(HttpURLConnection connection, Proxy proxy) throws IOException {
        try {
            while (isRedirect(connection.getResponseCode())) {
                URL newUrl = URL.of(new URI(connection.getHeaderField("Location")), null);
                connection.disconnect();
                HttpURLConnection newConnection = (HttpURLConnection) (proxy != null ? newUrl.openConnection(proxy) : newUrl.openConnection());
                newConnection.setDoOutput(false);
                newConnection.setDoInput(true);
                newConnection.connect();
                connection = newConnection;
            }
            return connection;
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    private static boolean isRedirect(int responseCode) {
        return responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_SEE_OTHER;
    }
}
