package top.aenp.msec;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import top.aenp.mwl.misc.EnvironmentDetector;
import top.aenp.mwl.misc.MythicReflectionUtils;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@SuppressWarnings("unchecked")
public class Utils {
    public static final String MOJANG_SESSION_SERVER;
    public static final String MOJANG_SERVICES_HOST;
    private static final MessageDigest MESSAGE_DIGEST_SHA1;
    private static final Class<Enum<?>> ServerLoginNetworkHandler$State;
    private static final Enum<?>[] serverLoginStates;
    public static final MythicReflectionUtils.ReflectedField<ServerLoginNetworkHandler, Enum<?>> ServerLoginNetworkHandler$state;
    public static final MythicReflectionUtils.ReflectedField<ServerLoginNetworkHandler, GameProfile> ServerLoginNetworkHandler$profile = new MythicReflectionUtils.ReflectedField<>(ServerLoginNetworkHandler.class, GameProfile.class, "profile", "field_14160");

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
        try {
            ServerLoginNetworkHandler$State = (Class<Enum<?>>) Class.forName(EnvironmentDetector.isYarn ? "net.minecraft.server.network.ServerLoginNetworkHandler$State" : "net.minecraft.class_3248$class_3249");
            ServerLoginNetworkHandler$state = new MythicReflectionUtils.ReflectedField<>(ServerLoginNetworkHandler.class, ServerLoginNetworkHandler$State, "state", "field_14163");
            serverLoginStates = ServerLoginNetworkHandler$State.getEnumConstants();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Unable to get class.", e);
        }
    }

    public static String hashBytes1(byte[] data) {
        byte[] hash = MESSAGE_DIGEST_SHA1.digest(data);
        return new BigInteger(1, hash).toString(16);
    }

    public static void setLoginHandlerState(ServerLoginNetworkHandler instance, int state) {
        ServerLoginNetworkHandler$state.setFieldValue(instance, serverLoginStates[state]);
    }
}
