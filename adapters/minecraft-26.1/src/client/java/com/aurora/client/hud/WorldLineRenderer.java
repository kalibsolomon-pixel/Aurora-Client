package com.aurora.client.hud;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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
        drawLine(buf, m, camPos, x1, y1, z1, x2, y2, z2, r, g, b, a, width, false);
    }

    /**
     * As above; with {@code sdf} the buffer must come from
     * {@link #getLineBuffer(MultiBufferSource, boolean)} with SDF on, and
     * each end also gets a round cap so box corners join cleanly.
     */
    public static void drawLine(VertexConsumer buf, Matrix4f m, Vec3 camPos,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                float r, float g, float b, float a,
                                float width, boolean sdf) {
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

        if (sdf) width = Math.min(width, SDF_ROLE - 1f);
        // Each end is transformed once into a reused vector (addVertex(Matrix4f, ...)
        // allocates per call) and shared by the segment and its cap.
        Vector3f p1 = m.transformPosition((float) rx1, (float) ry1, (float) rz1, END_A);
        Vector3f p2 = m.transformPosition((float) rx2, (float) ry2, (float) rz2, END_B);
        emit(buf, p1, r, g, b, a, nx, ny, nz, width);
        emit(buf, p2, r, g, b, a, nx, ny, nz, width);
        if (sdf) {
            // Zero-length segments at each end; the shader pushes the second
            // vertex outward (role 1 back, role 2 forward) into a half disc.
            emit(buf, p1, r, g, b, a, nx, ny, nz, width);
            emit(buf, p1, r, g, b, a, nx, ny, nz, width + SDF_ROLE);
            emit(buf, p2, r, g, b, a, nx, ny, nz, width);
            emit(buf, p2, r, g, b, a, nx, ny, nz, width + 2 * SDF_ROLE);
        }
    }

    /** Blocks beyond which SDF box corners are drawn without round caps. */
    private static final double CAP_DISTANCE = 16.0;

    /** Role step packed above the width in the SDF shader's LineWidth attribute. */
    private static final float SDF_ROLE = 1024f;

    /** Reused transformed positions; world lines are drawn on the render thread only. */
    private static final Vector3f END_A = new Vector3f(), END_B = new Vector3f();
    private static final Vector3f[] CORNERS = new Vector3f[8];
    static {
        for (int i = 0; i < CORNERS.length; i++) CORNERS[i] = new Vector3f();
    }

    /** One vertex at an already transformed position. */
    private static void emit(VertexConsumer buf, Vector3f p, float r, float g, float b, float a,
                             float nx, float ny, float nz, float width) {
        buf.addVertex(p.x, p.y, p.z)
                .setColor(r, g, b, a)
                .setNormal(nx, ny, nz)
                .setLineWidth(width);
    }

    /**
     * An SDF box: each corner is transformed once and closed with one full round cap (the
     * shader's back and forward half discs together), instead of a half cap per edge end.
     * 40 vertices and 8 transforms per box rather than 72 of each, with the same look:
     * the edges' own caps at a corner already filled the same disc.
     */
    private static void drawSdfBox(VertexConsumer buf, Matrix4f m, Vec3 camPos,
                                   double x1, double y1, double z1,
                                   double x2, double y2, double z2,
                                   float r, float g, float b, float a, float width) {
        width = Math.min(width, SDF_ROLE - 1f);
        for (int i = 0; i < 8; i++) {
            m.transformPosition((float) (((i & 1) == 0 ? x1 : x2) - camPos.x),
                    (float) (((i & 2) == 0 ? y1 : y2) - camPos.y),
                    (float) (((i & 4) == 0 ? z1 : z2) - camPos.z), CORNERS[i]);
        }
        // Edges along each axis: corner i to the corner differing in that axis' bit.
        float sx = Math.signum((float) (x2 - x1)), sy = Math.signum((float) (y2 - y1)), sz = Math.signum((float) (z2 - z1));
        for (int i = 0; i < 8; i++) {
            if ((i & 1) == 0 && sx != 0) {
                emit(buf, CORNERS[i], r, g, b, a, sx, 0, 0, width);
                emit(buf, CORNERS[i | 1], r, g, b, a, sx, 0, 0, width);
            }
            if ((i & 2) == 0 && sy != 0) {
                emit(buf, CORNERS[i], r, g, b, a, 0, sy, 0, width);
                emit(buf, CORNERS[i | 2], r, g, b, a, 0, sy, 0, width);
            }
            if ((i & 4) == 0 && sz != 0) {
                emit(buf, CORNERS[i], r, g, b, a, 0, 0, sz, width);
                emit(buf, CORNERS[i | 4], r, g, b, a, 0, 0, sz, width);
            }
        }
        // Past CAP_DISTANCE a corner's round cap is under a pixel wide; skip its 32 vertices.
        double mx = (x1 + x2) * 0.5 - camPos.x, my = (y1 + y2) * 0.5 - camPos.y, mz = (z1 + z2) * 0.5 - camPos.z;
        if (mx * mx + my * my + mz * mz > CAP_DISTANCE * CAP_DISTANCE) return;
        // A degenerate box has no edges to cap.
        float cx = sx != 0 ? sx : 0, cy = sx != 0 ? 0 : (sy != 0 ? sy : 0), cz = (sx == 0 && sy == 0) ? sz : 0;
        if (cx == 0 && cy == 0 && cz == 0) return;
        for (Vector3f c : CORNERS) {
            emit(buf, c, r, g, b, a, cx, cy, cz, width);
            emit(buf, c, r, g, b, a, cx, cy, cz, width + SDF_ROLE);
            emit(buf, c, r, g, b, a, cx, cy, cz, width);
            emit(buf, c, r, g, b, a, cx, cy, cz, width + 2 * SDF_ROLE);
        }
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
        drawBoxOutline(buf, m, camPos, box, r, g, b, a, width, false);
    }

    /** As above, optionally with SDF lines (see {@link #drawLine}). */
    public static void drawBoxOutline(VertexConsumer buf, Matrix4f m, Vec3 camPos, AABB box,
                                      float r, float g, float b, float a, float width, boolean sdf) {
        drawBoxOutline(buf, m, camPos,
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                r, g, b, a, width, sdf);
    }

    /**
     * Draws the 12 edges of a box defined by min/max corners, using camera-relative
     * double precision.
     */
    public static void drawBoxOutline(VertexConsumer buf, Matrix4f m, Vec3 camPos,
                                      double x1, double y1, double z1,
                                      double x2, double y2, double z2,
                                      float r, float g, float b, float a, float width) {
        drawBoxOutline(buf, m, camPos, x1, y1, z1, x2, y2, z2, r, g, b, a, width, false);
    }

    /** As above, optionally with SDF lines (see {@link #drawLine}). */
    public static void drawBoxOutline(VertexConsumer buf, Matrix4f m, Vec3 camPos,
                                      double x1, double y1, double z1,
                                      double x2, double y2, double z2,
                                      float r, float g, float b, float a, float width, boolean sdf) {
        if (sdf) {
            drawSdfBox(buf, m, camPos, x1, y1, z1, x2, y2, z2, r, g, b, a, width);
            return;
        }
        // Bottom 4 edges
        drawLine(buf, m, camPos, x1, y1, z1, x2, y1, z1, r, g, b, a, width, sdf);
        drawLine(buf, m, camPos, x2, y1, z1, x2, y1, z2, r, g, b, a, width, sdf);
        drawLine(buf, m, camPos, x2, y1, z2, x1, y1, z2, r, g, b, a, width, sdf);
        drawLine(buf, m, camPos, x1, y1, z2, x1, y1, z1, r, g, b, a, width, sdf);

        // Top 4 edges
        drawLine(buf, m, camPos, x1, y2, z1, x2, y2, z1, r, g, b, a, width, sdf);
        drawLine(buf, m, camPos, x2, y2, z1, x2, y2, z2, r, g, b, a, width, sdf);
        drawLine(buf, m, camPos, x2, y2, z2, x1, y2, z2, r, g, b, a, width, sdf);
        drawLine(buf, m, camPos, x1, y2, z2, x1, y2, z1, r, g, b, a, width, sdf);

        // Vertical 4 edges
        drawLine(buf, m, camPos, x1, y1, z1, x1, y2, z1, r, g, b, a, width, sdf);
        drawLine(buf, m, camPos, x2, y1, z1, x2, y2, z1, r, g, b, a, width, sdf);
        drawLine(buf, m, camPos, x2, y1, z2, x2, y2, z2, r, g, b, a, width, sdf);
        drawLine(buf, m, camPos, x1, y1, z2, x1, y2, z2, r, g, b, a, width, sdf);
    }

    /**
     * Convenience: resolves the correct line VertexConsumer from a
     * MultiBufferSource. Uses {@link RenderTypes#linesTranslucent()} — the
     * see-through look (the see-through option itself was removed with the
     * 2026-10-30 owner ruling; every caller keeps the former default).
     */
    public static VertexConsumer getLineBuffer(MultiBufferSource consumers) {
        return getLineBuffer(consumers, false);
    }

    /**
     * The translucent line buffer, drawn by Aurora's SDF line shader when
     * {@code sdf} is set: one pixel of anti-aliased edge and round caps
     * instead of vanilla's hard-edged quads. Callers pass
     * {@code option && sdfAvailable()}, and the same flag to {@link #drawLine}.
     */
    public static VertexConsumer getLineBuffer(MultiBufferSource consumers, boolean sdf) {
        var type = sdf ? SdfLineRenderTypes.linesOrNull() : null;
        return consumers.getBuffer(type != null ? type : RenderTypes.linesTranslucent());
    }

    /** False once the SDF line shader has failed this session. */
    public static boolean sdfAvailable() {
        return SdfLineRenderTypes.linesOrNull() != null;
    }

    /** Depth-tested lines (occluded by geometry) — the waypoint highlight's
     *  established look; unchanged by the see-through removal. */
    public static VertexConsumer getOccludedLineBuffer(MultiBufferSource consumers) {
        return consumers.getBuffer(RenderTypes.lines());
    }
}
