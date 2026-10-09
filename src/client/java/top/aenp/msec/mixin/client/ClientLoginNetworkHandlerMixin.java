package top.aenp.msec.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.ClientConnection;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aenp.msec.network.client.ClientTextureService;
import top.aenp.msec.network.client.MSecClientLoginHandshakeHandlerImpl;
import top.aenp.msec.network.interfaces.MSecClientLoginHandshakeHandler;
import top.aenp.msec.network.interfaces.MSecClientLoginNetworkHandler;
import top.aenp.msec.network.payloads.texture.LoginTextureCommandS2CPayload;

import java.time.Duration;
import java.util.function.Consumer;

@Mixin(ClientLoginNetworkHandler.class)
public class ClientLoginNetworkHandlerMixin implements MSecClientLoginNetworkHandler {
    @Shadow
    @Final
    private ClientConnection connection;

    @Unique
    private MSecClientLoginHandshakeHandler msecClientHandshakeHandler = null;

    @Override
    public void mythicworldsecurity$onTextureCommand(LoginTextureCommandS2CPayload payload) {
        ClientTextureService.getInstance().onTextureCommand(payload, connection);
    }

    @Override
    public MSecClientLoginHandshakeHandler mythicworldsecurity$getMSecHandshakeHandler() {
        return msecClientHandshakeHandler;
    }

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void init(ClientConnection connection, MinecraftClient client, ServerInfo serverInfo, Screen parentScreen, boolean newWorld, Duration worldLoadTime, Consumer<Text> statusConsumer, CookieStorage cookieStorage, CallbackInfo ci) {
        msecClientHandshakeHandler = new MSecClientLoginHandshakeHandlerImpl(connection, statusConsumer);
    }
}
