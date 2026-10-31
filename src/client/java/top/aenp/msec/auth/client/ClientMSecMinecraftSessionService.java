package top.aenp.msec.auth.client;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import top.aenp.msec.auth.AbstractMSecMinecraftSessionService;

import java.net.InetAddress;
import java.util.UUID;

public class ClientMSecMinecraftSessionService extends AbstractMSecMinecraftSessionService {
    private final MinecraftSessionService vanillaSessionService;

    public ClientMSecMinecraftSessionService(MinecraftSessionService vanillaSessionService) {
        this.vanillaSessionService = vanillaSessionService;
    }

    @Override
    public void joinServer(UUID profileId, String authenticationToken, String apiNonce) throws AuthenticationException {
        vanillaSessionService.joinServer(profileId, authenticationToken, apiNonce);
    }

    @Override
    public ProfileResult hasJoinedServer(String profileName, String serverId, InetAddress address) throws AuthenticationUnavailableException {
        return vanillaSessionService.hasJoinedServer(profileName, serverId, address);
    }

    @Override
    public ProfileResult fetchProfile(UUID profileId, boolean requireSecure) {
        //TODO Implement.
        return null;
    }
}
