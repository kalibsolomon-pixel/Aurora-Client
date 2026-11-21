package com.aurora.client.hud;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.BlockOverlayFeature;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

/**
 * Renders the translucent face fill AND optionally the outline over the
 * targeted block. The outline event selects the state; drawing runs after
 * translucent features, once 26.2's terrain and feature passes are present.
 *
 * <p><b>Outline behavior:</b> the outline is drawn as clean wireframe lines
 * using {@link net.minecraft.client.renderer.rendertype.RenderTypes#lines()}
 * with per-vertex line width. This uses the GPU-side line expansion shader
 * (new in MC 1.21.11), which reliably renders thick lines on all GPUs — no
 * more thick cuboid bars or 1px GL line width caps.
 *
 * <p>Vanilla's own wireframe outline is suppressed by {@code VertexRenderingMixin}
 * (rendered transparent) so only Aurora's outline is visible.
 *
 * <p><b>See-through mode.</b> When {@code AuroraConfig#blockOverlaySeeThrough}
 * is enabled, the outline is drawn via
 * {@link net.minecraft.client.renderer.rendertype.RenderTypes#linesTranslucent()},
 * which renders without depth testing — the outline shows through the block
 * itself for the Lunar/NoRisk style look.
 *
 * <p>The fill uses {@code RenderTypes.debugQuads()} — no diffuse shading, so
 * colors render exactly as specified.
 *
 * <p><b>Camera-relative double precision (critical fix).</b> Camera position is
 * subtracted in {@code double} precision before casting to {@code float} for
 * both outline and fill geometry, preventing float-precision jitter at large
 * world coordinates. The PoseStack is NOT pre-translated by the camera. This
 * mirrors how vanilla {@code ShapeRenderer} handles block outlines.
 *
 * <p><b>Unified epsilon.</b> Outline and fill use the same epsilon so the
 * outline bounds the fill perfectly at all corners without gaps or overrun.
 */
public final class BlockOverlayRenderer {

    /**
     * Outward expansion applied to both the fill and outline geometry on every
     * axis, in blocks. A single shared value ensures the outline bounds the
     * fill perfectly at all corners. Kept extremely small to avoid visual
     * overrun while still preventing z-fighting with the block surface.
     */
    private static final float EPSILON = 0.001f;

    private BlockOutlineRenderState pendingOutline;

    public boolean onBeforeBlockOutline(LevelRenderContext ctx, BlockOutlineRenderState bors) {
        // In 26.2 this event collects submissions, before terrain is drawn.
        pendingOutline = bors;
        return true;
    }

    public void render(LevelRenderContext ctx) {
        BlockOutlineRenderState bors = pendingOutline;
        pendingOutline = null; // no stale highlight if vanilla skips its outline next frame
        AuroraConfig cfg = AuroraConfig.get();
        boolean drawFill    = BlockOverlayFeature.isFillVisible();
        boolean drawOutline = BlockOverlayFeature.isOutlineVisible();
        if (!drawFill && !drawOutline) return;

        if (bors == null) return;
        VoxelShape shape = bors.shape();
        if (shape == null || shape.isEmpty()) return;
        BlockPos pos = bors.pos();
        if (pos == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        int outColor = drawOutline ? BlockOverlayFeature.currentOutlineColor() : 0;
        float oa = drawOutline ? ((outColor >>> 24) & 0xFF) / 255f : 0f;
        int fillColor = drawFill ? BlockOverlayFeature.currentFillColor() : 0;
        float fa = drawFill ? ((fillColor >>> 24) & 0xFF) / 255f : 0f;
        if (oa <= 0f && fa <= 0f) return;

        PoseStack matrices = ctx.poseStack();
        Camera cam = mc.gameRenderer.mainCamera();
        if (matrices == null || cam == null) return;
        Matrix4f model = matrices.last().pose();
        Vec3 camPos = cam.position();
        double blockX = pos.getX(), blockY = pos.getY(), blockZ = pos.getZ();

        try (WorldGeometryBuffers consumers = WorldGeometryBuffers.begin()) {
            // ---- Outline (drawn first via lines() render type) ----
            if (oa > 0f) {
                float or = ((outColor >> 16) & 0xFF) / 255f;
                float og = ((outColor >> 8)  & 0xFF) / 255f;
                float ob = ( outColor        & 0xFF) / 255f;
                float widthPx = (float) Math.max(1.0, cfg.blockOverlayLineWidth);

                boolean sdf = WorldLineRenderer.sdfAvailable();
                VertexConsumer lineBuf = WorldLineRenderer.getLineBuffer(consumers, sdf);

                shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                        WorldLineRenderer.drawBoxOutline(lineBuf, model, camPos,
                                blockX + minX - EPSILON, blockY + minY - EPSILON, blockZ + minZ - EPSILON,
                                blockX + maxX + EPSILON, blockY + maxY + EPSILON, blockZ + maxZ + EPSILON,
                                or, og, ob, oa, widthPx, sdf));
            }

            // ---- Fill (drawn via debugQuads in block-local space) ----
            if (fa > 0f) {
                float fr = ((fillColor >> 16) & 0xFF) / 255f;
                float fg = ((fillColor >> 8)  & 0xFF) / 255f;
                float fb = ( fillColor        & 0xFF) / 255f;
                VertexConsumer quadBuf = consumers.getBuffer(
                        net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads());

                shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                        drawFilledBox(quadBuf, model,
                                (float) (blockX + minX - camPos.x - EPSILON),
                                (float) (blockY + minY - camPos.y - EPSILON),
                                (float) (blockZ + minZ - camPos.z - EPSILON),
                                (float) (blockX + maxX - camPos.x + EPSILON),
                                (float) (blockY + maxY - camPos.y + EPSILON),
                                (float) (blockZ + maxZ - camPos.z + EPSILON),
                                fr, fg, fb, fa));
            }
        }
    }

    private static void drawFilledBox(VertexConsumer buf, Matrix4f m,
                                      float x1, float y1, float z1,
                                      float x2, float y2, float z2,
                                      float r, float g, float b, float a) {
        quad(buf, m, x1, y1, z2, x2, y1, z2, x2, y1, z1, x1, y1, z1, r, g, b, a);
        quad(buf, m, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2, r, g, b, a);
        quad(buf, m, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1, r, g, b, a);
        quad(buf, m, x2, y1, z2, x1, y1, z2, x1, y2, z2, x2, y2, z2, r, g, b, a);
        quad(buf, m, x1, y1, z2, x1, y1, z1, x1, y2, z1, x1, y2, z2, r, g, b, a);
        quad(buf, m, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
    }

    private static void quad(VertexConsumer buf, Matrix4f m,
                             float ax, float ay, float az,
                             float bx, float by, float bz,
                             float cx, float cy, float cz,
                             float dx, float dy, float dz,
                             float r, float g, float b, float a) {
        buf.addVertex(m, ax, ay, az).setColor(r, g, b, a);
        buf.addVertex(m, bx, by, bz).setColor(r, g, b, a);
        buf.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
        buf.addVertex(m, dx, dy, dz).setColor(r, g, b, a);
    }
}
