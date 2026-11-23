package dev.bum.ticket_service.service.captcha;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;

@Component
final class CaptchaImageGenerator {

    private static final int WIDTH = 210;
    private static final int HEIGHT = 72;
    private static final int ANSWER_LENGTH = 6;
    private static final String ALPHABET = "ACDEFGHJKMNPQRTUVWXY2346789";
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.BOLD, 36);

    private final SecureRandom random = new SecureRandom();

    PreparedCaptcha generate() {
        char[] characters = new char[ANSWER_LENGTH];
        for (int i = 0; i < characters.length; i++) {
            characters[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawBackground(graphics);
            drawAnswer(graphics, characters);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream png = new MemoryCacheImageOutputStream(output)) {
            if (!ImageIO.write(image, "png", png)) {
                throw new IllegalStateException("PNG image writer is unavailable");
            }
            png.flush();
            return new PreparedCaptcha(new String(characters), output.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode CAPTCHA image", e);
        }
    }

    private void drawBackground(Graphics2D graphics) {
        graphics.setColor(new Color(244, 247, 250));
        graphics.fillRect(0, 0, WIDTH, HEIGHT);

        graphics.setColor(new Color(205, 215, 227));
        for (int i = 0; i < 80; i++) {
            int size = random.nextInt(1, 3);
            graphics.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), size, size);
        }
        for (int i = 0; i < 3; i++) {
            graphics.draw(new QuadCurve2D.Double(0, random.nextInt(HEIGHT),
                    WIDTH / 2.0, random.nextInt(HEIGHT), WIDTH, random.nextInt(HEIGHT)));
        }
    }

    private void drawAnswer(Graphics2D graphics, char[] characters) {
        graphics.setFont(FONT);
        graphics.setColor(new Color(28, 44, 64));
        FontMetrics metrics = graphics.getFontMetrics();
        AffineTransform originalTransform = graphics.getTransform();

        for (int i = 0; i < characters.length; i++) {
            int center = 30 + i * 30 + random.nextInt(-2, 3);
            int baseline = 48 + random.nextInt(-4, 5);
            graphics.rotate(random.nextDouble(-0.18, 0.18), center, baseline - 12);
            graphics.drawChars(characters, i, 1, center - metrics.charWidth(characters[i]) / 2, baseline);
            graphics.setTransform(originalTransform);
        }
    }

    record PreparedCaptcha(String answer, byte[] png) {
    }
}
