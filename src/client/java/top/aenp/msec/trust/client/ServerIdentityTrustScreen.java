package top.aenp.msec.trust.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.util.NarratorManager;
import net.minecraft.network.ClientConnection;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.network.interfaces.MSecClientLoginHandshakeHandler;
import top.aenp.mwl.misc.MythicReflectionUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class ServerIdentityTrustScreen extends Screen {
    public static final MythicReflectionUtils.ReflectedField<ConnectScreen, Screen> ConnectScreen$parent = new MythicReflectionUtils.ReflectedField<>(ConnectScreen.class, Screen.class, "parent", "field_2412");
    private final Screen parentScreen;
    private final ClientConnection connection;
    private final String serverFingerprint;
    private final DirectionalLayoutWidget layout = DirectionalLayoutWidget.vertical();
    private final String serverAddressString;
    private final Map<String, TrustedFingerprintEntry> fingerprintMap;
    private final Set<String> matchingAddresses;
    private final boolean isIdentityChange;
    private final Runnable trustOrUpdateTask;
    private TextFieldWidget fingerprintField = null;
    private ButtonWidget pasteOrClearButton = null;
    private ButtonWidget trustButton = null;
    private ButtonWidget disconnectButton = null;

    private ServerIdentityTrustScreen(
            Screen parentScreen,
            ConnectScreen connectScreen,
            MSecClientLoginHandshakeHandler handler,
            ClientConnection connection,
            String serverAddressString,
            String serverFingerprint,
            Map<String, TrustedFingerprintEntry> fingerprintMap,
            Set<String> matchingAddresses
    ) {
        super(NarratorManager.EMPTY);
        this.parentScreen = parentScreen;
        this.connection = connection;
        this.serverAddressString = serverAddressString;
        this.fingerprintMap = fingerprintMap;
        this.matchingAddresses = matchingAddresses;
        this.serverFingerprint = serverFingerprint;
        isIdentityChange = this.fingerprintMap.containsKey(this.serverAddressString);
        trustOrUpdateTask = () -> {
            try {
                if (isIdentityChange) {
                    MythicWorldSecurity.LOGGER.info("Updating fingerprint of server {} from {} to {} via GUI. Paste verified: {}.", serverAddressString, fingerprintMap.get(serverAddressString).fingerprint(), serverFingerprint, !fingerprintField.getText().isEmpty());
                } else {
                    MythicWorldSecurity.LOGGER.info("Trusting server {} with fingerprint {} via GUI. Paste verified: {}.", serverAddressString, serverFingerprint, !fingerprintField.getText().isEmpty());
                }
                ClientFingerprintTrustService.addTrustedFingerprint(this.fingerprintMap, this.serverAddressString, this.serverFingerprint, false);
                ClientFingerprintTrustService.getInstance().writeFileAtomically(this.fingerprintMap);
                handler.resumeConnection();
                MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.translatable("msec.server_trust.message.connection_secured"));
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> client.setScreen(connectScreen));
            } catch (IOException e) {
                MythicWorldSecurity.LOGGER.error("Exception handling server identity.", e);
                this.connection.disconnect(Text.of("Client error when handling server identity."));
            }
        };
    }

    public static void switchToTrustScreen(
            MSecClientLoginHandshakeHandler handler,
            ClientConnection connection,
            String serverAddressString,
            String serverFingerprint,
            Map<String, TrustedFingerprintEntry> fingerprintMap,
            Set<String> matchingAddresses
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof ConnectScreen connectScreen) {
            Screen parentScreen = ConnectScreen$parent.getFieldValue(connectScreen);
            ServerIdentityTrustScreen serverIdentityTrustScreen = new ServerIdentityTrustScreen(parentScreen, connectScreen, handler, connection, serverAddressString, serverFingerprint, fingerprintMap, matchingAddresses);
            client.execute(() -> client.setScreen(serverIdentityTrustScreen));
        }
    }

    public void setRemainingTime(int remainingTime) {
        disconnectButton.setMessage(Text.translatable("msec.server_trust.common.disconnect_timed", remainingTime));
    }

    @Override
    protected void init() {
        layout.getMainPositioner().alignHorizontalCenter().margin(0, 0, 0, 20);
        if (!isIdentityChange) {
            layout.add(new TextWidget(Text.translatable("msec.server_trust.trust.title").formatted(Formatting.BOLD), textRenderer));
            layout.getMainPositioner().alignLeft().margin(0, 0, 0, 2);
            layout.add(new MultilineTextWidget(Text.translatable("msec.server_trust.trust.paragraph_1"), textRenderer).setCentered(false).setMaxWidth(Math.min(width - 80, 320)).setTextColor(-1710619));
            layout.getMainPositioner().alignLeft().margin(0, 0, 0, 5);
            layout.add(new MultilineTextWidget(Text.of(serverFingerprint).copyContentOnly().formatted(Formatting.LIGHT_PURPLE), textRenderer).setCentered(false).setMaxWidth(Math.min(width - 80, 320)));
            if (matchingAddresses.isEmpty()) {
                layout.getMainPositioner().alignLeft().margin(0, 0, 0, 2);
            }
            layout.add(new MultilineTextWidget(Text.translatable("msec.server_trust.trust.paragraph_2"), textRenderer).setCentered(false).setMaxWidth(Math.min(width - 100, 300)).setTextColor(-1710619));
            if (!matchingAddresses.isEmpty()) {
                layout.getMainPositioner().margin(0, 0, 0, 2);
                layout.add(new MultilineTextWidget(Text.translatable("msec.server_trust.trust.paragraph_3"), textRenderer).setCentered(false).setMaxWidth(Math.min(width - 80, 320)).setTextColor(-1710619));
                StringBuilder stringBuilder = new StringBuilder();
                for (String address : matchingAddresses) {
                    stringBuilder.append(address);
                    stringBuilder.append(", ");
                }
                stringBuilder.delete(stringBuilder.length() - 2, stringBuilder.length());
                layout.add(new MultilineTextWidget(Text.of(stringBuilder.toString()).copyContentOnly().formatted(Formatting.AQUA), textRenderer).setCentered(false).setMaxWidth(Math.min(width - 80, 320)));
            }
        } else {
            layout.add(new TextWidget(Text.translatable("msec.server_trust.update.title").formatted(Formatting.BOLD, Formatting.YELLOW), textRenderer));
            layout.getMainPositioner().alignLeft().margin(0, 0, 0, 2);
            layout.add(new MultilineTextWidget(Text.translatable("msec.server_trust.update.paragraph_1"), textRenderer).setCentered(false).setMaxWidth(Math.min(width - 80, 320)).setTextColor(-1710619));
            layout.getMainPositioner().alignLeft().margin(0, 0, 0, 5);
            layout.add(new MultilineTextWidget(Text.of(fingerprintMap.get(serverAddressString).fingerprint()).copyContentOnly().formatted(Formatting.GREEN), textRenderer).setCentered(false).setMaxWidth(Math.min(width - 80, 320)));
            layout.getMainPositioner().alignLeft().margin(0, 0, 0, 2);
            layout.add(new MultilineTextWidget(Text.translatable("msec.server_trust.update.paragraph_2"), textRenderer).setCentered(false).setMaxWidth(Math.min(width - 80, 320)).setTextColor(-1710619));
            layout.getMainPositioner().alignLeft().margin(0, 0, 0, 5);
            layout.add(new MultilineTextWidget(Text.of(serverFingerprint).copyContentOnly().formatted(Formatting.YELLOW), textRenderer).setCentered(false).setMaxWidth(Math.min(width - 80, 320)));
            layout.add(new MultilineTextWidget(Text.translatable("msec.server_trust.update.paragraph_3"), textRenderer).setCentered(false).setMaxWidth(Math.min(width - 80, 320)).setTextColor(-1710619));
            layout.add(new MultilineTextWidget(Text.translatable("msec.server_trust.update.server_address", "§b" + serverAddressString + "§r"), textRenderer).setCentered(false).setMaxWidth(Math.min(width - 80, 320)));
            layout.add(new MultilineTextWidget(Text.translatable("msec.server_trust.update.existing_trust_source", fingerprintMap.get(serverAddressString).fromHttps() ? "§eDNS HTTPS/Direct HTTPS§r" : "§eGUI§r"), textRenderer).setCentered(false).setMaxWidth(Math.min(width - 80, 320)).setTextColor(-1710619));
            layout.getMainPositioner().alignLeft().margin(0, 0, 0, 2);
        }
        Text trustButtonInsecureMessage;
        Text trustButtonMatchingMessage;
        if (!isIdentityChange) {
            trustButtonInsecureMessage = Text.translatable("msec.server_trust.trust.trust_server");
            trustButtonMatchingMessage = Text.translatable("msec.server_trust.trust.trust_server").formatted(Formatting.GREEN, Formatting.BOLD);
        } else {
            trustButtonInsecureMessage = Text.translatable("msec.server_trust.update.trust_new_insecure").formatted(Formatting.RED);
            trustButtonMatchingMessage = Text.translatable("msec.server_trust.update.trust_new").formatted(Formatting.YELLOW);
        }
        fingerprintField = new TextFieldWidget(textRenderer, 160, 20, Text.of(""));
        fingerprintField.setMaxLength(64);
        fingerprintField.setChangedListener(fingerprint -> {
            pasteOrClearButton.setMessage(fingerprint.isEmpty() ? Text.translatable("msec.server_trust.common.paste") : Text.translatable("msec.server_trust.common.clear"));
            if (fingerprint.isEmpty()) {
                trustButton.setMessage(trustButtonInsecureMessage);
                trustButton.active = true;
            } else if (serverFingerprint.equalsIgnoreCase(fingerprint)) {
                trustButton.setMessage(trustButtonMatchingMessage);
                trustButton.active = true;
            } else {
                trustButton.setMessage(Text.translatable("msec.server_trust.common.fingerprint_mismatch").formatted(Formatting.YELLOW));
                trustButton.active = false;
            }
        });
        DirectionalLayoutWidget fingerprintFieldCompound = DirectionalLayoutWidget.horizontal();
        fingerprintFieldCompound.getMainPositioner().margin(0);
        pasteOrClearButton = fingerprintFieldCompound.add(ButtonWidget.builder(Text.translatable("msec.server_trust.common.paste"), button -> {
            if (fingerprintField.getText().isEmpty()) {
                String clipboardContent = MinecraftClient.getInstance().keyboard.getClipboard().trim();
                fingerprintField.setText(clipboardContent.substring(0, Math.min(clipboardContent.length(), 64)));
                setFocused(fingerprintField);
            } else {
                fingerprintField.setText("");
            }
        }).size(40, 20).build());
        fingerprintFieldCompound.add(fingerprintField);
        fingerprintFieldCompound.refreshPositions();
        layout.getMainPositioner().alignHorizontalCenter().margin(0, 8, 0, 2);
        layout.add(new TextWidget(Text.translatable("msec.server_trust.common.hint"), textRenderer).setTextColor(-3092272));
        layout.getMainPositioner().alignHorizontalCenter().margin(0, 0, 0, 1);
        layout.add(fingerprintFieldCompound);
        trustButton = layout.add(ButtonWidget.builder(trustButtonInsecureMessage,
                        button -> {
                            fingerprintField.active = false;
                            trustButton.active = false;
                            Thread.startVirtualThread(trustOrUpdateTask).setName("MSec Server Identity Handler S");
                        })
                .size(200, 20)
                .build());
        disconnectButton = layout.add(ButtonWidget.builder(Text.translatable("msec.server_trust.common.disconnect"),
                        button -> {
                            connection.disconnect(ConnectScreen.ABORTED_TEXT);
                            MinecraftClient.getInstance().setScreen(parentScreen);
                        })
                .size(200, 20)
                .build());
        layout.refreshPositions();
        layout.forEachChild(this::addDrawableChild);
        initTabNavigation();
    }

    @Override
    protected void initTabNavigation() {
        SimplePositioningWidget.setPos(layout, getNavigationFocus());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void tick() {
        if (connection.isOpen()) {
            connection.tick();
        } else {
            connection.handleDisconnection();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (fingerprintField.getText().isEmpty()) {
            context.drawText(textRenderer, Text.translatable("msec.server_trust.common.field_hint"), fingerprintField.getX() + 4, fingerprintField.getY() + 6, -8355712, false);
        }
    }

    @Override
    public void setFocused(@Nullable Element focused) {
        if (!(focused instanceof ButtonWidget)) {
            super.setFocused(focused);
        }
    }
}
