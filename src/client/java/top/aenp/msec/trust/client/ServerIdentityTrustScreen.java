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
import top.aenp.msec.UtilsClient;
import top.aenp.msec.network.interfaces.MSecClientLoginHandshakeHandler;

import java.io.IOException;
import java.text.Normalizer;
import java.util.Map;
import java.util.Set;

public class ServerIdentityTrustScreen extends Screen {
    private final Screen parentScreen;
    private final ClientConnection connection;
    private final String serverFingerprint;
    private final DirectionalLayoutWidget layout = DirectionalLayoutWidget.vertical();
    private TextFieldWidget fingerprintField = null;
    private ButtonWidget pasteOrClearButton = null;
    private ButtonWidget trustButton = null;
    private ButtonWidget disconnectButton = null;
    private final String serverAddressString;
    private final Map<String, TrustedFingerprintEntry> fingerprintMap;
    private final Set<String> matchingAddresses;
    private final boolean isIdentityChange;
    private final Runnable trustOrUpdateTask;

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
        trustOrUpdateTask = () -> {
            try {
                ClientFingerprintTrustService.addTrustedFingerprint(this.fingerprintMap, this.serverAddressString, this.serverFingerprint, false);
                ClientFingerprintTrustService.getInstance().writeFileAtomically(this.fingerprintMap);
                handler.resumeConnection();
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> client.setScreen(connectScreen));
            } catch (IOException e) {
                MythicWorldSecurity.LOGGER.error("Exception handling server identity.", e);
                this.connection.disconnect(Text.of("Client error when handling server identity."));
            }
        };
        isIdentityChange = this.fingerprintMap.containsKey(this.serverAddressString);
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
            Screen parentScreen = UtilsClient.ConnectScreen$parent.getFieldValue(connectScreen);
            ServerIdentityTrustScreen serverIdentityTrustScreen = new ServerIdentityTrustScreen(parentScreen, connectScreen, handler, connection, serverAddressString, serverFingerprint, fingerprintMap, matchingAddresses);
            client.execute(() -> client.setScreen(serverIdentityTrustScreen));
        }
    }

    public void setRemainingTime(int remainingTime) {
        disconnectButton.setMessage(Text.of(String.format("Disconnect (%ds)", remainingTime)));
    }

    @Override
    protected void init() {
        //TODO Identity change warning screen.
        layout.getMainPositioner().alignHorizontalCenter().margin(0, 15);
        layout.add(new TextWidget(Text.of("MSec Server Identity Trust").copyContentOnly().formatted(Formatting.BOLD), textRenderer));
        layout.getMainPositioner().alignLeft().margin(0, 3);
        layout.add(new MultilineTextWidget(Text.of("You are connecting to this server for the first time. In order to secure your connection, please verify the fingerprint below: "), textRenderer).setCentered(false).setMaxWidth(Math.min(width - 100, 300)).setTextColor(-1710619));
        layout.add(new MultilineTextWidget(Text.of(serverFingerprint).copyContentOnly().formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC), textRenderer).setCentered(false).setMaxWidth(Math.min(width - 100, 300)));
        layout.add(new MultilineTextWidget(Text.of("Ask the server owner for the fingerprint and compare. If it matches exactly, click [Trust This Server] to resume connecting. Otherwise, click [Disconnect] immediately! Once trusted, this interface won't show up again."), textRenderer).setCentered(false).setMaxWidth(Math.min(width - 100, 300)).setTextColor(-1710619));
        fingerprintField = new TextFieldWidget(textRenderer, 160, 20, Text.of("Fingerprint"));
        fingerprintField.setMaxLength(64);
        fingerprintField.setChangedListener(fingerprint -> {
            pasteOrClearButton.setMessage(fingerprint.isEmpty() ? Text.of("Paste") : Text.of("Clear"));
            if (fingerprint.isEmpty()) {
                trustButton.setMessage(Text.of("Trust This Server").copyContentOnly());
                trustButton.active = true;
            } else if (serverFingerprint.equalsIgnoreCase(fingerprint)) {
                trustButton.setMessage(Text.of("Trust This Server").copyContentOnly().formatted(Formatting.GREEN, Formatting.BOLD));
                trustButton.active = true;
            } else {
                trustButton.setMessage(Text.of("Fingerprint Doesn't Match!"));
                trustButton.active = false;
            }
        });
        DirectionalLayoutWidget compoundWidget = DirectionalLayoutWidget.horizontal();
        compoundWidget.getMainPositioner().margin(0);
        pasteOrClearButton = compoundWidget.add(ButtonWidget.builder(Text.of("Paste"), button -> {
            if (fingerprintField.getText().isEmpty()) {
                String clipboardContent = MinecraftClient.getInstance().keyboard.getClipboard().trim();
                fingerprintField.setText(clipboardContent.substring(0, Math.min(clipboardContent.length(), 64)));
                setFocused(fingerprintField);
            } else {
                fingerprintField.setText("");
            }
        }).size(40, 20).build());
        compoundWidget.add(fingerprintField);
        compoundWidget.refreshPositions();
        layout.getMainPositioner().alignHorizontalCenter().margin(0, 7, 0, 1);
        layout.add(new TextWidget(Text.of("You can paste the fingerprint to verify below.").copyContentOnly().formatted(Formatting.GRAY), textRenderer));
        layout.getMainPositioner().alignHorizontalCenter().margin(0, 1);
        layout.add(compoundWidget);
        trustButton = layout.add(ButtonWidget.builder(Text.of("Trust This Server"),
                        button -> {
                            fingerprintField.active = false;
                            trustButton.active = false;
                            Thread.startVirtualThread(trustOrUpdateTask).setName("MSec Server Identity Handler S");
                        })
                .size(200, 20)
                .build());
        disconnectButton = layout.add(ButtonWidget.builder(Text.of("Disconnect"),
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
        //context.drawCenteredTextWithShadow(textRenderer, Text.of("MSec Server Identity Trust"), width / 2, 20, 16777215);
    }

    @Override
    public void setFocused(@Nullable Element focused) {
        if (!(focused instanceof ButtonWidget)) {
            super.setFocused(focused);
        }
    }
}
