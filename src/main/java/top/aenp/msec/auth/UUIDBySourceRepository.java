package top.aenp.msec.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.Uuids;
import top.aenp.msec.MythicWorldSecurity;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UUIDBySourceRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Codec<Map<String, Map<UUID, UUID>>> UUID_REPO_CODEC = Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Uuids.STRING_CODEC, Uuids.STRING_CODEC));
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static UUIDBySourceRepository instance = null;
    private final Path uuidRepoFilePath;
    private final Path uuidRepoTempFilePath;
    private final ConcurrentHashMap<String, Map<UUID, UUID>> inMemoryCache = new ConcurrentHashMap<>();

    private UUIDBySourceRepository() {
        Path workingDirPath = MythicWorldSecurity.MAIN_DIR.resolve("server").resolve("player_data");
        uuidRepoFilePath = workingDirPath.resolve("uuid_repository.json").normalize();
        uuidRepoTempFilePath = workingDirPath.resolve("uuid_repository.json.tmp").normalize();
        try {
            if (!Files.exists(workingDirPath)) {
                Files.createDirectories(workingDirPath);
            }
            if (!Files.isDirectory(workingDirPath)) {
                throw new IOException("MSec player data directory should be a directory!");
            }
            ensureFile();
            Files.deleteIfExists(uuidRepoTempFilePath);
            try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(uuidRepoFilePath))) {
                JsonElement jsonElement = JsonParser.parseReader(reader);
                Map<String, Map<UUID, UUID>> mapFromFile = UUID_REPO_CODEC.parse(JsonOps.INSTANCE, jsonElement).getOrThrow();
                inMemoryCache.putAll(mapFromFile);
            } catch (Exception e) {
                throw new IOException("Failed to read UUID repository from file!", e);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize uuid repository!", e);
        }
    }

    public static UUIDBySourceRepository getInstance() {
        if (instance != null) {
            return instance;
        } else {
            throw new IllegalStateException();
        }
    }

    public static void init() {
        if (instance == null) {
            instance = new UUIDBySourceRepository();
        } else {
            throw new IllegalStateException();
        }
    }

    public UUID mapUUID(String authSourceName, UUID uuid) {
        try {
            lock.readLock().lock();
            Map<UUID, UUID> existingUUIDMap1 = inMemoryCache.get(authSourceName);
            if (existingUUIDMap1 != null) {
                if (existingUUIDMap1.containsKey(uuid)) {
                    return existingUUIDMap1.get(uuid);
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        try {
            lock.writeLock().lock();
            Map<UUID, UUID> existingUUIDMap1 = inMemoryCache.get(authSourceName);
            if (existingUUIDMap1 != null) {
                if (existingUUIDMap1.containsKey(uuid)) {
                    return existingUUIDMap1.get(uuid);
                }
            }
            UUID result;
            if (searchUUIDInKey(uuid) == null && searchUUIDInValue(uuid) == null) {
                result = uuid;
            } else {
                do {
                    result = UUID.randomUUID();
                } while (searchUUIDInKey(result) != null || searchUUIDInValue(result) != null);
            }
            HashMap<String, Map<UUID, UUID>> backup = new HashMap<>(inMemoryCache);
            Map<UUID, UUID> existingUUIDMap2 = backup.get(authSourceName);
            HashMap<UUID, UUID> mutableUUIDMap = new HashMap<>();
            if (existingUUIDMap2 != null) {
                mutableUUIDMap.putAll(existingUUIDMap2);
            }
            mutableUUIDMap.put(uuid, result);
            backup.put(authSourceName, Map.copyOf(mutableUUIDMap));
            writeFileAtomically(backup);
            inMemoryCache.clear();
            inMemoryCache.putAll(backup);
            return result;
        } catch (IOException e) {
            MythicWorldSecurity.LOGGER.error("Failed to compute new UUID.", e);
            return null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private String searchUUIDInKey(UUID uuid) {
        String result = null;
        for (Map.Entry<String, Map<UUID, UUID>> entry : inMemoryCache.entrySet()) {
            if (entry.getValue().containsKey(uuid)) {
                result = entry.getKey();
            }
        }
        return result;
    }

    private String searchUUIDInValue(UUID uuid) {
        String result = null;
        for (Map.Entry<String, Map<UUID, UUID>> entry : inMemoryCache.entrySet()) {
            if (entry.getValue().containsValue(uuid)) {
                result = entry.getKey();
            }
        }
        return result;
    }

    public UUIDSearchResult searchAndReverseMapUUID(UUID uuid) {
        try {
            lock.readLock().lock();
            String authSourceName = searchUUIDInValue(uuid);
            if (authSourceName != null) {
                Map<UUID, UUID> uuidMap = inMemoryCache.get(authSourceName);
                if (uuidMap == null) {
                    return null;
                }
                for (Map.Entry<UUID, UUID> entry : uuidMap.entrySet()) {
                    if (entry.getValue().equals(uuid)) {
                        return new UUIDSearchResult(authSourceName, entry.getKey());
                    }
                }
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    private void writeFileAtomically(Map<String, Map<UUID, UUID>> map) throws IOException {
        ensureFile();
        Files.deleteIfExists(uuidRepoTempFilePath);
        JsonElement jsonElement = UUID_REPO_CODEC.encodeStart(JsonOps.INSTANCE, map).getOrThrow(message -> new IOException(String.format("Failed to encode UUID repository!: %s", message)));
        try (OutputStream outputStream = Files.newOutputStream(uuidRepoTempFilePath)) {
            outputStream.write(GSON.toJson(jsonElement).getBytes(StandardCharsets.UTF_8));
        }
        try {
            Files.move(uuidRepoTempFilePath, uuidRepoFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(uuidRepoTempFilePath, uuidRepoFilePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void ensureFile() throws IOException {
        if (!Files.exists(uuidRepoFilePath)) {
            Files.createFile(uuidRepoFilePath);
            try (OutputStream outputStream = Files.newOutputStream(uuidRepoFilePath)) {
                outputStream.write("{}".getBytes(StandardCharsets.UTF_8));
            }
        } else if (!Files.isRegularFile(uuidRepoFilePath)) {
            throw new IOException("UUID repository file should be a file!");
        }
    }

    public record UUIDSearchResult(String sourceConfigName, UUID reverseMappedUUID) {
    }
}
