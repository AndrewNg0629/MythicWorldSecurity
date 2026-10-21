package top.aenp.msec.network;

import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import top.aenp.msec.MythicWorldSecurity;
import top.aenp.msec.Utils;
import top.aenp.msec.network.payloads.texture.LoginTextureDataC2SPayload;
import top.aenp.msec.network.payloads.texture.PlayTextureRequestC2SPayload;
import top.aenp.msec.network.payloads.texture.PlayTextureResponseS2CPayload;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ServerTextureService {
    private static ServerTextureService INSTANCE = null;
    private final LinkedBlockingDeque<RequestEntry> requestQueue = new LinkedBlockingDeque<>();
    private final LinkedBlockingDeque<SubmissionEntry> submissionQueue = new LinkedBlockingDeque<>();
    private final HashMap<String, CacheEntry> inMemoryCache = new HashMap<>();
    private final Path cacheDirPath = MythicWorldSecurity.MAIN_DIR.resolve("server").resolve("texture_caches").normalize();
    private final Object lock = new Object();

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

    private static byte[] limitedRead(InputStream inputStream) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            long totalBytes = 0L;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if ((totalBytes += bytesRead) > 16385) {
                    throw new IOException("Input is larger than limit: 16385 Bytes.");
                }
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            return byteArrayOutputStream.toByteArray();
        }
    }

    private static int[] packByteArray(byte[] byteArray) {
        if (byteArray.length % 4 != 0) {
            return new int[0];
        }
        int intArrayLength = byteArray.length / 4;
        int[] intArray = new int[intArrayLength];
        for (int a = 0; a < intArrayLength; a++) {
            intArray[a] = ((byteArray[a * 4] & 0xff) << 24) | ((byteArray[a * 4 + 1] & 0xff) << 16) | ((byteArray[a * 4 + 2] & 0xff) << 8) | (byteArray[a * 4 + 3] & 0xff);
        }
        return intArray;
    }

    private static BufferedImage decompressTexture(byte[] compressedPixels, int width, int height) throws IOException {
        if (width > 64 || height > 64) {
            throw new IOException(String.format("Texture size %d*%d exceeds limit %d*%d.", width, height, 64, 64));
        }
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(compressedPixels))) {
            ZipEntry entry = zipInputStream.getNextEntry();
            if (entry == null || !entry.getName().equals("pixels")) {
                throw new IOException("Bad compressed texture data.");
            }
            byte[] bytePixels = limitedRead(zipInputStream);
            zipInputStream.closeEntry();
            int[] intPixels = packByteArray(bytePixels);
            BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            try {
                bufferedImage.setRGB(0, 0, width, height, intPixels, 0, width);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new IOException(e);
            }
            return bufferedImage;
        }
    }

    public static byte[] encodeBufferedImage(BufferedImage bufferedImage) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            ImageIO.write(bufferedImage, "PNG", byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
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
            Path cacheFilePath = cacheDirPath.resolve(hash + ".png").normalize();
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
                    Path cacheFilePath = getCacheFilePathSafely(hash);
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
            BufferedImage bufferedImage = decompressTexture(payload.data(), payload.width(), payload.height());
            byte[] texturePngBytes = encodeBufferedImage(bufferedImage);
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
}
