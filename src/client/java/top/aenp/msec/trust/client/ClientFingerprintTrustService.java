package top.aenp.msec.trust.client;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import top.aenp.msec.MythicWorldSecurity;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Function;

public class ClientFingerprintTrustService {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClientFingerprintTrustService instance = null;
    private final Path trustedFingerprintPath;
    private final Path tempFingerprintFilePath;
    public static final Codec<String> VALIDATED_HOST_STRING_CODEC;
    public static final Codec<Map<String, TrustedFingerprintEntry>> FINGERPRINT_MAP_CODEC;

    static {
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
        VALIDATED_HOST_STRING_CODEC = Codec.STRING.flatXmap(hostStringNormalizer, hostStringNormalizer);
        FINGERPRINT_MAP_CODEC = Codec.unboundedMap(VALIDATED_HOST_STRING_CODEC, TrustedFingerprintEntry.CODEC);
    }

    public record TrustedFingerprintEntry(String fingerprint, boolean fromHttps, Optional<Long> creationTimestamp) {
        public static final Codec<TrustedFingerprintEntry> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        Codec.STRING.fieldOf("fingerprint").forGetter(TrustedFingerprintEntry::fingerprint),
                        Codec.BOOL.fieldOf("from_https").forGetter(TrustedFingerprintEntry::fromHttps),
                        Codec.LONG.optionalFieldOf("creation_timestamp").forGetter(TrustedFingerprintEntry::creationTimestamp)
                ).apply(instance, TrustedFingerprintEntry::new)
        );
    }

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
            DataResult<Map<String, TrustedFingerprintEntry>> mapDataResult = FINGERPRINT_MAP_CODEC.parse(JsonOps.INSTANCE, jsonElement);
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
        JsonElement jsonElement = FINGERPRINT_MAP_CODEC.encodeStart(JsonOps.INSTANCE, map).getOrThrow(message -> new IOException(String.format("Failed to encode trusted fingerprint map: %s", message)));
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
            if (mapEntry.getValue().fingerprint.equalsIgnoreCase(fingerprint)) {
                hosts.add(mapEntry.getKey());
            }
        }
        return hosts;
    }

    public static void addTrustedFingerprint(Map<String, TrustedFingerprintEntry> map, String host, String fingerprint, boolean fromHttps) {
        fingerprint = fingerprint.toLowerCase(Locale.ROOT);
        map.put(host, new TrustedFingerprintEntry(fingerprint, fromHttps, Optional.of(System.currentTimeMillis())));
    }
}
