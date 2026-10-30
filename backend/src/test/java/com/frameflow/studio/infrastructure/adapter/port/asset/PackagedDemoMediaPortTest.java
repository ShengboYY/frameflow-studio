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
        var media = new MediaResourceCaseImpl(port);
        assertThat(port.publicCoverKeys()).containsExactlyInAnyOrder("portrait-cover", "postcard-cover");
        for (String key : port.publicCoverKeys()) {
            var image = media.findCover(key).orElseThrow();
            assertThat(image.mediaType()).isEqualTo("image/png");
            assertThat(ImageIO.read(new ByteArrayInputStream(image.bytes())).getWidth()).isEqualTo(640);
            byte[] copy = image.bytes();
            copy[0] = 0;
            assertThat(image.bytes()[0]).isEqualTo((byte) 0x89);
            copy = port.findCover(key).orElseThrow().bytes();
            copy[0] = 0;
            assertThat(media.findCover(key).orElseThrow().bytes()[0]).isEqualTo((byte) 0x89);
        }
        assertThat(media.resultFixture().reference()).isEqualTo("demo:mock-result");
        assertThat(ImageIO.read(new ByteArrayInputStream(media.resultFixture().bytes()))).isNotNull();
        for (String key : new String[]{"mock-result", "demo:mock-result", "../application.yml", "/etc/passwd",
                "portrait-cover.png", "demo-media/portrait-cover.png", "unknown"}) {
            assertThat(media.findCover(key)).as(key).isEmpty();
        }
    }

    @Test
    void actualMimeComesFromBytesRatherThanResourceName() throws Exception {
        Resource pngWithJpegName = new ClassPathResource("demo-media/portrait-cover.png") {
            @Override public String getFilename() { return "wrong.jpg"; }
        };
        var port = new PackagedDemoMediaPort(Map.of("cover", pngWithJpegName), pngWithJpegName);
        assertThat(port.findCover("cover").orElseThrow().mediaType()).isEqualTo("image/png");
        var jpeg = new ByteArrayOutputStream();
        ImageIO.write(ImageIO.read(pngWithJpegName.getInputStream()), "jpeg", jpeg);
        var jpegPort = new PackagedDemoMediaPort(Map.of("cover", new ByteArrayResource(jpeg.toByteArray())), pngWithJpegName);
        assertThat(jpegPort.findCover("cover").orElseThrow().mediaType()).isEqualTo("image/jpeg");
    }

    @Test
    void missingCorruptOrTruncatedImageFailsStartupIncludingInternalFixture() throws Exception {
        var valid = new ClassPathResource("demo-media/portrait-cover.png");
        byte[] truncated = java.util.Arrays.copyOf(valid.getContentAsByteArray(), 40);
        for (Resource invalid : new Resource[]{new ClassPathResource("missing.png"),
                new ByteArrayResource(new byte[]{1, 2, 3}), new ByteArrayResource(truncated)}) {
            assertThatThrownBy(() -> new PackagedDemoMediaPort(Map.of("cover", invalid), valid))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cannot load packaged demo image");
            try (var context = new AnnotationConfigApplicationContext()) {
                context.registerBean(PackagedDemoMediaPort.class,
                        () -> new PackagedDemoMediaPort(Map.of("cover", valid), invalid));
                assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(java.io.IOException.class);
                assertThat(context.isActive()).isFalse();
            }
        }
    }
}
