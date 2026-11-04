package top.aenp.msec.mixin.client;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aenp.msec.auth.client.ClientMSecGameProfileRepository;
import top.aenp.msec.auth.client.ClientMSecMinecraftSessionService;
import top.aenp.msec.network.client.ClientTextureService;
import top.aenp.msec.network.interfaces.MSecClientPlayNetworkHandler;
import top.aenp.msec.network.payloads.auth.s2c.PlayProfileBatchResponseS2CPayload;
import top.aenp.msec.network.payloads.auth.s2c.PlayProfileResponseS2CPayload;
import top.aenp.msec.network.payloads.texture.s2c.PlayTextureResponseS2CPayload;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin implements MSecClientPlayNetworkHandler {
    @Shadow
    public abstract ClientConnection getConnection();

    @Inject(method = "onGameJoin", at = @At(value = "RETURN"))
    private void onGameJoin(CallbackInfo info) {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        MinecraftSessionService sessionService = minecraftClient.getSessionService();
        if (!minecraftClient.isInSingleplayer() && sessionService instanceof ClientMSecMinecraftSessionService msecSessionService) {
            msecSessionService.onPlayHandlerReady(getConnection());
        }
        ClientMSecGameProfileRepository.onPlayHandlerReady(getConnection());
    }

    @Redirect(method = "onServerMetadata", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/toast/ToastManager;add(Lnet/minecraft/client/toast/Toast;)V"))
    private void addToast(ToastManager instance, Toast toast) {
    }

    @Override
    public void mythicworldsecurity$onTextureResponse(PlayTextureResponseS2CPayload payload) {
        ClientTextureService.getInstance().onTextureResponse(payload);
    }

    @Override
    public void mythicworldsecurity$onProfileResponse(PlayProfileResponseS2CPayload payload) {
        MinecraftSessionService sessionService = MinecraftClient.getInstance().getSessionService();
        if (sessionService instanceof ClientMSecMinecraftSessionService msecSessionService) {
            msecSessionService.onProfileResponse(payload);
        }
    }

    @Override
    public void mythicworldsecurity$onProfileBatchResponse(PlayProfileBatchResponseS2CPayload payload) {
        ClientMSecGameProfileRepository.onProfileResponse(payload);
    }
}
