package top.aenp.msec;

import net.fabricmc.api.ClientModInitializer;
import top.aenp.msec.auth.YggdrasilApiMetadata;
import top.aenp.msec.network.MSecNetwork;
import top.aenp.msec.network.client.ClientTextureService;
import top.aenp.msec.trust.client.ClientFingerprintTrustService;

import java.io.IOException;

public class MythicWorldSecurityClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        String apiUrl = UtilsClient.digAliYggdrasilURL();
        if (apiUrl != null) {
            try {
                YggdrasilApiMetadata metadata = MSecNetwork.fetchIOWithRetries(() -> YggdrasilApiMetadata.fetchApiMetadata(apiUrl, null), "api metadata", 5);
                MythicWorldSecurity.LOGGER.info("Server name: {}. Injected server URL: {}. Homepage: {}. Skin domains: {}.", metadata.metaServerName(), metadata.injectedApiUrl(), metadata.homepageUrl(), metadata.skinDomains());
            } catch (IOException e) {
                MythicWorldSecurity.LOGGER.info("Failed to fetch api metadata.");
            }
        } else {
            MythicWorldSecurity.LOGGER.info("No Ali");
        }
        ClientTextureService.init();
        ClientFingerprintTrustService.init();
    }
}
