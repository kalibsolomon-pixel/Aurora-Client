package com.aurora.client.hud;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * Draws Aurora's custom crosshair on top of the (mixin-suppressed) vanilla one.
 *
 * <p>Two configurable crosshairs exist: the default one, and an "indicator"
 * that replaces the default whenever the player is aiming at a targetable
 * entity within attack reach.
 *
 * <p><b>Centering note:</b> {@code GuiGraphics.fill()} takes integer pixel
 * coordinates, so a single-pixel-wide center column technically can't sit
 * on the screen's logical center seam (which is at {@code scaledWidth/2.0},
 * a half-integer position relative to drawn pixel grid). To put the
 * geometric center of the crosshair's central pixel on the screen center
 * rather than its corner, the matrix is translated by {@code (-0.5, -0.5)}
 * for the duration of the draw. This shifts every pixel by half a unit so
 * that the column previously spanning {@code [cx, cx+1)} now spans
 * {@code [cx-0.5, cx+0.5)} Ã¢â‚¬â€ its center exactly on the screen seam.
 *
 * <p>Vanilla doesn't do this, which is why vanilla's 15Ãƒâ€”15 crosshair is
 * technically half a pixel off-center toward the lower-right. Most people
 * never notice, but Aurora's crosshair is supposed to be visibly precise.
 *
 * <p>Note: {@link GuiGraphics#getMatrices()} in 1.21.8 returns a
 * {@code Matrix3x2fStack} (HUD-only 2D), so we use {@code pushMatrix()},
 * {@code popMatrix()}, and the 2-arg {@code translate(x, y)}, not the 3D
 * {@code PoseStack} API.
 *
 * <p>The {@code CUSTOM} style draws an 11Ãƒâ€”11 user-painted pixel grid
 * scaled by the {@code size} setting (each canvas pixel becomes a
 * {@code sizeÃƒâ€”size} block on screen). Thickness and gap are intentionally
 * ignored for CUSTOM Ã¢â‚¬â€ they don't have a meaningful interpretation on a
 * fixed-grid canvas.
 */
public final class CrosshairRenderer {

    public void render(GuiGraphics ctx) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.crosshairEnabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) return;
        if (client.options.getCameraType() != CameraType.FIRST_PERSON) return;
        if (client.screen != null) return;

        int cx = ctx.guiWidth() / 2;
        int cy = ctx.guiHeight() / 2;

        boolean useIndicator = cfg.crosshairIndicatorEnabled && isAimingAtEntity(client);

        AuroraConfig.CrosshairStyle style = useIndicator
                ? safe(cfg.crosshairIndicatorStyle)
                : safe(cfg.crosshairStyle);
        int size  = Math.max(1, useIndicator ? cfg.crosshairIndicatorSize      : cfg.crosshairSize);
        int thick = Math.max(1, useIndicator ? cfg.crosshairIndicatorThickness : cfg.crosshairThickness);
        int gap   = Math.max(0, useIndicator ? cfg.crosshairIndicatorGap       : cfg.crosshairGap);
        int color = useIndicator ? cfg.crosshairIndicatorColor : cfg.crosshairColor;

        // Half-pixel translate: see class javadoc. This lets a 1-px-wide
        // center column straddle the screen seam (its geometric center on
        // the seam) instead of having its left edge on the seam.
        ctx.pose().pushMatrix();
        ctx.pose().translate(-0.5f, -0.5f);

        drawShape(ctx, style, cx, cy, size, thick, gap, color,
                useIndicator ? cfg.crosshairIndicatorCustomPixels : cfg.crosshairCustomPixels,
                useIndicator ? cfg.crosshairIndicatorCustomWidth : cfg.crosshairCustomWidth,
                useIndicator ? cfg.crosshairIndicatorCustomHeight : cfg.crosshairCustomHeight);

        ctx.pose().popMatrix();
    }

    /**
     * Draws one crosshair shape centered on {@code (cx, cy)} — the single
     * implementation behind both the in-game HUD crosshair and the settings
     * screen's preset preview row ({@code CrosshairPreviewSetting}), so the
     * two can never drift apart (the {@code ItemSpriteRenderer} principle:
     * one pipeline, two call sites). The caller owns the half-pixel
     * translate — apply {@code translate(-0.5, -0.5)} before calling to get
     * the seam-straddling centering the HUD path uses.
     *
     * <p>For {@code CUSTOM}, the canvas pixels/dims are passed in by the
     * caller (the HUD path selects main vs indicator canvas; the preview
     * always shows the main one).
     */
    public static void drawShape(GuiGraphics ctx, AuroraConfig.CrosshairStyle style,
                                 int cx, int cy, int size, int thick, int gap, int color,
                                 boolean[] customPixels, int customW, int customH) {
        switch (safe(style)) {
            case DOT    -> drawDot(ctx, cx, cy, size, color);
            case CIRCLE -> drawCircle(ctx, cx, cy, size, gap, color);
            case SQUARE -> drawSquare(ctx, cx, cy, size, thick, gap, color);
            case CROSS  -> drawCross(ctx, cx, cy, size, thick, gap, color);
            case CUSTOM -> drawCustom(ctx, cx, cy, size, color, customPixels, customW, customH);
        }
    }

    private static AuroraConfig.CrosshairStyle safe(AuroraConfig.CrosshairStyle s) {
        return s != null ? s : AuroraConfig.CrosshairStyle.CROSS;
    }

    private static boolean isAimingAtEntity(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return false;

        double reach = player.entityInteractionRange();
        if (reach <= 0) reach = 3.0;

        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.x * reach, look.y * reach, look.z * reach);
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);

        Predicate<Entity> filter = e ->
                !e.isSpectator() && e.isPickable() && e != player && e instanceof LivingEntity;

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player, eye, end, searchBox, filter, reach * reach);

        return hit != null && hit.getEntity() != null;
    }

    // ===== Shape drawing =====
    //
    // Each shape is drawn assuming the matrix has already been translated
    // by (-0.5, -0.5), so a single-pixel center column at column index `cx`
    // visually straddles the screen seam.

    private static void drawDot(GuiGraphics ctx, int cx, int cy, int size, int color) {
        // Symmetric extents: size=1 Ã¢â€ â€™ [cx, cx+1), size=2 Ã¢â€ â€™ [cx-1, cx+1),
        // size=3 Ã¢â€ â€™ [cx-1, cx+2). The post-translate seam is at cx, so all
        // odd sizes are visibly centered on the seam.
        int half = size / 2;
        int x1 = cx - half;
        int y1 = cy - half;
        ctx.fill(x1, y1, x1 + size, y1 + size, color);
    }

    private static void drawCross(GuiGraphics ctx, int cx, int cy, int size, int thick, int gap, int color) {
        int halfT = thick / 2;
        int innerLeft = cx - gap;
        int innerRight = cx + gap + (thick % 2);
        int innerTop = cy - gap;
        int innerBottom = cy + gap + (thick % 2);
        int armTop = cy - halfT;
        int armBottom = armTop + thick;
        int armLeft = cx - halfT;
        int armRight = armLeft + thick;

        ctx.fill(innerLeft - size, armTop, innerLeft, armBottom, color);
        ctx.fill(innerRight, armTop, innerRight + size, armBottom, color);
        ctx.fill(armLeft, innerTop - size, armRight, innerTop, color);
        ctx.fill(armLeft, innerBottom, armRight, innerBottom + size, color);

        // With no gap the arms should form a solid plus, but for odd
        // thicknesses the four arms straddle the center without covering the
        // central hub itself (e.g. thick=1, gap=0 leaves the middle pixel
        // empty). Fill the thick×thick hub to close that hole. A positive gap
        // is an intentional opening, so only do this when gap == 0.
        if (gap == 0) {
            ctx.fill(armLeft, armTop, armRight, armBottom, color);
        }
    }

    private static void drawSquare(GuiGraphics ctx, int cx, int cy, int size, int thick, int gap, int color) {
        int x1 = cx - size;
        int y1 = cy - size;
        int x2 = cx + size + 1;
        int y2 = cy + size + 1;
        
        int gapL = cx - gap;
        int gapR = cx + gap + (thick % 2);
        int gapT = cy - gap;
        int gapB = cy + gap + (thick % 2);

        if (gap == 0) {
            ctx.fill(x1, y1, x2, y1 + thick, color);
            ctx.fill(x1, y2 - thick, x2, y2, color);
            ctx.fill(x1, y1, x1 + thick, y2, color);
            ctx.fill(x2 - thick, y1, x2, y2, color);
        } else {
            // Top edge
            if (gapL > x1) ctx.fill(x1, y1, gapL, y1 + thick, color);
            if (gapR < x2) ctx.fill(gapR, y1, x2, y1 + thick, color);
            // Bottom edge
            if (gapL > x1) ctx.fill(x1, y2 - thick, gapL, y2, color);
            if (gapR < x2) ctx.fill(gapR, y2 - thick, x2, y2, color);
            // Left edge
            if (gapT > y1) ctx.fill(x1, Math.max(y1 + thick, gapR < x2 ? y1 : y1), x1 + thick, gapT, color);
            if (gapB < y2) ctx.fill(x1, gapB, x1 + thick, Math.min(y2 - thick, gapR < x2 ? y2 : y2), color);
            // Right edge
            if (gapT > y1) ctx.fill(x2 - thick, Math.max(y1 + thick, gapR < x2 ? y1 : y1), x2, gapT, color);
            if (gapB < y2) ctx.fill(x2 - thick, gapB, x2, Math.min(y2 - thick, gapR < x2 ? y2 : y2), color);
            
            // To ensure corners are properly connected when gap doesn't completely remove them:
            if (gapT > y1) {
                ctx.fill(x1, y1, x1 + thick, y1 + thick, color);
                ctx.fill(x2 - thick, y1, x2, y1 + thick, color);
            }
            if (gapB < y2) {
                ctx.fill(x1, y2 - thick, x1 + thick, y2, color);
                ctx.fill(x2 - thick, y2 - thick, x2, y2, color);
            }
        }
    }

    private static void drawCircle(GuiGraphics ctx, int cx, int cy, int radius, int gap, int color) {
        int r = Math.max(1, radius);
        int x = r;
        int y = 0;
        int err = 1 - x;
        while (x >= y) {
            plot8(ctx, cx, cy, x, y, gap, color);
            y++;
            if (err < 0) {
                err += 2 * y + 1;
            } else {
                x--;
                err += 2 * (y - x) + 1;
            }
        }
    }

    private static void plot8(GuiGraphics ctx, int cx, int cy, int x, int y, int gap, int color) {
        if (x >= gap && y >= gap) {
            pixel(ctx, cx + x, cy + y, color);
            pixel(ctx, cx - x, cy + y, color);
            pixel(ctx, cx + x, cy - y, color);
            pixel(ctx, cx - x, cy - y, color);
            pixel(ctx, cx + y, cy + x, color);
            pixel(ctx, cx - y, cy + x, color);
            pixel(ctx, cx + y, cy - x, color);
            pixel(ctx, cx - y, cy - x, color);
        }
    }

    private static void pixel(GuiGraphics ctx, int x, int y, int color) {
        ctx.fill(x, y, x + 1, y + 1, color);
    }

    /**
     * Renders the user-painted custom grid (free-form width×height; the
     * dims come from the config fields, with a legacy perfect-square
     * fallback for old configs — see {@link com.aurora.client.util.GridDims}).
     * The grid's center cell lands on screen column {@code cx} at any
     * zoom; combined with the matrix translate, that means the center of
     * the canvas visually straddles the screen seam.
     *
     * <p>Lit cells are merged into horizontal runs before submission —
     * one {@code fill()} per run of consecutive lit cells instead of one
     * per cell (a solid 33×33 block costs 33 fills, not 1089; each fill
     * allocates a render state, so this matters at high resolutions).
     * Merged runs produce the identical pixel coverage as the per-cell
     * fills they replace.
     *
     * <p>Defensive: if the array is null or matches no valid dims, draws
     * nothing rather than crashing.
     */
    private static void drawCustom(GuiGraphics ctx, int cx, int cy, int size, int color,
                                   boolean[] pixels, int cfgW, int cfgH) {
        if (pixels == null || pixels.length == 0) return;

        com.aurora.client.util.GridDims dims =
                com.aurora.client.util.GridDims.resolve(pixels.length, cfgW, cfgH);
        if (!dims.matches(pixels)) return;
        int gridW = dims.w;
        int gridH = dims.h;

        // Scale the canvas so it occupies the same physical space regardless
        // of grid resolution: the LARGER axis spans size*3 logical pixels
        // (a size of 5 ⇒ 15-px crosshair, matching vanilla), keeping cells
        // square for non-square grids.
        float totalLogical = size * 3.0f;
        float scaleFactor = totalLogical / Math.max(gridW, gridH);

        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        ctx.pose().scale(scaleFactor, scaleFactor);
        ctx.pose().translate(-gridW / 2.0f, -gridH / 2.0f);

        for (int gy = 0; gy < gridH; gy++) {
            int rowBase = gy * gridW;
            int gx = 0;
            while (gx < gridW) {
                if (!pixels[rowBase + gx]) {
                    gx++;
                    continue;
                }
                int runStart = gx;
                while (gx < gridW && pixels[rowBase + gx]) gx++;
                // GPU matrix perfectly aligns integer vertices, preventing subpixel seams.
                ctx.fill(runStart, gy, gx, gy + 1, color);
            }
        }

        ctx.pose().popMatrix();
    }
}