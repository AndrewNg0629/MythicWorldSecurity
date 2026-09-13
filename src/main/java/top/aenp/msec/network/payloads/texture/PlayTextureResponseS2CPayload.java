package top.aenp.msec.network.payloads.texture;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecClientPlayNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicClientPlayNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicPlayS2CPayload;

public record PlayTextureResponseS2CPayload(String hash, byte[] data) implements MythicPlayS2CPayload {
    public static final Id<PlayTextureResponseS2CPayload> ID = new Id<>(Identifier.of("msec", "play_texture_response"));
    public static final PacketCodec<PacketByteBuf, PlayTextureResponseS2CPayload> CODEC = new PacketCodec<>() {
        @Override
        public PlayTextureResponseS2CPayload decode(PacketByteBuf buf) {
            String hash = buf.readString();
            byte[] data = buf.readNullable(buf0 -> buf0.readByteArray());
            return new PlayTextureResponseS2CPayload(hash, data);
        }

        @Override
        public void encode(PacketByteBuf buf, PlayTextureResponseS2CPayload value) {
            buf.writeString(value.hash);
            buf.writeNullable(value.data, (buf0, data0) -> buf0.writeByteArray(data0));
        }
    };

    @Override
    public void handle(MythicClientPlayNetworkHandler handler) {
        ((MSecClientPlayNetworkHandler) handler).mythicworldsecurity$onTextureResponse(this);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
