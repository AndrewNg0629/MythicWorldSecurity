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
import top.aenp.msec.auth.MSecEd25519Identity;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ExportHttpsMetadataCommand {
    public static final Set<String> TEST_ADDRESSES = Set.of("mc.aenp.top", "hub.aenp.top:25566");
    private static final Path METADATA_FILE_PATH = MythicWorldSecurity.MAIN_DIR.resolve("server").resolve("msec_server_metadata.json").normalize();
    private static final Path TEMP_METADATA_FILE_PATH = MythicWorldSecurity.MAIN_DIR.resolve("server").resolve("msec_server_metadata.json.tmp").normalize();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralCommandNode<ServerCommandSource> exportCommandNode =
                CommandManager.literal("export_metadata")
                        .requires(source -> source.hasPermissionLevel(4))
                        .then(
                                CommandManager.argument("override", BoolArgumentType.bool())
                                        .executes(context -> execute(context, BoolArgumentType.getBool(context, "override"), null))
                                        .then(
                                                CommandManager.argument("description", StringArgumentType.greedyString())
                                                        .executes(context -> execute(context, BoolArgumentType.getBool(context, "override"), StringArgumentType.getString(context, "description")))
                                        )
                        )
                        .then(
                                CommandManager.argument("description", StringArgumentType.greedyString())
                                        .executes(context -> execute(context, false, StringArgumentType.getString(context, "description")))

                        )
                        .executes(
                                context -> execute(context, false, null)
                        ).build();
        dispatcher.register(CommandManager.literal("msec").then(exportCommandNode));
    }

    private static int execute(CommandContext<ServerCommandSource> context, boolean override, String description) {
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
                for (String address : TEST_ADDRESSES) {
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
                context.getSource().sendFeedback(() -> Text.of("Place this file in your https server and make it accessible at https://server-domain/.well-known/msec/msec_server_metadata.json"), false);
            } catch (IOException e) {
                context.getSource().sendError(Text.of(String.format("Failed to export server metadata: %s", e)));
            }
        };
        Thread.startVirtualThread(exportMetadataTask).setName("MSec Server Metadata Exporeter");
        return 1;
    }
}
