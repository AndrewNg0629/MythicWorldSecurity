package top.aenp.msec;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.aenp.msec.auth.MSecEd25519Identity;
import top.aenp.msec.network.MSecNetwork;
import top.aenp.mwl.misc.EnvironmentDetector;

import java.util.*;

public class MythicWorldSecurity implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("MythicWorldSecurity");
    public static final String MOD_ID = "mythicworldsecurity";
    public static final String MOD_VERSION = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata().getVersion().getFriendlyString();

    @Override
    public void onInitialize() {
        if (!EnvironmentDetector.isPhyClient) {
            if (Arrays.stream(Package.getPackages()).anyMatch(pack -> pack.getName().startsWith("moe.yushi.authlibinjector"))) {
                LOGGER.error("**** AUTHLIB INJECTOR DETECTED ON SERVER! ****");
                LOGGER.error("Minecraft server is shutting down!");
                LOGGER.error("Authlib Injector can corrupt MSec's server logic, leading to malfunctions and unexpected behaviour!");
                LOGGER.error("MSec already handles authentication on dedicated servers, so you don't it here.");
                Runtime.getRuntime().halt(-1);
            } else {
                LOGGER.info("Authlib Injector is not detected, MSec will function properly.");
            }
        }
        Utils.EXTRA_DOMAINS.add("example.com");
        Utils.EXTRA_DOMAINS.add("127.0.0.1");
        MSecNetwork.init();
        MSecEd25519Identity.init();
    }
}
