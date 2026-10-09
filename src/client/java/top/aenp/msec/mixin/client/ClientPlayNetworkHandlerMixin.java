package top.aenp.msec.mixin.client;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aenp.msec.network.client.ClientTextureService;
import top.aenp.msec.network.interfaces.MSecClientPlayNetworkHandler;
import top.aenp.msec.network.payloads.texture.PlayTextureResponseS2CPayload;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin implements MSecClientPlayNetworkHandler {
    @Shadow
    private boolean displayedUnsecureChatWarning;

    @Shadow
    public abstract ClientConnection getConnection();

    //Suppress chat session warning.
    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void init(CallbackInfo info) {
        displayedUnsecureChatWarning = true;
    }

    @Override
    public void mythicworldsecurity$onTextureResponse(PlayTextureResponseS2CPayload payload) {
        ClientTextureService.getInstance().onTextureResponse(payload);
    }
}
