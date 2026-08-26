package com.aurora.client.hud;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Shared world-space line rendering utility used by both {@link HitboxRenderer}
 * and {@link BlockOverlayRenderer}.
 *
 * <p>In Minecraft 1.21.11, Mojang introduced per-vertex line width via
 * {@link VertexConsumer#setLineWidth(float)} on the {@code lines()} render
 * type. This uses a GPU-side line expansion shader — not {@code GL_LINE_WIDTH}
 * — so the old "modern OpenGL caps line width at 1px" limitation no longer
 * applies. Thick, clean wireframe lines render correctly on all GPUs.
 *
 * <p>The rendering pattern mirrors vanilla's {@code ShapeRenderer.renderShape}:
 * for each line segment, two vertices are emitted with position, color, a
 * normalized direction normal, and the per-vertex line width. The GPU shader
 * expands each line segment to the requested pixel width perpendicular to the
 * view direction.
 *
 * <h3>Camera-relative double precision (critical fix)</h3>
 * World coordinates (e.g. x=12500.5) lose precision when cast directly to
 * {@code float}, which only has ~7 significant digits. At 10000 blocks from
 * spawn, precision drops to ~1mm; at 50000 blocks, to ~4mm. This causes
 * visible jitter, misaligned outlines, and "block outline doesn't conform
 * to block" artifacts — the classic "works near spawn, breaks far away" bug.
 *
 * <p>The fix mirrors vanilla {@code LevelEntityDirectRenderer} and
 * {@code ShapeRenderer}: the camera position is subtracted in {@code double}
 * precision <i>before</i> casting to {@code float}. The resulting float is a
 * small camera-relative offset (typically < 1000 blocks) that retains full
 * sub-millimeter precision regardless of distance from the world origin.
 *
 * <p><b>Important:</b> the {@link Matrix4f} passed in must NOT be pre-translated
 * by the camera position. Callers must pass the raw model-view matrix (with
 * identity translation in the world-space portion), and the camera position
 * separately, so all position data goes through the double-precision subtraction
 * in {@code drawLineRelative}.
 *
 * <h3>See-through mode</h3>
 * When {@code seeThrough} is true, lines are drawn to the
 * {@link RenderTypes#linesTranslucent()} buffer instead of {@link RenderTypes#lines()}.
 * Combined with disabling depth test around the flush, this gives the signature
 * Lunar/NoRisk look where the full hitbox wireframe is visible through the
 * entity model. When false, standard depth-tested lines are used so edges
 * behind geometry are properly occluded.
 */
public final class WorldLineRenderer {

    private WorldLineRenderer() {}

    /**
     * Draws a single world-space line segment using camera-relative double
     * precision. This is the core method that prevents float-precision jitter
     * at large world coordinates.
     *
     * @param buf        the line-mode VertexConsumer (from {@code RenderTypes.lines()})
     * @param m          the raw model-view matrix (NOT pre-translated by camera)
     * @param camPos     the camera position (world space)
     * @param x1 y1 z1   start point (world space)
     * @param x2 y2 z2   end point (world space)
     * @param r g b a    color components [0..1]
     * @param width      line width in pixels
     */
    public static void drawLine(VertexConsumer buf, Matrix4f m, Vec3 camPos,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                float r, float g, float b, float a,
                                float width) {
        // Normalized direction of the line segment — the GPU shader uses this
        // to expand the line perpendicular to both the direction and the view.
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) return; // degenerate
        float nx = (float) (dx / len);
        float ny = (float) (dy / len);
        float nz = (float) (dz / len);

        // Subtract camera position in double precision BEFORE casting to float.
        // This keeps the per-vertex coordinate a small camera-relative offset,
        // preserving full float precision regardless of distance from origin.
        double rx1 = x1 - camPos.x;
        double ry1 = y1 - camPos.y;
        double rz1 = z1 - camPos.z;
        double rx2 = x2 - camPos.x;
        double ry2 = y2 - camPos.y;
        double rz2 = z2 - camPos.z;

        buf.addVertex(m, (float) rx1, (float) ry1, (float) rz1)
                .setColor(r, g, b, a)
                .setNormal(nx, ny, nz)
                .setLineWidth(width);

        buf.addVertex(m, (float) rx2, (float) ry2, (float) rz2)
                .setColor(r, g, b, a)
                .setNormal(nx, ny, nz)
                .setLineWidth(width);
    }

    /**
     * Draws the 12 edges of an AABB as wireframe lines using camera-relative
     * double precision.
     *
     * @param buf     the line-mode VertexConsumer
     * @param m       the raw model-view matrix (NOT pre-translated by camera)
     * @param camPos  the camera position (world space)
     * @param box     the AABB to outline (world space)
     * @param r g b a color components [0..1]
     * @param width   line width in pixels
     */
    public static void drawBoxOutline(VertexConsumer buf, Matrix4f m, Vec3 camPos, AABB box,
                                      float r, float g, float b, float a, float width) {
        drawBoxOutline(buf, m, camPos,
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                r, g, b, a, width);
    }

    /**
     * Draws the 12 edges of a box defined by min/max corners, using camera-relative
     * double precision.
     */
    public static void drawBoxOutline(VertexConsumer buf, Matrix4f m, Vec3 camPos,
                                      double x1, double y1, double z1,
                                      double x2, double y2, double z2,
                                      float r, float g, float b, float a, float width) {
        // Bottom 4 edges
        drawLine(buf, m, camPos, x1, y1, z1, x2, y1, z1, r, g, b, a, width);
        drawLine(buf, m, camPos, x2, y1, z1, x2, y1, z2, r, g, b, a, width);
        drawLine(buf, m, camPos, x2, y1, z2, x1, y1, z2, r, g, b, a, width);
        drawLine(buf, m, camPos, x1, y1, z2, x1, y1, z1, r, g, b, a, width);

        // Top 4 edges
        drawLine(buf, m, camPos, x1, y2, z1, x2, y2, z1, r, g, b, a, width);
        drawLine(buf, m, camPos, x2, y2, z1, x2, y2, z2, r, g, b, a, width);
        drawLine(buf, m, camPos, x2, y2, z2, x1, y2, z2, r, g, b, a, width);
        drawLine(buf, m, camPos, x1, y2, z2, x1, y2, z1, r, g, b, a, width);

        // Vertical 4 edges
        drawLine(buf, m, camPos, x1, y1, z1, x1, y2, z1, r, g, b, a, width);
        drawLine(buf, m, camPos, x2, y1, z1, x2, y2, z1, r, g, b, a, width);
        drawLine(buf, m, camPos, x2, y1, z2, x2, y2, z2, r, g, b, a, width);
        drawLine(buf, m, camPos, x1, y1, z2, x1, y2, z2, r, g, b, a, width);
    }

    /**
     * Convenience: resolves the correct line VertexConsumer from a
     * MultiBufferSource. Uses {@link RenderTypes#linesTranslucent()} when
     * {@code seeThrough} is true for the see-through look, otherwise
     * {@link RenderTypes#lines()} for depth-tested wireframe.
     */
    public static VertexConsumer getLineBuffer(MultiBufferSource consumers, boolean seeThrough) {
        return consumers.getBuffer(seeThrough ? RenderTypes.linesTranslucent() : RenderTypes.lines());
    }
}