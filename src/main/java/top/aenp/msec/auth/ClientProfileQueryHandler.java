package top.aenp.msec.auth;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.network.payloads.auth.s2c.PlayProfileResponseS2CPayload;
import top.aenp.mwl.network.v2.MythicNetwork;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

public class ClientProfileQueryHandler {
    private final LinkedBlockingQueue<ClientProfileQueryEntry> queue = new LinkedBlockingQueue<>();
    private static ClientProfileQueryHandler instance = null;

    private ClientProfileQueryHandler() {
        Runnable handlingTask = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ClientProfileQueryEntry queryEntry = queue.take();
                    GameProfile gameProfile = ServerMSecMinecraftSessionService.INSTANCE.fillProfileProperties(new GameProfile(queryEntry.uuid, null), false);
                    queryEntry.handler.sendPacket(MythicNetwork.createPlayS2CPacket(new PlayProfileResponseS2CPayload(queryEntry.uuid, gameProfile)));
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    MythicWorldSecurity.LOGGER.error("Exception in client profile query handler!", e);
                }
            }
        };
        Thread handlerThread = new Thread(handlingTask, "MSec Client Profile Query Handler");
        handlerThread.setDaemon(true);
        handlerThread.start();
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
