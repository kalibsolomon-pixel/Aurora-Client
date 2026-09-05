package com.aurora.client.screen.setting;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.component.RoundedPanel;
import com.aurora.client.ui.component.Slider;
import com.aurora.client.ui.component.ToggleSwitch;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Non-interactive live-preview card for the theme settings screen (Stage 3)
 * — the COSMIC Appearance panel's "see it before you leave" affordance.
 *
 * <p>Renders a mock card holding the four control archetypes — primary and
 * secondary buttons, an on-state toggle, a slider, and primary/secondary
 * text — so any accent/mode/roundness/opacity change is visible instantly.
 *
 * <p><b>Zero separate color logic:</b> every color and radius is read from
 * the same cached, re-projected token facade ({@code AuroraTheme.*}) the
 * real UI reads. A theme reload is literally the preview update — nothing
 * is recomputed here.
 *
 * <p>All shapes render through the mod's high-res engine
 * ({@link RenderUtil} AA family): physical-pixel scanlines with analytic
 * sub-pixel coverage — smooth, crisp curves at any GUI scale.
 */
public class ThemePreviewSetting extends FeatureSetting {

    private static final int PREVIEW_H = 88;
    private static final int PAD_X = 12;
    private static final int PAD_Y = 8;

    // Mock control geometry (logical px).
    private static final int BTN_W = 54;
    private static final int BTN_H = 18;
    private static final int TOGGLE_W = 28;
    private static final int TOGGLE_H = 16;
    private static final int KNOB_R = 5;
    private static final int CHIP_W = 26;
    private static final int CHIP_H = 10;

    public ThemePreviewSetting(String label) {
        super(label);
    }

    // The preview is literally the shared components themselves — a mock
    // card holding the four control archetypes, so any theme change shows
    // up instantly with zero separate color logic. Glass pilot: the card
    // body and the accent chip are LIVE glass (renderGlassPass) — the
    // RoundedPanel here is only the opaque FALLBACK; the primary button
    // uses accent-STAINED glass (selected/primary family) and the secondary
    // button NEUTRAL glass (raised, like every control on this screen).
    // The mock toggle and slider deliberately stay OPAQUE — glass on the
    // real toggle/slider family was reverted (too small to carry the
    // treatment); the preview shows that final state.
    private final RoundedPanel card = new RoundedPanel(false, ThemeToken.SURFACE);
    private final ToggleSwitch toggle = new ToggleSwitch(() -> true, v -> {});
    private final Slider slider = new Slider(() -> 0.6, v -> {}, 0, 1, 0.01);
    private final Button primaryBtn = new Button("Button", () -> {}, true)
            .glassStyle(Button.GlassStyle.STAINED);
    private final Button secondaryBtn = new Button("Button", () -> {}).glassBackground(true);

    // Rects remembered by the (cache-dirty-only) renderShapes pass so the
    // per-frame glass pass can draw at the right place; -1 = not yet laid out.
    private int cardX = -1, cardY = -1, cardW = -1, cardH = -1;
    private int chipX = -1, chipY = -1;

