package top.aenp.msec.offline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Uuids;
import top.aenp.msec.Utils;
import top.aenp.msec.config.ServerConfigManager;
import top.aenp.msec.network.MSecNetwork;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public record OfflinePlayerDataEntry(
        String playerName,
        UUID uuid,
        String skinHash,
        String skinModelType,
        String capeHash,
        String passwordHashAndSalt,
        boolean accountActive,
        ZonedDateTime creationTime,
        ZonedDateTime lastLoginTime,
        ZonedDateTime lastPasswordChange,
        Set<String> fastLoginFingerprints
) {
    public static final Codec<OfflinePlayerDataEntry> CODEC;
    public static final Codec<Set<OfflinePlayerDataEntry>> SET_CODEC;
    public static final Codec<String> PLAYER_NAME_CODEC;
    public static final Codec<String> SKIN_TYPE_CODEC;
    public static final String DEFAULT_MODEL = "default";
    public static final String SLIM_MODEL = "slim";

    static {
        PLAYER_NAME_CODEC = Codec.STRING.comapFlatMap(
                serializedValue -> {
                    if (!MSecNetwork.VALID_PLAYERNAME_PATTERN.matcher(serializedValue).matches()) {
                        return DataResult.error(() -> String.format("Player name %s contains invalid characters!", serializedValue));
                    } else if (ServerConfigManager.getConfig().bootstrapConfig().mojangAuthSourceConfig().enabled() && ServerConfigManager.getInstance().getMojangSuffixInfo().isSuffixed(serializedValue)) {
                        return DataResult.error(() -> String.format("Player name %s contains defined source suffix!", serializedValue));
                    } else {
                        return DataResult.success(serializedValue);
                    }
                },
                objectValue -> objectValue
        );
        SKIN_TYPE_CODEC = Codec.STRING.comapFlatMap(
                serializedValue -> {
                    if (serializedValue.equalsIgnoreCase(DEFAULT_MODEL)) {
                        return DataResult.success(DEFAULT_MODEL);
                    } else if (serializedValue.equalsIgnoreCase(SLIM_MODEL)) {
                        return DataResult.success(SLIM_MODEL);
                    } else {
                        return DataResult.error(() -> String.format("Invalid skin model type %s! Must be either default or slim!", serializedValue));
                    }
                },
                objectValue -> objectValue.toLowerCase(Locale.ROOT)
        );
        CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        PLAYER_NAME_CODEC.fieldOf("player_name").forGetter(OfflinePlayerDataEntry::playerName),
                        Uuids.STRING_CODEC.fieldOf("uuid").forGetter(OfflinePlayerDataEntry::uuid),
                        Codec.STRING.fieldOf("skin_hash").forGetter(OfflinePlayerDataEntry::skinHash),
                        SKIN_TYPE_CODEC.fieldOf("skin_model_type").forGetter(OfflinePlayerDataEntry::skinModelType),
                        Codec.STRING.fieldOf("cape_hash").forGetter(OfflinePlayerDataEntry::capeHash),
                        Codec.STRING.fieldOf("password_hash_and_salt").forGetter(OfflinePlayerDataEntry::passwordHashAndSalt),
                        Codec.BOOL.fieldOf("account_enabled").forGetter(OfflinePlayerDataEntry::accountActive),
                        Utils.DATE_TIME_CODEC.fieldOf("creation_time").forGetter(OfflinePlayerDataEntry::creationTime),
                        Utils.DATE_TIME_CODEC.fieldOf("last_login_time").forGetter(OfflinePlayerDataEntry::lastLoginTime),
                        Utils.DATE_TIME_CODEC.fieldOf("last_password_change").forGetter(OfflinePlayerDataEntry::lastPasswordChange),
                        Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf).fieldOf("fast_login_fingerprints").forGetter(OfflinePlayerDataEntry::fastLoginFingerprints)
                ).apply(instance, OfflinePlayerDataEntry::new)
        );
        SET_CODEC = CODEC.listOf().xmap(Set::copyOf, List::copyOf);
    }
}
