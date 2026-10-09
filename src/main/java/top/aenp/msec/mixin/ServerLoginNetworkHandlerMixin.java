package top.aenp.msec.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aenp.msec.network.MSecServerLoginHandshakeHandlerImpl;
import top.aenp.msec.network.ServerTextureService;
import top.aenp.msec.network.interfaces.MSecServerLoginHandshakeHandler;
import top.aenp.msec.network.interfaces.MSecServerLoginNetworkHandler;
import top.aenp.msec.network.payloads.texture.LoginTextureDataC2SPayload;
import top.aenp.msec.network.payloads.texture.LoginTextureMetadataC2SPayload;
import top.aenp.mwl.misc.EnvironmentDetector;

@Mixin(ServerLoginNetworkHandler.class)
public abstract class ServerLoginNetworkHandlerMixin implements MSecServerLoginNetworkHandler {
    @Shadow
    @Final
    private ClientConnection connection;
    @Shadow
    @Final
    private MinecraftServer server;

    @Shadow
    abstract void startVerify(GameProfile profile);

    @Shadow
    String profileName;

    @Shadow
    public abstract void disconnect(Text reason);

    @Unique
    private MSecServerLoginHandshakeHandler mSecServerLoginHandshakeHandler = null;
//    @Unique
//    private boolean testSent = false;
//    @Unique
//    private boolean testDone = false;
//    @Unique
//    private long ts = 0L;

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void init(MinecraftServer server, ClientConnection connection, boolean transferred, CallbackInfo info) {
        mSecServerLoginHandshakeHandler = new MSecServerLoginHandshakeHandlerImpl(connection, (ServerLoginNetworkHandler) (Object) this);
    }

    @Inject(method = "onHello", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;isOnlineMode()Z"), cancellable = true)
    private void interceptHello(LoginHelloC2SPacket packet, CallbackInfo info) {
        if (!EnvironmentDetector.isPhyClient) {
            mSecServerLoginHandshakeHandler.onVanillaHello(packet);
            info.cancel();
        }
    }

//    @Inject(method = "tick", at = @At(value = "HEAD"))
//    private void tick(CallbackInfo info) {
//        if (!testSent) {
//            connection.send(new LoginQueryRequestS2CPacket(MythicNetwork.QUERY_ID, new LoginTextureCommandS2CPayload(LoginTextureCommandS2CPayload.Command.GET_METADATA)));
//            testSent = true;
//        }
//    }
//
//    @Inject(method = "tickVerify", at = @At(value = "HEAD"), cancellable = true)
//    private void tickVerify(GameProfile profile, CallbackInfo info) {
//        if (!testDone) {
//            info.cancel();
//        }
//    }

    @Override
    public void mythicworldsecurity$onTextureMetadata(LoginTextureMetadataC2SPayload payload) {
//        MythicWorldSecurity.LOGGER.info(payload.toString());
//        boolean skinInCache = ServerTextureService.getCacheFilePathSafely(payload.skinHash()) != null;
//        boolean capeInCache = ServerTextureService.getCacheFilePathSafely(payload.capeHash()) != null;
//        MythicWorldSecurity.LOGGER.info("Skin in cache: {}, cape in cache: {}", skinInCache, capeInCache);
//        ts = System.currentTimeMillis();
//        connection.send(new LoginQueryRequestS2CPacket(MythicNetwork.QUERY_ID, new LoginTextureCommandS2CPayload(LoginTextureCommandS2CPayload.Command.GET_SKIN)));
//        connection.send(new LoginQueryRequestS2CPacket(MythicNetwork.QUERY_ID, new LoginTextureCommandS2CPayload(LoginTextureCommandS2CPayload.Command.GET_CAPE)));
    }

    @Override
    public void mythicworldsecurity$onTextureData(LoginTextureDataC2SPayload payload) {
        ServerTextureService.getInstance().onSubmission((ServerLoginNetworkHandler) (Object) this, payload);
    }

    @Override
    public void mythicworldsecurity$textureFileWriteCallback(LoginTextureDataC2SPayload.TextureType type, boolean successful, String hash) {
//        long t = System.currentTimeMillis() - ts;
//        MythicWorldSecurity.LOGGER.info("Time: {} ms", t);
//        MythicWorldSecurity.LOGGER.info("Type: {}, Hash: {}", type, hash);
//        testDone = true;
    }

    @Override
    public MSecServerLoginHandshakeHandler mythicworldsecurity$getMSecHandshakeHandler() {
        return mSecServerLoginHandshakeHandler;
    }

    @Override
    public void mythicworldsecurity$startVerify(GameProfile profile) {
        startVerify(profile);
    }
}
