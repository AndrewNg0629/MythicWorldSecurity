package top.aenp.msec;

import net.fabricmc.api.ClientModInitializer;
import top.aenp.msec.auth.client.ClientAuthUtils;
import top.aenp.msec.network.client.ClientTextureService;
import top.aenp.msec.trust.client.ClientFingerprintTrustService;

public class MythicWorldSecurityClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientAuthUtils.determineClientAuthSource();
        ClientTextureService.init();
        ClientFingerprintTrustService.init();
    }
}
