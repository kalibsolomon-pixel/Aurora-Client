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

/** Flag-independent, two-column raster/SDF specimen. One pose scale per invocation. */
final class SdfCompareScreen extends Screen {
    private static final float[] SCALES = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f};
    private static final Style RASTER = style("aurora:inter_medium");
    private static final Style SDF = style("aurora:inter_medium_sdf");
    private static final Style RASTER_UI = style("aurora:inter_medium_ui");
    private static final Style SDF_UI = style("aurora:inter_medium_ui_sdf");
    private static final List<Sample> SAMPLES = List.of(
            new Sample("family", "Aurora Client — Inter Medium", false, false),
            new Sample("digits", "0123456789 FPS 144", false, false),
            new Sample("case + punctuation", "Mixed Case: {!?;:,} 50% #42", false, false),
            new Sample("vanilla sentence", "The quick brown fox jumps over lazy dogs.", false, false),
            new Sample("8px base window label", "Text Renderer   Aurora UI Only", true, false),
            new Sample("HudTextPainter FULL", "HUD Full Outline 0123456789", false, true));

    private final float scale;

    SdfCompareScreen(String requestedScale) {
        super(Component.literal("Raster / SDF comparison"));
        this.scale = parseScale(requestedScale);
    }

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
        String scaleLabel = String.format(Locale.ROOT, "%sx", scale);
        g.text(font, "Inter Medium raster vs SDF | pose scale " + scaleLabel,
                12, 10, 0xffffffff, false);
        int left = 16, right = width / 2 + 8;
        g.text(font, "Raster Inter Medium", left, 26, 0xffa0a8b8, false);
        g.text(font, "SDF Inter Medium", right, 26, 0xffa0a8b8, false);

        float y = 44;
        for (Sample sample : SAMPLES) {
            g.text(font, scaleLabel + " | " + sample.label, 12, Math.round(y), 0xff7f8999, false);
            float textY = y + 11;
            draw(g, sample, left, textY, false);
            draw(g, sample, right, textY, true);
            y += Math.max(29, 11 + font.lineHeight * scale + 11);
        }
        g.text(font, "Probe-only explicit font IDs; aurora.text.sdf is not required for this screen",
                12, height - 14, 0xff7f8999, false);
    }

    private void draw(GuiGraphicsExtractor g, Sample sample, int x, float y, boolean sdf) {
        Style active = sample.compact ? (sdf ? SDF_UI : RASTER_UI) : (sdf ? SDF : RASTER);
        Component text = Component.literal(sample.text).withStyle(active);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        if (sample.hud) {
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

    private record Sample(String label, String text, boolean compact, boolean hud) {}
}
