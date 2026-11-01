package top.aenp.msec;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Utils {
    public static final String MOJANG_SESSION_SERVER;
    public static final String MOJANG_SERVICES_HOST;
    private static final MessageDigest MESSAGE_DIGEST_SHA1;

    static {
        String reversedSessionServer = "moc.gnajom.revresnoisses//:sptth";
        String reversedServicesHost = "moc.secivrestfarcenim.ipa//:sptth";
        MOJANG_SESSION_SERVER = new StringBuilder(reversedSessionServer).reverse().toString();
        MOJANG_SERVICES_HOST = new StringBuilder(reversedServicesHost).reverse().toString();
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
}
