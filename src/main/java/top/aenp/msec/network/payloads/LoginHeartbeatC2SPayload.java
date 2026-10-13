package top.aenp.msec.network.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.mwl.network.v2.interfaces.MythicClientLoginNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicServerLoginNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginC2SPayload;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginS2CPayload;

public record LoginHeartbeatC2SPayload() implements MythicLoginC2SPayload {
    public static final Identifier ID = Identifier.of("msec", "login_heartbeat_c2s");
    public static final PacketCodec<PacketByteBuf, LoginHeartbeatC2SPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginHeartbeatC2SPayload decode(PacketByteBuf buf) {
            return new LoginHeartbeatC2SPayload();
        }

        @Override
        public void encode(PacketByteBuf buf, LoginHeartbeatC2SPayload value) {
        }
    };

    @Override
    public Identifier mythicId() {
        return ID;
    }

    @Override
    public void handle(MythicServerLoginNetworkHandler handler) {
    }
}
