package top.aenp.msec.network.payloads.crypto.c2s;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import top.aenp.msec.auth.MSecEd25519Identity;
import top.aenp.msec.network.interfaces.MSecServerLoginNetworkHandler;
import top.aenp.msec.network.interfaces.SignedPayload;
import top.aenp.mwl.network.v2.interfaces.MythicServerLoginNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginC2SPayload;

import java.io.ByteArrayOutputStream;
import java.security.PublicKey;

public record LoginIdentityC2SPayload(PublicKey clientPublicKey) implements MythicLoginC2SPayload, SignedPayload {
    public static final Identifier ID = Identifier.of("msec", "login_identity_c2s");
    public static final PacketCodec<PacketByteBuf, LoginIdentityC2SPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginIdentityC2SPayload decode(PacketByteBuf buf) {
            byte[] encodedClientPublicKey = buf.readByteArray(44);
            PublicKey clientPublicKey = MSecEd25519Identity.decodeEd25519PublicKey(encodedClientPublicKey);
            return new LoginIdentityC2SPayload(clientPublicKey);
        }

        @Override
        public void encode(PacketByteBuf buf, LoginIdentityC2SPayload value) {
            byte[] encodedClientPublicKey = value.clientPublicKey.getEncoded();
            buf.writeByteArray(encodedClientPublicKey);
        }
    };

    @Override
    public Identifier mythicId() {
        return ID;
    }

    @Override
    public void handle(MythicServerLoginNetworkHandler handler) {
        ((MSecServerLoginNetworkHandler) handler).mythicworldsecurity$getMSecHandshakeHandler().onClientIdentity(this);
    }

    @Override
    public void writeMessageAsBytes(ByteArrayOutputStream byteArrayOutputStream) {
        byteArrayOutputStream.writeBytes(clientPublicKey.getEncoded());
    }
}
