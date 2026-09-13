package top.aenp.msec.network.payloads.crypto.s2c;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import top.aenp.msec.auth.MSecEd25519Identity;
import top.aenp.msec.network.interfaces.SignedPayload;
import top.aenp.mwl.network.v2.interfaces.MythicClientLoginNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginS2CPayload;

import java.io.ByteArrayOutputStream;
import java.security.PublicKey;

public record LoginX25519S2CPayload(PublicKey serverX25519PublicKey, byte[] KDSalt) implements MythicLoginS2CPayload, SignedPayload {
    public static final Identifier ID = Identifier.of("msec", "login_x25519_s2c");
    public static final PacketCodec<PacketByteBuf, LoginX25519S2CPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginX25519S2CPayload decode(PacketByteBuf buf) {
            byte[] encodedServerX25519PublicKey = buf.readByteArray(44);
            PublicKey serverX25519PublicKey = MSecEd25519Identity.decodePublicKey(encodedServerX25519PublicKey);
            byte[] KDSalt = buf.readByteArray(32);
            return new LoginX25519S2CPayload(serverX25519PublicKey, KDSalt);
        }

        @Override
        public void encode(PacketByteBuf buf, LoginX25519S2CPayload value) {
            byte[] encodedServerX25519PublicKey = value.serverX25519PublicKey.getEncoded();
            buf.writeByteArray(encodedServerX25519PublicKey);
            buf.writeByteArray(value.KDSalt);
        }
    };

    @Override
    public Identifier mythicId() {
        return ID;
    }

    @Override
    public void handle(MythicClientLoginNetworkHandler handler) {

    }

    @Override
    public void writeMessageAsBytes(ByteArrayOutputStream byteArrayOutputStream) {
        byteArrayOutputStream.writeBytes(serverX25519PublicKey.getEncoded());
        byteArrayOutputStream.writeBytes(KDSalt);
    }
}
