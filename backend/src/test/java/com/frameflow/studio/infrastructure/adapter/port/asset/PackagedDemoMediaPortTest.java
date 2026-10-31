package com.frameflow.studio.infrastructure.adapter.port.asset;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import com.frameflow.studio.cases.asset.impl.MediaResourceCaseImpl;

import static org.assertj.core.api.Assertions.*;

class PackagedDemoMediaPortTest {
    @Test
    void coversAndInternalFixtureAreDecodableAndContentCannotBeMutated() throws Exception {
        var port = new PackagedDemoMediaPort();
        var media = new MediaResourceCaseImpl(port, org.mockito.Mockito.mock(
                com.frameflow.studio.domain.asset.adapter.repository.IUploadedAssetRepository.class),
                org.mockito.Mockito.mock(com.frameflow.studio.domain.asset.adapter.port.IAssetStoragePort.class));
        assertThat(port.publicCoverKeys()).containsExactlyInAnyOrder("portrait-cover", "postcard-cover");
        for (String key : port.publicCoverKeys()) {
            var image = media.findCover(key).orElseThrow();
            assertThat(image.mediaType()).isEqualTo("image/jpeg");
            assertJpeg(image.bytes());
            byte[] copy = image.bytes();
            copy[0] = 0;
            assertThat(image.bytes()[0]).isEqualTo((byte) 0xff);
            copy = port.findCover(key).orElseThrow().bytes();
            copy[0] = 0;
            assertThat(media.findCover(key).orElseThrow().bytes()[0]).isEqualTo((byte) 0xff);
        }
        for (String reference : new String[]{"demo:mock-result", "demo:portrait-result", "demo:postcard-result"}) {
            var result = media.prepareResult(reference);
            assertThat(result.reference()).isEqualTo(reference);
            assertThat(result.mediaType()).isEqualTo("image/jpeg");
            assertJpeg(result.bytes());
        }
        assertThat(media.prepareResult("demo:portrait-result").bytes())
                .isNotEqualTo(media.prepareResult("demo:postcard-result").bytes())
                .isNotEqualTo(media.findCover("portrait-cover").orElseThrow().bytes());
        for (String key : new String[]{"mock-result", "portrait-result", "postcard-result", "demo:mock-result", "../application.yml", "/etc/passwd",
                "portrait-cover.jpg", "demo-media/portrait-cover.jpg", "unknown"}) {
            assertThat(media.findCover(key)).as(key).isEmpty();
        }
    }

    @Test
    void actualMimeComesFromBytesRatherThanResourceName() throws Exception {
        Resource jpegWithPngName = new ClassPathResource("demo-media/portrait-cover.jpg") {
            @Override public String getFilename() { return "wrong.png"; }
        };
        var port = new PackagedDemoMediaPort(Map.of("cover", jpegWithPngName), Map.of("demo:result", jpegWithPngName));
        assertThat(port.findCover("cover").orElseThrow().mediaType()).isEqualTo("image/jpeg");
        var png = new ByteArrayOutputStream();
        try (var input = jpegWithPngName.getInputStream()) {
            ImageIO.write(ImageIO.read(input), "png", png);
        }
        var pngPort = new PackagedDemoMediaPort(Map.of("cover", new ByteArrayResource(png.toByteArray())), Map.of("demo:result", jpegWithPngName));
        assertThat(pngPort.findCover("cover").orElseThrow().mediaType()).isEqualTo("image/png");
    }

    @Test
    void missingCorruptOrTruncatedImageFailsStartupIncludingInternalFixture() throws Exception {
        var valid = new ClassPathResource("demo-media/portrait-cover.jpg");
        byte[] truncated = java.util.Arrays.copyOf(valid.getContentAsByteArray(), 40);
        for (Resource invalid : new Resource[]{new ClassPathResource("missing.jpg"),
                new ByteArrayResource(new byte[]{1, 2, 3}), new ByteArrayResource(truncated)}) {
            assertThatThrownBy(() -> new PackagedDemoMediaPort(Map.of("cover", invalid), Map.of("demo:result", valid)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cannot load packaged demo image");
            try (var context = new AnnotationConfigApplicationContext()) {
                context.registerBean(PackagedDemoMediaPort.class,
                        () -> new PackagedDemoMediaPort(Map.of("cover", valid), Map.of("demo:result", invalid)));
                assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(java.io.IOException.class);
                assertThat(context.isActive()).isFalse();
            }
        }
    }

    private void assertJpeg(byte[] bytes) throws Exception {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var reader = ImageIO.getImageReaders(input).next();
            try {
                assertThat(reader.getFormatName()).isEqualToIgnoringCase("jpeg");
                reader.setInput(input);
                var image = reader.read(0);
                assertThat(image.getWidth()).isEqualTo(640);
                assertThat(image.getHeight()).isEqualTo(400);
            } finally {
                reader.dispose();
            }
        }
    }
}
