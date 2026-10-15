package top.aenp.msec.trust.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import top.aenp.msec.Utils;

import java.time.ZonedDateTime;
import java.util.Optional;

public record TrustedFingerprintEntry(String fingerprint, boolean fromHttps, Optional<ZonedDateTime> creationTime) {
    public static final Codec<TrustedFingerprintEntry> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("fingerprint").forGetter(TrustedFingerprintEntry::fingerprint),
                    Codec.BOOL.fieldOf("from_https").forGetter(TrustedFingerprintEntry::fromHttps),
                    Utils.DATE_TIME_CODEC.optionalFieldOf("creation_time").forGetter(TrustedFingerprintEntry::creationTime)
            ).apply(instance, TrustedFingerprintEntry::new)
    );
}
