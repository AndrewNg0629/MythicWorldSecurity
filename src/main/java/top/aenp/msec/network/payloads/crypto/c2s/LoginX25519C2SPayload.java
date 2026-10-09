package top.aenp.msec.network.payloads.crypto.c2s;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.MSecNetwork;
import top.aenp.msec.network.interfaces.MSecServerLoginNetworkHandler;
import top.aenp.msec.network.interfaces.SignedPayload;
import top.aenp.mwl.network.v2.interfaces.MythicServerLoginNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginC2SPayload;

import java.io.ByteArrayOutputStream;
import java.security.PublicKey;

public record LoginX25519C2SPayload(PublicKey clientX25519PublicKey) implements MythicLoginC2SPayload, SignedPayload {
    public static final Identifier ID = Identifier.of("msec", "login_x25519_c2s");
    public static final PacketCodec<PacketByteBuf, LoginX25519C2SPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginX25519C2SPayload decode(PacketByteBuf buf) {
            byte[] encodedClientX25519PublicKey = buf.readByteArray(44);
            PublicKey clientX25519PublicKey = MSecNetwork.decodeX25519PublicKey(encodedClientX25519PublicKey);
            return new LoginX25519C2SPayload(clientX25519PublicKey);
        }

        @Override
        public void encode(PacketByteBuf buf, LoginX25519C2SPayload value) {
            byte[] encodedClientX25519PublicKey = value.clientX25519PublicKey.getEncoded();
            buf.writeByteArray(encodedClientX25519PublicKey);
        }
    };

    @Override
    public Identifier mythicId() {
        return ID;
    }

    @Override
    public void handle(MythicServerLoginNetworkHandler handler) {
        ((MSecServerLoginNetworkHandler) handler).mythicworldsecurity$getMSecHandshakeHandler().onClientX25519(this);
    }

    @Override
    public void writeMessageAsBytes(ByteArrayOutputStream byteArrayOutputStream) {
        byteArrayOutputStream.writeBytes(clientX25519PublicKey.getEncoded());
    }
}
