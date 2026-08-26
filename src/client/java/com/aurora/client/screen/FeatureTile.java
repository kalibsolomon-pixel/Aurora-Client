package com.aurora.client.screen;

import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.AuroraShapes;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * iOS-style feature card.
 *
 * <p>The card is a flat squircle on {@code secondarySystemBackground}, no
 * border. The "on" state is communicated by a small iOS UISwitch in the
 * top-right corner — same visual language as a Settings-app row, so the
 * user reads ON/OFF without text. A subtle inner highlight at the top
 * edge fakes the soft top-edge sheen iOS cards have under the system
 * background blur.
 *
 * <p>Press animation: the entire tile scales to {@code 0.97} on
 * pointer-down (90 ms ease-out) and springs back on release (180 ms
 * spring overshoot). Hover lift is a 36-alpha white overlay fading in
 * over 140 ms — barely visible but enough to communicate focus.
 */
public class FeatureTile extends AbstractButton {

    private static final net.minecraft.network.chat.Style SYMBOL_STYLE = net.minecraft.network.chat.Style.EMPTY
            .withFont(new net.minecraft.network.chat.FontDescription.Resource(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("aurora", "material_symbols")
            ));

    private static final long HOVER_MS = 140L;
    private static final long PRESS_DOWN_MS = 90L;
    private static final long PRESS_UP_MS  = 180L;
    private static final long SLIDE_MS = 280L;

    /** Gear button square side length (px). Top-right of the tile. */
    private static final int GEAR_SIZE   = 14;
    /** Inset from the tile's top/right edges to the gear's outer bounding box. */
    private static final int GEAR_MARGIN = 6;

    private final FeatureMetadata meta;

    // Hover anim
    private long hoverStartMs = 0L;
    private boolean wasHovered = false;
    private float hoverT = 0f;

    // Press anim
    private long pressDownStartMs = -1L;
    private long pressUpStartMs = -1L;
    private boolean isPointerDown = false;

    // Toggle slide anim
    private long slideStartMs = 0L;
    private boolean slideTarget = false;
    private boolean slideInited = false;
    private float slideFromT = 0f;

    // Pre-fitted title/desc — recomputed only on width changes.
    private String fittedTitle;
    private String fittedDesc;
    private int fittedForWidth = -1;

    // Pre-computed icon/glyph metrics for rendering optimization
    private Component cachedSymbolComp;
    private int cachedGlyphWidth = -1;
    private int cachedGlyphHeight = -1;
    private net.minecraft.util.FormattedCharSequence cachedVisualOrderText;

    public FeatureTile(int x, int y, int width, int height, FeatureMetadata meta) {
        super(x, y, width, height, Component.literal(meta.displayName));
        this.meta = meta;
    }

    /** Called by AuroraMainScreen during scroll to reposition without rebuilding. */
    public void setPos(int x, int y) {
        this.setX(x);
        this.setY(y);
    }

    /** Exposes the underlying metadata for parent screens (context menu, search filter). */
    public FeatureMetadata meta() { return meta; }

