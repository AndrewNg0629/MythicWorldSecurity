package top.aenp.msec.network.payloads.crypto.c2s;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecServerLoginNetworkHandler;
import top.aenp.msec.network.interfaces.SignedPayload;
import top.aenp.mwl.network.v2.interfaces.MythicServerLoginNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginC2SPayload;

import java.io.ByteArrayOutputStream;

public record LoginSignatureC2SPayload(byte[] clientSignature) implements MythicLoginC2SPayload {
    public static final Identifier ID = Identifier.of("msec", "signature_c2s");
    public static final PacketCodec<PacketByteBuf, LoginSignatureC2SPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginSignatureC2SPayload decode(PacketByteBuf buf) {
            byte[] clientSignature = buf.readByteArray(64);
            return new LoginSignatureC2SPayload(clientSignature);
        }

        @Override
        public void encode(PacketByteBuf buf, LoginSignatureC2SPayload value) {
            buf.writeByteArray(value.clientSignature);
        }
    };

    @Override
    public Identifier mythicId() {
        return ID;
    }

    @Override
    public void handle(MythicServerLoginNetworkHandler handler) {
        ((MSecServerLoginNetworkHandler) handler).mythicworldsecurity$getMSecHandshakeHandler().onClientSignature(this);
    }
}
