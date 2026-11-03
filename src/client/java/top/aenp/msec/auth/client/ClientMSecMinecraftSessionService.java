package top.aenp.msec.auth.client;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.auth.AbstractMSecMinecraftSessionService;
import top.aenp.msec.network.payloads.auth.c2s.PlayProfileQueryC2SPayload;
import top.aenp.msec.network.payloads.auth.s2c.PlayProfileResponseS2CPayload;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.Map;
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
    public void joinServer(UUID profileId, String authenticationToken, String apiNonce) throws AuthenticationException {
        vanillaSessionService.joinServer(profileId, authenticationToken, apiNonce);
    }

    @Override
    public ProfileResult hasJoinedServer(String profileName, String serverId, InetAddress address) throws AuthenticationUnavailableException {
        return vanillaSessionService.hasJoinedServer(profileName, serverId, address);
    }

    @Override
    public ProfileResult fetchProfile(UUID profileId, boolean requireSecure) {
        ClientConnection connection = clientConnection;
        if (connection != null) {
            CompletableFuture<PlayProfileResponseS2CPayload> profileFuture = profileRequests.computeIfAbsent(profileId, uuid -> {
                CompletableFuture<PlayProfileResponseS2CPayload> future = new CompletableFuture<>();
                connection.send(new CustomPayloadC2SPacket(new PlayProfileQueryC2SPayload(profileId)));
                return future;
            });
            try {
                PlayProfileResponseS2CPayload payload = profileFuture.get(30, TimeUnit.SECONDS);
                return payload.profileResult();
            } catch (Exception e) {
                MythicWorldSecurity.LOGGER.error("Failed to fetch profile from server.", e);
                return null;
            }
        } else {
            return vanillaSessionService.fetchProfile(profileId, requireSecure);
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
