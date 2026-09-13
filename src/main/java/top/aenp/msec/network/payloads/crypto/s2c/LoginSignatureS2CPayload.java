package top.aenp.msec.network.payloads.crypto.s2c;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.SignedPayload;
import top.aenp.mwl.network.v2.interfaces.MythicClientLoginNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginS2CPayload;

import java.io.ByteArrayOutputStream;

public record LoginSignatureS2CPayload(byte[] serverSignature) implements MythicLoginS2CPayload {
    public static final Identifier ID = Identifier.of("msec", "signature_s2c");
    public static final PacketCodec<PacketByteBuf, LoginSignatureS2CPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginSignatureS2CPayload decode(PacketByteBuf buf) {
            byte[] serverSignature = buf.readByteArray(64);
            return new LoginSignatureS2CPayload(serverSignature);
        }

        @Override
        public void encode(PacketByteBuf buf, LoginSignatureS2CPayload value) {
            buf.writeByteArray(value.serverSignature);
        }
    };

    @Override
    public Identifier mythicId() {
        return ID;
    }

    @Override
    public void handle(MythicClientLoginNetworkHandler handler) {

    }
}
