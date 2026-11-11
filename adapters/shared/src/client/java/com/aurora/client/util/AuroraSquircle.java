package com.aurora.client.util;

import com.aurora.client.AuroraClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Procedurally-baked iOS continuous-corner ("squircle") alpha masks.
 *
 * <p>iOS rounded rectangles aren't simple rounded rectangles — Apple uses
 * a G2-continuous superellipse curve so the corners blend smoothly into
 * the straight edges with no visible curvature break. This class
 * approximates that with a degree-5 superellipse {@code |x|⁵ + |y|⁵ ≤ 1}
 * which is visually indistinguishable from Apple's curve at typical UI
 * sizes and trivial to evaluate per pixel.
 *
 * <p>Two textures are baked at startup:
 * <ul>
 *   <li><b>Panel</b> — solid white inside the squircle, transparent
 *       outside, anti-aliased at the boundary. Tinted at draw time to
 *       produce filled cards/buttons of any color.</li>
 *   <li><b>Outline</b> — the squircle perimeter rendered as a 1.5 px
 *       ring with alpha falloff on both sides. Tinted at draw time for
 *       hairline borders.</li>
 * </ul>
 *
 * <p><b>Texture layout (9-slice friendly):</b> the curve is confined to
 * the four {@link #CORNER_PX}×{@link #CORNER_PX} corners of the
 * {@link #TEX_SIZE}×{@link #TEX_SIZE} texture. The interior cross is
 * fully opaque so 9-slice edges and the center stretch to any size as
 * solid color. Drawing code samples the high-resolution corner and
 * downscales to the requested screen radius via bilinear filtering —
 * yielding crisp curves at any radius without per-size texture baking.
 *
 * <p>Bake-time quality: 8× supersampling (64 samples per output pixel)
 * locks in anti-aliasing at texture resolution. Runtime is one blit per
 * region (4 corners + 4 edges + 1 center for a filled panel). Negligible
 * cost compared to anything that touches the world renderer.
 */
public final class AuroraSquircle {

    /** Total texture dimension. Must equal {@code 2 * CORNER_PX}. */
    public static final int TEX_SIZE = 64;
    /**
     * Corner region size in texels — the squircle curve lives entirely in
     * each {@code CORNER_PX × CORNER_PX} corner of the texture. Larger =
     * more headroom for higher-radius UI elements.
     */
    public static final int CORNER_PX = TEX_SIZE / 2;

    /** Squircle exponent. 5 ≈ Apple's continuous-corner curve. */
    private static final double EXPONENT = 5.0;

    /** Supersampling factor at bake time. 8 → 64 samples per output pixel. */
    private static final int SSAA = 8;

    /** Outline ring half-width in texels of the corner region (~1.5 px stroke). */
    private static final double OUTLINE_HALF_WIDTH_TEXELS = 0.9;

    private static final Identifier PANEL_ID = Identifier.fromNamespaceAndPath(
            AuroraClient.MOD_ID, "squircle_panel");
    private static final Identifier OUTLINE_ID = Identifier.fromNamespaceAndPath(
            AuroraClient.MOD_ID, "squircle_outline");

    private static volatile boolean ready = false;
    private static DynamicTexture panelTex;
    private static DynamicTexture outlineTex;

    private AuroraSquircle() {}

    /** Identifier of the filled-squircle alpha mask. Lazy-registers on first call. */
    public static Identifier panelTex() {
        ensureReady();
        return PANEL_ID;
    }

    /** Identifier of the squircle perimeter alpha mask. Lazy-registers on first call. */
    public static Identifier outlineTex() {
        ensureReady();
        return OUTLINE_ID;
    }

    /** {@code true} once both textures are baked + uploaded. */
    public static boolean isReady() {
        return ready;
    }

    private static synchronized void ensureReady() {
        if (ready) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTextureManager() == null) return;

        NativeImage panel = new NativeImage(NativeImage.Format.RGBA, TEX_SIZE, TEX_SIZE, false);
        NativeImage outline = new NativeImage(NativeImage.Format.RGBA, TEX_SIZE, TEX_SIZE, false);
        bake(panel, outline);

        panelTex = new DynamicTexture(() -> "squircle_panel", panel);
        outlineTex = new DynamicTexture(() -> "squircle_outline", outline);
        mc.getTextureManager().register(PANEL_ID, panelTex);
        mc.getTextureManager().register(OUTLINE_ID, outlineTex);
        ready = true;
    }

    // ============================================================
    //  Bake (per-pixel, supersampled)
    // ============================================================

    /**
     * Bake both masks. For each output pixel we determine which "9-slice
     * region" it belongs to:
     * <ul>
     *   <li><b>Corner</b> ({@code px < CORNER_PX} && {@code py < CORNER_PX}
     *       and analogous for the other 3 corners) — evaluate the
     *       superellipse with normalized coordinates relative to the
     *       corner's inset center.</li>
     *   <li><b>Edge / center</b> — fully opaque (panel) or fully
     *       transparent (outline). Edges/center are stretched at draw
     *       time so their colors propagate through the 9-slice.</li>
     * </ul>
     */
    private static void bake(NativeImage panel, NativeImage outline) {
        double subStep = 1.0 / SSAA;
        double r = CORNER_PX; // also the "design radius" inside the corner box

        for (int py = 0; py < TEX_SIZE; py++) {
            for (int px = 0; px < TEX_SIZE; px++) {
                // Determine which corner box this pixel falls into, if any.
                // Inside the corner box the inset corner-center is at
                // (CORNER_PX, CORNER_PX) of the texture (top-left example);
                // the squircle curves from texture (CORNER_PX, 0) to
                // (0, CORNER_PX) through (some interior squircle point).
                double cornerCx, cornerCy;
                boolean inCornerBox;
                if (px < CORNER_PX && py < CORNER_PX) {
                    cornerCx = CORNER_PX; cornerCy = CORNER_PX;
                    inCornerBox = true;
                } else if (px >= CORNER_PX && py < CORNER_PX) {
                    cornerCx = CORNER_PX - 1; cornerCy = CORNER_PX;
                    inCornerBox = true;
                    // For the right corners we need to mirror — handled by
                    // computing dx as |px - cornerCx|.
                } else if (px < CORNER_PX && py >= CORNER_PX) {
                    cornerCx = CORNER_PX; cornerCy = CORNER_PX - 1;
                    inCornerBox = true;
                } else {
                    cornerCx = CORNER_PX - 1; cornerCy = CORNER_PX - 1;
                    inCornerBox = true;
                }
                // Note: inCornerBox is always true — every pixel of a
                // 2*CORNER_PX texture is inside one of the four corner
                // quadrants. The 9-slice "edges" emerge naturally because
                // pixels close to the inset center read as inside the
                // squircle (alpha = 1) while pixels close to the texture
                // corner read as outside (alpha = 0).

                int aPanel, aOutline;
                if (!inCornerBox) {
                    aPanel = 255;
                    aOutline = 0;
                } else {
                    double sumPanel = 0.0;
                    double sumOutline = 0.0;
                    for (int sy = 0; sy < SSAA; sy++) {
                        for (int sx = 0; sx < SSAA; sx++) {
                            double fx = (px + (sx + 0.5) * subStep) - cornerCx;
                            double fy = (py + (sy + 0.5) * subStep) - cornerCy;
                            double nx = Math.abs(fx) / r;
                            double ny = Math.abs(fy) / r;
                            // Superellipse value: 1.0 = on the boundary,
                            // < 1.0 = inside, > 1.0 = outside.
                            double v = Math.pow(nx, EXPONENT) + Math.pow(ny, EXPONENT);

                            if (v <= 1.0) sumPanel += 1.0;

                            // Outline: distance from the v=1 boundary in
                            // "v-space". Convert to approximate texel
                            // distance via the local gradient magnitude
                            // (~5 at the boundary for n=5 — close enough
                            // for a constant ring width in screen space).
                            double dist = Math.abs(v - 1.0) * r / EXPONENT;
                            if (dist < OUTLINE_HALF_WIDTH_TEXELS) {
                                sumOutline += 1.0 - dist / OUTLINE_HALF_WIDTH_TEXELS;
                            }
                        }
                    }
                    int total = SSAA * SSAA;
                    aPanel = (int) Math.round(sumPanel * 255.0 / total);
                    aOutline = (int) Math.round(sumOutline * 255.0 / total);
                    if (aPanel > 255) aPanel = 255;
                    if (aOutline > 255) aOutline = 255;
                }

                panel.setPixelABGR(px, py, (aPanel << 24) | 0x00FFFFFF);
                outline.setPixelABGR(px, py, (aOutline << 24) | 0x00FFFFFF);
            }
        }
    }
}
