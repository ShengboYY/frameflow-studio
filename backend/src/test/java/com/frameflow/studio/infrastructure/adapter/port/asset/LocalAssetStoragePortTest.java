package com.frameflow.studio.infrastructure.adapter.port.asset;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.frameflow.studio.domain.asset.model.valobj.AssetException;
import static org.assertj.core.api.Assertions.*;
import static com.frameflow.studio.support.PngFixtures.*;

class LocalAssetStoragePortTest {
    @TempDir Path root;

    @Test
    void detectsBothRealTypesAndPreservesBytesAcrossRecreatedStorage() throws Exception {
        for (String format : new String[]{"png", "jpeg"}) {
            byte[] bytes = image(format);
            var storage = new LocalAssetStoragePort(root);
            var image = storage.prepare(UUID.randomUUID(), new ByteArrayInputStream(bytes));
            assertThat(image.mediaType()).isEqualTo("image/" + format);
            assertThat(image.sizeBytes()).isEqualTo(bytes.length);
            storage.publish(image.contentKey());
            assertThat(new LocalAssetStoragePort(root).read(image.contentKey())).isEqualTo(bytes);
        }
    }

    @Test
    void eightMiBIsInclusiveAndExcessOrInputFailureLeavesNoFiles() throws Exception {
        var storage = new LocalAssetStoragePort(root);
        byte[] bytes = Arrays.copyOf(image("jpeg"), 8388608);
        var image = storage.prepare(UUID.randomUUID(), new ByteArrayInputStream(bytes));
        storage.publish(image.contentKey());
        assertThat(storage.read(image.contentKey())).hasSize(8388608);
        storage.discard(image.contentKey());
        assertReason(() -> storage.prepare(UUID.randomUUID(), new ByteArrayInputStream(new byte[8388609])), "TOO_LARGE");
        assertReason(() -> storage.prepare(UUID.randomUUID(), new InputStream() {
            @Override public int read() throws IOException { throw new IOException("source failed"); }
        }), "UNAVAILABLE");
        try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
    }

    @Test
    void emptyUnsupportedAndCorruptImagesLeaveNoTemporaryContent() throws Exception {
        var storage = new LocalAssetStoragePort(root);
        assertReason(() -> storage.prepare(UUID.randomUUID(), new ByteArrayInputStream(new byte[0])), "INVALID_IMAGE");
        assertReason(() -> storage.prepare(UUID.randomUUID(), new ByteArrayInputStream("fake.png".getBytes())), "UNSUPPORTED_TYPE");
        for (String format : new String[]{"png", "jpeg"}) {
            byte[] bytes = image(format);
            assertReason(() -> storage.prepare(UUID.randomUUID(), new ByteArrayInputStream(Arrays.copyOf(bytes, bytes.length / 2))), "INVALID_IMAGE");
        }
        try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
    }

    @Test
    void rejectsOversizedDimensionsPixelsAndDecodeBudgetBeforePublishing() throws Exception {
        var storage = new LocalAssetStoragePort(root);
        for (byte[] bytes : new byte[][]{
                solid(8193, 1, 8, false), solid(1, 8193, 8, false),
                solid(4000, 4001, 8, false), solid(3000, 3000, 16, true),
                solid(8192, 1023, 16, true),
                header(50_000_000, 1), header(Integer.MAX_VALUE, Integer.MAX_VALUE)}) {
            assertThat(bytes.length).isLessThan(8388608);
            assertReason(() -> storage.prepare(UUID.randomUUID(), new ByteArrayInputStream(bytes)), "TOO_LARGE");
            try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
        }
    }

    @Test
    void acceptsInclusiveDimensionAndPixelLimitsAndSafeSixteenBitImages() throws Exception {
        var storage = new LocalAssetStoragePort(root);
        for (byte[] bytes : new byte[][]{
                solid(8192, 1, 8, false), solid(1, 8192, 8, false),
                solid(4000, 4000, 8, false), solid(1024, 1024, 16, true),
                solid(8192, 1022, 16, true)}) {
            var image = storage.prepare(UUID.randomUUID(), new ByteArrayInputStream(bytes));
            storage.publish(image.contentKey());
            assertThat(storage.read(image.contentKey())).isEqualTo(bytes);
            storage.discard(image.contentKey());
        }
    }

    @Test
    void arbitraryKeysMissingFilesAndSymbolicLinksCannotBeReadOrPublished() throws Exception {
        var storage = new LocalAssetStoragePort(root);
        for (String key : new String[]{"../secret", "upload:../../secret", root.toString(), "https://example.org/file"}) {
            assertReason(() -> storage.read(key), "UNAVAILABLE");
            assertReason(() -> storage.publish(key), "UNAVAILABLE");
            assertReason(() -> storage.discard(key), "UNAVAILABLE");
        }
        UUID id = UUID.randomUUID();
        String key = "upload:" + id;
        assertReason(() -> storage.read(key), "UNAVAILABLE");
        Path outside = Files.createTempFile(root.getParent(), "frameflow-outside-", ".jpg");
        try {
            Files.write(outside, image("jpeg"));
            Files.createSymbolicLink(root.resolve(id + ".image"), outside);
            assertReason(() -> storage.read(key), "UNAVAILABLE");
            Files.createSymbolicLink(root.resolve(id + ".part"), outside);
            assertReason(() -> storage.publish(key), "UNAVAILABLE");
            storage.discard(key);
            assertThat(Files.readAllBytes(outside)).isEqualTo(image("jpeg"));
        } finally { Files.delete(outside); }
    }

    public static byte[] image(String format) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 12, BufferedImage.TYPE_INT_RGB), format, output);
        return output.toByteArray();
    }

    private void assertReason(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String reason) {
        assertThatThrownBy(action).isInstanceOf(AssetException.class).hasMessage(reason);
    }
}
