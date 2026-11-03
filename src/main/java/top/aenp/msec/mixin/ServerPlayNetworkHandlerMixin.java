package top.aenp.msec.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import top.aenp.msec.auth.ClientProfileBatchRequestHandler;
import top.aenp.msec.auth.ClientProfileQueryHandler;
import top.aenp.msec.network.ServerTextureService;
import top.aenp.msec.network.interfaces.MSecServerPlayNetworkHandler;
import top.aenp.msec.network.payloads.auth.c2s.PlayProfileBatchRequestC2SPayload;
import top.aenp.msec.network.payloads.auth.c2s.PlayProfileQueryC2SPayload;
import top.aenp.msec.network.payloads.texture.c2s.PlayTextureRequestC2SPayload;
import top.aenp.mwl.misc.EnvironmentDetector;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin implements MSecServerPlayNetworkHandler {
    @Override
    public void mythicworldsecurity$onTextureRequest(PlayTextureRequestC2SPayload payload) {
        if (!EnvironmentDetector.isPhyClient) {
            ServerTextureService.getInstance().onRequest((ServerPlayNetworkHandler) (Object) this, payload);
        }
    }

    @Override
    public void mythicworldsecurity$onProfileQuery(PlayProfileQueryC2SPayload payload) {
        ClientProfileQueryHandler.getInstance().onClientProfileQuery((ServerPlayNetworkHandler) (Object) this, payload.uuid());
    }

    @Override
    public void mythicworldsecurity$onProfileBatchRequest(PlayProfileBatchRequestC2SPayload payload) {
        ServerPlayNetworkHandler handler = (ServerPlayNetworkHandler) (Object) this;
        ClientProfileBatchRequestHandler.getInstance().onClientBatchRequest(handler, payload.queryId(), payload.names());
    }
}
