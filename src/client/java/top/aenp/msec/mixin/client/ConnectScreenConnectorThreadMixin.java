package top.aenp.msec.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.network.Address;
import net.minecraft.client.network.AllowedAddressResolver;
import net.minecraft.client.network.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.aenp.msec.network.client.MSecClientLoginHandshakeHandlerImpl;

import java.util.Optional;

@Mixin(targets = "net.minecraft.client.gui.screen.multiplayer.ConnectScreen$1")
public class ConnectScreenConnectorThreadMixin {
    @WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AllowedAddressResolver;resolve(Lnet/minecraft/client/network/ServerAddress;)Ljava/util/Optional;"))
    private Optional<Address> wrapResolve(AllowedAddressResolver instance, ServerAddress address, Operation<Optional<Address>> original) {
        MSecClientLoginHandshakeHandlerImpl.SERVER_ADDRESS_THREAD_LOCAL.set(address);
        return original.call(instance, address);
    }
}
