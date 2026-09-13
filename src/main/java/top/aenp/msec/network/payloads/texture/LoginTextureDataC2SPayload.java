package top.aenp.msec.network.payloads.texture;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecServerLoginNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicServerLoginNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginC2SPayload;

public record LoginTextureDataC2SPayload(TextureType type, int width, int height, byte[] data) implements MythicLoginC2SPayload {
    public static final Identifier ID = Identifier.of("msec", "texture_data");
    public static final PacketCodec<PacketByteBuf, LoginTextureDataC2SPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginTextureDataC2SPayload decode(PacketByteBuf buf) {
            TextureType type = buf.readEnumConstant(TextureType.class);
            int width = buf.readInt();
            int height = buf.readInt();
            byte[] data = buf.readNullable(buf1 -> buf.readByteArray(131072));
            return new LoginTextureDataC2SPayload(type, width, height, data);
        }

        @Override
        public void encode(PacketByteBuf buf, LoginTextureDataC2SPayload value) {
            buf.writeEnumConstant(value.type);
            buf.writeInt(value.width);
            buf.writeInt(value.height);
            buf.writeNullable(value.data, (buf1, value1) -> buf.writeByteArray(value1));
        }
    };

    public enum TextureType {
        SKIN, CAPE
    }

    @Override
    public Identifier mythicId() {
        return ID;
    }

    @Override
    public void handle(MythicServerLoginNetworkHandler handler) {
        ((MSecServerLoginNetworkHandler) handler).mythicworldsecurity$onTextureData(this);
    }
}
