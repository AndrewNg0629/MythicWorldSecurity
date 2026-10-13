package top.aenp.msec;

import net.fabricmc.api.ClientModInitializer;
import top.aenp.msec.auth.YggdrasilApiMetadata;
import top.aenp.msec.network.client.ClientTextureService;
import top.aenp.msec.trust.client.ClientFingerprintTrustService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
        ClientFingerprintTrustService.init();

        try {
            Map<String, ClientFingerprintTrustService.TrustedFingerprintEntry> map = ClientFingerprintTrustService.getInstance().readFile();
            ClientFingerprintTrustService.addTrustedFingerprint(map, "andrew2077.online:25565", "fingerprint", false);
            ClientFingerprintTrustService.getInstance().writeFileAtomically(map);
        } catch (IOException e) {
            MythicWorldSecurity.LOGGER.error("Failed to do the test.");
        }
    }
}
