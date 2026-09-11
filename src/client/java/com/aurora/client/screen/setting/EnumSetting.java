package com.aurora.client.screen.setting;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.Widget;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.util.MaterialIconRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.HoverAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class EnumSetting<E extends Enum<E>> extends FeatureSetting {
    private static final int CONTROL_H = 28;
    private static final int BTN_W = 100; // slightly wider to accommodate arrow indicators
    private static final int BTN_H = 18;
    private static final int OPT_H = 16;  // height of individual dropdown option

    private static final net.minecraft.network.chat.Style SYMBOL_STYLE = net.minecraft.network.chat.Style.EMPTY
            .withFont(new net.minecraft.network.chat.FontDescription.Resource(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("aurora", "material_symbols")
            ));

    private final Supplier<E> getter;
    private final Consumer<E> setter;
    private final E[] values;
    private Function<E, String> labelFn = null;

    /**
     * Glass rollout: the closed dropdown button renders as NEUTRAL raised
     * glass by default — the button family's secondary look, the same
     * contract as {@code Button#glassBackground} (the glass rim replaces
     * the outline; the tint is {@code WINDOW_FILL}, whose alpha carries the
     * theme's Background Opacity). Pass {@code false} to force the flat
     * look. The expanded option popup is also raised
     * glass (a floating panel above other content) with the same neutral
     * tint — see the expanded block in {@link #render}.
     */
    private boolean glassButton = true;

    /**
     * Glass-pass bookkeeping (the {@code Button} scheme, §6 convention 6):
     * the frame in which {@link #renderGlassPass} painted this row's trigger
     * pill pre-dim, and whether it drew. In that frame {@link #render}
     * paints content only (or the flat pill if the glass declined);
     * otherwise it paints the surface in place — the legacy order screens
     * not on the structural pass still use, pixel-identical. The expanded
     * popup is deliberately NOT part of the split: it floats above the
     * dimmed screen by design (§9's named above-the-dim case) and keeps
     * painting in place in {@link #render} on every screen.
     */
    private long glassPassFrame = -1L;
    private boolean passDrewButton = false;

    private int lastBtnX, lastBtnY;
    private int lastWidth = 240;

    private String cachedName;
    private int cachedNameW;

    private net.minecraft.util.FormattedCharSequence cachedUpArrow;
    private net.minecraft.util.FormattedCharSequence cachedDownArrow;
    private int cachedUpArrowWidth = -1;
    private int cachedDownArrowWidth = -1;

    private boolean expanded = false;
    private int scrollOffset = 0; // scroll offset for listing max 5 elements

    private final HoverAnim hoverAnim = new HoverAnim(140L);

    public EnumSetting(String label, Class<E> enumClass, Supplier<E> getter, Consumer<E> setter) {
        super(label);
        this.getter = getter;
        this.setter = setter;
        this.values = enumClass.getEnumConstants();
    }

    @Override public EnumSetting<E> description(String desc) { super.description(desc); return this; }
    @Override public EnumSetting<E> description(Supplier<String> desc) { super.description(desc); return this; }

    public EnumSetting<E> valueDescriptions(Function<E, String> fn) {
        super.description(() -> {
            E v = getter.get();
            return v == null ? null : fn.apply(v);
        });
        return this;
    }

    /** Custom option label for the button + dropdown (default: raw enum name). */
    public EnumSetting<E> labels(Function<E, String> fn) {
        this.labelFn = fn;
        this.cachedName = null;
        return this;
    }

    /** Glass — see {@link #glassButton}. Default on; {@code false} forces flat. */
    public EnumSetting<E> glassButton(boolean g) {
        this.glassButton = g;
        return this;
    }

    private String displayName(E v) {
        return labelFn != null ? labelFn.apply(v) : v.name();
    }

    /** Truncate long values with an ellipsis (matches the mod's plainSubstrByWidth convention). */
    private static String fit(Font tr, String text, int maxW) {
        if (tr.width(text) <= maxW) return text;
        return tr.plainSubstrByWidth(text, maxW - tr.width("...")) + "...";
    }

    @Override public int baseHeight() {
        int h = CONTROL_H;
        if (expanded) {
            h += Math.min(5, values.length) * OPT_H + 6;
        }
        return h;
    }
    @Override public int height() { return baseHeight() + descriptionHeight(lastWidth); }

    /**
     * Pre-dim surface (the split's surface half — see the glass-pass
     * bookkeeping field). Only the trigger pill: the popup stays a
     * render-painted above-the-dim surface. Geometry derives from the row
     * params the screen passes (the same formula {@link #render} uses), so
     * a pass on a scrolling frame lands exactly where the content will.
     */
    @Override
    public void renderGlassPass(GuiGraphics ctx, int x, int y, int width) {
        if (!GlassSurface.passOpen()) return; // legacy frame order — render paints in place
        glassPassFrame = GlassSurface.frame();
        int btnX = x + width - BTN_W - 14;
        int btnY = y + (CONTROL_H - BTN_H) / 2;
        float glassR = Math.min(BTN_H / 2f, ThemeManager.current().roundness().radiusSmall());
        passDrewButton = glassButton && !isDisabled()
                && GlassSurface.control(ctx, btnX, btnY, BTN_W, BTN_H, glassR);
    }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        boolean disabled = isDisabled();
        renderLabelWithTooltip(ctx, label, x + 12, y + (CONTROL_H - tr.lineHeight) / 2,
                AuroraTheme.TEXT_PRIMARY, mouseX, mouseY, disabled);

        // Cache arrow glyphs first (their width reserves space for the label).
        if (cachedUpArrow == null) {
            Component upComp = Component.literal("\uE5C6").withStyle(SYMBOL_STYLE);
            cachedUpArrow = upComp.getVisualOrderText();
            cachedUpArrowWidth = tr.width(upComp);
            Component downComp = Component.literal("\uE5CF").withStyle(SYMBOL_STYLE);
            cachedDownArrow = downComp.getVisualOrderText();
            cachedDownArrowWidth = tr.width(downComp);
        }

        E current = getter.get();
        String raw = current == null ? "—" : displayName(current);
        int arrowReserve = (expanded ? cachedUpArrowWidth : cachedDownArrowWidth) + 8;
        String name = fit(tr, raw, BTN_W - arrowReserve);
        // Value equality, not reference equality — fit() builds a fresh
        // trimmed string every frame, so != never hit and the width was
        // re-measured per frame.
        if (!name.equals(cachedName)) {
            cachedNameW = tr.width(name);
            cachedName = name;
        }

        int btnX = x + width - BTN_W - 14;
        int btnY = y + (CONTROL_H - BTN_H) / 2;
        lastBtnX = btnX;
        lastBtnY = btnY;

        boolean hover = !disabled && Widget.inBounds(mouseX, mouseY, btnX, btnY, BTN_W, BTN_H);
        float hT = hoverAnim.update((hover || expanded) && !disabled);

        int fillTint   = disabled ? ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND), 0x22)
                                  : AuroraAnim.lerpArgb(AuroraTheme.PANEL_OFF, AuroraTheme.PANEL_OFF_HOVER, hT);
        int borderTint = disabled ? ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND), 0x33)
                                  : AuroraAnim.lerpArgb(AuroraTheme.BORDER_OFF, AuroraTheme.BORDER_OFF_HOVER, hT);
        int textColor  = disabled ? AuroraTheme.TEXT_DIM : AuroraAnim.lerpArgb(AuroraTheme.TEXT_SECONDARY, AuroraTheme.TEXT_PRIMARY, hT);

        // Glass: raised glass + neutral tint replace the fill + outline —
        // the glass rim replaces the border, no double outline. Disabled
        // rows keep the flat look. On decline (menu context, screenshot
        // suppression, failure) the complete flat button returns — the same
        // fallback contract every glass integration uses. Hover keeps the
        // text-color cue; the tint stays constant, exactly like every other
        // glass control. If the screen ran this row's glass pass this frame
        // the surface is already on screen UNDER the dim and only its
        // result matters here; otherwise (legacy frame order) it is painted
        // in place now — through the shared GlassSurface helper, whose
        // no-pass path makes the identical calls the raw idiom made.
        boolean glassOk;
        if (glassPassFrame == GlassSurface.frame()) {
            glassOk = passDrewButton;
        } else if (glassButton && !disabled) {
            float glassR = Math.min(BTN_H / 2f, ThemeManager.current().roundness().radiusSmall());
            glassOk = GlassSurface.control(ctx, btnX, btnY, BTN_W, BTN_H, glassR);
        } else {
            glassOk = false;
        }
        if (!glassOk) {
            RenderUtil.drawSquircle(ctx, btnX, btnY, BTN_W, BTN_H, AuroraTheme.RADIUS_SMALL, fillTint);
            RenderUtil.drawSquircleOutline(ctx, btnX, btnY, BTN_W, BTN_H, AuroraTheme.RADIUS_SMALL, 1.0f, borderTint);
        }

        // Draw the option name centered within the space left of the arrow.
        int textX = btnX + (BTN_W - arrowReserve - cachedNameW) / 2;
        int textY = btnY + (BTN_H - tr.lineHeight) / 2 + 1;
        ctx.drawString(tr, name, textX, textY, textColor, false);

        // Draw the Material Symbols arrow icon on the right. Rendered at the
        // font's natural em size, but through MaterialIconRenderer so the
        // glyph is rasterized at the device's true pixel grid (the shared
        // atlas is point-sampled and washes chevron strokes out at GUI
        // scales where 11 GUI units ≠ a texel multiple). Slot metrics are
        // unchanged, so layout and hit area are identical.
        int arrowW = expanded ? cachedUpArrowWidth : cachedDownArrowWidth;
        int arrowX = btnX + BTN_W - arrowW - 4; // 4px margin from right edge
        int arrowY = btnY + (BTN_H - tr.lineHeight) / 2 + 1; // centered vertically exactly with text
        MaterialIconRenderer.drawIcon(ctx, tr, expanded ? "\uE5C6" : "\uE5CF",
                arrowX + arrowW / 2f, arrowY + tr.lineHeight / 2f,
                MaterialIconRenderer.NATURAL_EM_GUI, textColor);

        if (expanded) {
            int dropdownY = btnY + BTN_H + 2;
            int visibleCount = Math.min(5, values.length);
            int dropdownH = visibleCount * OPT_H + 4;

            // Glass: the expanded option list is a floating panel above other
            // content — RAISED glass with the neutral WINDOW_FILL tint (never
            // stained: no individual list row is a selected/primary element;
            // the current value reads through its accent text color, hover
            // through the plain SURFACE_VARIANT wash — no per-row glass). The
            // popup is an ABOVE-THE-DIM surface by design (§9's named case:
            // it must float above the dimmed screen), so it paints here in
            // the content pass through GlassSurface.aboveDimControl — the
            // identical idiom, in place, exempt from the glass-pass ordering
            // — on migrated and legacy screens alike. On decline the flat
            // panel returns — the same fallback contract every glass
            // integration uses.
            float popR = Math.min(dropdownH / 2f, ThemeManager.current().roundness().radiusSmall());
            boolean popGlass = glassButton && GlassSurface.aboveDimControl(ctx, btnX, dropdownY, BTN_W, dropdownH, popR);
            if (popGlass) {
                RenderUtil.drawRoundedRectAA(ctx, btnX, dropdownY, BTN_W, dropdownH, popR,
                        ThemeManager.color(ThemeToken.WINDOW_FILL));
                BlurPanelRenderer.drawRimFinish(ctx, btnX, dropdownY, BTN_W, dropdownH, popR);
            } else {
                RenderUtil.drawSquircle(ctx, btnX, dropdownY, BTN_W, dropdownH, AuroraTheme.RADIUS_SMALL, ThemeManager.surfaceColor(ThemeToken.SURFACE));
                RenderUtil.drawSquircleOutline(ctx, btnX, dropdownY, BTN_W, dropdownH, AuroraTheme.RADIUS_SMALL, 1.0f, borderTint);
            }

            for (int vIdx = 0; vIdx < visibleCount; vIdx++) {
                int actualIdx = scrollOffset + vIdx;
                if (actualIdx >= 0 && actualIdx < values.length) {
                    E val = values[actualIdx];
                    String optName = fit(tr, displayName(val), BTN_W - 10);
                    int optY = dropdownY + 2 + vIdx * OPT_H;

                    boolean optHover = Widget.inBounds(mouseX, mouseY, btnX, optY, BTN_W, OPT_H);

                    if (optHover) {
                        ctx.fill(btnX + 2, optY, btnX + BTN_W - 2, optY + OPT_H, ThemeManager.surfaceColor(ThemeToken.SURFACE_VARIANT));
                    }

                    int optTextX = btnX + (BTN_W - tr.width(optName)) / 2;
                    int optTextY = optY + (OPT_H - tr.lineHeight) / 2 + 1;
                    int optColor = optHover ? AuroraTheme.TEXT_PRIMARY : AuroraTheme.TEXT_SECONDARY;

                    if (val == current) {
                        optColor = AuroraTheme.TEXT_ACCENT;
                    }

                    ctx.drawString(tr, optName, optTextX, optTextY, optColor, false);
                }
            }

            // Draw scrollbar if there are more than 5 options
            if (values.length > 5) {
                int scrollbarX = btnX + BTN_W - 4;
                int scrollbarY = dropdownY + 2;
                int scrollbarH = dropdownH - 4;
                int thumbH = (int) (scrollbarH * (5.0 / values.length));
                int thumbY = scrollbarY + (int) ((scrollbarH - thumbH) * ((double) scrollOffset / (values.length - 5)));
                ctx.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbH,
                        ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND), 0x66));
            }
        }

        renderDescription(ctx, x, y + baseHeight(), width);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (isDisabled()) return false;
        if (button != 0) return false;

        int btnX = rowX + rowWidth - BTN_W - 14;
        int btnY = rowY + (CONTROL_H - BTN_H) / 2;

        boolean clickedButton = Widget.inBounds(mouseX, mouseY, btnX, btnY, BTN_W, BTN_H);

        if (clickedButton) {
            expanded = !expanded;
            if (expanded) {
                requestFocus();
            } else {
                releaseFocus();
            }
            return true;
        }

        if (expanded) {
            int dropdownY = btnY + BTN_H + 2;
            int visibleCount = Math.min(5, values.length);
            int dropdownH = visibleCount * OPT_H + 4;

            boolean clickedDropdown = Widget.inBounds(mouseX, mouseY, btnX, dropdownY, BTN_W, dropdownH);

            if (clickedDropdown) {
                int clickedIndex = (int) ((mouseY - (dropdownY + 2)) / OPT_H);
                if (clickedIndex >= 0 && clickedIndex < visibleCount) {
                    int actualIdx = scrollOffset + clickedIndex;
                    if (actualIdx >= 0 && actualIdx < values.length) {
                        setter.accept(values[actualIdx]);
                        com.aurora.client.config.AuroraConfig.save();
                    }
                }
                expanded = false;
                releaseFocus();
                return true;
            } else {
                // Outside click collapses the popup AND consumes the click —
                // letting it fall through would act on whatever renders
                // underneath the now-dismissed dropdown.
                expanded = false;
                releaseFocus();
                return true;
            }
        }

        return false;
    }

    /**
     * Reset popup state on screen close so the next visit starts collapsed
     * and this row cannot hold the static focus registry across screens —
     * same lifecycle contract as KeyListSetting/ParticleConfigSetting.
     */
    @Override
    public void onDetailScreenClose() {
        expanded = false;
        scrollOffset = 0;
        releaseFocus();
    }

    @Override
    public boolean onScroll(double vertical) {
        if (!expanded) return false;
        int maxVisible = 5;
        if (values.length <= maxVisible) return false;

        scrollOffset -= (int) Math.signum(vertical);
        if (scrollOffset < 0) scrollOffset = 0;
        int maxScroll = values.length - maxVisible;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        return true;
    }
}
