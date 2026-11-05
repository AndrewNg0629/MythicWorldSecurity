package top.aenp.msec.network.payloads.auth.s2c;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileActionType;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import top.aenp.msec.network.interfaces.MSecClientPlayNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicClientPlayNetworkHandler;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicPlayS2CPayload;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record PlayProfileResponseS2CPayload(UUID uuid, ProfileResult profileResult) implements MythicPlayS2CPayload {
    public static final CustomPayload.Id<PlayProfileResponseS2CPayload> ID = new Id<>(Identifier.of("msec", "play_profile_response_s2c"));
    public static final PacketCodec<PacketByteBuf, PlayProfileResponseS2CPayload> CODEC = new PacketCodec<>() {
        @Override
        public PlayProfileResponseS2CPayload decode(PacketByteBuf buf) {
            UUID uuid = buf.readUuid();
            boolean isResultNull = !buf.readBoolean();
            ProfileResult profileResult = null;
            if (!isResultNull) {
                GameProfile gameProfile = PacketCodecs.GAME_PROFILE.decode(buf);
                int profileActionsCount = buf.readInt();
                HashSet<ProfileActionType> profileActions = new HashSet<>();
                for (int i = 0; i < profileActionsCount; i++) {
                    ProfileActionType profileAction = buf.readEnumConstant(ProfileActionType.class);
                    profileActions.add(profileAction);
                }
                profileResult = new ProfileResult(gameProfile, profileActions);
            }
            return new PlayProfileResponseS2CPayload(uuid, profileResult);
        }

        @Override
        public void encode(PacketByteBuf buf, PlayProfileResponseS2CPayload value) {
            buf.writeUuid(value.uuid);
            boolean isResultNull = value.profileResult == null;
            if (isResultNull) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                PacketCodecs.GAME_PROFILE.encode(buf, value.profileResult.profile());
                Set<ProfileActionType> profileActions = value.profileResult.actions();
                buf.writeInt(profileActions.size());
                for (ProfileActionType profileAction : profileActions) {
                    buf.writeEnumConstant(profileAction);
                }
            }
        }
    };

    @Override
    public void handle(MythicClientPlayNetworkHandler handler) {
        ((MSecClientPlayNetworkHandler) handler).mythicworldsecurity$onProfileResponse(this);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
