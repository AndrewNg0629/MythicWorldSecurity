package top.aenp.msec.network.payloads.auth.s2c;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import top.aenp.msec.auth.ServerMSecGameProfileRepository;
import top.aenp.msec.network.interfaces.MSecClientPlayNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.MythicClientPlayNetworkHandler;
import top.aenp.mwl.network.v2.interfaces.PacketCodec;
import top.aenp.mwl.network.v2.payloads.interfaces.MythicPlayS2CPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public record PlayProfileBatchResponseS2CPayload(int queryId, Map<String, ServerMSecGameProfileRepository.ProfileQueryResultEntry> queryResult) implements MythicPlayS2CPayload {
    public static final Identifier ID = new Identifier("msec", "play_profile_batch_response_s2c");
    public static final PacketCodec<PacketByteBuf, PlayProfileBatchResponseS2CPayload> CODEC = new PacketCodec<>() {
        @Override
        public PlayProfileBatchResponseS2CPayload decode(PacketByteBuf buf) {
            int queryId = buf.readInt();
            HashMap<String, ServerMSecGameProfileRepository.ProfileQueryResultEntry> resultMap = new HashMap<>();
            int mapSize = buf.readInt();
            for (int i = 0; i < mapSize; i++) {
                String parameterName = buf.readString();
                GameProfile gameProfile = null;
                boolean isGameProfilePresent = buf.readBoolean();
                if (isGameProfilePresent) {
                    gameProfile = buf.readGameProfile();
                }
                boolean isNotFound = buf.readBoolean();
                ServerMSecGameProfileRepository.ProfileQueryResultEntry resultEntry = new ServerMSecGameProfileRepository.ProfileQueryResultEntry(gameProfile, isNotFound);
                resultMap.put(parameterName, resultEntry);
            }
            return new PlayProfileBatchResponseS2CPayload(queryId, resultMap);
        }

        @Override
        public void encode(PacketByteBuf buf, PlayProfileBatchResponseS2CPayload value) {
            buf.writeInt(value.queryId);
            Set<Map.Entry<String, ServerMSecGameProfileRepository.ProfileQueryResultEntry>> mapEntrySet = value.queryResult.entrySet();
            buf.writeInt(mapEntrySet.size());
            for (Map.Entry<String, ServerMSecGameProfileRepository.ProfileQueryResultEntry> entry : mapEntrySet) {
                buf.writeString(entry.getKey());
                ServerMSecGameProfileRepository.ProfileQueryResultEntry responseEntry = entry.getValue();
                boolean isProfilePresent = responseEntry.profile() != null;
                buf.writeBoolean(isProfilePresent);
                if (isProfilePresent) {
                    buf.writeGameProfile(responseEntry.profile());
                }
                buf.writeBoolean(responseEntry.isNotFound());
            }
        }
    };

    @Override
    public void handle(MythicClientPlayNetworkHandler handler) {
        ((MSecClientPlayNetworkHandler) handler).mythicworldsecurity$onProfileBatchResponse(this);
    }

    @Override
    public Identifier mythicId() {
        return ID;
    }
}
