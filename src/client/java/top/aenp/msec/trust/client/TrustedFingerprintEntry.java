package top.aenp.msec.trust.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public record TrustedFingerprintEntry(String fingerprint, boolean fromHttps, Optional<Long> creationTimestamp) {
    public static final Codec<TrustedFingerprintEntry> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("fingerprint").forGetter(TrustedFingerprintEntry::fingerprint),
                    Codec.BOOL.fieldOf("from_https").forGetter(TrustedFingerprintEntry::fromHttps),
                    Codec.LONG.optionalFieldOf("creation_timestamp").forGetter(TrustedFingerprintEntry::creationTimestamp)
            ).apply(instance, TrustedFingerprintEntry::new)
    );
}
