package top.aenp.msec.network.client;

import com.google.common.collect.Iterables;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.response.MinecraftTexturesPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginQueryResponseC2SPacket;
import net.minecraft.text.Text;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.Utils;
import top.aenp.msec.network.payloads.texture.LoginTextureCommandS2CPayload;
import top.aenp.msec.network.payloads.texture.LoginTextureDataC2SPayload;
import top.aenp.msec.network.payloads.texture.LoginTextureMetadataC2SPayload;
import top.aenp.msec.network.payloads.texture.PlayTextureRequestC2SPayload;
import top.aenp.msec.network.payloads.texture.PlayTextureResponseS2CPayload;
import top.aenp.mwl.network.v2.MythicNetwork;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;

public class ClientTextureService {
    public static final ClientTextureService INSTANCE = new ClientTextureService();
    private final HashMap<String, ClientTextureRequestEntry> requests = new HashMap<>();
    private final LinkedBlockingDeque<PlayTextureResponseS2CPayload> requestQueue = new LinkedBlockingDeque<>();
    private final LinkedBlockingDeque<TextureCommandEntry> commandQueue = new LinkedBlockingDeque<>();
    private LocalTextureCache localTextureCache = null;
    private final Object lock = new Object();

    public static BufferedImage fetchBufferedImageFromHttp(String url) throws IOException {
        URL textureUrl;
        try {
            textureUrl = URL.of(new URI(url), null);
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        HttpURLConnection connection = (HttpURLConnection) textureUrl.openConnection(MinecraftClient.getInstance().getNetworkProxy());
        connection.setDoInput(true);
        connection.setDoOutput(false);
        connection.connect();
        int responseCode = connection.getResponseCode();
        if (responseCode >= 200 && responseCode < 400) {
            try (InputStream inputStream = connection.getInputStream()) {
                return ImageIO.read(inputStream);
            }
        } else {
            throw new IOException(String.format("Failed to fetch %s", url));
        }
    }

    private record ClientTextureRequestEntry(CompletableFuture<PlayTextureResponseS2CPayload> future, long requestedOn) {
    }

    private record TextureCommandEntry(LoginTextureCommandS2CPayload.Command command, ClientConnection connection) {
    }

    private record LocalTextureCache(BufferedImage skin, boolean slimModel, BufferedImage cape) {
    }

    public CompletableFuture<PlayTextureResponseS2CPayload> requestTexture(ClientPlayNetworkHandler handler, String hash) {
        synchronized (lock) {
            ClientTextureRequestEntry existingEntry = requests.get(hash);
            if (existingEntry != null) {
                return existingEntry.future;
            } else {
                CompletableFuture<PlayTextureResponseS2CPayload> future = new CompletableFuture<>();
                requests.put(hash, new ClientTextureRequestEntry(future, System.currentTimeMillis()));
                handler.getConnection().send(new CustomPayloadC2SPacket(new PlayTextureRequestC2SPayload(hash)));
                return future;
            }
        }
    }

    private void cacheLocalTexturesIfAbsent() {
        if (localTextureCache == null) {
            localTextureCache = new LocalTextureCache(null, false, null);
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                GameProfile profile = client.getGameProfile();
                Property textureProperty = Iterables.getFirst(profile.getProperties().get("textures"), null);
                if (textureProperty != null) {
                    MinecraftTexturesPayload payload = Utils.PROP_GSON.fromJson(new String(Base64.getDecoder().decode(textureProperty.value()), StandardCharsets.UTF_8), MinecraftTexturesPayload.class);
                    Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = payload.textures();
                    MinecraftProfileTexture skinProfileTexture = textures.get(MinecraftProfileTexture.Type.SKIN);
                    boolean slim = false;
                    MinecraftProfileTexture capeProfileTexture = textures.get(MinecraftProfileTexture.Type.CAPE);
                    BufferedImage skinImage = null;
                    BufferedImage capeImage = null;
                    if (skinProfileTexture != null) {
                        skinImage = fetchBufferedImageFromHttp(skinProfileTexture.getUrl());
                        String model = skinProfileTexture.getMetadata("model");
                        if (model != null) {
                            slim = model.equalsIgnoreCase("slim");
                        }
                    }
                    if (capeProfileTexture != null) {
                        capeImage = fetchBufferedImageFromHttp(capeProfileTexture.getUrl());
                    }
                    localTextureCache = new LocalTextureCache(skinImage, slim, capeImage);
                }
            } catch (IOException e) {
                MythicWorldSecurity.LOGGER.error("Error caching local texture.", e);
            }
        }
    }

