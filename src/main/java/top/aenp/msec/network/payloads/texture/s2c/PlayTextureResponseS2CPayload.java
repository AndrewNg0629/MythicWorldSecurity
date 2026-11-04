package top.aenp.msec.network.payloads.texture.s2c;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecClientPlayNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicClientPlayNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.PacketCodec;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicPlayS2CPayload;

public record PlayTextureResponseS2CPayload(String hash, byte[] data) implements MythicPlayS2CPayload {
    public static final Identifier ID = new Identifier("msec", "play_texture_response");
    public static final PacketCodec<PacketByteBuf, PlayTextureResponseS2CPayload> CODEC = new PacketCodec<>() {
        @Override
        public PlayTextureResponseS2CPayload decode(PacketByteBuf buf) {
            String hash = buf.readString();
            byte[] data = buf.readNullable(PacketByteBuf::readByteArray);
            return new PlayTextureResponseS2CPayload(hash, data);
        }

        @Override
        public void encode(PacketByteBuf buf, PlayTextureResponseS2CPayload value) {
            buf.writeString(value.hash);
            buf.writeNullable(value.data, PacketByteBuf::writeByteArray);
        }
    };

    @Override
    public void handle(MythicClientPlayNetworkHandler handler) {
        ((MSecClientPlayNetworkHandler) handler).mythicworldsecurity$onTextureResponse(this);
    }

    @Override
    public Identifier mythicId() {
        return ID;
    }
}
