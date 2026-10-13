package top.aenp.msec.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.network.client.ClientTextureService;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Mixin(targets = "net.minecraft.client.texture.PlayerSkinProvider$FileCache")
public abstract class PlayerSkinProviderFileCacheMixin {
    @Shadow
    protected abstract CompletableFuture<Identifier> store(MinecraftProfileTexture texture);

    @Shadow
    @Final
    private Map<String, CompletableFuture<Identifier>> hashToTexture;

    @Inject(method = "get", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", shift = At.Shift.AFTER), cancellable = true)
    private void getTextureFuture(MinecraftProfileTexture texture, CallbackInfoReturnable<CompletableFuture<Identifier>> info, @Local String hash) {
        if (ClientTextureService.getInstance().failedPlayerTextures.contains(hash)) {
            MythicWorldSecurity.LOGGER.info("Retrying to fetch texture {}.", hash);
            CompletableFuture<Identifier> textureFuture = store(texture);
            hashToTexture.put(hash, textureFuture);
            info.setReturnValue(textureFuture);
        }
    }
}
