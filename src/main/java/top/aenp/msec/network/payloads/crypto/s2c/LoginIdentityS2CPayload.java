package top.aenp.msec.network.payloads.crypto.s2c;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecClientLoginNetworkHandler;
import top.aenp.msec.network.interfaces.SignedPayload;
import top.aenp.msec.trust.MSecEd25519Identity;
import top.aenp.mwl.network.v2.interfaces.MythicClientLoginNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.PacketCodec;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginS2CPayload;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public record LoginIdentityS2CPayload(
        PublicKey serverPublicKey,
        List<String> dnsHttpsMetaDomains,
        List<String> directHttpsMetaDomains
) implements MythicLoginS2CPayload, SignedPayload {
    public static final Identifier ID = new Identifier("msec", "login_identity_s2c");
    public static final PacketCodec<PacketByteBuf, LoginIdentityS2CPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginIdentityS2CPayload decode(PacketByteBuf buf) {
            byte[] encodedServerPublicKey = buf.readByteArray(44);
            PublicKey serverPublicKey = MSecEd25519Identity.decodeEd25519PublicKey(encodedServerPublicKey);
            int dnsHttpsDomainsCount = buf.readInt();
            ArrayList<String> dnsHttpsMetaDomains = new ArrayList<>();
            for (int i = 0; i < dnsHttpsDomainsCount; i++) {
                dnsHttpsMetaDomains.add(buf.readString());
            }
            int directHttpsDomainsCount = buf.readInt();
            ArrayList<String> directHttpsMetaDomains = new ArrayList<>();
            for (int i = 0; i < directHttpsDomainsCount; i++) {
                directHttpsMetaDomains.add(buf.readString());
            }
            return new LoginIdentityS2CPayload(serverPublicKey, dnsHttpsMetaDomains, directHttpsMetaDomains);
        }

        @Override
        public void encode(PacketByteBuf buf, LoginIdentityS2CPayload value) {
            byte[] encodedServerPublicKey = value.serverPublicKey.getEncoded();
            buf.writeByteArray(encodedServerPublicKey);
            buf.writeInt(value.dnsHttpsMetaDomains.size());
            for (String domain : value.dnsHttpsMetaDomains) {
                buf.writeString(domain);
            }
            buf.writeInt(value.directHttpsMetaDomains.size());
            for (String domain : value.directHttpsMetaDomains) {
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
        for (String domain : dnsHttpsMetaDomains) {
            byteArrayOutputStream.writeBytes(domain.getBytes(StandardCharsets.UTF_8));
        }
        for (String domain : directHttpsMetaDomains) {
            byteArrayOutputStream.writeBytes(domain.getBytes(StandardCharsets.UTF_8));
        }
    }
}
