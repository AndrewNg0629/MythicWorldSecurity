package top.aenp.msec.auth;

import top.aenp.msec.MythicWorldSecurity;
import top.aenp.mwl.misc.EnvironmentDetector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class MSecEd25519Identity {
    private static final MessageDigest MESSAGE_DIGEST_SHA_256;
    private static MSecEd25519Identity INSTANCE = null;
    private static final KeyFactory KEY_FACTORY;

    static {
        try {
            KEY_FACTORY = KeyFactory.getInstance("Ed25519");
            MESSAGE_DIGEST_SHA_256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public final PublicKey publicKey;
    public final PrivateKey privateKey;
    public final String sha256Fingerprint;

    private MSecEd25519Identity() {
        try {
            Path keypairDirPath = MythicWorldSecurity.MAIN_DIR.resolve(EnvironmentDetector.isPhyClient ? "identity-client" : "identity-server").normalize();
            if (!Files.exists(keypairDirPath)) {
                Files.createDirectories(keypairDirPath);
            }
            if (!Files.isDirectory(keypairDirPath)) {
                throw new IOException("Keypair dir should be a directory!");
            }
            Path publicKeyPath = keypairDirPath.resolve("msec-ed25519-id.pub");
            Path privateKryPath = keypairDirPath.resolve("msec-ed25519-id");
            boolean publicKeyExists = Files.exists(publicKeyPath);
            boolean privateKeyExists = Files.exists(privateKryPath);
            if (publicKeyExists && privateKeyExists) {
                MythicWorldSecurity.LOGGER.info("Keypair files are found, loading...");
                try (
                        InputStream publicKeyInputStream = Files.newInputStream(publicKeyPath);
                        InputStream privateKeyInputStream = Files.newInputStream(privateKryPath)
                ) {
                    byte[] encodedPublicKeyBytes = Base64.getDecoder().decode(publicKeyInputStream.readAllBytes());
                    byte[] encodedPrivateKeyBytes = Base64.getDecoder().decode(privateKeyInputStream.readAllBytes());
                    try {
                        publicKey = decodeEd25519PublicKey(encodedPublicKeyBytes);
                        privateKey = decodeEd25519PrivateKey(encodedPrivateKeyBytes);
                        byte[] testData = "MythicWorldSecurity".getBytes(StandardCharsets.UTF_8);
                        Signature signer = Signature.getInstance("Ed25519");
                        signer.initSign(privateKey);
                        signer.update(testData);
                        byte[] signature = signer.sign();
                        Signature verifier = Signature.getInstance("Ed25519");
                        verifier.initVerify(publicKey);
                        verifier.update(testData);
                        if (!verifier.verify(signature)) {
                            throw new RuntimeException("Keypair doesn't match!");
                        }
                    } catch (SignatureException | InvalidKeyException e) {
                        throw new RuntimeException("Broken Ed25519 keypair.", e);
                    }
                }
                MythicWorldSecurity.LOGGER.info("Keypair has been successfully loaded!");
            } else if (!publicKeyExists && !privateKeyExists) {
                MythicWorldSecurity.LOGGER.info("No keypair file was found, generating new...");
                KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
                KeyPair keyPair = generator.generateKeyPair();
                publicKey = keyPair.getPublic();
                privateKey = keyPair.getPrivate();
                try (
                        OutputStream publicKeyOutputStream = Files.newOutputStream(publicKeyPath);
                        OutputStream privateKeyOutputStream = Files.newOutputStream(privateKryPath)
                ) {
                    publicKeyOutputStream.write(Base64.getEncoder().encode(publicKey.getEncoded()));
                    privateKeyOutputStream.write(Base64.getEncoder().encode(privateKey.getEncoded()));
                }
                MythicWorldSecurity.LOGGER.info("New keypair has been successfully created!");
            } else {
                throw new RuntimeException("Only half of the keypair is found! Either find another half or regenerate!");
            }
            sha256Fingerprint = hashEd25519PublicKey(publicKey);
            MythicWorldSecurity.LOGGER.info("The SHA-256 fingerprint of this minecraft {} is {}", EnvironmentDetector.isPhyClient ? "client" : "server", sha256Fingerprint);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to initialize keypair.", e);
        }
    }

    public static String hashEd25519PublicKey(PublicKey publicKey) {
        MESSAGE_DIGEST_SHA_256.update(publicKey.getEncoded(), 12, 32);
        return new BigInteger(1, MESSAGE_DIGEST_SHA_256.digest()).toString(16);
    }

    public static MSecEd25519Identity getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("Identity hasn't been loaded yet!");
        } else {
            return INSTANCE;
        }
    }

    public static void init() {
        if (INSTANCE == null) {
            INSTANCE = new MSecEd25519Identity();
        } else {
            throw new IllegalStateException("Identity has already been initialized!");
        }
    }

    public static PublicKey decodeEd25519PublicKey(byte[] encodedPublicKey) {
        try {
            return KEY_FACTORY.generatePublic(new X509EncodedKeySpec(encodedPublicKey));
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException("Broken Ed25519 public key.", e);
        }
    }

    private static PrivateKey decodeEd25519PrivateKey(byte[] encodedPrivateKey) {
        try {
            return KEY_FACTORY.generatePrivate(new PKCS8EncodedKeySpec(encodedPrivateKey));
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException("Broken Ed25519 private key.", e);
        }
    }
}