    public void onTextureResponse(PlayTextureResponseS2CPayload payload) {
        requestQueue.add(payload);
    }

    public void onTextureCommand(LoginTextureCommandS2CPayload payload, ClientConnection connection) {
        commandQueue.add(new TextureCommandEntry(payload.command(), connection));
    }

    @SuppressWarnings("BusyWait")
    private ClientTextureService() {
        Runnable receivingTask = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    PlayTextureResponseS2CPayload payload = requestQueue.take();
                    synchronized (lock) {
                        String hash = payload.hash();
                        ClientTextureRequestEntry entry = requests.get(hash);
                        if (entry != null) {
                            entry.future.complete(payload);
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    MythicWorldSecurity.LOGGER.error("Exception in texture receiver thread.", e);
                }
            }
        };
        Runnable commandExecTask = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    TextureCommandEntry entry = commandQueue.take();
                    try {
                        cacheLocalTexturesIfAbsent();
                        switch (entry.command) {
                            case GET_METADATA:
                                String skinHash = null;
                                String capeHash = null;
                                BufferedImage skinImage = localTextureCache.skin;
                                BufferedImage capeImage = localTextureCache.cape;
                                boolean slim = localTextureCache.slimModel;
                                if (skinImage != null) {
                                    byte[] encodedSkin = Utils.encodeBufferedImage(skinImage);
                                    skinHash = Utils.hashBytes1(encodedSkin);
                                }
                                if (capeImage != null) {
                                    byte[] encodedCape = Utils.encodeBufferedImage(capeImage);
                                    capeHash = Utils.hashBytes1(encodedCape);
                                }
                                LoginTextureMetadataC2SPayload textureMetadataPayload = new LoginTextureMetadataC2SPayload(skinHash, slim, capeHash);
                                entry.connection.send(new LoginQueryResponseC2SPacket(MythicNetwork.QUERY_ID, textureMetadataPayload));
                                break;
                            case GET_SKIN:
                                LoginTextureDataC2SPayload skinDataPayload = new LoginTextureDataC2SPayload(LoginTextureDataC2SPayload.TextureType.SKIN, 0, 0, null);
                                BufferedImage cachedSkinImage = localTextureCache.skin;
                                if (cachedSkinImage != null) {
                                    byte[] compressedSkin = Utils.compressBufferedImage(cachedSkinImage);
                                    skinDataPayload = new LoginTextureDataC2SPayload(LoginTextureDataC2SPayload.TextureType.SKIN, cachedSkinImage.getWidth(), cachedSkinImage.getHeight(), compressedSkin);
                                }
                                entry.connection.send(new LoginQueryResponseC2SPacket(MythicNetwork.QUERY_ID, skinDataPayload));
                                break;
                            case GET_CAPE:
                                LoginTextureDataC2SPayload capeDataPayload = new LoginTextureDataC2SPayload(LoginTextureDataC2SPayload.TextureType.CAPE, 0, 0, null);
                                BufferedImage cachedCapeImage = localTextureCache.cape;
                                if (cachedCapeImage != null) {
                                    byte[] compressedCape = Utils.compressBufferedImage(cachedCapeImage);
                                    capeDataPayload = new LoginTextureDataC2SPayload(LoginTextureDataC2SPayload.TextureType.CAPE, cachedCapeImage.getWidth(), cachedCapeImage.getHeight(), compressedCape);
                                }
                                entry.connection.send(new LoginQueryResponseC2SPacket(MythicNetwork.QUERY_ID, capeDataPayload));
                                break;
                        }
                    } catch (IOException e) {
                        entry.connection.disconnect(Text.of("MSec client error."));
                        MythicWorldSecurity.LOGGER.error("Error exec texture command.", e);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        };
        Runnable cleaner = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    synchronized (lock) {
                        Iterator<Map.Entry<String, ClientTextureRequestEntry>> iterator = requests.entrySet().iterator();
                        while (iterator.hasNext()) {
                            Map.Entry<String, ClientTextureRequestEntry> entry = iterator.next();
                            if (entry.getValue().requestedOn + 30000 <= System.currentTimeMillis()) {
                                entry.getValue().future.complete(new PlayTextureResponseS2CPayload(entry.getKey(), null));
                                iterator.remove();
                            }
                        }
                    }
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    MythicWorldSecurity.LOGGER.error("Exception in client requests cleanup thread.", e);
                }
            }
        };
        Thread.startVirtualThread(receivingTask).setName("MSec Texture Receiver");
        Thread.startVirtualThread(commandExecTask).setName("MSec Server Texture Command Executor");
        Thread.startVirtualThread(cleaner).setName("MSec Zombie Requests Cleaner");
    }
}
