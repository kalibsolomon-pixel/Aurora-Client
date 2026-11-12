package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.AuroraBrand;
import com.aurora.client.ui.util.CubicBezier;
import com.aurora.client.ui.util.MaterialIconRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.TimedTransition;
import com.aurora.client.util.AuroraAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * A square, icon-only action (the title screen's top bar). Vanilla keeps
 * pointer, keyboard, focus, click sound and narration; the name doubles as
 * the tooltip. The face is the outlined card of {@link Button.Theming#OUTLINED}
 * at a smaller size; hover and focus are carried by the icon colour and the
 * focus ring, never by the tint (glass rule 9).
 */
public class IconButton extends AbstractButton {
    private static final float ICON_EM = 12f;
    private final Supplier<String> glyph;
    private final boolean brand;
    private final Runnable onPress;
    private final TimedTransition hover = new TimedTransition(120, CubicBezier.EASE);

    private IconButton(int x, int y, int size, Component name, Supplier<String> glyph, boolean brand, Runnable onPress) {
        super(x, y, size, size, name);
        this.glyph = glyph;
        this.brand = brand;
        this.onPress = onPress;
        setTooltip(Tooltip.create(name));
    }

    /** A Material Symbols glyph; the supplier lets a toggle swap its icon (Fullscreen). */
    public static IconButton glyph(int x, int y, int size, Component name, Supplier<String> glyph, Runnable onPress) {
        return new IconButton(x, y, size, name, glyph, false, onPress);
    }

    /** The Aurora mark, untinted. */
    public static IconButton brand(int x, int y, int size, Component name, Runnable onPress) {
        return new IconButton(x, y, size, name, () -> "", true, onPress);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (onPress != null) onPress.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        float t = (float) hover.update(this.active && isHovered() ? 1 : 0, System.nanoTime());
        float x = getX(), y = getY(), w = getWidth(), h = getHeight();
        float r = ThemeManager.current().roundness().radiusSmall();
        int windowAlpha = ThemeManager.color(ThemeToken.WINDOW_FILL) >>> 24;
        int fill = AuroraAnim.lerpArgb(ThemeManager.color(ThemeToken.MATERIAL_RAISED), ThemeManager.color(ThemeToken.MATERIAL_HOVER), t);
        RenderUtil.drawRoundedRectAA(g, x, y, w, h, r, ThemeManager.withAlpha(fill, windowAlpha));
        RenderUtil.drawRoundedOutlineAA(g, x, y, w, h, r, 1, MaterialSurface.color(ThemeToken.BORDER_HOVER));
        // The ring is for keyboard focus; a mouse click focuses too, and must not leave a ring behind.
        if (isFocused() && this.active && Minecraft.getInstance().getLastInputType().isKeyboard()) RenderUtil.drawRoundedOutlineAA(g, x - 2, y - 2, w + 4, h + 4,
                MaterialSurface.outsetRadius(r, 2), 1, MaterialSurface.color(ThemeToken.ACCENT));
        if (brand) {
            int size = Math.round(w) - 6;
            AuroraBrand.draw(g, Math.round(x) + 3, Math.round(y) + 3, size);
            return;
        }
        Font font = Minecraft.getInstance().font;
        int color = AuroraAnim.lerpArgb(ThemeManager.color(ThemeToken.MATERIAL_TEXT_SECONDARY),
                ThemeManager.color(ThemeToken.MATERIAL_TEXT), t);
        if (font != null) MaterialIconRenderer.drawIcon(g, font, glyph.get(), x + w / 2f, y + h / 2f, ICON_EM, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        this.defaultButtonNarrationText(builder);
    }
}
