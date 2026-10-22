package top.aenp.msec.trust;

import com.google.gson.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.config.ServerConfig;
import top.aenp.msec.config.ServerConfigManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.*;

public class HttpsMetadataCommand {
    private static final Path METADATA_FILE_PATH = MythicWorldSecurity.MAIN_DIR.resolve("server").resolve("msec_server_metadata.json").normalize();
    private static final Path TEMP_METADATA_FILE_PATH = MythicWorldSecurity.MAIN_DIR.resolve("server").resolve("msec_server_metadata.json.tmp").normalize();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralCommandNode<ServerCommandSource> exportCommandNode =
                CommandManager.literal("export_metadata")
                        .requires(source -> source.hasPermissionLevel(4))
                        .then(
                                CommandManager.argument("override", BoolArgumentType.bool())
                                        .executes(context -> executeExport(context, BoolArgumentType.getBool(context, "override"), null))
                                        .then(
                                                CommandManager.argument("description", StringArgumentType.greedyString())
                                                        .executes(context -> executeExport(context, BoolArgumentType.getBool(context, "override"), StringArgumentType.getString(context, "description")))
                                        )
                        )
                        .then(
                                CommandManager.argument("description", StringArgumentType.greedyString())
                                        .executes(context -> executeExport(context, false, StringArgumentType.getString(context, "description")))

                        )
                        .executes(
                                context -> executeExport(context, false, null)
                        ).build();
        LiteralCommandNode<ServerCommandSource> testMetadataCommandNode =
                CommandManager.literal("test_metadata")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(HttpsMetadataCommand::executeTest)
                        .build();
        LiteralCommandNode<ServerCommandSource> metadataHelpCommandNode =
                CommandManager.literal("metadata_help")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(
                                context -> {
                                    context.getSource().sendFeedback(() -> Text.of("Commands:"), false);
                                    context.getSource().sendFeedback(() -> Text.of("msec metadata_help -> Displays this help."), false);
                                    context.getSource().sendFeedback(() -> Text.of("msec export_metadata -> Exports server metadata for DNS HTTPS and Direct HTTPS."), false);
                                    context.getSource().sendFeedback(() -> Text.of("msec test_metadata -> Tests DNS HTTPS and Direct HTTPS verification of this server."), false);
                                    context.getSource().sendFeedback(() -> Text.of("Directions:"), false);
                                    context.getSource().sendFeedback(() -> Text.of("DNS HTTPS and Direct HTTPS are two methods to prove the identity of your server. It requires control over the domain name pointed to the server."), false);
                                    context.getSource().sendFeedback(() -> Text.of("To use DNS HTTPS, export msecc_server_metadata.json with the export command. Host this file over https and create a TXT record in _msec.server-domain containing the URL to it. Split with space or create multiple records if there are multiple URLS."), false);
                                    context.getSource().sendFeedback(() -> Text.of("To use Direct HTTPS, export the metadata json, and make it accessible at https://server-domain/.well-known/msec/msec_server_metadata.json"), false);
                                    context.getSource().sendFeedback(() -> Text.of("You have to configure every domain/IP separately, and HTTPS is enforced. For DNS HTTPS, only domains are applicable. For Direct HTTPS, public IPs are also okay."), false);
                                    return 1;
                                }
                        ).build();
        dispatcher.register(CommandManager.literal("msec").then(exportCommandNode));
        dispatcher.register(CommandManager.literal("msec").then(testMetadataCommandNode));
        dispatcher.register(CommandManager.literal("msec").then(metadataHelpCommandNode));
    }

    private static int executeExport(CommandContext<ServerCommandSource> context, boolean override, String description) {
        Runnable exportMetadataTask = () -> {
            try {
                HashMap<String, HttpsMetadataEntry> metadataMap = new HashMap<>();
                if (Files.exists(METADATA_FILE_PATH)) {
                    if (!Files.isRegularFile(METADATA_FILE_PATH)) {
                        context.getSource().sendError(Text.of("A directory has the same name as the metadata file, preventing operation."));
                        return;
                    }
                    context.getSource().sendFeedback(() -> Text.of("An existing metadata file is found, appending."), false);
                    try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(METADATA_FILE_PATH))) {
                        JsonElement jsonElement = JsonParser.parseReader(reader);
                        DataResult<Map<String, HttpsMetadataEntry>> dataResult = HttpsMetadataEntry.METADATA_MAP_CODEC.parse(JsonOps.INSTANCE, jsonElement);
                        Map<String, HttpsMetadataEntry> decodedMetadata = dataResult.getOrThrow(JsonParseException::new);
                        metadataMap.putAll(decodedMetadata);
                    } catch (JsonParseException e) {
                        context.getSource().sendError(Text.of(String.format("The existing metadata file is malformed: %s", e.getMessage())));
                        return;
                    }
                } else {
                    context.getSource().sendFeedback(() -> Text.of("Creating mew metadata file."), false);
                }
                String fingerprint = MSecEd25519Identity.hashEd25519PublicKey(MSecEd25519Identity.getInstance().publicKey);
                ZonedDateTime dateTime = ZonedDateTime.now();
                Set<String> combinedAddresses = new HashSet<>();
                combinedAddresses.addAll(ServerConfigManager.getConfig().mutableConfig().dnsHttpsConfig().applicableAddresses());
                combinedAddresses.addAll(ServerConfigManager.getConfig().mutableConfig().directHttpsConfig().applicableAddresses());
                for (String address : combinedAddresses) {
                    if (metadataMap.containsKey(address) && !override) {
                        context.getSource().sendError(Text.of("Existing metadata file contains metadata keys of this server. Use \"true\" argument to override."));
                        return;
                    }
                    metadataMap.put(address, new HttpsMetadataEntry(fingerprint, Optional.ofNullable(description), Optional.of(dateTime)));
                }
                Files.deleteIfExists(TEMP_METADATA_FILE_PATH);
                try (OutputStream outputStream = Files.newOutputStream(TEMP_METADATA_FILE_PATH)) {
                    JsonElement jsonElement = HttpsMetadataEntry.METADATA_MAP_CODEC.encodeStart(JsonOps.INSTANCE, metadataMap).getOrThrow(IOException::new);
                    outputStream.write(GSON.toJson(jsonElement).getBytes(StandardCharsets.UTF_8));
                }
                try {
                    Files.move(TEMP_METADATA_FILE_PATH, METADATA_FILE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(TEMP_METADATA_FILE_PATH, METADATA_FILE_PATH, StandardCopyOption.REPLACE_EXISTING);
                }
                context.getSource().sendFeedback(() -> Text.of("Metadata has been successfully written to msec_server_metadata.json."), false);
                context.getSource().sendFeedback(() -> Text.of("To use Direct HTTPS mode, host this file at https://server-domain/.well-known/msec/msec_server_metadata.json"), false);
            } catch (IOException e) {
                context.getSource().sendError(Text.of(String.format("Failed to export server metadata: %s", e)));
            }
        };
        Thread.startVirtualThread(exportMetadataTask).setName("MSec Server Metadata Exporter");
        return 1;
    }

    private static int executeTest(CommandContext<ServerCommandSource> context) {
        Runnable testMetadataTask = () -> {
            ServerConfig.MutableConfig.DnsHttpsConfig dnsHttpsConfig = ServerConfigManager.getConfig().mutableConfig().dnsHttpsConfig();
            ServerConfig.MutableConfig.DirectHttpsConfig directHttpsConfig = ServerConfigManager.getConfig().mutableConfig().directHttpsConfig();
            String serverFingerprint = MSecEd25519Identity.hashEd25519PublicKey(MSecEd25519Identity.getInstance().publicKey);
            boolean tested = false;
            boolean errored = false;
            if (dnsHttpsConfig.enabled()) {
                if (!dnsHttpsConfig.applicableAddresses().isEmpty()) {
                    context.getSource().sendFeedback(() -> Text.of("---------DNS HTTPS test begins.---------"), false);
                    for (String address : dnsHttpsConfig.applicableAddresses()) {
                        try {
                            context.getSource().sendFeedback(() -> Text.of(String.format("Testing DNS HTTPS metadata for %s", address)), false);
                            String domain = address.split(":")[0];
                            String domainToQuery = TrustUtils.DNS_HTTPS_DOMAIN_PREFIX + domain;
                            Set<URI> metadataUriSet = TrustUtils.fetchDnsHttpsUrisFromDomain(domainToQuery);
                            boolean found = false;
                            for (URI metadataUri : metadataUriSet) {
                                Map<String, HttpsMetadataEntry> metadataMap = TrustUtils.fetchMetadataFromUri(metadataUri);
                                HttpsMetadataEntry entry = metadataMap.get(address);
                                if (entry != null) {
                                    found = true;
                                    if (entry.fingerprint().equalsIgnoreCase(serverFingerprint)) {
                                        context.getSource().sendFeedback(() -> Text.of(String.format("\t[OK] Matching metadata was found from: %s -> %s -> %s", address, domainToQuery, metadataUri)), false);
                                    } else {
                                        context.getSource().sendFeedback(() -> Text.of(String.format("\t[ERROR] Mismatched metadata was found from: %s -> %s -> %s ! Metadata fingerprint: %s", address, domainToQuery, metadataUri, entry.fingerprint())), false);
                                        errored = true;
                                    }
                                }
                            }
                            if (!found) {
                                context.getSource().sendFeedback(() -> Text.of("\t[ERROR] No metadata was found for this address."), false);
                                errored = true;
                            }
                        } catch (Exception e) {
                            context.getSource().sendFeedback(() -> Text.of(String.format("[ERROR] Failed to test address %s : %s", address, e)), false);
                            errored = true;
                        }
                    }
                    context.getSource().sendFeedback(() -> Text.of("--------DNS HTTPS test finished.--------"), false);
                    tested = true;
                } else {
                    context.getSource().sendFeedback(() -> Text.of("DNS HTTPS is enabled, but no applicable address exists, so test is not run."), false);
                }
            } else {
                context.getSource().sendFeedback(() -> Text.of("DNS HTTPS is disabled in config, so not tested."), false);
            }
            if (directHttpsConfig.enabled()) {
                if (!directHttpsConfig.applicableAddresses().isEmpty()) {
                    context.getSource().sendFeedback(() -> Text.of("--------Direct HTTPS test begins.-------"), false);
                    for (String address : directHttpsConfig.applicableAddresses()) {
                        try {
                            context.getSource().sendFeedback(() -> Text.of(String.format("Testing Direct HTTPS metadata for %s", address)), false);
                            String domain = address.split(":")[0];
                            URI metadataUri = URI.create("https://" + domain + TrustUtils.HTTPS_META_PATH);
                            Map<String, HttpsMetadataEntry> metadataMap = TrustUtils.fetchMetadataFromUri(metadataUri);
                            HttpsMetadataEntry entry = metadataMap.get(address);
                            if (entry != null) {
                                if (entry.fingerprint().equalsIgnoreCase(serverFingerprint)) {
                                    context.getSource().sendFeedback(() -> Text.of(String.format("\t[OK] Matching metadata was found from: %s -> %s", address, metadataUri)), false);
                                } else {
                                    context.getSource().sendFeedback(() -> Text.of(String.format("\t[ERROR] Mismatched metadata was found from: %s -> %s ! Metadata fingerprint: %s", address, metadataUri, entry.fingerprint())), false);
                                    errored = true;
                                }
                            } else {
                                context.getSource().sendFeedback(() -> Text.of("\t[ERROR] No metadata was found for this address."), false);
                                errored = true;
                            }
                        } catch (Exception e) {
                            context.getSource().sendFeedback(() -> Text.of(String.format("[ERROR] Failed to test address %s : %s", address, e)), false);
                            errored = true;
                        }
                    }
                    context.getSource().sendFeedback(() -> Text.of("-------Direct HTTPS test finished.------"), false);
                    tested = true;
                } else {
                    context.getSource().sendFeedback(() -> Text.of("Direct HTTPS is enabled, but no applicable address exists, so test is not run."), false);
                }
            } else {
                context.getSource().sendFeedback(() -> Text.of("Direct HTTPS is disabled in config, so not tested."), false);
            }
            if (tested) {
                if (!errored) {
                    context.getSource().sendFeedback(() -> Text.of("Congratulations, test passed! Clients can verify this server properly via DNS HTTPS/Direct HTTPS."), false);
                } else {
                    context.getSource().sendFeedback(() -> Text.of("Test failed. Clients may have problems verifying this server via DNS HTTPS/Direct HTTPS and fallback to GUI confirmation."), false);
                }
            }
        };
        Thread.startVirtualThread(testMetadataTask).setName("MSec Server Metadata Tester");
        return 1;
    }
}
