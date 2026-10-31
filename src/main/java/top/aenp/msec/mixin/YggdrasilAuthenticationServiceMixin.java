package top.aenp.msec.mixin;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.aenp.msec.auth.ServerMSecMinecraftSessionService;
import top.aenp.msec.config.ServerConfigManager;
import top.aenp.mwl.misc.EnvironmentDetector;

@Mixin(value = YggdrasilAuthenticationService.class, priority = 990)
public class YggdrasilAuthenticationServiceMixin {
//    @Inject(method = "determineEnvironment", at = @At(value = "RETURN"), cancellable = true)
//    private static void determineEnvironment(CallbackInfoReturnable<Environment> info) {
//        if (!EnvironmentDetector.isPhyClient) {
//            info.setReturnValue(new Environment(
//                    "https://littleskin.cn/api/yggdrasil/sessionserver",
//                    "https://littleskin.cn/api/yggdrasil/minecraftservices",
//                    "TEST"
//            ));
//        }
//    }

    @Inject(method = "createMinecraftSessionService", at = @At(value = "RETURN"), cancellable = true)
    private void createMinecraftSessionService(CallbackInfoReturnable<MinecraftSessionService> info) {
        if (!EnvironmentDetector.isPhyClient) {
            if (ServerConfigManager.getConfig().bootstrapConfig().modEnabled()) {
                info.setReturnValue(new ServerMSecMinecraftSessionService());
            }
        }
    }

    @Inject(method = "createProfileRepository", at = @At(value = "HEAD"))
    private void createProfileRepository(CallbackInfoReturnable<GameProfileRepository> info) {

    }

    @Inject(method = "createUserApiService", at = @At(value = "HEAD"), cancellable = true)
    private void createUserApiService(CallbackInfoReturnable<UserApiService> info) {
        info.setReturnValue(UserApiService.OFFLINE);
    }

    @Inject(method = "getServicesKeySet", at = @At(value = "HEAD"), cancellable = true)
    private void getServicesKeySet(CallbackInfoReturnable<ServicesKeySet> info) {
        info.setReturnValue(ServicesKeySet.EMPTY);
    }
}
