package top.aenp.msec.config;


import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import top.aenp.msec.trust.TrustUtils;

import java.util.Set;

public record ServerConfig(BootstrapConfig bootstrapConfig, MutableConfig mutableConfig, int configVersion) {
    public static final Codec<ServerConfig> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    BootstrapConfig.CODEC.fieldOf("bootstrap_config").forGetter(ServerConfig::bootstrapConfig),
                    MutableConfig.CODEC.fieldOf("mutable_config").forGetter(ServerConfig::mutableConfig),
                    Codec.INT.fieldOf("config_version").forGetter(ServerConfig::configVersion)
            ).apply(instance, ServerConfig::new)
    );
    public static final ServerConfig DEFAULT_INSTANCE = new ServerConfig(
            new BootstrapConfig(false),
            new MutableConfig(
                    new MutableConfig.DnsHttpsConfig(false, Set.of()),
                    new MutableConfig.DirectHttpsConfig(false, Set.of())
            ),
            ServerConfigManager.CONFIG_VERSION
    );

    public record BootstrapConfig(boolean modEnabled) {
        public static final Codec<BootstrapConfig> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        Codec.BOOL.fieldOf("mod_enabled").forGetter(BootstrapConfig::modEnabled)
                ).apply(instance, BootstrapConfig::new)
        );
    }

    public record MutableConfig(DnsHttpsConfig dnsHttpsConfig, DirectHttpsConfig directHttpsConfig) {
        public static final Codec<MutableConfig> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        DnsHttpsConfig.CODEC.fieldOf("dns_https_config").forGetter(MutableConfig::dnsHttpsConfig),
                        DirectHttpsConfig.CODEC.fieldOf("direct_https_config").forGetter(MutableConfig::directHttpsConfig)
                ).apply(instance, MutableConfig::new)
        );

        public record DnsHttpsConfig(boolean enabled, Set<String> applicableAddresses) {
            public static final Codec<DnsHttpsConfig> CODEC = RecordCodecBuilder.create(
                    instance -> instance.group(
                            Codec.BOOL.fieldOf("enabled").forGetter(DnsHttpsConfig::enabled),
                            TrustUtils.DNS_HTTPS_ADDRS_CODEC.fieldOf("applicable_addresses").forGetter(DnsHttpsConfig::applicableAddresses)
                    ).apply(instance, DnsHttpsConfig::new)
            );
        }

        public record DirectHttpsConfig(boolean enabled, Set<String> applicableAddresses) {
            public static final Codec<DirectHttpsConfig> CODEC = RecordCodecBuilder.create(
                    instance -> instance.group(
                            Codec.BOOL.fieldOf("enabled").forGetter(DirectHttpsConfig::enabled),
                            TrustUtils.DIRECT_HTTPS_ADDRS_CODEC.fieldOf("applicable_addresses").forGetter(DirectHttpsConfig::applicableAddresses)
                    ).apply(instance, DirectHttpsConfig::new)
            );
        }
    }
}
