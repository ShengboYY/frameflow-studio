package com.frameflow.studio.infrastructure.adapter.port.asset;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import com.frameflow.studio.domain.asset.adapter.port.IDemoMediaPort;
import com.frameflow.studio.domain.asset.model.valobj.PackagedImage;

public class PackagedDemoMediaPort implements IDemoMediaPort {
    private final Map<String, PackagedImage> covers;
    private final Map<String, PackagedImage> results;

    public PackagedDemoMediaPort() {
        this(Map.of("portrait-cover", new ClassPathResource("demo-media/portrait-cover.jpg"),
                        "postcard-cover", new ClassPathResource("demo-media/postcard-cover.jpg")),
                Map.of("demo:portrait-result", new ClassPathResource("demo-media/portrait-result.jpg"),
                        "demo:postcard-result", new ClassPathResource("demo-media/postcard-result.jpg"),
                        // Existing successful tasks keep resolving their original stable reference.
                        "demo:mock-result", new ClassPathResource("demo-media/mock-result.jpg")));
    }

    PackagedDemoMediaPort(Map<String, Resource> coverResources, Map<String, Resource> resultResources) {
        var loaded = new LinkedHashMap<String, PackagedImage>();
        coverResources.forEach((key, resource) -> loaded.put(key, load("demo:" + key, resource)));
        covers = Map.copyOf(loaded);
        var loadedResults = new LinkedHashMap<String, PackagedImage>();
        resultResources.forEach((reference, resource) -> loadedResults.put(reference, load(reference, resource)));
        results = Map.copyOf(loadedResults);
    }

    public Set<String> publicCoverKeys() {
        return covers.keySet();
    }

    @Override
    public Optional<PackagedImage> findCover(String mediaKey) {
        // Keys only index already loaded images; request text never becomes a resource path.
        return Optional.ofNullable(covers.get(mediaKey));
    }

    @Override
    public Optional<PackagedImage> findResult(String reference) {
        return Optional.ofNullable(results.get(reference));
    }

    private static PackagedImage load(String reference, Resource resource) {
        try (var input = resource.getInputStream()) {
            byte[] bytes = input.readAllBytes();
            try (var imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) throw new IOException("Unsupported packaged image");
                var reader = readers.next();
                try {
                    String mime = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                        case "png" -> "image/png";
                        case "jpeg", "jpg" -> "image/jpeg";
                        default -> throw new IOException("Unsupported packaged image type");
                    };
                    reader.setInput(imageInput);
                    if (reader.read(0) == null) throw new IOException("Unreadable packaged image");
                    return new PackagedImage(reference, mime, bytes);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot load packaged demo image: " + reference, exception);
        }
    }
}
