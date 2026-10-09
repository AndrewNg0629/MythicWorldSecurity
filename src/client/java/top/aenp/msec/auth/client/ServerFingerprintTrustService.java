package top.aenp.msec.auth.client;

import top.aenp.msec.MythicWorldSecurity;
import java.nio.file.Path;

public class ServerFingerprintTrustService {
    private static final Path trustedFingerprintPath = MythicWorldSecurity.MAIN_DIR.resolve("trusted_servers.json").normalize();

}
