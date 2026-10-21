package top.aenp.msec.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import top.aenp.msec.MythicWorldSecurity;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.Objects;

public class ServerConfigManager {
    public static final int CONFIG_VERSION = 0;
    private static final Path CONFIG_DIR_PATH;
    private static final Path CONFIG_FILE_PATH;
    private static final Path DEFAULT_CONFIG_FILE_PATH;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile ServerConfigManager instance = new ServerConfigManager(ServerConfig.DEFAULT_INSTANCE);
    private static volatile boolean initialized = false;

    static {
        CONFIG_DIR_PATH = Path.of(System.getProperty("user.dir")).resolve("config").resolve("msec").resolve("server").normalize();
        CONFIG_FILE_PATH = CONFIG_DIR_PATH.resolve("config.json");
        DEFAULT_CONFIG_FILE_PATH = CONFIG_DIR_PATH.resolve(String.format("default_config_v%s.json", CONFIG_VERSION));
    }

    private final ServerConfig.BootstrapConfig bootstrapConfig;
    private volatile ServerConfig configFromFile;
    private volatile ServerConfig combinedConfig;
    private boolean initialConfigSucceeded = false;

    private ServerConfigManager(ServerConfig initialConfig) {
        bootstrapConfig = initialConfig.bootstrapConfig();
        configFromFile = initialConfig;
        combineConfig();
    }

    public static ServerConfigManager getInstance() {
        return instance;
    }

    public static ServerConfig getConfig() {
        return instance.combinedConfig;
    }

    private static Path ensureFile(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        if (Files.notExists(path)) {
            Files.createFile(path);
            try (FileWriter writer = new FileWriter(path.toFile())) {
                JsonElement jsonElement = ServerConfig.CODEC.encodeStart(JsonOps.INSTANCE, ServerConfig.DEFAULT_INSTANCE).getOrThrow();
                writer.write(GSON.toJson(jsonElement));
                writer.flush();
            }
            MythicWorldSecurity.LOGGER.info("Created default MSec config file {}", path.getFileName());
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException(String.format("Path %s is a directory, but a file is expected!", path));
        }
        return path;
    }

    private static DataResult<ServerConfig> readConfigFromFile() {
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(ensureFile(CONFIG_FILE_PATH)))) {
            JsonElement jsonElement = JsonParser.parseReader(reader);
            int fileConfigVersion = jsonElement.getAsJsonObject().get("config_version").getAsInt();
            if (fileConfigVersion == CONFIG_VERSION) {
                return ServerConfig.CODEC.parse(JsonOps.INSTANCE, jsonElement);
            } else {
                return DataResult.error(() -> String.format("The MSec config version of your file is %s, while %s is expected! Migrate your config according to the generated default config %s.", fileConfigVersion, CONFIG_VERSION, DEFAULT_CONFIG_FILE_PATH.getFileName()));
            }
        } catch (Exception e) {
            return DataResult.error(() -> String.format("Exception occurred when reading MSec config from file:\n%s", e));
        }
    }

    @SuppressWarnings({"unchecked", "BusyWait"})
    public static void init() {
        if (!initialized) {
            readConfigFromFile().ifSuccess(
                    config -> {
                        instance = new ServerConfigManager(config);
                        instance.initialConfigSucceeded = true;
                        MythicWorldSecurity.LOGGER.info("Successfully parsed initial MSec config.");
                    }
            ).ifError(error -> MythicWorldSecurity.LOGGER.error("Failed to initialize MSec config. See below for error message, correct your config, and restart minecraft.\n{}", error.message()));
            try {
                ensureFile(DEFAULT_CONFIG_FILE_PATH);
            } catch (IOException e) {
                MythicWorldSecurity.LOGGER.error("Failed to place default MSec config.", e);
            }
            if (instance.initialConfigSucceeded) {
                Runnable watchAndUpdateTask = () -> {
                    try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                        CONFIG_DIR_PATH.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
                        while (!Thread.currentThread().isInterrupted()) {
                            try {
                                WatchKey watchKey = watchService.take();
                                for (WatchEvent<?> event : watchKey.pollEvents()) {
                                    WatchEvent.Kind<?> watchEventKind = event.kind();
                                    if (watchEventKind == StandardWatchEventKinds.ENTRY_MODIFY) {
                                        WatchEvent<Path> modifyEvent = (WatchEvent<Path>) event;
                                        if (modifyEvent.context().toString().startsWith("config.json")) {
                                            Thread.sleep(1000);
                                            instance.updateConfigFromFile();
                                            Thread.sleep(3000L);
                                        }
                                    }
                                }
                                if (!watchKey.reset()) {
                                    throw new RuntimeException();
                                }
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                    } catch (Exception e) {
                        MythicWorldSecurity.LOGGER.error("MSec config file listener terminated unexpectedly.", e);
                    }
                    MythicWorldSecurity.LOGGER.info("MSec config file listener done.");
                };
                Thread.startVirtualThread(watchAndUpdateTask).setName("MSec Config File Listener");
                MythicWorldSecurity.LOGGER.info("Spun up MSec config file listener.");
            }
            initialized = true;
        }
    }

    public void updateConfigFromFile() {
        readConfigFromFile().ifSuccess(
                result -> {
                    if (!Objects.equals(result.bootstrapConfig(), bootstrapConfig)) {
                        MythicWorldSecurity.LOGGER.warn("You edited bootstrap config of MSec, which can't be reloaded on-the-fly. Restart minecraft to change them.");
                    }
                    if (Objects.equals(result.mutableConfig(), configFromFile.mutableConfig())) {
                        MythicWorldSecurity.LOGGER.info("Your MSec mutable config didn't change.");
                    } else {
                        configFromFile = result;
                        combineConfig();
                        MythicWorldSecurity.LOGGER.info("Your MSec mutable config has been successfully updated.");
                    }
                }).ifError(error -> MythicWorldSecurity.LOGGER.error("Failed to parse MSec config, config was not updated. See below for error message and correct your config.\n{}", error.message()));
    }

    private void combineConfig() {
        ServerConfig.MutableConfig.DnsHttpsConfig dnsHttpsConfigFromFile = configFromFile.mutableConfig().dnsHttpsConfig();
        ServerConfig.MutableConfig.DirectHttpsConfig directHttpsConfigFromFile = configFromFile.mutableConfig().directHttpsConfig();
        combinedConfig = bootstrapConfig.modEnabled() ?
                new ServerConfig(
                        bootstrapConfig,
                        new ServerConfig.MutableConfig(
                                dnsHttpsConfigFromFile.enabled() ? dnsHttpsConfigFromFile : ServerConfig.DEFAULT_INSTANCE.mutableConfig().dnsHttpsConfig(),
                                directHttpsConfigFromFile.enabled() ? directHttpsConfigFromFile : ServerConfig.DEFAULT_INSTANCE.mutableConfig().directHttpsConfig()
                        ),
                        configFromFile.configVersion()
                )
                : ServerConfig.DEFAULT_INSTANCE;
    }
}
