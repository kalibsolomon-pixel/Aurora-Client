package com.aurora.client.screen.setting;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.component.SegmentedControl;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Segmented control for a small enum — an iOS-style split track where the
 * selected segment is filled with the accent. Stage 3 uses it for the
 * theme's Mode (Dark/Light) and Corner Style (Round/Slightly Round/Square)
 * rows, matching COSMIC's Appearance settings.
 *
 * <p>Reads the same projected token cache as every other control
 * ({@code AuroraTheme.*}); the track's corner radius follows
 * {@link AuroraTheme#RADIUS_SMALL}, so the roundness setting is reflected
 * live in the control itself.
 *
 * <p>Clicking a segment commits exactly once per genuine change: clicking
 * the already-selected segment is a no-op (no reload, no save).
 */
public class SegmentedSetting<E extends Enum<E>> extends FeatureSetting {

    private static final int CONTROL_H = 40;
    private static final int TRACK_H = 20;
    /** Track indent — label above, full-width control below (slider row layout). */
    private static final int TRACK_PAD_X = 12;
    private static final int SEG_GAP = 2;

    private final Supplier<E> getter;
    private final Consumer<E> setter;
    private final E[] values;
    private final Function<E, String> labelFn;

    private int lastWidth = 240;
    private int lastTrackX, lastTrackY, lastTrackW;

    /** Shared themed segmented control — owns the track + segment drawing/hit-testing. */
    private final SegmentedControl control;

    public SegmentedSetting(String label, Class<E> enumClass,
                            Supplier<E> getter, Consumer<E> setter) {
        this(label, enumClass, getter, setter, null);
    }

    public SegmentedSetting(String label, Class<E> enumClass,
                            Supplier<E> getter, Consumer<E> setter,
                            Function<E, String> labelFn) {
        super(label);
        this.getter = getter;
        this.setter = setter;
        this.values = enumClass.getEnumConstants();
        this.labelFn = labelFn != null ? labelFn : SegmentedSetting::prettify;
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = this.labelFn.apply(values[i]);
        this.control = new SegmentedControl(labels,
                () -> indexOf(getter.get()),
                i -> { setter.accept(values[i]); AuroraConfig.save(); });
    }

    private int indexOf(E v) {
        for (int i = 0; i < values.length; i++) if (values[i] == v) return i;
        return 0;
    }

    /** "SLIGHTLY_ROUND" → "Slightly Round". */
    private static String prettify(Enum<?> e) {
        String n = e.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    @Override public SegmentedSetting<E> description(String desc) { super.description(desc); return this; }
    @Override public SegmentedSetting<E> description(Supplier<String> desc) { super.description(desc); return this; }

    /**
     * Glass: every segment renders as raised glass by default — neutral
     * tint unselected, accent-stained tint selected. Forwarded to the
     * shared control. Pass {@code false} to force the flat track.
     */
    public SegmentedSetting<E> glassSegments(boolean g) {
        this.glass = g;
        control.glassEnabled(g);
        return this;
    }

    /** Whether segments render as glass; glass is the mod-wide default look. */
    private boolean glass = true;

    @Override public int baseHeight() { return CONTROL_H; }
    @Override public int height() { return CONTROL_H + descriptionHeight(lastWidth); }

    /**
     * Pre-dim surface (§6 convention 6): drives the shared control's glass
     * pass at the track rect derived from the row geometry — the same rect
     * renderShapes/renderOverlay compute, so a scroll frame's pass lands
     * exactly where the labels will.
     */
    @Override
    public void renderGlassPass(GuiGraphics ctx, int x, int y, int width) {
        if (!com.aurora.client.ui.component.GlassSurface.passOpen()) return; // legacy frame order
        control.glassEnabled(glass);
        control.layout(x + TRACK_PAD_X, y + 16, width - TRACK_PAD_X * 2, TRACK_H);
        control.renderGlassPass(ctx, x + TRACK_PAD_X, y + 16, width - TRACK_PAD_X * 2, TRACK_H);
    }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        renderShapes(ctx, x, y, width, mouseX, mouseY);
        renderOverlay(ctx, x, y, width, mouseX, mouseY);
    }

    @Override
    public void renderShapes(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        lastTrackX = x + TRACK_PAD_X;
        lastTrackW = width - TRACK_PAD_X * 2;
        lastTrackY = y + 16;
        control.glassEnabled(glass);
        control.layout(lastTrackX, lastTrackY, lastTrackW, TRACK_H);
        control.renderShapes(ctx, lastTrackX, lastTrackY, lastTrackW, TRACK_H);
    }

    @Override
    public void renderOverlay(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        boolean disabled = isDisabled();
        renderLabelWithTooltip(ctx, label, x + TRACK_PAD_X, y + 6, AuroraTheme.IOS_LABEL, mouseX, mouseY, disabled);

        lastTrackX = x + TRACK_PAD_X;
        lastTrackW = width - TRACK_PAD_X * 2;
        lastTrackY = y + 16;
        control.disabled(disabled);
        control.layout(lastTrackX, lastTrackY, lastTrackW, TRACK_H);
        control.renderOverlay(ctx, lastTrackX, lastTrackY, lastTrackW, TRACK_H, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (isDisabled() || button != 0) return false;
        if (lastTrackW <= 0) return false; // not laid out yet
        return control.mouseClicked(mouseX, mouseY, button);
    }
}
