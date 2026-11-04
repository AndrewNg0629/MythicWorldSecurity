package top.aenp.msec.network.client;

import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.response.MinecraftTexturesPayload;
import com.mojang.util.UUIDTypeAdapter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.text.Text;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.Utils;
import top.aenp.msec.network.MSecNetwork;
import top.aenp.msec.network.payloads.texture.c2s.LoginTextureDataC2SPayload;
import top.aenp.msec.network.payloads.texture.c2s.LoginTextureMetadataC2SPayload;
import top.aenp.msec.network.payloads.texture.c2s.PlayTextureRequestC2SPayload;
import top.aenp.msec.network.payloads.texture.s2c.LoginTextureCommandS2CPayload;
import top.aenp.msec.network.payloads.texture.s2c.PlayTextureResponseS2CPayload;
import top.aenp.mwl.network.v2.MythicNetwork;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ClientTextureService {
    private static final Gson PROFILE_GSON = new GsonBuilder()
            .registerTypeAdapter(GameProfile.class, new GameProfile.Serializer())
            .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
            .registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer())
            .create();
    private static ClientTextureService INSTANCE = null;
    private final HashMap<String, ClientTextureRequestEntry> textureRequests = new HashMap<>();
    private final LinkedBlockingDeque<PlayTextureResponseS2CPayload> textureResponseQueue = new LinkedBlockingDeque<>();
    private final LinkedBlockingDeque<TextureCommandEntry> textureCommandQueue = new LinkedBlockingDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private LocalTextureCache localTextureCache = null;

    private ClientTextureService() {
        Runnable receptionTask = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    PlayTextureResponseS2CPayload payload = textureResponseQueue.take();
                    try {
                        lock.lock();
                        String hash = payload.hash();
                        ClientTextureRequestEntry entry = textureRequests.get(hash);
                        if (entry != null) {
                            entry.future.complete(payload);
                            textureRequests.remove(hash);
                        }
                    } finally {
                        lock.unlock();
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
                    TextureCommandEntry entry = textureCommandQueue.take();
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
                                    byte[] encodedSkin = encodeBufferedImage(skinImage);
                                    skinHash = Utils.hashBytes1(encodedSkin);
                                }
                                if (capeImage != null) {
                                    byte[] encodedCape = encodeBufferedImage(capeImage);
                                    capeHash = Utils.hashBytes1(encodedCape);
                                }
                                LoginTextureMetadataC2SPayload textureMetadataPayload = new LoginTextureMetadataC2SPayload(skinHash, slim, capeHash);
                                entry.connection.send(MythicNetwork.createLoginC2SPacket(textureMetadataPayload));
                                break;
                            case GET_SKIN:
                                LoginTextureDataC2SPayload skinDataPayload = new LoginTextureDataC2SPayload(LoginTextureDataC2SPayload.TextureType.SKIN, 0, 0, null);
                                BufferedImage cachedSkinImage = localTextureCache.skin;
                                if (cachedSkinImage != null) {
                                    byte[] compressedSkin = compressBufferedImage(cachedSkinImage);
                                    skinDataPayload = new LoginTextureDataC2SPayload(LoginTextureDataC2SPayload.TextureType.SKIN, cachedSkinImage.getWidth(), cachedSkinImage.getHeight(), compressedSkin);
                                }
                                entry.connection.send(MythicNetwork.createLoginC2SPacket(skinDataPayload));
                                break;
                            case GET_CAPE:
                                LoginTextureDataC2SPayload capeDataPayload = new LoginTextureDataC2SPayload(LoginTextureDataC2SPayload.TextureType.CAPE, 0, 0, null);
                                BufferedImage cachedCapeImage = localTextureCache.cape;
                                if (cachedCapeImage != null) {
                                    byte[] compressedCape = compressBufferedImage(cachedCapeImage);
                                    capeDataPayload = new LoginTextureDataC2SPayload(LoginTextureDataC2SPayload.TextureType.CAPE, cachedCapeImage.getWidth(), cachedCapeImage.getHeight(), compressedCape);
                                }
                                entry.connection.send(MythicNetwork.createLoginC2SPacket(capeDataPayload));
                                break;
                        }
                    } catch (IOException e) {
                        entry.connection.disconnect(Text.of("MSec client error."));
                        MythicWorldSecurity.LOGGER.error("Error executing texture command.", e);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        };
        Thread receiverThread = new Thread(receptionTask, "MSec Texture Receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();
        Thread commonExecutor = new Thread(commandExecTask, "MSec Server Texture Command Executor");
        commonExecutor.setDaemon(true);
        commonExecutor.start();
    }

    private static BufferedImage fetchBufferedImageFromHttp(String url) throws IOException {
        HttpResponse<InputStream> response;
        try {
            URI textureUri = new URI(url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(textureUri)
                    .GET()
                    .build();
            response = MSecNetwork.getHttpClient(MinecraftClient.getInstance().getNetworkProxy()).send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
        } catch (URISyntaxException | InterruptedException e) {
            throw new IOException(e);
        }
        int responseCode = response.statusCode();
        if (responseCode >= 200 && responseCode < 400) {
            try (InputStream inputStream = response.body()) {
                return ImageIO.read(inputStream);
            }
        } else {
            throw new IOException(String.format("Failed to fetch %s", url));
        }
    }

    public static ClientTextureService getInstance() {
        if (INSTANCE != null) {
            return INSTANCE;
        } else {
            throw new IllegalStateException("Client texture service has not been initialized!");
        }
    }

    public static void init() {
        if (INSTANCE == null) {
            INSTANCE = new ClientTextureService();
        } else {
            throw new IllegalStateException("Client texture service has already been initialized!");
        }
    }

    private static byte[] unpackIntArray(int[] intArray) {
        byte[] byteArray = new byte[intArray.length * 4];
        for (int a = 0; a < intArray.length; a++) {
            byteArray[a * 4] = (byte) (intArray[a] >>> 24 & 0xff);
            byteArray[a * 4 + 1] = (byte) (intArray[a] >>> 16 & 0xff);
            byteArray[a * 4 + 2] = (byte) (intArray[a] >>> 8 & 0xff);
            byteArray[a * 4 + 3] = (byte) (intArray[a] & 0xff);
        }
        return byteArray;
    }

    private static byte[] compressBufferedImage(BufferedImage bufferedImage) throws IOException {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        int[] intPixelData = bufferedImage.getRGB(0, 0, width, height, null, 0, width);
        byte[] bytePixelData = unpackIntArray(intPixelData);
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)
        ) {
            zipOutputStream.setMethod(ZipOutputStream.DEFLATED);
            zipOutputStream.setLevel(Deflater.DEFAULT_COMPRESSION);
            zipOutputStream.putNextEntry(new ZipEntry("pixels"));
            zipOutputStream.write(bytePixelData);
            zipOutputStream.closeEntry();
            return byteArrayOutputStream.toByteArray();
        }
    }

    public static byte[] encodeBufferedImage(BufferedImage bufferedImage) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            ImageIO.write(bufferedImage, "PNG", byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        }
    }

    public CompletableFuture<PlayTextureResponseS2CPayload> requestTexture(ClientPlayNetworkHandler handler, String hash) {
        try {
            lock.lock();
            ClientTextureRequestEntry existingEntry = textureRequests.get(hash);
            if (existingEntry != null) {
                return existingEntry.future;
            } else {
                CompletableFuture<PlayTextureResponseS2CPayload> future = new CompletableFuture<>();
                textureRequests.put(hash, new ClientTextureRequestEntry(future, System.currentTimeMillis()));
                handler.getConnection().send(MythicNetwork.createPlayC2SPacket(new PlayTextureRequestC2SPayload(hash)));
                return future;
            }
        } finally {
            lock.unlock();
        }
    }

    private void cacheLocalTexturesIfAbsent() {
        if (localTextureCache == null) {
            localTextureCache = new LocalTextureCache(null, false, null);
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                PropertyMap properties = client.getSessionProperties();
                Property textureProperty = Iterables.getFirst(properties.get("textures"), null);
                if (textureProperty != null) {
                    MinecraftTexturesPayload payload = PROFILE_GSON.fromJson(new String(Base64.getDecoder().decode(textureProperty.getValue()), StandardCharsets.UTF_8), MinecraftTexturesPayload.class);
                    Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = payload.getTextures();
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
        textureResponseQueue.add(payload);
    }

    public void onTextureCommand(LoginTextureCommandS2CPayload payload, ClientConnection connection) {
        textureCommandQueue.add(new TextureCommandEntry(payload.command(), connection));
    }

    private record ClientTextureRequestEntry(CompletableFuture<PlayTextureResponseS2CPayload> future, long requestedOn) {
    }

    private record TextureCommandEntry(LoginTextureCommandS2CPayload.Command command, ClientConnection connection) {
    }

    private record LocalTextureCache(BufferedImage skin, boolean slimModel, BufferedImage cape) {
    }
}
