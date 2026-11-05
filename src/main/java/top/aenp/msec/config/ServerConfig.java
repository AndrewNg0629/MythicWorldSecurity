package top.aenp.msec.config;


import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import top.aenp.msec.network.MSecNetwork;
import top.aenp.msec.trust.TrustUtils;

import java.util.List;
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
            new BootstrapConfig(
                    false,
                    new BootstrapConfig.CommonSuffixConfig('.', "mojang"),
                    new BootstrapConfig.MojangAuthSourceConfig(true, "mojang", Set.of()),
                    new BootstrapConfig.OfflineAuthSourceConfig(false, "offline", Set.of()),
                    false,
                    Set.of(),
                    List.of()
            ),
            new MutableConfig(
                    new MutableConfig.DnsHttpsConfig(false, Set.of()),
                    new MutableConfig.DirectHttpsConfig(false, Set.of())
            ),
            ServerConfigManager.CONFIG_VERSION
    );

    public record BootstrapConfig(boolean modEnabled,
                                  CommonSuffixConfig commonSuffixConfig,
                                  MojangAuthSourceConfig mojangAuthSourceConfig,
                                  OfflineAuthSourceConfig offlineAuthSourceConfig,
                                  boolean yggdrasilSourcesEnabled,
                                  Set<YggdrasilAuthSourceUnit> yggdrasilAuthSourceUnits,
                                  List<String> unknownUUIDMatchSequence
    ) {
        public static final Codec<BootstrapConfig> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        Codec.BOOL.fieldOf("mod_enabled").forGetter(BootstrapConfig::modEnabled),
                        CommonSuffixConfig.CODEC.fieldOf("common_suffix_config").forGetter(BootstrapConfig::commonSuffixConfig),
                        MojangAuthSourceConfig.CODEC.fieldOf("mojang_auth_source_config").forGetter(BootstrapConfig::mojangAuthSourceConfig),
                        OfflineAuthSourceConfig.CODEC.fieldOf("offline_auth_source_config").forGetter(BootstrapConfig::offlineAuthSourceConfig),
                        Codec.BOOL.fieldOf("yggdrasil_sources_enabled").forGetter(BootstrapConfig::yggdrasilSourcesEnabled),
                        YggdrasilAuthSourceUnit.CODEC.listOf().xmap(Set::copyOf, List::copyOf).fieldOf("yggdrasil_auth_sources").forGetter(BootstrapConfig::yggdrasilAuthSourceUnits),
                        Codec.STRING.listOf().fieldOf("unknown_uuid_trial_sequence").forGetter(BootstrapConfig::unknownUUIDMatchSequence)
                ).apply(instance, BootstrapConfig::new)
        );

        public record CommonSuffixConfig(char suffixSeparatorChar, String suffixExemptedSource) {
            public static final Codec<CommonSuffixConfig> CODEC = RecordCodecBuilder.create(
                    instance -> instance.group(
                            MSecNetwork.SEPARATOR_CODEC.fieldOf("suffix_separator_char").forGetter(CommonSuffixConfig::suffixSeparatorChar),
                            Codec.STRING.fieldOf("suffix_exemped_sorce").forGetter(CommonSuffixConfig::suffixExemptedSource)
                    ).apply(instance, CommonSuffixConfig::new)
            );
        }

        public record MojangAuthSourceConfig(boolean enabled, String suffix, Set<String> formerSuffixes) {
            public static final Codec<MojangAuthSourceConfig> CODEC = RecordCodecBuilder.create(
                    instance -> instance.group(
                            Codec.BOOL.fieldOf("enabled").forGetter(MojangAuthSourceConfig::enabled),
                            MSecNetwork.PLAYER_NAME_COMPONENT_CODEC.fieldOf("suffix").forGetter(MojangAuthSourceConfig::suffix),
                            MSecNetwork.PLAYER_NAME_COMPONENT_SET_CODEC.fieldOf("former_suffixes").forGetter(MojangAuthSourceConfig::formerSuffixes)
                    ).apply(instance, MojangAuthSourceConfig::new)
            );
        }

        public record OfflineAuthSourceConfig(boolean enabled, String suffix, Set<String> formerSuffixes) {
            public static final Codec<OfflineAuthSourceConfig> CODEC = RecordCodecBuilder.create(
                    instance -> instance.group(
                            Codec.BOOL.fieldOf("enabled").forGetter(OfflineAuthSourceConfig::enabled),
                            MSecNetwork.PLAYER_NAME_COMPONENT_CODEC.fieldOf("suffix").forGetter(OfflineAuthSourceConfig::suffix),
                            MSecNetwork.PLAYER_NAME_COMPONENT_SET_CODEC.fieldOf("former_suffixes").forGetter(OfflineAuthSourceConfig::formerSuffixes)
                    ).apply(instance, OfflineAuthSourceConfig::new)
            );
        }

        public record YggdrasilAuthSourceUnit(String name, boolean enabled, String apiURL, String suffix,
                                              Set<String> formerSuffixes) {
            public static final Codec<YggdrasilAuthSourceUnit> CODEC = RecordCodecBuilder.create(
                    instance -> instance.group(
                            Codec.STRING.fieldOf("name").forGetter(YggdrasilAuthSourceUnit::name),
                            Codec.BOOL.fieldOf("enabled").forGetter(YggdrasilAuthSourceUnit::enabled),
                            Codec.STRING.fieldOf("api_url").forGetter(YggdrasilAuthSourceUnit::apiURL),
                            MSecNetwork.PLAYER_NAME_COMPONENT_CODEC.fieldOf("suffix").forGetter(YggdrasilAuthSourceUnit::suffix),
                            MSecNetwork.PLAYER_NAME_COMPONENT_SET_CODEC.fieldOf("former_suffixes").forGetter(YggdrasilAuthSourceUnit::formerSuffixes)
                    ).apply(instance, YggdrasilAuthSourceUnit::new)
            );
        }
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
