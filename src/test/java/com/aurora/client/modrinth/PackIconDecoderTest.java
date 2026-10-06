package com.aurora.client.modrinth;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class PackIconDecoderTest {
    @Test void lossyAndLosslessWebpNormalizeToPngWithDimensionsAndAlpha() throws Exception {
        for (String name : new String[]{"lossy", "lossless"}) {
            try (var input = getClass().getResourceAsStream("/pack-icons/" + name + ".webp")) {
                assertNotNull(input);
                byte[] png = PackIconDecoder.png(input.readAllBytes());
                assertEquals(0x89, png[0] & 255);
                var image = ImageIO.read(new ByteArrayInputStream(png));
                assertEquals(24, image.getWidth());
                assertEquals(12, image.getHeight());
                assertEquals(255, image.getRGB(2, 4) >>> 24);
                assertEquals(80, image.getRGB(20, 4) >>> 24);
            }
        }
    }

    @Test void pngAndJpegIconsStillLoadAndMalformedOrOversizedIconsAreRejected() throws Exception {
        var image = new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB);
        for (String format : new String[]{"PNG", "JPEG"}) {
            var bytes = new ByteArrayOutputStream();
            ImageIO.write(image, format, bytes);
            var normalized = ImageIO.read(new ByteArrayInputStream(PackIconDecoder.png(bytes.toByteArray())));
            assertEquals(16, normalized.getWidth());
            assertEquals(8, normalized.getHeight());
        }
        assertThrows(IOException.class, () -> PackIconDecoder.png(new byte[]{1, 2, 3}));
        assertThrows(IOException.class, () -> PackIconDecoder.png(new byte[PackIconDecoder.MAX_BYTES + 1]));
        var oversized = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1025, 1, BufferedImage.TYPE_INT_ARGB), "PNG", oversized);
        assertThrows(IOException.class, () -> PackIconDecoder.png(oversized.toByteArray()));
    }
}
