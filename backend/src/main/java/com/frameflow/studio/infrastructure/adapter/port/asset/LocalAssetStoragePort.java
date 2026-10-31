package com.frameflow.studio.infrastructure.adapter.port.asset;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.frameflow.studio.domain.asset.adapter.port.IAssetStoragePort;
import com.frameflow.studio.domain.asset.model.valobj.AssetException;
import com.frameflow.studio.domain.asset.model.valobj.StoredImage;
import static com.frameflow.studio.domain.asset.model.valobj.AssetException.Reason.*;

public class LocalAssetStoragePort implements IAssetStoragePort {
    private static final Logger log = LoggerFactory.getLogger(LocalAssetStoragePort.class);
    private static final int MAX_BYTES = 8388608;
    private static final int MAX_DIMENSION = 8192;
    private static final long MAX_PIXELS = 16_000_000;
    private static final long MAX_DECODE_BYTES = 128L * 1024 * 1024;
    private final Path root;

    public LocalAssetStoragePort(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public StoredImage prepare(UUID assetId, InputStream input) {
        String key = "upload:" + assetId;
        Path temporary = path(key, true);
        boolean created = false;
        boolean prepared = false;
        try {
            Files.createDirectories(root);
            try (var output = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                byte[] buffer = new byte[8192];
                int total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_BYTES) throw new AssetException(TOO_LARGE);
                    output.write(buffer, 0, count);
                }
            }
            long size = Files.size(temporary);
            if (size == 0) throw new AssetException(INVALID_IMAGE);
            String mime = inspect(temporary);
            prepared = true;
            return new StoredImage(key, mime, size);
        } catch (IOException exception) {
            throw new AssetException(UNAVAILABLE);
        } finally {
            // A failed prepare cannot hand its key back to the Case for compensation.
            if (created && !prepared) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException cleanupFailure) { log.warn("Temporary upload cleanup failed for asset {}", assetId); }
            }
        }
    }

    private String inspect(Path file) {
        try (var input = new FileImageInputStream(file.toFile())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new AssetException(UNSUPPORTED_TYPE);
            var reader = readers.next();
            try {
                String mime = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpeg", "jpg" -> "image/jpeg";
                    case "png" -> "image/png";
                    default -> throw new AssetException(UNSUPPORTED_TYPE);
                };
                // Validation does not need descriptive metadata; keep the original file bytes intact.
                reader.setInput(input, true, true);
                boolean[] warning = {false};
                reader.addIIOReadWarningListener((source, message) -> warning[0] = true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDecodeBudget(reader, width, height);
                var parameters = reader.getDefaultReadParam();
                // Sampling reduces output allocation only; original dimensions were checked before decoding.
                int sample = Math.max(1, (Math.max(width, height) + 2047) / 2048);
                parameters.setSourceSubsampling(sample, sample, 0, 0);
                if (reader.read(0, parameters) == null || warning[0]) throw new AssetException(INVALID_IMAGE);
                return mime;
            } finally {
                reader.dispose();
            }
        } catch (AssetException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new AssetException(INVALID_IMAGE);
        }
    }

    private void validateDecodeBudget(ImageReader reader, int width, int height) throws IOException {
        if (width < 1 || height < 1) throw new AssetException(INVALID_IMAGE);
        long pixels = (long) width * height;
        if (width > MAX_DIMENSION || height > MAX_DIMENSION || pixels > MAX_PIXELS) {
            throw new AssetException(TOO_LARGE);
        }
        var rawType = reader.getRawImageType(0);
        int bytesPerPixel = rawType == null ? 8 : Math.max(4,
                (Arrays.stream(rawType.getSampleModel().getSampleSize()).sum() + 7) / 8);
        // Budget two full rasters and three original-width scanlines. PNG 16-bit RGBA needs 8 bytes/pixel.
        // This is a per-image working-memory estimate, not a bound on total JVM memory or concurrency.
        long estimatedBytes = 2L * pixels * bytesPerPixel + 3L * width * bytesPerPixel;
        if (estimatedBytes > MAX_DECODE_BYTES) throw new AssetException(TOO_LARGE);
    }

    @Override
    public void publish(String contentKey) {
        try {
            Path temporary = path(contentKey, true);
            if (!Files.isRegularFile(temporary, LinkOption.NOFOLLOW_LINKS)) throw new AssetException(UNAVAILABLE);
            // Same directory/filesystem, no replacement of an existing immutable input.
            Files.move(temporary, path(contentKey, false));
        } catch (IOException exception) {
            throw new AssetException(UNAVAILABLE);
        }
    }

    @Override
    public byte[] read(String contentKey) {
        Path file = path(contentKey, false);
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new AssetException(UNAVAILABLE);
            try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new AssetException(UNAVAILABLE);
                return bytes;
            }
        } catch (IOException exception) {
            throw new AssetException(UNAVAILABLE);
        }
    }

    @Override
    public void discard(String contentKey) {
        try {
            Files.deleteIfExists(path(contentKey, true));
            Files.deleteIfExists(path(contentKey, false));
        } catch (IOException exception) {
            throw new AssetException(UNAVAILABLE);
        }
    }

    private Path path(String key, boolean temporary) {
        if (key == null || !key.matches("upload:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new AssetException(UNAVAILABLE);
        }
        Path path = root.resolve(key.substring(7) + (temporary ? ".part" : ".image")).normalize();
        if (!path.getParent().equals(root)) throw new AssetException(UNAVAILABLE);
        return path;
    }
}
