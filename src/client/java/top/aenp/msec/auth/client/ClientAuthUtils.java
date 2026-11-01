package top.aenp.msec.auth.client;

import net.minecraft.client.MinecraftClient;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.Utils;
import top.aenp.msec.auth.YggdrasilApiMetadata;
import top.aenp.msec.network.MSecNetwork;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.util.NoSuchElementException;

public class ClientAuthUtils {
    private static YggdrasilApiMetadata yggdrasilApiMetadata = null;
    private static boolean isMojangLogin = false;

    public static void determineClientAuthSource() {
        try {
            String aliYggdrasilUrl = digAliYggdrasilURL();
            if (aliYggdrasilUrl == null) {
                isMojangLogin = MSecNetwork.fetchIOWithRetries(ClientAuthUtils::validateMojangSession, "Mojang Login Validity", 5);
            } else {
                yggdrasilApiMetadata = MSecNetwork.fetchIOWithRetries(() -> YggdrasilApiMetadata.fetchApiMetadata(aliYggdrasilUrl, MinecraftClient.getInstance().getNetworkProxy()), "Yggdrasil Api Metadata", 5);
            }
        } catch (IOException e) {
            MythicWorldSecurity.LOGGER.error("Failed to determine auth source. You will fallback to offline login on MSec servers.", e);
        }
    }

    public static boolean isMojangLogin() {
        return isMojangLogin;
    }

    public static YggdrasilApiMetadata getYggdrasilApiMetadata() {
        return yggdrasilApiMetadata;
    }

    public static String digAliYggdrasilURL() {
        try {
            Class<?> aliClass = Class.forName("moe.yushi.authlibinjector.AuthlibInjector", false, ClassLoader.getSystemClassLoader());
            URL aliJarUrl = aliClass.getProtectionDomain().getCodeSource().getLocation();
            String aliJarName = Paths.get(aliJarUrl.toURI()).getFileName().toString();
            String aliSuffix1 = aliJarName + "=";
            String aliSuffix2 = aliJarName + "\"=";
            String aliArgString = ManagementFactory.getRuntimeMXBean()
                    .getInputArguments()
                    .stream()
                    .filter(arg -> arg.startsWith("-javaagent:"))
                    .filter(agArg -> agArg.contains(":" + aliSuffix1) || agArg.contains(":\"" + aliSuffix2) || agArg.contains("\\" + aliSuffix1) || agArg.contains("/" + aliSuffix1))
                    .findFirst()
                    .orElseThrow();
            if (aliArgString.contains(":\"" + aliSuffix2)) {
                return aliArgString.substring(aliArgString.indexOf(aliSuffix2) + aliSuffix2.length());
            } else {
                return aliArgString.substring(aliArgString.indexOf(aliSuffix1) + aliSuffix1.length());
            }
        } catch (ClassNotFoundException e) {
            return null;
        } catch (NullPointerException | NoSuchElementException | URISyntaxException e) {
            throw new RuntimeException("Failed to dig Yggdrasil URL from Authlib Injector params!", e);
        }
    }

    public static boolean validateMojangSession() throws IOException {
        try {
            MinecraftClient minecraftClient = MinecraftClient.getInstance();
            URI profileUri = new URI(Utils.MOJANG_SESSION_SERVER + "/minecraft/profile");
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(profileUri)
                    .header("Authorization", "Bearer " + minecraftClient.getSession().getAccessToken())
                    .GET()
                    .build();
            HttpResponse<Void> httpResponse = MSecNetwork.getHttpClient(minecraftClient.getNetworkProxy()).send(httpRequest, HttpResponse.BodyHandlers.discarding());
            return httpResponse.statusCode() == 200;
        } catch (URISyntaxException | InterruptedException e) {
            throw new IOException(e);
        }
    }
}
