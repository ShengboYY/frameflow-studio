import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Original geometric demo artwork. Run from backend/ with Java 17 source-file mode. */
public class GenerateDemoMedia {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of("src/main/resources/demo-media");
        Files.createDirectories(directory);
        for (String name : new String[]{"portrait-cover", "postcard-cover", "mock-result"}) {
            var image = new BufferedImage(640, 400, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (name.equals("portrait-cover")) portrait(graphics);
            else landscape(graphics, name.equals("mock-result"));
            graphics.dispose();
            ImageIO.write(image, "jpeg", directory.resolve(name + ".jpg").toFile());
        }
    }

    private static void portrait(Graphics2D graphics) {
        graphics.setPaint(new GradientPaint(0, 0, new Color(238, 224, 247), 640, 400, new Color(185, 200, 241)));
        graphics.fillRect(0, 0, 640, 400);
        graphics.setColor(new Color(250, 247, 241));
        graphics.fillRoundRect(190, 34, 260, 332, 28, 28);
        graphics.setColor(new Color(219, 190, 165));
        graphics.fillOval(275, 98, 90, 105);
        graphics.setColor(new Color(57, 62, 94));
        graphics.fillArc(270, 85, 100, 100, 0, 180);
        graphics.fillRoundRect(231, 211, 178, 99, 75, 75);
        graphics.setColor(new Color(176, 133, 197));
        graphics.fillOval(96, 81, 48, 48);
        graphics.setStroke(new BasicStroke(6));
        graphics.drawOval(489, 257, 60, 60);
        graphics.setColor(new Color(210, 184, 146));
        graphics.fillRoundRect(258, 328, 124, 7, 7, 7);
    }

    private static void landscape(Graphics2D graphics, boolean result) {
        graphics.setColor(new Color(244, 239, 225));
        graphics.fillRect(0, 0, 640, 400);
        graphics.setPaint(new GradientPaint(0, 35, new Color(181, 218, 229), 0, 300, new Color(241, 223, 188)));
        graphics.fillRect(30, 30, 580, 305);
        graphics.setColor(new Color(244, 184, 110));
        graphics.fillOval(result ? 415 : 445, 65, 72, 72);
        graphics.setColor(new Color(99, 147, 150));
        graphics.fillPolygon(new int[]{30, 210, 380, 520, 610}, new int[]{260, 96, 240, 131, 260}, 5);
        graphics.setColor(new Color(55, 106, 112));
        graphics.fillPolygon(new int[]{30, 140, 320, 480, 610, 610, 30},
                new int[]{300, 218, 292, 198, 260, 335, 335}, 7);
        graphics.setColor(new Color(213, 231, 223));
        graphics.fillPolygon(new int[]{270, 340, 460, 385}, new int[]{335, 266, 335, 335}, 4);
        graphics.setColor(new Color(89, 117, 117));
        graphics.fillRoundRect(32, 357, 160, 8, 8, 8);
        graphics.fillRoundRect(32, 375, 104, 5, 5, 5);
        graphics.setStroke(new BasicStroke(2));
        graphics.drawRect(568, 349, 39, 34);
        if (result) {
            graphics.setColor(new Color(244, 239, 225));
            graphics.fillOval(62, 54, 82, 82);
            graphics.setColor(new Color(55, 106, 112));
            graphics.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.drawPolyline(new int[]{84, 99, 124}, new int[]{94, 108, 79}, 3);
        }
    }
}
