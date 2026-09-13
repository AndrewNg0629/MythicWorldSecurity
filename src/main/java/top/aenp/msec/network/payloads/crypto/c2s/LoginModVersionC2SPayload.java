package top.aenp.msec.network.payloads.crypto.c2s;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecServerLoginNetworkHandler;
import top.aenp.msec.network.interfaces.SignedPayload;
import top.aenp.mwl.network.v2.interfaces.MythicServerLoginNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginC2SPayload;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public record LoginModVersionC2SPayload(String clientModVersion, int clientProtocolVersion) implements MythicLoginC2SPayload, SignedPayload {
    public static final Identifier ID = Identifier.of("msec", "mod_version_c2s");
    public static final PacketCodec<PacketByteBuf, LoginModVersionC2SPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginModVersionC2SPayload decode(PacketByteBuf buf) {
            String clientModVersion = buf.readString();
            int clientProtocolVersion = buf.readInt();
            return new LoginModVersionC2SPayload(clientModVersion, clientProtocolVersion);
        }

        @Override
        public void encode(PacketByteBuf buf, LoginModVersionC2SPayload value) {
            buf.writeString(value.clientModVersion);
            buf.writeInt(value.clientProtocolVersion);
        }
    };

    @Override
    public Identifier mythicId() {
        return ID;
    }

    @Override
    public void handle(MythicServerLoginNetworkHandler handler) {
        ((MSecServerLoginNetworkHandler) handler).mythicworldsecurity$getMSecHandshakeHandler().onModVersion(this);
    }

    @Override
    public void writeMessageAsBytes(ByteArrayOutputStream byteArrayOutputStream) {
        byteArrayOutputStream.writeBytes(clientModVersion.getBytes(StandardCharsets.UTF_8));
        byteArrayOutputStream.write(clientProtocolVersion);
    }
}
