package top.aenp.msec.mixin.client;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.aenp.msec.auth.client.ClientMSecGameProfileRepository;
import top.aenp.msec.auth.client.ClientMSecMinecraftSessionService;
import top.aenp.mwl.misc.EnvironmentDetector;

@Mixin(value = YggdrasilAuthenticationService.class, priority = 990, remap = false)
public class ClientYggdrasilAuthenticationServiceMixin {
    @Inject(method = "createMinecraftSessionService", at = @At(value = "RETURN"), cancellable = true)
    private void createMinecraftSessionService(CallbackInfoReturnable<MinecraftSessionService> info) {
        if (EnvironmentDetector.isPhyClient) {
            info.setReturnValue(new ClientMSecMinecraftSessionService(info.getReturnValue()));
        }
    }

    @Inject(method = "createProfileRepository", at = @At(value = "RETURN"), cancellable = true)
    private void createProfileRepository(CallbackInfoReturnable<GameProfileRepository> info) {
        if (EnvironmentDetector.isPhyClient) {
            info.setReturnValue(new ClientMSecGameProfileRepository(info.getReturnValue()));
        }
    }
}
