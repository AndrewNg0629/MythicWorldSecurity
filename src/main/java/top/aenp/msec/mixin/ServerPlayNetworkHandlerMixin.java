package top.aenp.msec.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import top.aenp.msec.network.ServerTextureService;
import top.aenp.msec.network.interfaces.MSecServerPlayNetworkHandler;
import top.aenp.msec.network.payloads.texture.PlayTextureRequestC2SPayload;
import top.aenp.mwl.misc.EnvironmentDetector;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin implements MSecServerPlayNetworkHandler {
    @Override
    public void mythicworldsecurity$onTextureRequest(PlayTextureRequestC2SPayload payload) {
        if (!EnvironmentDetector.isPhyClient) {
            ServerTextureService.INSTANCE.onRequest((ServerPlayNetworkHandler) (Object) this, payload);
        }
    }
}