    @Override public int baseHeight() { return PREVIEW_H; }
    @Override public int height() { return PREVIEW_H; }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        renderShapes(ctx, x, y, width, mouseX, mouseY);
        renderOverlay(ctx, x, y, width, mouseX, mouseY);
    }

    @Override
    public void renderShapes(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        // The preview card + mock controls are the shared themed components
        // themselves — a theme reload is literally the preview update.
        int px = x + PAD_X;
        int py = y + PAD_Y;
        int pw = width - PAD_X * 2;
        int ph = PREVIEW_H - PAD_Y;

        // Glass pilot: the card's SURFACE fill + outline and the accent chip
        // are NOT drawn into the cached layer — they are live glass drawn
        // each frame by renderGlassPass (before the cached layer blit, so
        // the mock controls rasterized into the cache still stack on top of
        // them). Only the rects are remembered here, because this method
        // runs solely when the static cache is dirty. The toggle and slider
        // render fully into the cache (opaque by design).
        cardX = px;
        cardY = py;
        cardW = pw;
        cardH = ph;

        int togX = px + 12;
        int togY = py + 52;
        toggle.renderShapes(ctx, togX, togY, TOGGLE_W, TOGGLE_H);

        int slX = togX + TOGGLE_W + 14;
        int slY = togY + TOGGLE_H / 2 - 2;
        int slW = 64;
        slider.layout(slX, slY - 4, slW, 16);
        slider.renderShapes(ctx, slX, slY - 4, slW, 16);
        slider.renderOverlay(ctx, slX, slY - 4, slW, 16, -1, -1);

        int b1x = px + pw - BTN_W - 12;
        int b1y = py + 10;
        primaryBtn.renderOverlay(ctx, b1x, b1y, BTN_W, BTN_H, -1, -1);

        int b2x = b1x;
        int b2y = b1y + BTN_H + 8;
        secondaryBtn.renderOverlay(ctx, b2x, b2y, BTN_W, BTN_H, -1, -1);

        // Accent chip — rect remembered only; the chip itself is live
        // stained glass in renderGlassPass (flat accent = the fallback).
        int swY = py + ph - 16;
        int swX = px + pw - 12 - 26;
        this.chipX = swX;
        this.chipY = swY;
    }

    /**
     * Live glass pass (Glass pilot) — called every frame by the theme detail
     * screen right after the window's own glass and the static-cache capture
     * pass (which refreshes the rects), and before the overlay dim + cached
     * blit so cached content (texts, thumbs, knobs) stacks above the glass.
     *
     * <p>Treatments per the catalog: the card container is NEUTRAL RAISED
     * glass (a control-holding card reads as floating above the recessed
     * window — the pilot's "recessed well vs raised card" flag, answered);
     * the accent chip is accent-STAINED raised glass. The toggle and slider
     * are deliberately opaque (drawn fully into the cached layer — the glass
     * treatment was reverted for those element types). Elements fall back to
     * their old opaque look whenever the blur renderer declines (screenshot
     * suppression, tiny panel, failure) — the same fallback contract every
     * glass integration uses.
     */
    @Override
    public void renderGlassPass(GuiGraphics ctx) {
        if (cardX < 0 || cardY < 0 || cardW <= 0 || cardH <= 0) return; // not laid out yet
        float radius = ThemeManager.current().roundness().radius();
        boolean ok = BlurPanelRenderer.renderPanel(ctx, cardX, cardY, cardW, cardH,
                radius, BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX,
                BlurPanelRenderer.Lighting.raised());
        if (ok) {
            // Tint: the same translucent-fill model every glass surface
            // uses — WINDOW_FILL's alpha carries the theme's Background
            // Opacity (its single application point).
            RenderUtil.drawRoundedRectAA(ctx, cardX, cardY, cardW, cardH, radius,
                    ThemeManager.color(ThemeToken.WINDOW_FILL));
            BlurPanelRenderer.drawRimFinish(ctx, cardX, cardY, cardW, cardH, radius);
        } else {
            card.renderShapes(ctx, cardX, cardY, cardW, cardH);
        }

        // Accent chip — stained family; draws its own flat fallback on decline.
        if (chipX >= 0) {
            float chipR = Math.min(BTN_H / 2.0f, ThemeManager.current().roundness().radiusSmall());
            if (BlurPanelRenderer.renderPanel(ctx, chipX, chipY, CHIP_W, CHIP_H, chipR,
                    BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX,
                    BlurPanelRenderer.Lighting.raised())) {
                RenderUtil.drawRoundedRectAA(ctx, chipX, chipY, CHIP_W, CHIP_H, chipR,
                        ThemeManager.stainedTint());
                BlurPanelRenderer.drawRimFinish(ctx, chipX, chipY, CHIP_W, CHIP_H, chipR);
            } else {
                RenderUtil.drawRoundedRectAA(ctx, chipX, chipY, CHIP_W, CHIP_H, chipR,
                        AuroraTheme.IOS_BLUE);
            }
        }
    }

    @Override
    public void renderOverlay(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        Font tr = Minecraft.getInstance().font;
        int px = x + PAD_X;
        int py = y + PAD_Y;
        int pw = width - PAD_X * 2;
        int b1x = px + pw - BTN_W - 12;
        int b1y = py + 10;
        int b2x = b1x;
        int b2y = b1y + BTN_H + 8;

        int tx = px + 12;
        int ty = py + 10;
        ctx.drawString(tr, "Primary text", tx, ty, AuroraTheme.IOS_LABEL, false);
        ctx.drawString(tr, "Secondary text", tx, ty + 12, AuroraTheme.IOS_SECONDARY_LABEL, false);
        ctx.drawString(tr, "Muted text", tx, ty + 24, AuroraTheme.IOS_TERTIARY_LABEL, false);

        AuroraFontRenderer.drawCentered(ctx, tr, "Button", b1x + BTN_W / 2, b1y + (BTN_H - tr.lineHeight) / 2,
                AuroraTheme.ON_ACCENT);
        AuroraFontRenderer.drawCentered(ctx, tr, "Button", b2x + BTN_W / 2, b2y + (BTN_H - tr.lineHeight) / 2,
                AuroraTheme.IOS_LABEL);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int rowX, int rowY, int rowWidth) {
        // Non-interactive preview: never consume.
        return false;
    }
}