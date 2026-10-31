package top.aenp.msec.network.payloads.auth.s2c;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecClientLoginNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicClientLoginNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginS2CPayload;

import java.util.HashSet;
import java.util.Set;

public record LoginAllowedSkinDomainsS2CPayload(Set<String> allowedSkinDomains) implements MythicLoginS2CPayload {
    public static final Identifier ID = Identifier.of("msec", "login_allowed_skin_domains_s2c");
    public static final PacketCodec<PacketByteBuf, LoginAllowedSkinDomainsS2CPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginAllowedSkinDomainsS2CPayload decode(PacketByteBuf buf) {
            int domainsCount = buf.readInt();
            HashSet<String> allowedTextureDomains = new HashSet<>();
            for (int i = 0; i < domainsCount; i++) {
                String domain = buf.readString();
                allowedTextureDomains.add(domain);
            }
            return new LoginAllowedSkinDomainsS2CPayload(allowedTextureDomains);
        }

        @Override
        public void encode(PacketByteBuf buf, LoginAllowedSkinDomainsS2CPayload value) {
            buf.writeInt(value.allowedSkinDomains.size());
            for (String domain : value.allowedSkinDomains) {
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
        ((MSecClientLoginNetworkHandler) handler).mythicworldsecurity$getMSecHandshakeHandler().onSkinDomains(this);
    }
}
