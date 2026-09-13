package top.aenp.msec.network.payloads.texture;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecClientLoginNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicClientLoginNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginS2CPayload;

public record LoginTextureCommandS2CPayload(Command command) implements MythicLoginS2CPayload {
    public enum Command {
        GET_METADATA, GET_SKIN, GET_CAPE
    }

    public static final Identifier ID = Identifier.of("msec", "login_texture_command");
    public static final PacketCodec<PacketByteBuf, LoginTextureCommandS2CPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginTextureCommandS2CPayload decode(PacketByteBuf buf) {
            return new LoginTextureCommandS2CPayload(buf.readEnumConstant(Command.class));
        }

        @Override
        public void encode(PacketByteBuf buf, LoginTextureCommandS2CPayload value) {
            buf.writeEnumConstant(value.command);
        }
    };

    @Override
    public Identifier mythicId() {
        return ID;
    }

    @Override
    public void handle(MythicClientLoginNetworkHandler handler) {
        ((MSecClientLoginNetworkHandler) handler).mythicworldsecurity$onTextureCommand(this);
    }
}
