package top.aenp.msec;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import top.aenp.msec.auth.YggdrasilApiMetadata;
import top.aenp.msec.network.client.ClientTextureService;

import java.io.IOException;

public class MythicWorldSecurityClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        String apiUrl = UtilsClient.digAliYggdrasilURL();
        if (apiUrl != null) {
            YggdrasilApiMetadata metadata = YggdrasilApiMetadata.fetchApiMetadataWithRetries(apiUrl, null, 5);
            MythicWorldSecurity.LOGGER.info("Server name: {}. Injected server URL: {}. Homepage: {}. Skin domains: {}.", metadata.metaServerName(), metadata.injectedApiUrl(), metadata.homepageUrl(), metadata.skinDomains());
        } else {
            MythicWorldSecurity.LOGGER.info("No Ali");
        }
        ClientTextureService.init();
    }
}
