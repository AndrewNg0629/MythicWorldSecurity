package top.aenp.msec.mixin.client;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aenp.msec.auth.client.ClientMSecGameProfileRepository;
import top.aenp.msec.auth.client.ClientMSecMinecraftSessionService;
import top.aenp.msec.network.MSecNetwork;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {
    @Inject(method = "handleDisconnection", at = @At(value = "HEAD"))
    private void handleDisconnection(CallbackInfo info) {
        MSecNetwork.EXTRA_ALLOWED_DOMAINS.clear();
        MinecraftSessionService sessionService = MinecraftClient.getInstance().getSessionService();
        if (sessionService instanceof ClientMSecMinecraftSessionService msecSessionService) {
            msecSessionService.onDisconnected();
        }
        ClientMSecGameProfileRepository.onDisconnected();
    }
}
