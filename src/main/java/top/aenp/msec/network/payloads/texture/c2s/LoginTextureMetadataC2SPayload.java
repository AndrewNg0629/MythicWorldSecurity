package top.aenp.msec.network.payloads.texture.c2s;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecServerLoginNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicServerLoginNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.PacketCodec;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginC2SPayload;

public record LoginTextureMetadataC2SPayload(
        String skinHash,
        boolean slim,
        String capeHash
) implements MythicLoginC2SPayload {
    public static final Identifier ID = new Identifier("msec", "texture_metadata");
    public static final PacketCodec<PacketByteBuf, LoginTextureMetadataC2SPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginTextureMetadataC2SPayload decode(PacketByteBuf buf) {
            String skinHash = buf.readNullable(PacketByteBuf::readString);
            boolean slim = buf.readBoolean();
            String capeHash = buf.readNullable(PacketByteBuf::readString);
            return new LoginTextureMetadataC2SPayload(skinHash, slim, capeHash);
        }

        @Override
        public void encode(PacketByteBuf buf, LoginTextureMetadataC2SPayload value) {
            buf.writeNullable(value.skinHash, PacketByteBuf::writeString);
            buf.writeBoolean(value.slim);
            buf.writeNullable(value.capeHash, PacketByteBuf::writeString);
        }
    };

    @Override
    public Identifier mythicId() {
        return ID;
    }

    @Override
    public void handle(MythicServerLoginNetworkHandler handler) {
        ((MSecServerLoginNetworkHandler) handler).mythicworldsecurity$onTextureMetadata(this);
    }
}
