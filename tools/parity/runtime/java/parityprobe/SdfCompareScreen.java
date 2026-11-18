package parityprobe;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.HudTextPainter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;

import static java.lang.Math.max;

final class SdfCompareScreen extends Screen {
    private static final float[] SCALES = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f};
    private static final List<Sample> SAMPLES = List.of(
            new Sample("9px regular", "Mixed Case: {!?;:,} 50% #42", "", false),
            new Sample("9px spacing", "The quick brown fox jumps over lazy dogs.", "", false),
            new Sample("8px compact", "Text Renderer Aurora UI Only", "_ui", false),
            new Sample("11px title", "Aurora Client", "_title", false),
            new Sample("HUD FULL", "HUD Full Outline 0123456789", "", true));

    private final float scale;
    private final String scaleLabel;
    private boolean previousSdf;

    SdfCompareScreen(String requestedScale) {
        super(Component.literal("Raster / SDF comparison"));
        this.scale = parseScale(requestedScale);
        this.scaleLabel = String.format(Locale.ROOT, "%sx", scale);
    }

    @Override public void added() {
        previousSdf = AuroraConfig.get().textSdf;
        AuroraConfig.get().textSdf = true;
    }

    @Override public void removed() { AuroraConfig.get().textSdf = previousSdf; }

    private static float parseScale(String value) {
        float parsed = Float.parseFloat(value);
        for (float allowed : SCALES) if (parsed == allowed) return parsed;
        throw new IllegalArgumentException("Scale must be one of 0.5, 0.75, 1, 1.25, 1.5, 2, 3");
    }

    private static Style style(String id) {
        return Style.EMPTY.withFont(new FontDescription.Resource(Identifier.parse(id)));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xff20242b);
        g.text(font, "Inter Medium raster vs SDF | pose scale " + scaleLabel,
                12, 10, 0xffffffff, false);
        int left = 16, right = width / 2 + 8;
        g.text(font, "Raster Inter Medium", left, 26, 0xffa0a8b8, false);
        g.text(font, "SDF Inter Medium", right, 26, 0xffa0a8b8, false);

        int column = max(1, (int) ((width / 2f - 26) / scale));
        float step = max(13, 12 * scale);
        float y = 44;
        for (Sample sample : SAMPLES) {
            Style raster = style("aurora:inter_medium" + sample.suffix);
            Style sdf = style("aurora:inter_medium" + sample.suffix + "_sdf");
            int difference = measure(sample.text, sdf) - measure(sample.text, raster);
            if (y + 11 + step > height - 17) break;
            g.text(font, sample.label + " | width delta " + difference, 12, Math.round(y), 0xff7f8999, false);
            y += 11;
            for (String line : wrap(sample.text, raster, sdf, column)) {
                if (y + step > height - 17) break;
                draw(g, Component.literal(line).withStyle(raster), sample.hud, left, y);
                draw(g, Component.literal(line).withStyle(sdf), sample.hud, right, y);
                y += step;
            }
            y += 6;
        }
        g.text(font, "Explicit font IDs; SDF enabled temporarily. Widen or lower GUI scale for all rows.",
                12, height - 14, 0xff7f8999, false);
    }

    private List<String> wrap(String text, Style raster, Style sdf, int limit) {
        var lines = new java.util.ArrayList<String>();
        String line = "";
        for (String word : text.split(" ")) {
            String combined = line.isEmpty() ? word : line + " " + word;
            if (max(measure(combined, raster), measure(combined, sdf)) <= limit) {
                line = combined;
            } else {
                if (!line.isEmpty()) lines.add(line);
                line = max(measure(word, raster), measure(word, sdf)) <= limit ? word : "…";
                if (max(measure(line, raster), measure(line, sdf)) > limit) line = "";
            }
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }

    private int measure(String text, Style style) { return font.width(Component.literal(text).withStyle(style)); }

    private void draw(GuiGraphicsExtractor g, Component text, boolean hud, int x, float y) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        if (hud) {
            AuroraConfig.TextShadow previous = AuroraConfig.get().textHudShadow;
            try {
                AuroraConfig.get().textHudShadow = AuroraConfig.TextShadow.FULL;
                HudTextPainter.draw(g, font, text.getVisualOrderText(), 0, 0, 0xffffffff);
            } finally {
                AuroraConfig.get().textHudShadow = previous;
            }
        } else {
            g.text(font, text, 0, 0, 0xffffffff, false);
        }
        g.pose().popMatrix();
    }

    private record Sample(String label, String text, String suffix, boolean hud) {}
}
