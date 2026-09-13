package top.aenp.msec.network.payloads.crypto.s2c;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.SignedPayload;
import top.aenp.mwl.network.v2.interfaces.MythicClientLoginNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicLoginS2CPayload;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public record LoginModVersionS2CPayload(String serverModVersion, int serverProtocolVersion) implements MythicLoginS2CPayload, SignedPayload {
    public static final Identifier ID = Identifier.of("msec", "mod_version_s2c");
    public static final PacketCodec<PacketByteBuf, LoginModVersionS2CPayload> CODEC = new PacketCodec<>() {
        @Override
        public LoginModVersionS2CPayload decode(PacketByteBuf buf) {
            String serverModVersion = buf.readString();
            int serverProtocolVersion = buf.readInt();
            return new LoginModVersionS2CPayload(serverModVersion, serverProtocolVersion);
        }

        @Override
        public void encode(PacketByteBuf buf, LoginModVersionS2CPayload value) {
            buf.writeString(value.serverModVersion);
            buf.writeInt(value.serverProtocolVersion);
        }
    };

    @Override
    public Identifier mythicId() {
        return ID;
    }

    @Override
    public void handle(MythicClientLoginNetworkHandler handler) {

    }

    @Override
    public void writeMessageAsBytes(ByteArrayOutputStream byteArrayOutputStream) {
        byteArrayOutputStream.writeBytes(serverModVersion.getBytes(StandardCharsets.UTF_8));
        byteArrayOutputStream.write(serverProtocolVersion);
    }
}
