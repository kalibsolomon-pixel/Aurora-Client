package com.aurora.client.screen.setting;

import com.aurora.client.ui.component.ToggleSwitch;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * iOS UISwitch — pill-shaped toggle with a circular thumb that slides on
 * a spring curve between off and on.
 *
 * <p><b>Visual spec</b> (matching Apple's UISwitch dark mode):
 * <ul>
 *   <li>Track: 36×20 px capsule. Off: {@code systemFill} translucent
 *       gray. On: {@code systemBlue}. Color crossfades on the same curve
 *       as the thumb position.</li>
 *   <li>Thumb: circle inset 2 px from each side of the track. Pure white.
 *       Subtle 2-pass shadow underneath fakes a real drop shadow.</li>
 *   <li>Slide: spring-overshoot easing, 280 ms — the thumb peaks slightly
 *       past its target and settles back, matching the iOS feel.</li>
 *   <li>Hover: a faint white halo behind the thumb, fading in over
 *       140 ms ease-out.</li>
 * </ul>
 */
public class BooleanSetting extends FeatureSetting {
    private static final int MIN_ROW_H = 28;
    private static final int TRACK_W = 36;
    private static final int TRACK_H = 20;
    private static final int LABEL_PAD = 12;
    private static final int RIGHT_RESERVE = TRACK_W + 28;

    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private final Component labelText;

    private int lastWidth = 240;

    private int cachedLabelArea = -1;
    private List<FormattedCharSequence> cachedLines;
    private int cachedBaseHeight;

    /** Shared themed switch — owns the pill/thumb drawing + slide animation. */
    private final ToggleSwitch toggle;

    public BooleanSetting(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        super(label);
        this.getter = getter;
        this.setter = setter;
        this.labelText = Component.literal(label);
        this.toggle = new ToggleSwitch(getter, setter);
    }

    @Override public BooleanSetting description(String desc) { super.description(desc); return this; }
    @Override public BooleanSetting description(Supplier<String> desc) { super.description(desc); return this; }

    /**
     * §4 live-value subtitle — the mechanism lives on the
     * {@link FeatureSetting} base; this override only restores the
     * covariant return type and re-invalidates this widget's label
     * layout cache (the subtitle line changes the row's height).
     */
    @Override public BooleanSetting valueLine(Supplier<String> supplier) {
        super.valueLine(supplier);
        cachedLabelArea = -1; // force a height re-layout
        return this;
    }

    private void ensureLayout(Font tr) {
        int labelArea = Math.max(40, lastWidth - LABEL_PAD - RIGHT_RESERVE);
        if (labelArea == cachedLabelArea && cachedLines != null) return;
        cachedLines = tr.split(labelText, labelArea);
        int blockH = cachedLines.size() * (tr.lineHeight + 1) + valueLineHeight(tr);
        cachedBaseHeight = cachedLines.isEmpty()
                ? MIN_ROW_H
                : Math.max(MIN_ROW_H, blockH + 12);
        cachedLabelArea = labelArea;
    }

    @Override
    public int baseHeight() {
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return MIN_ROW_H;
        ensureLayout(tr);
        return cachedBaseHeight;
    }

    @Override
    public int height() { return baseHeight() + descriptionHeight(lastWidth); }

    @Override
    public int shapeFingerprint() {
        return toggle.shapeFingerprint();
    }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        renderShapes(ctx, x, y, width, mouseX, mouseY);
        renderOverlay(ctx, x, y, width, mouseX, mouseY);
    }

    @Override
    public void renderShapes(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;
        ensureLayout(tr);

        float switchX = x + width - 28 - 14;
        float switchY = y + (cachedBaseHeight - 15) / 2.0f;
        toggle.layout(switchX, switchY, 28, 15);
        toggle.renderShapes(ctx, switchX, switchY, 28, 15);
    }

    @Override
    public void renderOverlay(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;
        ensureLayout(tr);

        int controlH = cachedBaseHeight;
        int textBlockH = cachedLines.size() * (tr.lineHeight + 1) + valueLineHeight(tr);
        int textY = y + (controlH - textBlockH) / 2;
        String key = tooltipKey();
        boolean disabled = isDisabled();
        for (int li = 0; li < cachedLines.size(); li++) {
            var line = cachedLines.get(li);
            ctx.drawString(tr, line, x + LABEL_PAD, textY, AuroraTheme.IOS_LABEL, false);
            // Each wrapped line of the label text is a dwell region for
            // the description tooltip — hover the label itself, no badge.
            // The description supplier is only evaluated while hovered.
            trackLabelHover(key, this::currentDescription, x + LABEL_PAD, textY,
                    tr.width(line), tr.lineHeight, mouseX, mouseY, disabled);
            textY += tr.lineHeight + 1;
        }
        // §4 live-value subtitle (base mechanism): one small secondary
        // line under the label block.
        drawValueLine(ctx, x + LABEL_PAD, textY + 2, disabled);

        float switchX = x + width - 28 - 14;
        float switchY = y + (controlH - 15) / 2.0f;
        toggle.layout(switchX, switchY, 28, 15);
        toggle.renderOverlay(ctx, switchX, switchY, 28, 15, mouseX, mouseY);

        renderDescription(ctx, x, y + controlH, width);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (isDisabled()) return false;
        if (button != 0) return false;
        int controlH = baseHeight();
        if (mouseY < rowY || mouseY > rowY + controlH) return false;
        if (mouseX < rowX || mouseX > rowX + rowWidth) return false;
        toggle.toggle();
        com.aurora.client.config.AuroraConfig.save();
        return true;
    }
}
