package top.aenp.msec.auth.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.network.ClientConnection;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.auth.AbstractMSecMinecraftSessionService;
import top.aenp.msec.network.payloads.auth.c2s.PlayProfileQueryC2SPayload;
import top.aenp.msec.network.payloads.auth.s2c.PlayProfileResponseS2CPayload;
import top.aenp.mwl.network.v2.MythicNetwork;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ClientMSecMinecraftSessionService extends AbstractMSecMinecraftSessionService {
    private final MinecraftSessionService vanillaSessionService;
    private final ConcurrentHashMap<UUID, CompletableFuture<PlayProfileResponseS2CPayload>> profileRequests = new ConcurrentHashMap<>();
    private volatile ClientConnection clientConnection = null;

    public ClientMSecMinecraftSessionService(MinecraftSessionService vanillaSessionService) {
        this.vanillaSessionService = vanillaSessionService;
    }

    @Override
    public void joinServer(GameProfile profile, String authenticationToken, String apiNonce) throws AuthenticationException {
        vanillaSessionService.joinServer(profile, authenticationToken, apiNonce);
    }

    @Override
    public GameProfile hasJoinedServer(GameProfile user, String serverId, InetAddress address) throws AuthenticationUnavailableException {
        return vanillaSessionService.hasJoinedServer(user, serverId, address);
    }

    @Override
    public GameProfile fillProfileProperties(GameProfile profile, boolean requireSecure) {
        ClientConnection connection = clientConnection;
        if (connection != null) {
            CompletableFuture<PlayProfileResponseS2CPayload> profileFuture = profileRequests.computeIfAbsent(profile.getId(), uuid -> {
                CompletableFuture<PlayProfileResponseS2CPayload> future = new CompletableFuture<>();
                connection.send(MythicNetwork.createPlayC2SPacket(new PlayProfileQueryC2SPayload(profile.getId())));
                return future;
            });
            try {
                PlayProfileResponseS2CPayload payload = profileFuture.get(30, TimeUnit.SECONDS);
                GameProfile gameProfile = payload.gameProfile();
                return Objects.requireNonNullElse(gameProfile, profile);
            } catch (Exception e) {
                MythicWorldSecurity.LOGGER.error("Failed to fetch profile from server.", e);
                return profile;
            }
        } else {
            return vanillaSessionService.fillProfileProperties(profile, false);
        }
    }

    public void onProfileResponse(PlayProfileResponseS2CPayload payload) {
        CompletableFuture<PlayProfileResponseS2CPayload> profileFuture = profileRequests.remove(payload.uuid());
        if (profileFuture != null) {
            profileFuture.complete(payload);
        }
    }

    public void onPlayHandlerReady(ClientConnection connection) {
        clientConnection = connection;
    }

    public void onDisconnected() {
        clientConnection = null;
        Iterator<Map.Entry<UUID, CompletableFuture<PlayProfileResponseS2CPayload>>> iterator = profileRequests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, CompletableFuture<PlayProfileResponseS2CPayload>> entry = iterator.next();
            entry.getValue().complete(new PlayProfileResponseS2CPayload(entry.getKey(), null));
            iterator.remove();
        }
    }
}
