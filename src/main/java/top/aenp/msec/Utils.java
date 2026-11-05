package top.aenp.msec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class Utils {
    public static final String MOJANG_SESSION_SERVER;
    public static final String MOJANG_SERVICES_HOST;
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
