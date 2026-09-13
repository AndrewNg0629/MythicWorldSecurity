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

//TODO Replace boolean flag with a list of supported domain names for https trust.
public record LoginIdentityS2CPayload(PublicKey serverPublicKey, boolean useHttpsTrustMode) implements MythicLoginS2CPayload, SignedPayload {
    public static final Identifier ID = Identifier.of("msec", "login_identity_s2c");
    public static final PacketCodec<PacketByteBuf, LoginIdentityS2CPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginIdentityS2CPayload decode(PacketByteBuf buf) {
            byte[] encodedServerPublicKey = buf.readByteArray(44);
            PublicKey serverPublicKey = MSecEd25519Identity.decodePublicKey(encodedServerPublicKey);
            boolean useHttpsTrustMode = buf.readBoolean();
            return new LoginIdentityS2CPayload(serverPublicKey, useHttpsTrustMode);
        }

        @Override
        public void encode(PacketByteBuf buf, LoginIdentityS2CPayload value) {
            byte[] encodedServerPublicKey = value.serverPublicKey.getEncoded();
            buf.writeByteArray(encodedServerPublicKey);
            buf.writeBoolean(value.useHttpsTrustMode);
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
        byteArrayOutputStream.writeBytes(serverPublicKey.getEncoded());
        byteArrayOutputStream.write(useHttpsTrustMode ? 1 : 0);
    }
}
