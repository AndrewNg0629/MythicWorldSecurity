package top.aenp.msec;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.aenp.msec.auth.MSecEd25519Identity;
import top.aenp.msec.network.MSecNetwork;
import top.aenp.msec.network.ServerTextureService;
import top.aenp.mwl.misc.EnvironmentDetector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MythicWorldSecurity implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("MythicWorldSecurity");
    public static final String MOD_ID = "mythicworldsecurity";
    public static final String MOD_VERSION = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata().getVersion().getFriendlyString();
    public static final Path MAIN_DIR = Path.of(System.getProperty("user.dir"), "msec").normalize();

    @Override
    public void onInitialize() {
        if (!EnvironmentDetector.isPhyClient) {
            if (Arrays.stream(Package.getPackages()).anyMatch(pack -> pack.getName().startsWith("moe.yushi.authlibinjector"))) {
                LOGGER.error("**** AUTHLIB INJECTOR DETECTED ON DEDICATED SERVER! ****");
                LOGGER.error("Minecraft server is shutting down!");
                LOGGER.error("Authlib Injector can corrupt MSec's server logic, leading to malfunctions and unexpected behaviour!");
                LOGGER.error("MSec already handles authentication on dedicated servers, so you don't need it here.");
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
        Utils.EXTRA_DOMAINS.add(MSecNetwork.MSEC_DUMMY_DOMAIN);
        MSecNetwork.init();
        MSecEd25519Identity.init();
        if (!EnvironmentDetector.isPhyClient) {
            ServerTextureService.init();
        }
    }
}
