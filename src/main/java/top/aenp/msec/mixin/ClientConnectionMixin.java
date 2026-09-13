package top.aenp.msec.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.encryption.PacketDecryptor;
import net.minecraft.network.encryption.PacketEncryptor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.crypto.Cipher;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {
    @Shadow
    private boolean encrypted;

    @Shadow
    private Channel channel;

    @Inject(method = "setupEncryption", at = @At(value = "HEAD"), cancellable = true)
    private void setupEncryption(Cipher decryptionCipher, Cipher encryptionCipher, CallbackInfo info) {
        encrypted = true;
        channel.pipeline().addAfter("splitter", "decrypt", new PacketDecryptor(decryptionCipher));
        channel.pipeline().addAfter("prepender", "encrypt", new PacketEncryptor(encryptionCipher));
        info.cancel();
    }
}
