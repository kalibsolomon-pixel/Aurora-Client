package com.aurora.client.modrinth;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Launcher-style bounded normalization, with WebP support for Modrinth's thumbnails. */
public final class PackIconDecoder {
    public static final int MAX_BYTES = 512 * 1024;
    private PackIconDecoder() {}

    public static byte[] png(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES)
            throw new IOException("Invalid icon size");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader;
            if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
                // Explicit provider avoids ImageIO discovery issues with Fabric's class loader.
                reader = new WebPImageReaderSpi().createReaderInstance();
            } else {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IOException("Unsupported icon format");
                reader = readers.next();
            }
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 1024 || height > 1024)
                    throw new IOException("Icon dimensions exceed limit");
                var image = reader.read(0);
                try {
                    var output = new ByteArrayOutputStream();
                    if (!ImageIO.write(image, "PNG", output)) throw new IOException("PNG writer unavailable");
                    return output.toByteArray();
                } finally { image.flush(); }
            } finally { reader.dispose(); }
        }
    }
}