    @Override
    public void onPress(net.minecraft.client.input.InputWithModifiers input) {
        if ("resourcepack_browser".equals(meta.id)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.screen != null) {
                mc.setScreen(new ResourcePackBrowserScreen(mc.screen));
            }
            return;
        }
        meta.setEnabled(!meta.isEnabled());
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent ev, boolean dbl) {
        // Right-click does nothing on tiles now — the gear in the
        // top-right is the only path into a feature's settings.
        if (ev.button() == 1) return false;
        if (this.active && this.visible && ev.button() == 0
                && isMouseOverGear(ev.x(), ev.y()) && meta.hasDetail()) {
            // Gear click — open settings without toggling. The current
            // screen is the AuroraMainScreen instance hosting this tile,
            // and that's exactly the parent we want for back-navigation.
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.screen != null) {
                if ("resourcepack_browser".equals(meta.id)) {
                    mc.setScreen(new ResourcePackBrowserScreen(mc.screen));
                } else {
                    mc.setScreen(new FeatureDetailScreen(mc.screen, meta));
                }
            }
            return true;
        }
        if (this.active && this.visible && this.isMouseOver(ev.x(), ev.y()) && ev.button() == 0) {
            isPointerDown = true;
            pressDownStartMs = System.currentTimeMillis();
            pressUpStartMs = -1L;
        }
        return super.mouseClicked(ev, dbl);
    }

    /**
     * Hit test for the top-right hover gear region. Slightly inflated
     * past the rendered glyph so the cursor doesn't have to be pixel-
     * perfect on a 14-px target.
     */
    private boolean isMouseOverGear(double mx, double my) {
        int gx = getX() + getWidth() - GEAR_MARGIN - GEAR_SIZE;
        int gy = getY() + GEAR_MARGIN;
        return mx >= gx && mx < gx + GEAR_SIZE && my >= gy && my < gy + GEAR_SIZE;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent ev) {
        if (isPointerDown) {
            isPointerDown = false;
            pressUpStartMs = System.currentTimeMillis();
        }
        return super.mouseReleased(ev);
    }

    private float currentScale() {
        long now = System.currentTimeMillis();
        if (isPointerDown && pressDownStartMs > 0L) {
            float raw = AuroraAnim.clamp01((now - pressDownStartMs) / (float) PRESS_DOWN_MS);
            return AuroraAnim.lerp(1.0f, 0.97f, AuroraAnim.easeOutCubic(raw));
        } else if (pressUpStartMs > 0L) {
            float raw = AuroraAnim.clamp01((now - pressUpStartMs) / (float) PRESS_UP_MS);
            if (raw >= 1f) { pressUpStartMs = -1L; return 1.0f; }
            return AuroraAnim.lerp(0.97f, 1.00f, AuroraAnim.springOvershoot(raw));
        }
        return 1.0f;
    }

    private float updateSlide(boolean target) {
        long now = System.currentTimeMillis();
        if (!slideInited) {
            slideTarget = target;
            slideFromT = target ? 1f : 0f;
            slideInited = true;
            return slideFromT;
        }
        if (target != slideTarget) {
            slideFromT = currentSlideValue(now);
            slideTarget = target;
            slideStartMs = now;
        }
        float endT = target ? 1f : 0f;
        if (slideFromT == endT) return endT;
        return currentSlideValue(now);
    }

    private float currentSlideValue(long now) {
        long elapsed = Math.max(0L, now - slideStartMs);
        float raw = AuroraAnim.clamp01(elapsed / (float) SLIDE_MS);
        float endT = slideTarget ? 1f : 0f;
        if (raw >= 1f) { slideFromT = endT; return endT; }
        return AuroraAnim.lerp(slideFromT, endT, AuroraAnim.springOvershoot(raw));
    }

    @Override
    protected void renderContents(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        boolean hovered = this.isHovered();
        if (hovered != wasHovered) {
            hoverStartMs = System.currentTimeMillis() - (long) ((1f - hoverT) * HOVER_MS);
            wasHovered = hovered;
        }
        float hoverEnd = hovered ? 1f : 0f;
        if (hoverT != hoverEnd) {
            long elapsed = System.currentTimeMillis() - hoverStartMs;
            float raw = AuroraAnim.clamp01(elapsed / (float) HOVER_MS);
            float t = hovered ? raw : (1f - raw);
            hoverT = AuroraAnim.easeOutCubic(t);
            if (raw >= 1f) hoverT = hoverEnd;
        }

        boolean on = meta.isEnabled();
        // Slide animation is no longer rendered (mini-switch removed)
        // but the state tracker is kept warm so that on/off toggles
        // remain consistent with the previous animation timing if a
        // switch is ever brought back.
        updateSlide(on);

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int cx = x + w / 2;
        int cy = y + h / 2;
        float scale = currentScale();

        ctx.pose().pushMatrix();
        if (scale != 1.0f) {
            ctx.pose().translate(cx, cy);
            ctx.pose().scale(scale, scale);
            ctx.pose().translate(-cx, -cy);
        }

        // ----- Card surface -----
        // Tile sits inside the screen's blurred window panel: the only
        // solid element is the outline (itself translucent white). The
        // fill is a translucent white-on-window overlay that reads as
        // one more step of "blur" beyond the surrounding window. Hover
        // adds a second translucent overlay; on-state brightens the
        // outline as the toggle affordance.
        //
        // The chamfer uses {@link AuroraTheme#RADIUS_SMALL} (9 px) rather
        // than the default {@code RADIUS} (14 px). At the smaller
        // tile size the larger radius produced visible stair-step
        // "bridge" segments at each corner of the outline; the smaller
        // chamfer keeps the corners crisp without the jutting artifact.
        final int tileRadius = AuroraTheme.RADIUS_SMALL;
        // (Drop shadow intentionally omitted on tiles.) The window is
        // now an opaque charcoal (#0A0A0A) sheet, and the tile fill is
        // a lighter charcoal (#2A2A2A); the contrast step already reads
        // as a stack of two surfaces without any per-tile shadow pass.
        // Re-introducing dropShadow here costs ~648 ctx.fill() calls
        // per tile per frame (four chamfered fills at this radius) and
        // produces no visible lift against the new window background.
        // Desaturated translucent vertical gradient (light gray top →
        // dark gray bottom) replacing the previous flat frosted-white
        // fill. Alpha is preserved on both stops so the window's blur
        // still shows through the card.
        AuroraShapes.panelGradient(ctx, x, y, w, h,
                AuroraTheme.TILE_GRAD_TOP,
                AuroraTheme.TILE_GRAD_BOT,
                tileRadius);
        if (hoverT > 0f) {
            int baseA = (AuroraTheme.TILE_FILL_HOVER >>> 24) & 0xFF;
            int liftAlpha = Math.round(baseA * hoverT);
            int liftColor = (liftAlpha << 24)
                    | (AuroraTheme.TILE_FILL_HOVER & 0x00FFFFFF);
            AuroraShapes.panel(ctx, x, y, w, h, liftColor, tileRadius);
        }
        // ON-state outline picks up the module's accent color if one is
        // registered (ModuleAccentColors), falling back to iOS blue so
        // ON tiles read as "active" with a colored frame. OFF state
        // always uses the dim translucent white outline.
        int outlineColor;
        if (on) {
            int accent = ModuleAccentColors.get(meta.id);
            outlineColor = accent != 0 ? accent : AuroraTheme.MODULE_ACCENT_ON;
        } else {
            outlineColor = AuroraTheme.TILE_OUTLINE_OFF;
        }
        // Inward-fading shadow vignette traced just inside the outline,
        // adding depth so each tile reads as a recessed surface behind
        // its frame. Drawn before the outline so the outline crowns the
        // shadowed border crisply.
        AuroraShapes.innerShadow(ctx, x, y, w, h, tileRadius);
        AuroraShapes.outline(ctx, x, y, w, h, outlineColor, tileRadius);

        Font tr = Minecraft.getInstance().font;
        ensureFittedText(tr);

        // ----- Icon (top, centered) -----
        // Layout: the upper ~70 % of the tile is icon space, lower ~25 %
        // holds the title. ON state renders at full alpha; OFF state at
        // 50 % alpha so the on/off affordance reads against either
        // white-silhouette or full-color authored PNGs. Falls back to
        // the legacy Unicode glyph from FeatureIcons when no PNG ships
        // for this id, so the grid stays populated during incremental
        // art rollout.
        // Reserve title row at the bottom (≈ 12 px including padding).
        final int TITLE_ROW_H   = 14;
        final int TITLE_PAD_TOP = 2;
        if (h >= 40) {
            int iconRowTop = y + 6;
            int iconRowBot = y + h - TITLE_ROW_H;
            float cxF = x + w / 2f;
            float cyF = (iconRowTop + iconRowBot) / 2f;
            int iconArea = Math.max(1, iconRowBot - iconRowTop);
            // Target on-screen icon size for PNG art — fills most of
            // the icon row above the title. Glyph fallback uses a much
            // tighter budget (see else-branch) since Unifont glyphs lose
            // legibility when scaled this aggressively.
            float TARGET_PX = Mth.clamp(iconArea * 1.50f, 32f, 141f);

            String icon = FeatureIcons.get(meta.id);
            if (!icon.isEmpty()) {
                if (cachedSymbolComp == null) {
                    cachedSymbolComp = Component.literal(icon).withStyle(SYMBOL_STYLE);
                    cachedGlyphWidth = Math.max(1, tr.width(cachedSymbolComp));
                    cachedGlyphHeight = Math.max(1, tr.lineHeight);
                    cachedVisualOrderText = cachedSymbolComp.getVisualOrderText();
                }

                int gw = cachedGlyphWidth;
                int gh = cachedGlyphHeight;
                final float MAX_SCALE = 16f;
                float glyphTarget = Mth.clamp(iconArea * 0.55f, 16f, 48f);
                float iconScale = Math.min(MAX_SCALE,
                        Math.min(glyphTarget / gw, glyphTarget / gh));

                int glyphAccent = ModuleAccentColors.get(meta.id);
                if (glyphAccent == 0) glyphAccent = AuroraTheme.MODULE_ACCENT_ON;
                int iconColor = on
                        ? glyphAccent
                        : AuroraTheme.IOS_TERTIARY_LABEL;

                ctx.pose().pushMatrix();
                ctx.pose().translate(cxF, cyF);
                ctx.pose().scale(iconScale, iconScale);
                
                ctx.drawString(tr, cachedVisualOrderText, -gw / 2, -gh / 2, iconColor, false);
                ctx.pose().popMatrix();
            }
        }

        // ----- Title (bottom, centered, small) -----
        // No description row anymore — the title alone identifies the
        // feature; mouse-over the tile gives the description in vanilla
        // tooltip space. Fractional scaling removed for cleaner text rendering.
        int titleColor = on
                ? AuroraTheme.IOS_LABEL
                : AuroraTheme.IOS_SECONDARY_LABEL;
        int titleW = tr.width(fittedTitle);
        float titleX = x + (w - titleW) / 2f;
        float titleY = y + h - TITLE_ROW_H + TITLE_PAD_TOP;
        ctx.drawString(tr, fittedTitle, (int) titleX, (int) titleY, titleColor, false);

        // ----- Hover gear (top-right) -----
        // Fades in with hoverT so it stays out of the way on idle tiles
        // and reveals only when the user is actively considering this
        // module. Tiles without a settings detail (meta.hasDetail() ==
        // false) skip the gear entirely so the user isn't promised an
        // affordance that goes nowhere.
        if (hoverT > 0f && meta.hasDetail()) {
            String gear = "\u2699"; // ⚙ (U+2699 GEAR)
            int gw = Math.max(1, tr.width(gear));
            int gh = Math.max(1, tr.lineHeight);
            int boxX = x + w - GEAR_MARGIN - GEAR_SIZE;
            int boxY = y + GEAR_MARGIN;
            float gcxF = boxX + GEAR_SIZE / 2f;
            float gcyF = boxY + GEAR_SIZE / 2f;
            // Hit-test is slightly inflated past the rendered glyph
            // (see isMouseOverGear); fade the box-fill in too so the
            // affordance reads as a clickable button at full hover.
            int gearAlpha = Math.round(0xCC * hoverT);
            int gearColor = (gearAlpha << 24)
                    | (AuroraTheme.IOS_LABEL & 0x00FFFFFF);
            ctx.pose().pushMatrix();
            ctx.pose().translate(gcxF, gcyF);
            // Glyph is the unifont fallback at typical scales; nudge to
            // ~1.1× so the cog reads at the small button target size.
            ctx.pose().scale(1.1f, 1.1f);
            ctx.drawString(tr, gear, -gw / 2, -gh / 2, gearColor, false);
            ctx.pose().popMatrix();
        }

        ctx.pose().popMatrix();
    }

    private void ensureFittedText(Font tr) {
        int w = getWidth();
        if (w == fittedForWidth && fittedTitle != null) return;

        // Title is centered along the bottom row of the tile.
        int maxW = w - 8;
        int ellipsisW = tr.width("...");

        String title = meta.displayName;
        if (tr.width(title) > maxW) {
            title = tr.plainSubstrByWidth(title, maxW - ellipsisW) + "...";
        }
        fittedTitle = title;
        // fittedDesc is no longer drawn but is kept on the type to
        // preserve binary compatibility with anything that still reads
        // it via reflection / debug tools.
        fittedDesc = meta.description;

        fittedForWidth = w;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        this.defaultButtonNarrationText(builder);
    }
}
