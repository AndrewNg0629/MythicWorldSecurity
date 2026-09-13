package top.aenp.msec.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.SignatureState;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.aenp.msec.Utils;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

@Mixin(YggdrasilMinecraftSessionService.class)
public class YggdrasilMinecraftSessionServiceMixin {
    //Bypass properties verification.
    @Inject(method = "getPropertySignatureState", at = @At(value = "HEAD"), cancellable = true)
    private void getPropertySignatureState(CallbackInfoReturnable<SignatureState> info) {
        info.setReturnValue(SignatureState.SIGNED);
    }

    @WrapOperation(method = "unpackTextures", at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/yggdrasil/TextureUrlChecker;isAllowedTextureDomain(Ljava/lang/String;)Z"))
    private boolean wrapAllowedDomains(String url, Operation<Boolean> original) {
        return original.call(url) || Utils.isAllowedTextureDomain(url);
    }
}
