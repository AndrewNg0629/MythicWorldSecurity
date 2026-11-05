package top.aenp.msec.offline;

import com.google.gson.*;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import top.aenp.msec.MythicWorldSecurity;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class OfflinePlayerDB {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static OfflinePlayerDB instance = null;
    public final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    public final HashMap<String, OfflinePlayerDataEntry> inMemoryDB = new HashMap<>();
    private final LinkedBlockingDeque<IOAction> actionQueue = new LinkedBlockingDeque<>();
    private final Path dbFilePath;
    private final Path dbTempFilePath;
    private final AtomicLong lastWriteTime = new AtomicLong();

    @SuppressWarnings("unchecked")
    private OfflinePlayerDB() {
        Path workingDirPath = MythicWorldSecurity.MAIN_DIR.resolve("server").resolve("player_data");
        dbFilePath = workingDirPath.resolve("offline_player_db.json").normalize();
        dbTempFilePath = workingDirPath.resolve("offline_player_db.json.tmp").normalize();
        try {
            if (!Files.exists(workingDirPath)) {
                Files.createDirectories(workingDirPath);
            }
            if (!Files.isDirectory(workingDirPath)) {
                throw new IOException("MSec offline player DB directory should be a directory!");
            }
            ensureFile();
            Files.deleteIfExists(dbTempFilePath);
            loadDBFromFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize offline player DB!", e);
        }
        Runnable ioWorkerTask = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    IOAction action = actionQueue.take();
                    switch (action) {
                        case LOAD:
                            loadDBFromFile();
                            break;
                        case SAVE:
                            saveDBToFile();
                            break;
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    MythicWorldSecurity.LOGGER.error("Exception in offline user DB IO worker!", e);
                }
            }
        };
        Runnable dbFileWatchingTask = () -> {
            long lastUpdateTime = 0L;
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                workingDirPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        WatchKey watchKey = watchService.take();
                        for (WatchEvent<?> event : watchKey.pollEvents()) {
                            WatchEvent.Kind<?> watchEventKind = event.kind();
                            if (watchEventKind == StandardWatchEventKinds.ENTRY_MODIFY) {
                                WatchEvent<Path> modifyEvent = (WatchEvent<Path>) event;
                                String fileChanged = modifyEvent.context().toString();
                                if (fileChanged.startsWith("offline_player_db.json") && !fileChanged.startsWith("offline_player_db.json.tmp")) {
                                    long currentTime = System.currentTimeMillis();
                                    if (currentTime > lastUpdateTime + 5000L && currentTime > lastWriteTime.get() + 3000L) {
                                        MythicWorldSecurity.LOGGER.info("Performing offline player db reload.");
                                        lastUpdateTime = currentTime;
                                        scheduleLoad();
                                    }
                                }
                            }
                        }
                        if (!watchKey.reset()) {
                            throw new RuntimeException();
                        }
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        MythicWorldSecurity.LOGGER.error("Exception in offline player DB file listener!", e);
                    }
                }
            } catch (IOException e) {
                MythicWorldSecurity.LOGGER.error("Failed to initialize offline player DB file listener!", e);
            }
        };
        Thread.startVirtualThread(ioWorkerTask).setName("MSec Offline User DB IO Worker");
        Thread.startVirtualThread(dbFileWatchingTask).setName("MSec Offline User DB File Listener");
    }

    public static void init() {
        if (instance == null) {
            instance = new OfflinePlayerDB();
        } else {
            throw new IllegalStateException("Offline player DB has already been initialized!");
        }
    }

    public static OfflinePlayerDB getInstance() {
        if (instance != null) {
            return instance;
        } else {
            throw new IllegalStateException("Offline player DB has not been initialized!");
        }
    }

    private void ensureFile() throws IOException {
        if (!Files.exists(dbFilePath)) {
            Files.createFile(dbFilePath);
            try (OutputStream outputStream = Files.newOutputStream(dbFilePath)) {
                outputStream.write("[]".getBytes(StandardCharsets.UTF_8));
            }
        } else if (!Files.isRegularFile(dbFilePath)) {
            throw new IOException("Offline player DB file should be a file!");
        }
    }

    private void loadDBFromFile() throws IOException {
        ensureFile();
        HashMap<String, OfflinePlayerDataEntry> dbFromFile = new HashMap<>();
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(dbFilePath))) {
            JsonArray jsonArray = JsonParser.parseReader(reader).getAsJsonArray();
            jsonArray.asList().forEach(json -> {
                DataResult<OfflinePlayerDataEntry> offlinePlayerDataResult = OfflinePlayerDataEntry.CODEC.parse(JsonOps.INSTANCE, json);
                OfflinePlayerDataEntry offlinePlayerEntry = offlinePlayerDataResult.getOrThrow(JsonParseException::new);
                dbFromFile.put(offlinePlayerEntry.playerName().toLowerCase(Locale.ROOT), offlinePlayerEntry);
            });
        } catch (Exception e) {
            throw new IOException("Failed to read and parse offline user DB!", e);
        }
        try {
            lock.writeLock().lock();
            inMemoryDB.clear();
            inMemoryDB.putAll(dbFromFile);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void saveDBToFile() throws IOException {
        Set<OfflinePlayerDataEntry> offlinePlayerEntries;
        try {
            lock.readLock().lock();
            offlinePlayerEntries = Set.copyOf(inMemoryDB.values());
        } finally {
            lock.readLock().unlock();
        }
        ensureFile();
        Files.deleteIfExists(dbTempFilePath);
        try (OutputStream outputStream = Files.newOutputStream(dbTempFilePath)) {
            lastWriteTime.set(System.currentTimeMillis());
            DataResult<JsonElement> jsonResult = OfflinePlayerDataEntry.SET_CODEC.encodeStart(JsonOps.INSTANCE, offlinePlayerEntries);
            JsonElement json = jsonResult.getOrThrow(IOException::new);
            outputStream.write(GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
        }
        try {
            Files.move(dbTempFilePath, dbFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(dbTempFilePath, dbFilePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void scheduleLoad() {
        actionQueue.add(IOAction.LOAD);
    }

    public void scheduleSave() {
        actionQueue.add(IOAction.SAVE);
    }

    private enum IOAction {
        LOAD, SAVE
    }
}
