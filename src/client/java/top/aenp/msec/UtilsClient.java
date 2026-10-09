package top.aenp.msec;

import java.lang.management.ManagementFactory;
import java.net.*;
import java.nio.file.Paths;
import java.util.NoSuchElementException;

public class UtilsClient {
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
}
