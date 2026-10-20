package top.aenp.msec.trust.client;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.Utils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.*;

public class ClientFingerprintTrustService {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClientFingerprintTrustService instance = null;
    private final Path trustedFingerprintPath;
    private final Path tempFingerprintFilePath;
    public static final Codec<Map<String, TrustedFingerprintEntry>> TRUSTED_FINGERPRINT_MAP_CODEC = Codec.unboundedMap(Utils.VALIDATED_ADDR_STRING_CODEC, TrustedFingerprintEntry.CODEC);

    private ClientFingerprintTrustService() {
        trustedFingerprintPath = MythicWorldSecurity.MAIN_DIR.resolve("client").resolve("trusted_servers.json").normalize();
        tempFingerprintFilePath = MythicWorldSecurity.MAIN_DIR.resolve("client").resolve("trusted_servers.json.tmp").normalize();
        try {
            ensureFile();
            Files.deleteIfExists(tempFingerprintFilePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize fingerprint trust service!", e);
        }
    }

    public static void init() {
        if (instance == null) {
            instance = new ClientFingerprintTrustService();
        } else {
            throw new IllegalStateException();
        }
    }

    public static ClientFingerprintTrustService getInstance() {
        if (instance != null) {
            return instance;
        } else {
            throw new IllegalStateException();
        }
    }

    private void ensureFile() throws IOException {
        if (!Files.exists(trustedFingerprintPath)) {
            Files.createFile(trustedFingerprintPath);
            try (OutputStream outputStream = Files.newOutputStream(trustedFingerprintPath)) {
                outputStream.write("{}".getBytes(StandardCharsets.UTF_8));
            }
        } else if (!Files.isRegularFile(trustedFingerprintPath)) {
            throw new IOException("Trusted fingerprint file should be a file!");
        }
    }

    public HashMap<String, TrustedFingerprintEntry> readFile() throws IOException {
        ensureFile();
        try (InputStreamReader inputStreamReader = new InputStreamReader(Files.newInputStream(trustedFingerprintPath))) {
            JsonElement jsonElement = JsonParser.parseReader(inputStreamReader);
            DataResult<Map<String, TrustedFingerprintEntry>> mapDataResult = TRUSTED_FINGERPRINT_MAP_CODEC.parse(JsonOps.INSTANCE, jsonElement);
            if (mapDataResult.isSuccess()) {
                return new HashMap<>(mapDataResult.getOrThrow());
            } else {
                throw new IOException(String.format("Failed to parse trusted fingerprint file: %s", mapDataResult.error().map(DataResult.Error::message)));
            }
        } catch (JsonParseException e) {
            throw new IOException("Failed to parse trusted fingerprint JSON!", e);
        }
    }

    public void writeFileAtomically(Map<String, TrustedFingerprintEntry> map) throws IOException {
        ensureFile();
        Files.deleteIfExists(tempFingerprintFilePath);
        JsonElement jsonElement = TRUSTED_FINGERPRINT_MAP_CODEC.encodeStart(JsonOps.INSTANCE, map).getOrThrow(message -> new IOException(String.format("Failed to encode trusted fingerprint map: %s", message)));
        try (OutputStream outputStream = Files.newOutputStream(tempFingerprintFilePath)) {
            outputStream.write(GSON.toJson(jsonElement).getBytes(StandardCharsets.UTF_8));
        }
        try {
            Files.move(tempFingerprintFilePath, trustedFingerprintPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFingerprintFilePath, trustedFingerprintPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static Set<String> findHostsByFingerprints(Map<String, TrustedFingerprintEntry> map, String fingerprint) {
        HashSet<String> hosts = new HashSet<>();
        for (Map.Entry<String, TrustedFingerprintEntry> mapEntry : map.entrySet()) {
            if (mapEntry.getValue().fingerprint().equalsIgnoreCase(fingerprint)) {
                hosts.add(mapEntry.getKey());
            }
        }
        return hosts;
    }

    public static void addTrustedFingerprint(Map<String, TrustedFingerprintEntry> map, String host, String fingerprint, boolean fromHttps) {
        fingerprint = fingerprint.toLowerCase(Locale.ROOT);
        map.put(host, new TrustedFingerprintEntry(fingerprint, fromHttps, Optional.of(ZonedDateTime.now())));
    }
}
