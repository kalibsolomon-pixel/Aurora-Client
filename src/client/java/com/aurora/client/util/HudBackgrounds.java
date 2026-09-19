package com.aurora.client.util;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Shared background renderer for HUD modules.
 *
 * <p>The {@code AURORA} mode is a single-pass vertical gradient from
 * near-black at the top to a deep {@code systemBlue} at the bottom —
 * gives the HUD a glowing/lit look that ties into the rest of Aurora's
 * blue accent system without needing a separate outline. No top sheen.
 * Corner radius is intentionally small ({@link #RADIUS}) so the panels
 * read as boxy / techy rather than pill-soft.
 *
 * <p>The {@code VANILLA} mode tiles the authentic vanilla hotbar-slot
 * graphic behind the HUD item so it reads as sitting inside a vanilla
 * slot outline — the look popularised by mods like Uku's ArmorHUD.
 */
public final class HudBackgrounds {

    private HudBackgrounds() {}

    /**
     * Boxier corners than the rest of the UI ({@link AuroraTheme#RADIUS}) —
     * a SANCTIONED deviation, Square-mode-exempt by ruling (C-7): HUD module
     * panels are floating HUD content (DESIGN_LANGUAGE §16's exception
     * domain) whose compact boxy look is deliberate feature identity, not
     * rectangular chrome that forgot the token. Do not "fix" this to follow
     * Square mode.
     */
    private static final int RADIUS = 2;
    private static final int PAD = 3;

    /** A single hotbar slot, cropped from the vanilla {@code hud/hotbar} strip. */
    private static final Identifier HOTBAR_SLOT_TEX =
            Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "textures/gui/hotbar_slot.png");
    /** Native edge length of a single hotbar slot (24×24 incl. its border). */
    private static final int SLOT = 24;

    private static int getAuroraTop() {
        return ThemeManager.color(ThemeToken.HUD_BACKDROP_TOP);
    }

    private static int getAuroraBot() {
        return ThemeManager.color(ThemeToken.HUD_BACKDROP_BOT);
    }

    public static void draw(GuiGraphics ctx, int x, int y, int w, int h,
                            AuroraConfig.HudBackground mode, int solidColor) {
        draw(ctx, x, y, w, h, mode, solidColor, 1.0f);
    }

    /**
     * Background draw with a uniform alpha multiplier in [0, 1]. Used by
     * modules that want their background to fade in/out alongside their
     * content (e.g. {@link com.aurora.client.hud.module.PotionModule}
     * gliding the panel away once the last effect expires).
     */
    public static void draw(GuiGraphics ctx, int x, int y, int w, int h,
                            AuroraConfig.HudBackground mode, int solidColor,
                            float alphaScale) {
        if (mode == null || mode == AuroraConfig.HudBackground.NONE) return;
        if (alphaScale <= 0.001f) return;

        int px = x - PAD, py = y - PAD, pw = w + PAD * 2, ph = h + PAD * 2;
        int x2 = px + pw, y2 = py + ph;

        switch (mode) {
            case SOLID -> RoundedRect.fill(ctx, px, py, x2, y2, RADIUS,
                    scaleAlpha(solidColor, alphaScale));
            case AURORA -> AuroraShapes.panelGradient(ctx, px, py, pw, ph,
                    scaleAlpha(getAuroraTop(), alphaScale),
                    scaleAlpha(getAuroraBot(), alphaScale),
                    RADIUS);
            case VANILLA -> drawVanillaSlots(ctx, px, py, x2, y2, alphaScale);
            default -> {}
        }
    }

    /**
     * Tiles the vanilla hotbar-slot graphic across the module's padded AABB so
     * the HUD item sits inside a vanilla slot outline. The 20×20 slot is
     * repeated on a regular grid and clipped with a scissor so partial edge
     * tiles stay clean. A white tint whose alpha tracks {@code alphaScale} is
     * applied so the grid fades in/out with the panel.
     */
    private static void drawVanillaSlots(GuiGraphics ctx, int x1, int y1, int x2, int y2, float alphaScale) {
        int pw = x2 - x1;
        int ph = y2 - y1;
        if (pw <= 0 || ph <= 0) return;

        int tint = scaleAlpha(0xFFFFFFFF, alphaScale);
        ctx.enableScissor(x1, y1, x2, y2);
        try {
            for (int ty = 0; ty < ph; ty += SLOT) {
                for (int tx = 0; tx < pw; tx += SLOT) {
                    ctx.blit(RenderPipelines.GUI_TEXTURED, HOTBAR_SLOT_TEX,
                            x1 + tx, y1 + ty, 0f, 0f,
                            SLOT, SLOT, SLOT, SLOT, SLOT, SLOT);
                }
            }
        } finally {
            ctx.disableScissor();
        }
    }

    private static int scaleAlpha(int argb, float scale) {
        float s = Math.max(0f, Math.min(1f, scale));
        int a = (argb >>> 24) & 0xFF;
        int newA = Math.round(a * s);
        return (newA << 24) | (argb & 0x00FFFFFF);
    }
}