package top.aenp.msec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.util.UUIDTypeAdapter;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class Utils {
    public static final Gson PROFILE_GSON = new GsonBuilder()
            .registerTypeAdapter(GameProfile.class, new GameProfile.Serializer())
            .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
            .registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer())
            .create();
    private static final MessageDigest MESSAGE_DIGEST_SHA1;

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
}
