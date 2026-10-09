package top.aenp.msec.network;

import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.Utils;
import top.aenp.msec.network.payloads.texture.LoginTextureDataC2SPayload;
import top.aenp.msec.network.payloads.texture.PlayTextureRequestC2SPayload;
import top.aenp.msec.network.payloads.texture.PlayTextureResponseS2CPayload;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingDeque;

public class ServerTextureService {
    private static ServerTextureService INSTANCE = null;
    private final LinkedBlockingDeque<RequestEntry> requestQueue = new LinkedBlockingDeque<>();
    private final LinkedBlockingDeque<SubmissionEntry> submissionQueue = new LinkedBlockingDeque<>();
    private final HashMap<String, CacheEntry> inMemoryCache = new HashMap<>();
    private final Path cacheDirPath = MythicWorldSecurity.MAIN_DIR.resolve("texture_caches").normalize();
    private final Object lock = new Object();

    public static ServerTextureService getInstance() {
        if (INSTANCE != null) {
            return INSTANCE;
        } else {
            throw new IllegalStateException("Server texture service has not been initialized!");
        }
    }

    public static void init() {
        if (INSTANCE == null) {
            INSTANCE = new ServerTextureService();
        } else {
            throw new IllegalStateException("Server texture service has already been initialized!");
        }
    }

    private record RequestEntry(ServerPlayNetworkHandler handler, PlayTextureRequestC2SPayload payload) {
    }

    private record SubmissionEntry(ServerLoginNetworkHandler handler, LoginTextureDataC2SPayload payload) {
    }

    private static class CacheEntry {
        private final byte[] cache;
        private long lastTouched;

        public CacheEntry(byte[] data) {
            cache = data;
            lastTouched = System.currentTimeMillis();
        }

        public byte[] get() {
            lastTouched = System.currentTimeMillis();
            return cache;
        }
    }

    public void onRequest(ServerPlayNetworkHandler handler, PlayTextureRequestC2SPayload payload) {
        requestQueue.add(new RequestEntry(handler, payload));
    }

    public void onSubmission(ServerLoginNetworkHandler handler, LoginTextureDataC2SPayload payload) {
        submissionQueue.add(new SubmissionEntry(handler, payload));
    }

    public Path getCacheFilePathSafely(String hash) {
        if (hash == null) {
            return null;
        }
        try {
            Path cacheFilePath = cacheDirPath.resolve(hash).normalize();
            if (!cacheFilePath.startsWith(cacheDirPath)) {
                return null;
            }
            if (Files.isSymbolicLink(cacheFilePath)) {
                return null;
            }
            if (!Files.exists(cacheFilePath)) {
                return null;
            }
            cacheFilePath = cacheFilePath.toRealPath();
            if (!cacheFilePath.startsWith(cacheDirPath) || !Files.isRegularFile(cacheFilePath)) {
                return null;
            }
            return cacheFilePath;
        } catch (Exception e) {
            MythicWorldSecurity.LOGGER.warn("Failed to get path for texture {}", hash, e);
            return null;
        }
    }

    private PlayTextureResponseS2CPayload computeResponsePayload(RequestEntry entry) {
        synchronized (lock) {
            String hash = entry.payload.textureHash();
            CacheEntry cacheEntry = inMemoryCache.get(hash);
            if (cacheEntry != null) {
                return new PlayTextureResponseS2CPayload(hash, cacheEntry.get());
            } else {
                try {
                    Path cacheFilePath = getCacheFilePathSafely(hash + ".png");
                    if (cacheFilePath == null) {
                        return new PlayTextureResponseS2CPayload(hash, null);
                    }
                    try (InputStream inputStream = Files.newInputStream(cacheFilePath)) {
                        byte[] data = inputStream.readAllBytes();
                        inMemoryCache.put(hash, new CacheEntry(data));
                        return new PlayTextureResponseS2CPayload(hash, data);
                    }
                } catch (Exception e) {
                    MythicWorldSecurity.LOGGER.warn("Failed to distribute texture {}", hash, e);
                    return new PlayTextureResponseS2CPayload(hash, null);
                }
            }
        }
    }

    private void collectTexture(SubmissionEntry entry) {
        LoginTextureDataC2SPayload payload = entry.payload;
        try {
            if (payload.data() == null) {
                entry.handler.mythicworldsecurity$textureFileWriteCallback(payload.type(), false, null);
                return;
            }
            BufferedImage bufferedImage = Utils.decompressTexture(payload.data(), payload.width(), payload.height(), 64, 64);
            byte[] texturePngBytes = Utils.encodeBufferedImage(bufferedImage);
            String hash = Utils.hashBytes1(texturePngBytes);
            boolean sizeValid = switch (payload.type()) {
                case SKIN -> bufferedImage.getWidth() == 64 && bufferedImage.getHeight() == 64;
                case CAPE -> bufferedImage.getWidth() == 64 && bufferedImage.getHeight() == 32;
            };
            if (!sizeValid) {
                MythicWorldSecurity.LOGGER.info("Texture {} has invalid size of {}*{}.", hash, bufferedImage.getWidth(), bufferedImage.getHeight());
                entry.handler.mythicworldsecurity$textureFileWriteCallback(payload.type(), false, null);
                return;
            }
            Path cacheFilePath = cacheDirPath.resolve(hash + ".png");
            createTextureDirIfAbsent();
            try (OutputStream outputStream = Files.newOutputStream(cacheFilePath)) {
                outputStream.write(texturePngBytes);
                entry.handler.mythicworldsecurity$textureFileWriteCallback(payload.type(), true, hash);
            }
        } catch (Exception e) {
            MythicWorldSecurity.LOGGER.error("Exception collecting texture: {}", e.toString());
            entry.handler.mythicworldsecurity$textureFileWriteCallback(payload.type(), false, null);
        }
    }

    private void createTextureDirIfAbsent() throws IOException {
        if (!Files.exists(cacheDirPath)) {
            Files.createDirectories(cacheDirPath);
        }
        if (!Files.isDirectory(cacheDirPath)) {
            throw new IOException("Texture cache dir should be a directory!");
        }
    }

    @SuppressWarnings("BusyWait")
    private ServerTextureService() {
        try {
            createTextureDirIfAbsent();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Runnable distributionTask = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    RequestEntry entry = requestQueue.take();
                    PlayTextureResponseS2CPayload payload = computeResponsePayload(entry);
                    entry.handler.sendPacket(new CustomPayloadS2CPacket(payload));
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    MythicWorldSecurity.LOGGER.error("Exception in texture distribution thread.", e);
                }
            }
        };
        Runnable collectTask = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    SubmissionEntry entry = submissionQueue.take();
                    collectTexture(entry);
                } catch (InterruptedException e) {
                    break;
                }
            }
        };
        Runnable cacheCleaner = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    synchronized (lock) {
                        inMemoryCache.entrySet().removeIf(stringCacheEntryEntry -> stringCacheEntryEntry.getValue().lastTouched + 180000 <= System.currentTimeMillis());
                    }
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    MythicWorldSecurity.LOGGER.error("Exception in server texture cache cleaner thread.", e);
                }
            }
        };
        Thread.startVirtualThread(distributionTask).setName("MSec Texture Distributor");
        Thread.startVirtualThread(collectTask).setName("MSec Texture Collector");
        Thread.startVirtualThread(cacheCleaner).setName("MSec Server Cache Cleaner");
    }
}
