package top.aenp.msec.mixin;

import com.mojang.authlib.Environment;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.aenp.mwl.misc.EnvironmentDetector;

import java.net.URL;

@Mixin(YggdrasilAuthenticationService.class)
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

    //Strips all key services.
    @Redirect(method = "<init>(Ljava/net/Proxy;Lcom/mojang/authlib/Environment;)V", at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/yggdrasil/YggdrasilServicesKeyInfo;get(Ljava/net/URL;Lcom/mojang/authlib/minecraft/client/MinecraftClient;)Lcom/mojang/authlib/yggdrasil/ServicesKeySet;"))
    private ServicesKeySet getKeySet(URL url, MinecraftClient client) {
        return ServicesKeySet.EMPTY;
    }
}
