package top.aenp.msec.trust.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.NarratorManager;
import net.minecraft.network.ClientConnection;
import net.minecraft.text.Text;
import top.aenp.msec.UtilsClient;
import top.aenp.msec.network.interfaces.MSecClientLoginHandshakeHandler;

public class ServerIdentityTrustScreen extends Screen {
    private final Screen parentScreen;
    private final ConnectScreen connectScreen;
    private final ClientConnection connection;
    private final MSecClientLoginHandshakeHandler handler;
    private final String serverFingerprint;
    private TextFieldWidget fingerprintField = null;
    private ButtonWidget trustButton = null;
    private ButtonWidget disconnectButton = null;

    private ServerIdentityTrustScreen(Screen parentScreen, ConnectScreen connectScreen, MSecClientLoginHandshakeHandler handler, ClientConnection connection, String serverFingerprint) {
        super(NarratorManager.EMPTY);
        this.parentScreen = parentScreen;
        this.connectScreen = connectScreen;
        this.handler = handler;
        this.connection = connection;
        this.serverFingerprint = serverFingerprint;
    }

    public static void switchToTrustScreen(MSecClientLoginHandshakeHandler handler, ClientConnection connection, String serverFingerprint) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof ConnectScreen connectScreen) {
            Screen parentScreen = UtilsClient.ConnectScreen$parent.getFieldValue(connectScreen);
            ServerIdentityTrustScreen serverIdentityTrustScreen = new ServerIdentityTrustScreen(parentScreen, connectScreen, handler, connection, serverFingerprint);
            client.execute(() -> client.setScreen(serverIdentityTrustScreen));
        }
    }

    public void setRemainingTime(int remainingTime) {
        disconnectButton.setMessage(Text.of(String.format("Disconnect (%ds)", remainingTime)));
    }

    @Override
    protected void init() {
        fingerprintField = new TextFieldWidget(textRenderer, width / 2 - 100, height / 4 + 72 + 12, 200, 20, Text.of("Fingerprint"));
        fingerprintField.setMaxLength(64);
        fingerprintField.setChangedListener(fingerprint -> {
            if (fingerprint.isEmpty() || serverFingerprint.equalsIgnoreCase(fingerprint)) {
                trustButton.setMessage(Text.of("Resume Connecting"));
                trustButton.active = true;
            } else {
                trustButton.setMessage(Text.of("Fingerprint Doesn't Match!"));
                trustButton.active = false;
            }
        });
        trustButton = ButtonWidget.builder(Text.of("Resume Connecting"),
                        button -> {
                            handler.finishTrust();
                            MinecraftClient.getInstance().setScreen(connectScreen);
                        })
                .dimensions(width / 2 - 100, height / 4 + 96 + 12, 200, 20)
                .build();
        disconnectButton = ButtonWidget.builder(Text.of("Disconnect"),
                        button -> {
                            connection.disconnect(ConnectScreen.ABORTED_TEXT);
                            MinecraftClient.getInstance().setScreen(parentScreen);
                        })
                .dimensions(width / 2 - 100, height / 4 + 120 + 12, 200, 20)
                .build();
        addDrawableChild(trustButton);
        addDrawableChild(disconnectButton);
        addDrawableChild(fingerprintField);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        init(client, width, height);
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
        context.drawCenteredTextWithShadow(textRenderer, Text.of("Prototype Trust Screen"), width / 2, 20, 16777215);
    }
}
