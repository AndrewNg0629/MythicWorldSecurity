package top.aenp.msec.network.payloads.auth.c2s;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecServerLoginNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicServerLoginNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginC2SPayload;

public record LoginContactFinishC2SPayload() implements MythicLoginC2SPayload {
    public static final Identifier ID = Identifier.of("msec", "login_contact_finish_c2s");
    public static final PacketCodec<PacketByteBuf, LoginContactFinishC2SPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginContactFinishC2SPayload decode(PacketByteBuf buf) {
            return new LoginContactFinishC2SPayload();
        }

        @Override
        public void encode(PacketByteBuf buf, LoginContactFinishC2SPayload value) {
        }
    };

    @Override
    public Identifier mythicId() {
        return ID;
    }

    @Override
    public void handle(MythicServerLoginNetworkHandler handler) {
        ((MSecServerLoginNetworkHandler) handler).mythicworldsecurity$getMSecHandshakeHandler().onContactFinish(this);
    }
}
