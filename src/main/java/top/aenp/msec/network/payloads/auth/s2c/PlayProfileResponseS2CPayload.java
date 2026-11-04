package top.aenp.msec.network.payloads.auth.s2c;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecClientPlayNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicClientPlayNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.PacketCodec;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicPlayS2CPayload;

import java.util.UUID;

public record PlayProfileResponseS2CPayload(UUID uuid, GameProfile gameProfile) implements MythicPlayS2CPayload {
    public static final Identifier ID = new Identifier("msec", "play_profile_response_s2c");
    public static final PacketCodec<PacketByteBuf, PlayProfileResponseS2CPayload> CODEC = new PacketCodec<>() {
        @Override
        public PlayProfileResponseS2CPayload decode(PacketByteBuf buf) {
            UUID uuid = buf.readUuid();
            boolean isProfilePresent = buf.readBoolean();
            GameProfile gameProfile = null;
            if (isProfilePresent) {
                gameProfile = buf.readGameProfile();
            }
            return new PlayProfileResponseS2CPayload(uuid, gameProfile);
        }

        @Override
        public void encode(PacketByteBuf buf, PlayProfileResponseS2CPayload value) {
            buf.writeUuid(value.uuid);
            boolean isProfilePresent = value.gameProfile != null;
            if (isProfilePresent) {
                buf.writeBoolean(true);
                buf.writeGameProfile(value.gameProfile);
            } else {
                buf.writeBoolean(false);
            }
        }
    };

    @Override
    public void handle(MythicClientPlayNetworkHandler handler) {
        ((MSecClientPlayNetworkHandler) handler).mythicworldsecurity$onProfileResponse(this);
    }

    @Override
    public Identifier mythicId() {
        return ID;
    }
}
