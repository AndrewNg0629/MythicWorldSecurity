package top.aenp.msec.network.payloads.texture;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecServerLoginNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicServerLoginNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginC2SPayload;

public record LoginTextureMetadataC2SPayload(String skinHash, boolean slim, String capeHash) implements MythicLoginC2SPayload {
    public static final Identifier ID = Identifier.of("msec", "texture_metadata");
    public static final PacketCodec<PacketByteBuf, LoginTextureMetadataC2SPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginTextureMetadataC2SPayload decode(PacketByteBuf buf) {
            String skinHash = buf.readNullable(PacketCodecs.STRING);
            boolean slim = buf.readBoolean();
            String capeHash = buf.readNullable(PacketCodecs.STRING);
            return new LoginTextureMetadataC2SPayload(skinHash, slim, capeHash);
        }

        @Override
        public void encode(PacketByteBuf buf, LoginTextureMetadataC2SPayload value) {
            buf.writeNullable(value.skinHash, PacketCodecs.STRING);
            buf.writeBoolean(value.slim);
            buf.writeNullable(value.capeHash, PacketCodecs.STRING);
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
