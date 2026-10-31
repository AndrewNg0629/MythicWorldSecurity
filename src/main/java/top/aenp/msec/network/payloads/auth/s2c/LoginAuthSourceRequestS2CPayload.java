package top.aenp.msec.network.payloads.auth.s2c;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecClientLoginNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicClientLoginNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginS2CPayload;

public record LoginAuthSourceRequestS2CPayload() implements MythicLoginS2CPayload {
    public static final Identifier ID = Identifier.of("msec", "login_auth_source_request");
    public static final PacketCodec<PacketByteBuf, LoginAuthSourceRequestS2CPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginAuthSourceRequestS2CPayload decode(PacketByteBuf buf) {
            return new LoginAuthSourceRequestS2CPayload();
        }

        @Override
        public void encode(PacketByteBuf buf, LoginAuthSourceRequestS2CPayload value) {
        }
    };

    @Override
    public Identifier mythicId() {
        return ID;
    }

    @Override
    public void handle(MythicClientLoginNetworkHandler handler) {
        ((MSecClientLoginNetworkHandler) handler).mythicworldsecurity$getMSecHandshakeHandler().onAuthSourceRequest(this);
    }
}
