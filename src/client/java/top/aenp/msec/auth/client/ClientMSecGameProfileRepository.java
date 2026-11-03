package top.aenp.msec.auth.client;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.yggdrasil.ProfileNotFoundException;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.auth.ServerMSecGameProfileRepository;
import top.aenp.msec.network.payloads.auth.c2s.PlayProfileBatchRequestC2SPayload;
import top.aenp.msec.network.payloads.auth.s2c.PlayProfileBatchResponseS2CPayload;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientMSecGameProfileRepository implements GameProfileRepository {
    private static final ConcurrentHashMap<Integer, CompletableFuture<PlayProfileBatchResponseS2CPayload>> BATCH_PROFILE_REQUESTS = new ConcurrentHashMap<>();
    private static volatile ClientConnection clientConnection = null;
    private static final AtomicInteger QUERY_ID = new AtomicInteger(0);
    private final GameProfileRepository vanillaGameProfileRepo;

    public ClientMSecGameProfileRepository(GameProfileRepository vanillaGameProfileRepo) {
        this.vanillaGameProfileRepo = vanillaGameProfileRepo;
    }

    @Override
    public void findProfilesByNames(String[] names, ProfileLookupCallback callback) {
        Set<String> nameSet = Set.copyOf(List.of(names));
        ClientConnection connection = clientConnection;
        if (connection != null) {
            CompletableFuture<PlayProfileBatchResponseS2CPayload> batchProfileFuture = BATCH_PROFILE_REQUESTS.computeIfAbsent(QUERY_ID.getAndIncrement(), queryId -> {
                CompletableFuture<PlayProfileBatchResponseS2CPayload> future = new CompletableFuture<>();
                PlayProfileBatchRequestC2SPayload payload = new PlayProfileBatchRequestC2SPayload(queryId, nameSet);
                clientConnection.send(new CustomPayloadC2SPacket(payload));
                return future;
            });
            try {
                PlayProfileBatchResponseS2CPayload payload = batchProfileFuture.get(30, TimeUnit.SECONDS);
                Map<String, ServerMSecGameProfileRepository.ProfileQueryResultEntry> lookupResult = payload.queryResult();
                for (Map.Entry<String, ServerMSecGameProfileRepository.ProfileQueryResultEntry> entry : lookupResult.entrySet()) {
                    ServerMSecGameProfileRepository.ProfileQueryResultEntry queryResult = entry.getValue();
                    if (queryResult.profile() != null) {
                        callback.onProfileLookupSucceeded(queryResult.profile());
                    } else {
                        if (queryResult.isNotFound()) {
                            callback.onProfileLookupFailed(entry.getKey(), new ProfileNotFoundException("Requested profile was not found."));
                        } else {
                            callback.onProfileLookupFailed(entry.getKey(), new AuthenticationException("Unknown exception."));
                        }
                    }
                }
            } catch (Exception e) {
                MythicWorldSecurity.LOGGER.error("Failed to fetch profile in batch from server.", e);
                for (String name : nameSet) {
                    callback.onProfileLookupFailed(name, new AuthenticationException(e));
                }
            }
        } else {
            vanillaGameProfileRepo.findProfilesByNames(names, callback);
        }
    }

    public static void onProfileResponse(PlayProfileBatchResponseS2CPayload payload) {
        CompletableFuture<PlayProfileBatchResponseS2CPayload> profileFuture = BATCH_PROFILE_REQUESTS.remove(payload.queryId());
        if (profileFuture != null) {
            profileFuture.complete(payload);
        }
    }

    public static void onPlayHandlerReady(ClientConnection connection) {
        clientConnection = connection;
    }

    public static void onDisconnected() {
        clientConnection = null;
        Iterator<Map.Entry<Integer, CompletableFuture<PlayProfileBatchResponseS2CPayload>>> iterator = BATCH_PROFILE_REQUESTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, CompletableFuture<PlayProfileBatchResponseS2CPayload>> entry = iterator.next();
            entry.getValue().complete(new PlayProfileBatchResponseS2CPayload(entry.getKey(), Map.of()));
            iterator.remove();
        }
    }
}
