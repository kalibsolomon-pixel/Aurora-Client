package com.aurora.client.hud;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponents;
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
    private static final Identifier ATTACK_FULL = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_full");
    private static final Identifier ATTACK_BACKGROUND = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_background");
    private static final Identifier ATTACK_PROGRESS = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_progress");

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
        if (cfg.crosshairAttackIndicatorEnabled && client.player != null && !client.player.isSpectator()) {
            var layout = CrosshairAttackIndicator.layout(cfg.crosshairAttackIndicatorSize, style, size, thick, gap,
                    useIndicator ? cfg.crosshairIndicatorCustomWidth : cfg.crosshairCustomWidth,
                    useIndicator ? cfg.crosshairIndicatorCustomHeight : cfg.crosshairCustomHeight);
            drawAttackIndicator(ctx, client, cx, cy, layout);
        }
    }

    private static void drawAttackIndicator(GuiGraphics ctx, Minecraft client, int cx, int cy,
                                            CrosshairAttackIndicator.Layout layout) {
        LocalPlayer player = client.player;
        float cooldown = player.getAttackStrengthScale(0.0f);
        boolean ready = cooldown >= 1.0f && player.getCurrentItemAttackStrengthDelay() > 5.0f
                && client.crosshairPickEntity instanceof LivingEntity target && target.isAlive();
        var attackRange = player.getActiveItem().get(DataComponents.ATTACK_RANGE);
        if (ready && attackRange != null) {
            ready = client.hitResult != null && attackRange.isInRange(player, client.hitResult.getLocation());
        }
        if (!ready && cooldown >= 1.0f) return;

        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy + layout.top());
        ctx.pose().scale(layout.scale(), layout.scale());
        if (ready) {
            ctx.blitSprite(RenderPipelines.CROSSHAIR, ATTACK_FULL, -8, 0, 16, 16);
        } else {
            ctx.blitSprite(RenderPipelines.CROSSHAIR, ATTACK_BACKGROUND, -8, 0, 16, 4);
            int progress = Math.clamp((int) (cooldown * 17), 0, 16);
            if (progress > 0) ctx.blitSprite(RenderPipelines.CROSSHAIR, ATTACK_PROGRESS,
                    16, 4, 0, 0, -8, 0, progress, 4);
        }
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
            case DOT, CIRCLE, SQUARE -> drawRaster(ctx, safe(style), cx, cy, size, thick, gap, color);
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

    private record RasterKey(AuroraConfig.CrosshairStyle style, int size, int thickness, int gap, double scale) {}
    private static final java.util.Map<RasterKey, java.util.List<CrosshairRaster.Run>> RASTERS =
            new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override protected boolean removeEldestEntry(java.util.Map.Entry<RasterKey, java.util.List<CrosshairRaster.Run>> entry) {
                    return size() > 16;
                }
            };

    private static void drawRaster(GuiGraphics ctx, AuroraConfig.CrosshairStyle style,
                                   int cx, int cy, int size, int thickness, int gap, int color) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        var key = new RasterKey(style, size, thickness, gap, scale);
        var runs = RASTERS.computeIfAbsent(key,
                k -> CrosshairRaster.raster(k.style(), k.size(), k.thickness(), k.gap(), k.scale()));
        ctx.pose().pushMatrix();
        // Undo the caller's half-pixel bias for symmetric even device-pixel bounds.
        ctx.pose().translate(cx + 0.5f, cy + 0.5f);
        ctx.pose().scale((float) (1.0 / scale), (float) (1.0 / scale));
        for (var run : runs) {
            int alpha = ((color >>> 24) * run.alpha() + 127) / 255;
            if (alpha > 0) ctx.fill(run.x(), run.y(), run.x() + run.width(), run.y() + 1,
                    (alpha << 24) | (color & 0x00ffffff));
        }
        ctx.pose().popMatrix();
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
