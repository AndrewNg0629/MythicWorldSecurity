package top.aenp.msec.network.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.network.interfaces.MSecClientLoginNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicClientLoginNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginS2CPayload;

public record LoginHeartbeatS2CPayload(int remainingSeconds) implements MythicLoginS2CPayload {
    public static final Identifier ID = Identifier.of("msec", "login_heartbeat_s2c");
    public static final PacketCodec<PacketByteBuf, LoginHeartbeatS2CPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginHeartbeatS2CPayload decode(PacketByteBuf buf) {
            return new LoginHeartbeatS2CPayload(buf.readInt());
        }

        @Override
        public void encode(PacketByteBuf buf, LoginHeartbeatS2CPayload value) {
            buf.writeInt(value.remainingSeconds);
        }
    };

    @Override
    public Identifier mythicId() {
        return ID;
    }

    @Override
    public void handle(MythicClientLoginNetworkHandler handler) {
        ((MSecClientLoginNetworkHandler) handler).mythicworldsecurity$onHeartbeat(this);
    }
}
