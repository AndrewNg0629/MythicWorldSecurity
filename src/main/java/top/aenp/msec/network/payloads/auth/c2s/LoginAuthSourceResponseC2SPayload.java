package top.aenp.msec.network.payloads.auth.c2s;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import top.aenp.msec.auth.YggdrasilApiMetadata;
import top.aenp.msec.network.interfaces.MSecServerLoginNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicServerLoginNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.PacketCodec;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginC2SPayload;

import java.util.HashSet;

public record LoginAuthSourceResponseC2SPayload(
        AuthSourceType authSourceType,
        YggdrasilApiMetadata yggdrasilApiMetadata
) implements MythicLoginC2SPayload {
    public static final Identifier ID = new Identifier("msec", "login_auth_source_response");
    public static final PacketCodec<PacketByteBuf, LoginAuthSourceResponseC2SPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginAuthSourceResponseC2SPayload decode(PacketByteBuf buf) {
            AuthSourceType authSourceType = buf.readEnumConstant(AuthSourceType.class);
            YggdrasilApiMetadata yggdrasilApiMetadata = null;
            boolean metadataPresent = buf.readBoolean();
            if (metadataPresent) {
                String metaServerName = buf.readString();
                String apiRootUrl = buf.readString();
                String homepageUrl = buf.readString();
                HashSet<String> skinDomains = new HashSet<>();
                int skinDomainsListSize = buf.readInt();
                for (int i = 0; i < skinDomainsListSize; i++) {
                    String skinDomain = buf.readString();
                    skinDomains.add(skinDomain);
                }
                yggdrasilApiMetadata = new YggdrasilApiMetadata(metaServerName, apiRootUrl, homepageUrl, skinDomains);
            }
            return new LoginAuthSourceResponseC2SPayload(authSourceType, yggdrasilApiMetadata);
        }

        @Override
        public void encode(PacketByteBuf buf, LoginAuthSourceResponseC2SPayload value) {
            buf.writeEnumConstant(value.authSourceType);
            YggdrasilApiMetadata metadata = value.yggdrasilApiMetadata;
            if (metadata != null) {
                buf.writeBoolean(true);
                buf.writeString(metadata.metaServerName());
                buf.writeString(metadata.apiRootUrl());
                buf.writeString(metadata.homepageUrl());
                buf.writeInt(metadata.skinDomains().size());
                for (String skinDomain : metadata.skinDomains()) {
                    buf.writeString(skinDomain);
                }
            } else {
                buf.writeBoolean(false);
            }
        }
    };

    @Override
    public Identifier mythicId() {
        return ID;
    }

    @Override
    public void handle(MythicServerLoginNetworkHandler handler) {
        ((MSecServerLoginNetworkHandler) handler).mythicworldsecurity$getMSecHandshakeHandler().onAuthSourceMetadata(this);
    }

    public enum AuthSourceType {
        MOJANG, YGGDRASIL, OFFLINE
    }
}
