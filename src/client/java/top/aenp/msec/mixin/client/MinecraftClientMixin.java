package top.aenp.msec.mixin.client;

import com.mojang.authlib.minecraft.UserApiService;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    //Force minimal user api service, plus disabling telemetry.
    @Inject(method = "createUserApiService", at = @At(value = "RETURN"), cancellable = true)
    private void createUserApiService(CallbackInfoReturnable<UserApiService> info) {
        info.setReturnValue(UserApiService.OFFLINE);
    }
}
