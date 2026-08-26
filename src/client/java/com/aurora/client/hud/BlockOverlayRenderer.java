package com.aurora.client.hud;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.BlockOverlayFeature;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

/**
 * Renders the translucent face fill AND optionally the outline over the
 * targeted block. Hooked via Fabric's BEFORE_BLOCK_OUTLINE event.
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

    public boolean onBeforeBlockOutline(WorldRenderContext ctx, BlockOutlineRenderState bors) {
        AuroraConfig cfg = AuroraConfig.get();
        boolean drawFill    = BlockOverlayFeature.isFillVisible();
        boolean drawOutline = BlockOverlayFeature.isOutlineVisible();
        if (!drawFill && !drawOutline) return true;

        if (bors == null) return true;
        VoxelShape shape = bors.shape();
        if (shape == null || shape.isEmpty()) return true;
        BlockPos pos = bors.pos();
        if (pos == null) return true;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return true;

        MultiBufferSource consumers = ctx.consumers();
        PoseStack matrices = ctx.matrices();
        Camera cam = mc.gameRenderer.getMainCamera();
        if (consumers == null || matrices == null || cam == null) return true;

        Vec3 camPos = cam.position();

        // ---- Outline (drawn first via lines() render type) ----
        if (drawOutline) {
            int outColor = BlockOverlayFeature.currentOutlineColor();
            float oa = ((outColor >>> 24) & 0xFF) / 255f;
            if (oa > 0f) {
                float or = ((outColor >> 16) & 0xFF) / 255f;
                float og = ((outColor >> 8)  & 0xFF) / 255f;
                float ob = ( outColor        & 0xFF) / 255f;

                float widthPx = (float) Math.max(1.0, cfg.blockOverlayLineWidth);
                boolean seeThrough = cfg.blockOverlaySeeThrough;

                // Do NOT translate the pose stack by the camera. WorldLineRenderer
                // subtracts the camera position in double precision per-vertex,
                // preventing float-precision jitter at large world coordinates.
                matrices.pushPose();
                Matrix4f lineModel = matrices.last().pose();

                VertexConsumer lineBuf = WorldLineRenderer.getLineBuffer(consumers, seeThrough);

                double blockX = pos.getX();
                double blockY = pos.getY();
                double blockZ = pos.getZ();

                shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
                    double x1 = blockX + minX - EPSILON;
                    double y1 = blockY + minY - EPSILON;
                    double z1 = blockZ + minZ - EPSILON;
                    double x2 = blockX + maxX + EPSILON;
                    double y2 = blockY + maxY + EPSILON;
                    double z2 = blockZ + maxZ + EPSILON;
                    WorldLineRenderer.drawBoxOutline(lineBuf, lineModel, camPos,
                            x1, y1, z1, x2, y2, z2, or, og, ob, oa, widthPx);
                });

                matrices.popPose();
            }
        }

        // ---- Fill (drawn via debugQuads in block-local space) ----
        if (drawFill) {
            int fillColor = BlockOverlayFeature.currentFillColor();
            float fa = ((fillColor >>> 24) & 0xFF) / 255f;
            if (fa > 0f) {
                float fr = ((fillColor >> 16) & 0xFF) / 255f;
                float fg = ((fillColor >> 8)  & 0xFF) / 255f;
                float fb = ( fillColor        & 0xFF) / 255f;

                VertexConsumer quadBuf = consumers.getBuffer(
                        net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads());

                // Do NOT translate the pose stack by the camera. Instead, we
                // compute camera-relative vertex positions in double precision
                // and pass them to drawFilledBox, preventing precision loss.
                matrices.pushPose();
                Matrix4f fillModel = matrices.last().pose();

                double blockX = pos.getX();
                double blockY = pos.getY();
                double blockZ = pos.getZ();

                shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
                    // Camera-relative double precision coordinates for each vertex.
                    // Subtracting camPos here (in double) before casting to float
                    // prevents the "outline doesn't conform to block" jitter at
                    // large world coordinates.
                    double x1 = blockX + minX - camPos.x - EPSILON;
                    double y1 = blockY + minY - camPos.y - EPSILON;
                    double z1 = blockZ + minZ - camPos.z - EPSILON;
                    double x2 = blockX + maxX - camPos.x + EPSILON;
                    double y2 = blockY + maxY - camPos.y + EPSILON;
                    double z2 = blockZ + maxZ - camPos.z + EPSILON;
                    drawFilledBox(quadBuf, fillModel,
                            (float) x1, (float) y1, (float) z1,
                            (float) x2, (float) y2, (float) z2,
                            fr, fg, fb, fa);
                });

                matrices.popPose();
            }
        }

        return true;
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