package com.aurora.client.screen.setting;

import com.aurora.client.ui.component.SegmentedControl;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A row of independent on/off segments under one label — the Text section's
 * Modifiers (Italic, Underline; 2026-11-12, after OneConfig's B/I/U row).
 * Same geometry, glass and semantics as {@link SegmentedSetting} through the
 * shared {@link SegmentedControl} in its toggle mode: every lit segment is
 * selected, and a click flips only the one it lands on.
 */
public class ToggleGroupSetting extends FeatureSetting {

    /** One segment: its label and the switch it reads and writes. */
    public record Toggle(String label, BooleanSupplier getter, Consumer<Boolean> setter) {}

    private static final int CONTROL_H = 40;
    private static final int TRACK_H = 20;
    private static final int TRACK_PAD_X = 12;

    private final SegmentedControl control;
    private boolean glass = true;
    private int lastWidth = 240;
    private int lastTrackW;

    public ToggleGroupSetting(String label, List<Toggle> toggles) {
        super(label);
        String[] labels = new String[toggles.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = toggles.get(i).label();
        this.control = SegmentedControl.toggles(labels,
                i -> toggles.get(i).getter().getAsBoolean(),
                i -> {
                    Toggle t = toggles.get(i);
                    t.setter().accept(!t.getter().getAsBoolean());
                });
    }

    @Override public ToggleGroupSetting description(String desc) { super.description(desc); return this; }
    @Override public ToggleGroupSetting description(Supplier<String> desc) { super.description(desc); return this; }

    @Override
    public java.util.List<com.aurora.client.ui.interaction.SemanticActionControl> interactionControls() {
        return control.interactionControls(label);
    }

    @Override public int baseHeight() { return CONTROL_H; }
    @Override public int height() { return CONTROL_H + descriptionHeight(lastWidth); }

    @Override
    public void renderGlassPass(GuiGraphicsExtractor ctx, int x, int y, int width) {
        if (!com.aurora.client.ui.component.GlassSurface.passOpen()) return;
        control.glassEnabled(glass);
        control.layout(x + TRACK_PAD_X, y + 16, width - TRACK_PAD_X * 2, TRACK_H);
        control.renderGlassPass(ctx, x + TRACK_PAD_X, y + 16, width - TRACK_PAD_X * 2, TRACK_H);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {
        renderShapes(ctx, x, y, width, mouseX, mouseY);
        renderOverlay(ctx, x, y, width, mouseX, mouseY);
    }

    @Override
    public void renderShapes(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        lastTrackW = width - TRACK_PAD_X * 2;
        control.glassEnabled(glass);
        control.layout(x + TRACK_PAD_X, y + 16, lastTrackW, TRACK_H);
        control.renderShapes(ctx, x + TRACK_PAD_X, y + 16, lastTrackW, TRACK_H);
    }

    @Override
    public void renderOverlay(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        boolean disabled = isDisabled();
        renderLabelWithTooltip(ctx, com.aurora.client.ui.util.AuroraFontRenderer.ellipsize(tr, label, Math.max(0, width - 24), 0),
                x + TRACK_PAD_X, y + 6, AuroraTheme.IOS_LABEL, mouseX, mouseY, disabled);
        lastTrackW = width - TRACK_PAD_X * 2;
        control.disabled(disabled);
        control.layout(x + TRACK_PAD_X, y + 16, lastTrackW, TRACK_H);
        control.renderOverlay(ctx, x + TRACK_PAD_X, y + 16, lastTrackW, TRACK_H, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (isDisabled() || button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
        if (lastTrackW <= 0) return false;
        return control.mouseClicked(mouseX, mouseY, button);
    }
}
