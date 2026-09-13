package top.aenp.msec.network.payloads.texture;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecServerPlayNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicServerPlayNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicPlayC2SPayload;

public record PlayTextureRequestC2SPayload(String textureHash) implements MythicPlayC2SPayload {
    public static final Id<PlayTextureRequestC2SPayload> ID = new Id<>(Identifier.of("msec", "play_texture_request"));
    public static final PacketCodec<PacketByteBuf, PlayTextureRequestC2SPayload> CODEC = new PacketCodec<>() {
        @Override
        public PlayTextureRequestC2SPayload decode(PacketByteBuf buf) {
            return new PlayTextureRequestC2SPayload(buf.readString());
        }

        @Override
        public void encode(PacketByteBuf buf, PlayTextureRequestC2SPayload value) {
            buf.writeString(value.textureHash);
        }
    };

    @Override
    public void handle(MythicServerPlayNetworkHandler handler) {
        ((MSecServerPlayNetworkHandler) handler).mythicworldsecurity$onTextureRequest(this);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
