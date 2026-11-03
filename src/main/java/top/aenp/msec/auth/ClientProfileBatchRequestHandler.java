package top.aenp.msec.auth;

import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.network.payloads.auth.s2c.PlayProfileBatchResponseS2CPayload;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

public class ClientProfileBatchRequestHandler {
    private static ClientProfileBatchRequestHandler instance = null;
    private final LinkedBlockingQueue<ProfileBatchRequestEntry> queue = new LinkedBlockingQueue<>();

    private ClientProfileBatchRequestHandler() {
        Runnable handlingTask = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ProfileBatchRequestEntry queryEntry = queue.take();
                    Map<String, ServerMSecGameProfileRepository.ProfileQueryResultEntry> lookupResult = ServerMSecGameProfileRepository.INSTANCE.findProfilesByNamesAsMap(queryEntry.names);
                    PlayProfileBatchResponseS2CPayload payload = new PlayProfileBatchResponseS2CPayload(queryEntry.queryId, lookupResult);
                    queryEntry.handler.sendPacket(new CustomPayloadS2CPacket(payload));
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    MythicWorldSecurity.LOGGER.error("Exception in client profile batch request handler!", e);
                }
            }
        };
        Thread.startVirtualThread(handlingTask).setName("MSec Client Profile BATCH Request Handler");
    }

    public void onClientBatchRequest(ServerPlayNetworkHandler handler, int queryId, Set<String> names) {
        queue.add(new ProfileBatchRequestEntry(handler, queryId, names));
    }

    public static void init() {
        if (instance == null) {
            instance = new ClientProfileBatchRequestHandler();
        } else {
            throw new IllegalStateException();
        }
    }

    public static ClientProfileBatchRequestHandler getInstance() {
        if (instance != null) {
            return instance;
        } else {
            throw new IllegalStateException("Client profile batch request handler hasn't been initialized!");
        }
    }

    public record ProfileBatchRequestEntry(ServerPlayNetworkHandler handler, int queryId, Set<String> names) {
    }
}
