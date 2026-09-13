package top.aenp.msec.mixin.client;

import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import top.aenp.msec.network.client.ClientTextureService;
import top.aenp.msec.network.interfaces.MSecClientLoginNetworkHandler;
import top.aenp.msec.network.payloads.texture.LoginTextureCommandS2CPayload;

@Mixin(ClientLoginNetworkHandler.class)
public class ClientLoginNetworkHandlerMixin implements MSecClientLoginNetworkHandler {
    @Shadow
    @Final
    private ClientConnection connection;

    @Override
    public void mythicworldsecurity$onTextureCommand(LoginTextureCommandS2CPayload payload) {
        ClientTextureService.INSTANCE.onTextureCommand(payload, connection);
    }
}
