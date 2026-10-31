import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Packages imagegen artwork as bounded JPEGs. Run from backend/; see demo-media/SOURCES.md. */
public class GenerateDemoMedia {
    public static void main(String[] args) throws Exception {
        if (args.length != 7) {
            throw new IllegalArgumentException("Expected portrait-source, coast-source, town-source, portrait-cover, postcard-cover, portrait-result, postcard-result image paths");
        }
        Path media = Path.of("src/main/resources/demo-media");
        Path examples = Path.of("../frontend/public/demo-examples");
        Files.createDirectories(media);
        Files.createDirectories(examples);
        String[] names = {"portrait-source", "travel-coast-source", "travel-town-source",
                "portrait-cover", "postcard-cover", "portrait-result", "postcard-result"};
        for (int i = 0; i < names.length; i++) {
            Path output = (i < 3 ? examples : media).resolve(names[i] + ".jpg");
            var source = ImageIO.read(Path.of(args[i]).toFile());
            if (source == null) throw new IllegalArgumentException("Unreadable image: " + names[i]);
            var image = new BufferedImage(i < 3 ? 1200 : 640, i < 3 ? 800 : 400, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, image.getWidth(), image.getHeight(), null);
            graphics.dispose();
            if (!ImageIO.write(image, "jpeg", output.toFile())) throw new IllegalStateException("JPEG writer unavailable");
            if (i >= 5) Files.copy(output, examples.resolve(output.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        // mock-result.jpg is retained unchanged for already-persisted demo:mock-result tasks.
    }
}
