package top.aenp.msec.auth;

import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.network.payloads.auth.s2c.PlayProfileResponseS2CPayload;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

public class ClientProfileQueryHandler {
    private static ClientProfileQueryHandler instance = null;
    private final LinkedBlockingQueue<ClientProfileQueryEntry> queue = new LinkedBlockingQueue<>();

    private ClientProfileQueryHandler() {
        Runnable handlingTask = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ClientProfileQueryEntry queryEntry = queue.take();
                    ProfileResult profileResult = ServerMSecMinecraftSessionService.INSTANCE.fetchProfile(queryEntry.uuid, false);
                    queryEntry.handler.sendPacket(new CustomPayloadS2CPacket(new PlayProfileResponseS2CPayload(queryEntry.uuid, profileResult)));
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    MythicWorldSecurity.LOGGER.error("Exception in client profile query handler!", e);
                }
            }
        };
        Thread.startVirtualThread(handlingTask).setName("MSec Client Profile Query Handler");
    }

    public static void init() {
        if (instance == null) {
            instance = new ClientProfileQueryHandler();
        } else {
            throw new IllegalStateException();
        }
    }

    public static ClientProfileQueryHandler getInstance() {
        if (instance != null) {
            return instance;
        } else {
            throw new IllegalStateException("Client profile query handler hasn't been initialized!");
        }
    }

    public void onClientProfileQuery(ServerPlayNetworkHandler handler, UUID uuid) {
        queue.add(new ClientProfileQueryEntry(handler, uuid));
    }

    public record ClientProfileQueryEntry(ServerPlayNetworkHandler handler, UUID uuid) {
    }
}
