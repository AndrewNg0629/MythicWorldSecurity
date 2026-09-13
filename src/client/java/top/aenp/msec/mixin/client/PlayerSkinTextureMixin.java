package top.aenp.msec.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinTexture;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.network.client.ClientTextureService;
import top.aenp.msec.Utils;
import top.aenp.msec.network.payloads.texture.PlayTextureResponseS2CPayload;

import java.io.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(PlayerSkinTexture.class)
public abstract class PlayerSkinTextureMixin {
    @Shadow
    @Final
    private String url;
    @Shadow
    @Final
    private @Nullable File cacheFile;

    @Shadow
    protected abstract void onTextureLoaded(NativeImage image);

    @Shadow
    @Nullable
    protected abstract NativeImage loadTexture(InputStream stream);

    @SuppressWarnings("resource")
    @WrapOperation(method = "load", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;runAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> wrapDownloadAction(Runnable runnable, Executor executor, Operation<CompletableFuture<Void>> original) {
        ClientPlayerEntity playerEntity = MinecraftClient.getInstance().player;
        if (playerEntity != null && url.startsWith("https://example.com/msecofflinetextures/")) {
            return CompletableFuture.runAsync(() -> {
                String hash = url.substring(40);
                CompletableFuture<PlayTextureResponseS2CPayload> future = ClientTextureService.INSTANCE.requestTexture(playerEntity.networkHandler, hash);
                PlayTextureResponseS2CPayload payload = future.join();
                if (payload.data() != null) {
                    byte[] textureData = payload.data();
                    try {
                        InputStream textureInputStream;
                        if (cacheFile != null) {
                            Utils.ensureParents(cacheFile);
                            try (FileOutputStream fileOutputStream = new FileOutputStream(cacheFile)) {
                                fileOutputStream.write(textureData);
                                textureInputStream = new FileInputStream(cacheFile);
                            }
                        } else {
                            textureInputStream = new ByteArrayInputStream(textureData);
                        }
                        MinecraftClient.getInstance().execute(() -> {
                            NativeImage nativeImage = this.loadTexture(textureInputStream);
                            if (nativeImage != null) {
                                this.onTextureLoaded(nativeImage);
                            }
                        });
                    } catch (Exception e) {
                        MythicWorldSecurity.LOGGER.error("Failed to inject texture.", e);
                    }
                } else {
                    MythicWorldSecurity.LOGGER.warn("Failed to fetch texture {} from server.", hash);
                }
            }, executor);
        } else {
            return original.call(runnable, executor);
        }
    }
}
