package top.aenp.msec.network;

import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

public class MSecAESKeySet {
    private static final byte[] info = "MSec AES Keyset".getBytes(StandardCharsets.UTF_8);

    public final SecretKeySpec s2cKey;
    public final SecretKeySpec c2sKey;
    public final IvParameterSpec s2cIV;
    public final IvParameterSpec c2sIV;
    private final Mac expMac;

    private byte ctr = 1;

    public MSecAESKeySet(byte[] masterSecret, byte[] salt) {
        try {
            Mac extMac = Mac.getInstance("HmacSHA256");
            extMac.init(new SecretKeySpec(salt, "HmacSHA256"));
            byte[] prk = extMac.doFinal(masterSecret);

            Mac expMac = Mac.getInstance("HmacSHA256");
            expMac.init(new SecretKeySpec(prk, "HmacSHA256"));
            this.expMac = expMac;

            byte[] t1 = rollKD(new byte[0]);
            s2cKey = new SecretKeySpec(t1, "AES");
            byte[] t2 = rollKD(t1);
            c2sKey = new SecretKeySpec(t2, "AES");

            byte[] t3 = rollKD(t2);
            byte[] s2cIVBytes = new byte[16];
            System.arraycopy(t3, 0, s2cIVBytes, 0, 8);
            s2cIV = new IvParameterSpec(s2cIVBytes);

            byte[] c2sIVBytes = new byte[16];
            System.arraycopy(t3, 8, c2sIVBytes, 0, 8);
            c2sIV = new IvParameterSpec(c2sIVBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive keyset", e);
        }
    }

    private byte[] rollKD(byte[] tn) {
        expMac.reset();
        expMac.update(tn);
        expMac.update(info);
        expMac.update(ctr++);
        return expMac.doFinal();
    }
}
