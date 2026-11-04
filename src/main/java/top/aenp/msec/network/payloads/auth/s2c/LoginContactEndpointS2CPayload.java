package top.aenp.msec.network.payloads.auth.s2c;

import net.minecraft.network.PacketByteBuf;
import top.aenp.mwl.network.v2.interfaces.PacketCodec;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecClientLoginNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicClientLoginNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginS2CPayload;

public record LoginContactEndpointS2CPayload(String apiNonce) implements MythicLoginS2CPayload {
    public static final Identifier ID = new Identifier("msec", "login_contact_endpoint_s2c");
    public static final PacketCodec<PacketByteBuf, LoginContactEndpointS2CPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginContactEndpointS2CPayload decode(PacketByteBuf buf) {
            String apiNonce = buf.readString();
            return new LoginContactEndpointS2CPayload(apiNonce);
        }

        @Override
        public void encode(PacketByteBuf buf, LoginContactEndpointS2CPayload value) {
            buf.writeString(value.apiNonce);
        }
    };

    @Override
    public Identifier mythicId() {
        return ID;
    }

    @Override
    public void handle(MythicClientLoginNetworkHandler handler) {
        ((MSecClientLoginNetworkHandler) handler).mythicworldsecurity$getMSecHandshakeHandler().onContactEndpoint(this);
    }
}
