package top.aenp.msec.trust;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import top.aenp.msec.Utils;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

public record HttpsMetadataEntry(String fingerprint, Optional<String> description, Optional<ZonedDateTime> creationTime) {
    public static final Codec<HttpsMetadataEntry> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("fingerprint").forGetter(HttpsMetadataEntry::fingerprint),
                    Codec.STRING.optionalFieldOf("description").forGetter(HttpsMetadataEntry::description),
                    Utils.DATE_TIME_CODEC.optionalFieldOf("creation_time").forGetter(HttpsMetadataEntry::creationTime)
            ).apply(instance, HttpsMetadataEntry::new)
    );
    public static final Codec<Map<String, HttpsMetadataEntry>> METADATA_MAP_CODEC = Codec.unboundedMap(Utils.VALIDATED_ADDR_STRING_CODEC, CODEC);
}
