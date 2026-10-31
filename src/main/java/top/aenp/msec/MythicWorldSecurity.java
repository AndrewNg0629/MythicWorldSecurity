package top.aenp.msec;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.aenp.msec.auth.UUIDBySourceRepository;
import top.aenp.msec.config.ServerConfigManager;
import top.aenp.msec.network.MSecNetwork;
import top.aenp.msec.network.ServerTextureService;
import top.aenp.msec.trust.HttpsMetadataCommand;
import top.aenp.msec.trust.MSecEd25519Identity;
import top.aenp.mwl.misc.EnvironmentDetector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

public class MythicWorldSecurity implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("MythicWorldSecurity");
    public static final String MOD_ID = "mythicworldsecurity";
    public static final String MOD_VERSION = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata().getVersion().getFriendlyString();
    public static final Path MAIN_DIR = Path.of(System.getProperty("user.dir"), "msec").normalize();

    @Override
    public void onInitialize() {
        if (!EnvironmentDetector.isPhyClient) {
            if (Arrays.stream(Package.getPackages()).anyMatch(pack -> pack.getName().startsWith("moe.yushi.authlibinjector"))) {
                LOGGER.error("**** AUTHLIB INJECTOR IS DETECTED ON DEDICATED SERVER! ****");
                LOGGER.error("Minecraft server is shutting down!");
                LOGGER.error("Authlib Injector can corrupt MSec's server logic, leading to malfunctions and undefined behaviour!");
                LOGGER.error("MSec already handles authentication on dedicated servers, so you don't need Authlib Injector here.");
                Runtime.getRuntime().halt(-1);
            } else {
                LOGGER.info("Authlib Injector is not detected, MSec will function properly.");
            }
        }
        try {
            if (!Files.exists(MAIN_DIR)) {
                Files.createDirectories(MAIN_DIR);
            }
            if (!Files.isDirectory(MAIN_DIR)) {
                throw new IOException("MSec main directory should be a directory!");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        MSecNetwork.init();
        MSecEd25519Identity.init();
        if (!EnvironmentDetector.isPhyClient) {
            ServerConfigManager.init();
            ServerTextureService.init();
            UUIDBySourceRepository.init();
        }
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            if (!EnvironmentDetector.isPhyClient) {
                HttpsMetadataCommand.register(dispatcher);
                //test
                dispatcher.register(
                        CommandManager.literal("test_fetch")
                                .requires(source -> source.hasPermissionLevel(4))
                                .executes(context -> {
                                    MinecraftServer server = context.getSource().getServer();
                                    MinecraftSessionService sessionService = server.getSessionService();
                                    UUID u1 = UUID.fromString("ddd47f22-838a-4913-981c-9ad919dd1843");
                                    UUID u2 = UUID.fromString("1e2268c2-f7ba-4a1e-872c-f5024d0bfd47");
                                    UUID u3 = UUID.fromString("ca02eaee-4a4b-4c7b-bfb9-52f0d4996e4a");
                                    ProfileResult r1 = sessionService.fetchProfile(u1, false);
                                    ProfileResult r2 = sessionService.fetchProfile(u2, false);
                                    ProfileResult r3 = sessionService.fetchProfile(u3, false);
                                    String s1 = Utils.PROFILE_GSON.toJson(r1);
                                    String s2 = Utils.PROFILE_GSON.toJson(r2);
                                    String s3 = Utils.PROFILE_GSON.toJson(r3);
                                    MythicWorldSecurity.LOGGER.info(s1);
                                    MythicWorldSecurity.LOGGER.info(s2);
                                    MythicWorldSecurity.LOGGER.info(s3);
                                    return 1;
                                })
                );
                //test
            }
        });
    }
}
