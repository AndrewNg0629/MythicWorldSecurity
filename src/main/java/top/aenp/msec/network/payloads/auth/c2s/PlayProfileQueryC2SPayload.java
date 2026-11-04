package top.aenp.msec.network.payloads.auth.c2s;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecServerPlayNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicServerPlayNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.PacketCodec;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicPlayC2SPayload;
import java.util.UUID;

public record PlayProfileQueryC2SPayload(UUID uuid) implements MythicPlayC2SPayload {
    public static final Identifier ID = new Identifier("msec", "play_profile_query_c2s");
    public static final PacketCodec<PacketByteBuf, PlayProfileQueryC2SPayload> CODEC = new PacketCodec<>() {
        @Override
        public PlayProfileQueryC2SPayload decode(PacketByteBuf buf) {
            UUID uuid = buf.readUuid();
            return new PlayProfileQueryC2SPayload(uuid);
        }

        @Override
        public void encode(PacketByteBuf buf, PlayProfileQueryC2SPayload value) {
            buf.writeUuid(value.uuid);
        }
    };

    @Override
    public void handle(MythicServerPlayNetworkHandler handler) {
        ((MSecServerPlayNetworkHandler) handler).mythicworldsecurity$onProfileQuery(this);
    }

    @Override
    public Identifier mythicId() {
        return ID;
    }
}
