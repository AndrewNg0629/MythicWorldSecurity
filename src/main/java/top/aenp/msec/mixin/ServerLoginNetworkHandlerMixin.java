package top.aenp.msec.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aenp.msec.network.MSecLoginHandshakeHandler;
import top.aenp.msec.network.interfaces.MSecServerLoginNetworkHandler;
import top.aenp.msec.network.payloads.texture.LoginTextureDataC2SPayload;
import top.aenp.msec.network.payloads.texture.LoginTextureMetadataC2SPayload;

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

    @Unique
    private final MSecLoginHandshakeHandler msecHandshakeHandler = new MSecLoginHandshakeHandler(connection, (ServerLoginNetworkHandler) (Object) this);
//    @Unique
//    private boolean testSent = false;
//    @Unique
//    private boolean testDone = false;
//    @Unique
//    private long ts = 0L;

//    @Inject(method = "startVerify", at = @At(value = "HEAD"))
//    private void rewriteProfile(GameProfile profile, CallbackInfo info) {
//        if(!EnvironmentDetector.isPhyClient) {
//            HashMap<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = new HashMap<>();
//            MinecraftProfileTexture skinTexture = new MinecraftProfileTexture("https://example.com/msecofflinetextures/6e8c08bdc69709032d6cf708efba03989eb32e58", null);
//            MinecraftProfileTexture capeTexture = new MinecraftProfileTexture("https://example.com/msecofflinetextures/cb2b84510e457c3f746f55fae6f7cbadf52c40e2", null);
//            textures.put(MinecraftProfileTexture.Type.SKIN, skinTexture);
//            textures.put(MinecraftProfileTexture.Type.CAPE, capeTexture);
//            MinecraftTexturesPayload payload = new MinecraftTexturesPayload(
//                    System.currentTimeMillis(),
//                    profile.getId(),
//                    profile.getName(),
//                    false,
//                    textures
//            );
//            String rewrittenPayload = Utils.PROP_GSON.toJson(payload);
//            String encodedRewrittenPayload = Base64.getEncoder().encodeToString(rewrittenPayload.getBytes(StandardCharsets.UTF_8));
//            profile.getProperties().removeAll("textures");
//            profile.getProperties().put("textures", new Property("textures", encodedRewrittenPayload));
//        }
//    }

    @Inject(method = "onHello", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;isOnlineMode()Z"), cancellable = true)
    private void interceptHello(LoginHelloC2SPacket packet, CallbackInfo info) {
//        if (server.isOnlineMode() && !this.connection.isLocal()) {
//            GameProfile gameProfile = new GameProfile(Uuids.getOfflinePlayerUuid(profileName), profileName + "_test");
//            HashMap<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = new HashMap<>();
//            MinecraftProfileTexture skinTexture = new MinecraftProfileTexture("https://example.com/msecofflinetextures/49d53d3894c4bf776518b384ced33f56e1d78a19", null);
//            MinecraftProfileTexture capeTexture = new MinecraftProfileTexture("https://example.com/msecofflinetextures/ed1cb191817b61be89acebd65e94a98d651119b8", null);
//            textures.put(MinecraftProfileTexture.Type.SKIN, skinTexture);
//            textures.put(MinecraftProfileTexture.Type.CAPE, capeTexture);
//            MinecraftTexturesPayload payload = new MinecraftTexturesPayload(
//                    System.currentTimeMillis(),
//                    gameProfile.getId(),
//                    gameProfile.getName(),
//                    false,
//                    textures
//            );
//            String rewrittenPayload = Utils.PROP_GSON.toJson(payload);
//            String encodedRewrittenPayload = Base64.getEncoder().encodeToString(rewrittenPayload.getBytes(StandardCharsets.UTF_8));
//            gameProfile.getProperties().put("textures", new Property("textures", encodedRewrittenPayload));
//            startVerify(gameProfile);
//        } else {
//            startVerify(Uuids.getOfflinePlayerProfile(this.profileName));
//        }
        msecHandshakeHandler.onVanillaHello(packet);
        info.cancel();
    }

//    @Inject(method = "onHello", at = @At(value = "HEAD"))
//    private void onHello(LoginHelloC2SPacket packet, CallbackInfo info) {
//
//    }


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
//        ServerTextureService.INSTANCE.onSubmission((ServerLoginNetworkHandler) (Object) this, payload);
    }

    @Override
    public void mythicworldsecurity$textureFileWriteCallback(LoginTextureDataC2SPayload.TextureType type, boolean successful, String hash) {
//        long t = System.currentTimeMillis() - ts;
//        MythicWorldSecurity.LOGGER.info("Time: {} ms", t);
//        MythicWorldSecurity.LOGGER.info("Type: {}, Hash: {}", type, hash);
//        testDone = true;
    }

    @Override
    public MSecLoginHandshakeHandler mythicworldsecurity$getMSecHandshakeHandler() {
        return msecHandshakeHandler;
    }
}
