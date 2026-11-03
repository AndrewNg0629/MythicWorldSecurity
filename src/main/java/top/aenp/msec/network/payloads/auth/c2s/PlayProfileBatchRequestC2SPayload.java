package top.aenp.msec.network.payloads.auth.c2s;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecServerPlayNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicServerPlayNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicPlayC2SPayload;

import java.util.HashSet;
import java.util.Set;

public record PlayProfileBatchRequestC2SPayload(int queryId, Set<String> names) implements MythicPlayC2SPayload {
    public static final Id<PlayProfileBatchRequestC2SPayload> ID = new Id<>(Identifier.of("msec", "play_profile_batch_request_s2c"));
    public static final PacketCodec<PacketByteBuf, PlayProfileBatchRequestC2SPayload> CODEC = new PacketCodec<>() {
        @Override
        public PlayProfileBatchRequestC2SPayload decode(PacketByteBuf buf) {
            int queryId = buf.readInt();
            int namesCount = buf.readInt();
            HashSet<String> names = new HashSet<>();
            for (int i = 0; i < namesCount; i++) {
                String name = buf.readString();
                names.add(name);
            }
            return new PlayProfileBatchRequestC2SPayload(queryId, names);
        }

        @Override
        public void encode(PacketByteBuf buf, PlayProfileBatchRequestC2SPayload value) {
            buf.writeInt(value.queryId);
            buf.writeInt(value.names.size());
            for (String name : value.names) {
                buf.writeString(name);
            }
        }
    };

    @Override
    public void handle(MythicServerPlayNetworkHandler handler) {
        ((MSecServerPlayNetworkHandler) handler).mythicworldsecurity$onProfileBatchRequest(this);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
