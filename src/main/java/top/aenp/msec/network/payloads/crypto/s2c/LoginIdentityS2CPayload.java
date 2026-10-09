package top.aenp.msec.network.payloads.crypto.s2c;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import top.aenp.msec.auth.MSecEd25519Identity;
import top.aenp.msec.network.interfaces.MSecClientLoginNetworkHandler;
import top.aenp.msec.network.interfaces.SignedPayload;
import top.aenp.mwl.network.v2.interfaces.MythicClientLoginNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginS2CPayload;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.Set;

public record LoginIdentityS2CPayload(PublicKey serverPublicKey, Set<String> supportedHttpsTrustDomains) implements MythicLoginS2CPayload, SignedPayload {
    public static final Identifier ID = Identifier.of("msec", "login_identity_s2c");
    public static final PacketCodec<PacketByteBuf, LoginIdentityS2CPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginIdentityS2CPayload decode(PacketByteBuf buf) {
            byte[] encodedServerPublicKey = buf.readByteArray(44);
            PublicKey serverPublicKey = MSecEd25519Identity.decodeEd25519PublicKey(encodedServerPublicKey);
            int domainsCount = buf.readInt();
            HashSet<String> domains = new HashSet<>();
            for (int i = 0; i < domainsCount; i++) {
                domains.add(buf.readString());
            }
            return new LoginIdentityS2CPayload(serverPublicKey, domains);
        }

        @Override
        public void encode(PacketByteBuf buf, LoginIdentityS2CPayload value) {
            byte[] encodedServerPublicKey = value.serverPublicKey.getEncoded();
            buf.writeByteArray(encodedServerPublicKey);
            buf.writeInt(value.supportedHttpsTrustDomains.size());
            for (String domain : value.supportedHttpsTrustDomains) {
                buf.writeString(domain);
            }
        }
    };

    @Override
    public Identifier mythicId() {
        return ID;
    }

    @Override
    public void handle(MythicClientLoginNetworkHandler handler) {
        ((MSecClientLoginNetworkHandler) handler).mythicworldsecurity$getMSecHandshakeHandler().onServerIdentity(this);
    }

    @Override
    public void writeMessageAsBytes(ByteArrayOutputStream byteArrayOutputStream) {
        byteArrayOutputStream.writeBytes(serverPublicKey.getEncoded());
        for (String domain : supportedHttpsTrustDomains) {
            byteArrayOutputStream.writeBytes(domain.getBytes(StandardCharsets.UTF_8));
        }
    }
}
