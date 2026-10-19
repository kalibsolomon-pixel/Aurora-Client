package com.aurora.client.hud;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.AuroraConfig.Waypoint;
import com.aurora.client.feature.impl.WaypointFeature;
import com.aurora.client.worldmap.MapMarkers;
import com.aurora.client.ui.util.MaterialIconRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Beacon-style waypoint visualization with two render passes:
 *
 * <ul>
 *   <li>{@link #renderWorld} — hooks {@code WorldRenderEvents.AFTER_ENTITIES}
 *       and submits 3D geometry: the beam column and/or block highlight
 *       cubes. Pure vertex output via {@code RenderTypes.debugQuads()}.</li>
 *   <li>{@link #renderHud} — hooks {@code HudRenderCallback} and draws the
 *       screen-space name + distance labels using {@link GuiGraphics}.
 *       Uses {@link net.minecraft.client.renderer.GameRenderer#projectPointToScreen}
 *       (the same projector vanilla 1.21+ uses for tracked waypoints) so
 *       labels stay perfectly aligned with the world point. Drawing on
 *       the HUD layer means the labels are <em>always</em> visible when
 *       the feature is enabled — no depth test, no buffer-flush quirks,
 *       no back-face culling.</li>
 * </ul>
 *
 * <p>Waypoints whose stored dimension differs from the player's current
 * dimension are silently skipped in both passes.
 */
public final class WaypointRenderer {
    private final com.aurora.client.worldmap.MarkerLabelLayout labelLayout = new com.aurora.client.worldmap.MarkerLabelLayout();

    /** Vertical extent of the beam in 1.21 overworld terms (-64 .. 320). */
    private static final float BEAM_BOTTOM = -64f;
    private static final float BEAM_TOP    = 320f;

    /** Padding from screen edges when a waypoint is off-screen. */
    private static final int LABEL_EDGE_MARGIN = 24;

    public void renderWorld(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;
        if (mc.screen != null) return;

        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.waypointsEnabled) return;

        WaypointFeature feat = WaypointFeature.get();
        if (feat == null) return;

        List<Waypoint> list = feat.nearbyWaypoints();
        if (list.isEmpty()) return;

        Camera cam = mc.gameRenderer.getMainCamera();
        if (cam == null) return;
        PoseStack matrices = ctx.matrices();
        if (matrices == null) return;
        MultiBufferSource consumers = ctx.consumers();
        if (consumers == null) return;

        String curDim = mc.level.dimension().identifier().toString();
        Vec3 camPos = cam.position();
        Font font = mc.font;

        AuroraConfig.WaypointDisplay mode = cfg.waypointDisplay == null
                ? AuroraConfig.WaypointDisplay.BEAM
                : cfg.waypointDisplay;
        boolean drawBeam  = mode == AuroraConfig.WaypointDisplay.BEAM
                         || mode == AuroraConfig.WaypointDisplay.BOTH;
        boolean drawBlock = mode == AuroraConfig.WaypointDisplay.BLOCK
                         || mode == AuroraConfig.WaypointDisplay.BOTH;

        int remaining = com.aurora.client.worldmap.MapTransform.bounded(cfg.waypointWorldMarkerLimit, 1, 64);
        for (Waypoint w : list) {
            if (w == null) continue;
            if (w.dimension != null && !w.dimension.equals(curDim)) continue;
            if (remaining-- <= 0) break;

            int a = (w.color >>> 24) & 0xFF;
            int r = (w.color >> 16)  & 0xFF;
            int g = (w.color >> 8)   & 0xFF;
            int b =  w.color         & 0xFF;
            if (a == 0) a = 0xFF;

            double beamDistance = Math.hypot(w.x + .5 - camPos.x, w.z + .5 - camPos.z);
            float beamOpacity = beamOpacity(beamDistance);
            if (drawBeam && beamOpacity > 0) {
                drawBeam(matrices, consumers, w.x + 0.5, w.z + 0.5, camPos,
                        (float) com.aurora.client.worldmap.MapTransform.bounded(cfg.waypointBeamWidth, .1, 1),
                        r / 255f, g / 255f, b / 255f, (a / 255f) * beamOpacity);
            }
            if (drawBlock) {
                drawBlockHighlight(matrices, consumers, w, camPos,
                        com.aurora.client.worldmap.MapTransform.bounded(cfg.waypointBlockRadius, 0, 16),
                        r / 255f, g / 255f, b / 255f, a / 255f);
            }
        }
    }

    /** A camera inside a double-sided beam otherwise sees an opaque full-screen wall. */
    public static float beamOpacity(double horizontalDistance) {
        return (float) com.aurora.client.worldmap.MapTransform.bounded((horizontalDistance - 2) / 6, 0, 1);
    }

    /**
     * HUD-phase label pass. Iterates every active waypoint, projects its
     * anchor point through {@link net.minecraft.client.renderer.GameRenderer#projectPointToScreen},
     * and draws the name + distance with {@link GuiGraphics}. Off-screen
     * waypoints are clamped to the screen edge with a small directional
     * arrow so users always know where they are.
     */
    public void renderHud(GuiGraphics g, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;
        if (mc.screen != null) return;
        if (mc.options != null && mc.options.hideGui) return;

        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.waypointsEnabled || !cfg.waypointShowLabel) return;

        WaypointFeature feat = WaypointFeature.get();
        if (feat == null) return;
        List<Waypoint> list = feat.nearbyWaypoints();
        if (list.isEmpty()) return;

        Camera cam = mc.gameRenderer.getMainCamera();
        if (cam == null) return;

        String curDim = mc.level.dimension().identifier().toString();
        Vec3 camPos = cam.position();
        Font font = mc.font;
        int sw = g.guiWidth();
        int sh = g.guiHeight();

        labelLayout.reset();
        for (Waypoint w : list) {
            if (w == null) continue;
            if (w.dimension != null && !w.dimension.equals(curDim)) continue;

            // Anchor at the *base* of the waypoint (just above the
            // marker block) so the label visually sits next to the spot
            // the player is travelling toward, regardless of whether a
            // tall beam is drawn over it. Lifting by a few blocks keeps
            // the label from being clipped by the block itself when
            // viewed near-eye-level.
            Vec3 anchor = new Vec3(w.x + 0.5, w.y + 2.2, w.z + 0.5);

            // Vanilla {@code GameRenderer.projectPointToScreen} returns
            // a Vec3 in <em>NDC</em> space ({@code x, y \u2208 [-1, 1]} for
            // points inside the frustum), <strong>not</strong> pixels.
            // The bytecode is essentially:
            // {@code (proj * conj(camRotation)).transformProject(point - camPos)}.
            // We do the NDC \u2192 GUI pixel conversion below.
            //
            // Behind-camera detection cannot rely on the projected Z (it
            // wraps unhelpfully when {@code w < 0}); instead use a dot
            // product of the camera's forward vector with the world-
            // space displacement, which is unambiguous.
            Vec3 projected;
            try {
                projected = mc.gameRenderer.projectPointToScreen(anchor);
            } catch (Throwable t) {
                continue;
            }
            if (projected == null) continue;
            if (Double.isNaN(projected.x) || Double.isNaN(projected.y)) continue;

            org.joml.Vector3fc fwd = cam.forwardVector();
            double rdx = anchor.x - camPos.x;
            double rdy = anchor.y - camPos.y;
            double rdz = anchor.z - camPos.z;
            double facing = fwd.x() * rdx + fwd.y() * rdy + fwd.z() * rdz;
            boolean inFront = facing > 0;

            // NDC \u2192 GUI pixels. GUI Y axis points down (origin
            // top-left), so the Y component is flipped relative to the
            // OpenGL/NDC convention.
            float screenX = (float) ((projected.x * 0.5 + 0.5) * sw);
            float screenY = (float) ((1.0 - (projected.y * 0.5 + 0.5)) * sh);

            // Slant distance for the readout, using the actual marker
            // location (not the lifted anchor) so the number reflects
            // what the player is travelling toward.
            double dx = (w.x + 0.5) - camPos.x;
            double dy = (w.y + 0.5) - camPos.y;
            double dz = (w.z + 0.5) - camPos.z;
            double slant = Math.sqrt(dx * dx + dy * dy + dz * dz);

            String name = (w.name == null || w.name.isEmpty()) ? "Waypoint" : w.name;
            double scale = com.aurora.client.worldmap.MapTransform.bounded(cfg.waypointLabelScale, .5, 3);
            name = com.aurora.client.ui.util.AuroraFontRenderer.ellipsize(font, name,
                    (int) Math.min(180, (sw - 16) / scale - 8), 1);
            String dist = (int) Math.round(slant) + "m";

            // Off-screen / behind-camera handling. When the waypoint
            // is behind the camera the NDC math wraps and produces
            // bogus screen coordinates, so route the indicator to the
            // appropriate vertical screen edge using the dot product
            // against the camera's left vector — point on the player's
            // left ⇒ indicator pinned to left edge, and vice versa.
            boolean clamped = false;
            if (!inFront) {
                org.joml.Vector3fc left = cam.leftVector();
                double leftDot = left.x() * rdx + left.y() * rdy + left.z() * rdz;
                screenX = leftDot > 0 ? LABEL_EDGE_MARGIN : sw - LABEL_EDGE_MARGIN;
                screenY = sh / 2f;
                clamped = true;
            }
            if (screenX < LABEL_EDGE_MARGIN) { screenX = LABEL_EDGE_MARGIN; clamped = true; }
            if (screenX > sw - LABEL_EDGE_MARGIN) { screenX = sw - LABEL_EDGE_MARGIN; clamped = true; }
            if (screenY < LABEL_EDGE_MARGIN) { screenY = LABEL_EDGE_MARGIN; clamped = true; }
            if (screenY > sh - LABEL_EDGE_MARGIN) { screenY = sh - LABEL_EDGE_MARGIN; clamped = true; }

            float labelWidth = (float) ((Math.max(font.width(name) + 12, font.width(dist)) + 8) * scale);
            float labelHeight = (float) ((font.lineHeight * 2 + 6) * scale);
            if (!labelLayout.place(screenX, screenY, labelWidth, labelHeight, sw, sh)) continue;
            drawHudLabel(g, font, labelLayout.centerX(), labelLayout.bottomY(), name, dist,
                    w, w.id.equals(feat.navigationId()), scale, clamped);
        }
    }

    /**
     * Draws a centered, optionally scaled label with a translucent
     * background plate. Color goes to the name line; distance is dimmed
     * for hierarchy. When {@code clamped} is true a small chevron is
     * drawn under the label to signal "this waypoint is off-screen".
     */
    private static void drawHudLabel(GuiGraphics g, Font font,
                                      float screenX, float screenY,
                                      String name, String dist,
                                      Waypoint waypoint, boolean selected, double scaleFactor,
                                      boolean clamped) {
        float scale = (float) Math.max(0.5, scaleFactor);
        int nameW = font.width(name);
        int distW = font.width(dist);
        int boxW  = Math.max(nameW + 12, distW) + 8;
        int boxH  = font.lineHeight * 2 + 6;

        g.pose().pushMatrix();
        g.pose().translate(screenX, screenY);
        g.pose().scale(scale, scale);
        g.pose().translate(-boxW / 2f, -boxH);

        MapMarkers.labelPlate(g, boxW * .5f, boxH * .5f, boxW, boxH, waypoint.color);
        MapMarkers.waypoint(g, 8, 8, waypoint, selected);
        int textColor = 0xFF000000 | (waypoint.color & 0xFFFFFF);
        MapMarkers.text(g, font, name, (boxW + 12 - nameW) * .5f, 3, textColor, false);
        MapMarkers.text(g, font, dist, (boxW - distW) * .5f, 3 + font.lineHeight, 0xFFCCCCCC, false);
        if (clamped) {
            float gui = Minecraft.getInstance().getWindow().getGuiScale();
            float em = 8 * MapMarkers.density(g) / gui;
            float cx = MapMarkers.pixelX(g, boxW * .5f, boxH + 4) / gui;
            float cy = MapMarkers.pixelY(g, boxW * .5f, boxH + 4) / gui;
            g.pose().pushMatrix(); g.pose().identity();
            MaterialIconRenderer.drawIcon(g, font, "\uE5CF", cx, cy, em, textColor);
            g.pose().popMatrix();
        }

        g.pose().popMatrix();
    }

    // ===== Beam =====

    /**
     * Submits the four side quads of a vertical column centred on
     * {@code (centerX, centerZ)} with side length {@code width}, double-
     * wound so the beam is visible from inside the column too.
     */
    private static void drawBeam(PoseStack matrices, MultiBufferSource consumers,
                                  double centerX, double centerZ, Vec3 camPos,
                                  float width,
                                  float r, float g, float b, float a) {
        matrices.pushPose();
        matrices.translate(centerX - camPos.x, -camPos.y, centerZ - camPos.z);
        Matrix4f m = matrices.last().pose();

        float half = Math.max(0.02f, width * 0.5f);
        float x1 = -half, x2 = half;
        float z1 = -half, z2 = half;
        float y1 = BEAM_BOTTOM;
        float y2 = BEAM_TOP;

        VertexConsumer buf = consumers.getBuffer(RenderTypes.debugQuads());

        // +X face
        quad(buf, m, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
        quad(buf, m, x2, y2, z1, x2, y2, z2, x2, y1, z2, x2, y1, z1, r, g, b, a);
        // -X face
        quad(buf, m, x1, y1, z2, x1, y1, z1, x1, y2, z1, x1, y2, z2, r, g, b, a);
        quad(buf, m, x1, y2, z2, x1, y2, z1, x1, y1, z1, x1, y1, z2, r, g, b, a);
        // +Z face
        quad(buf, m, x2, y1, z2, x1, y1, z2, x1, y2, z2, x2, y2, z2, r, g, b, a);
        quad(buf, m, x2, y2, z2, x1, y2, z2, x1, y1, z2, x2, y1, z2, r, g, b, a);
        // -Z face
        quad(buf, m, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1, r, g, b, a);
        quad(buf, m, x1, y2, z1, x2, y2, z1, x2, y1, z1, x1, y1, z1, r, g, b, a);

        matrices.popPose();
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

    // ===== Block highlight =====

    /**
     * Tiny outward expansion (in blocks) on every face of the highlight so
     * its surfaces sit just proud of the real block they cover. Without
     * this the fill/outline quads are coplanar with the world block faces
     * and depth-fight, which reads as flicker as the camera moves. Matches
     * the epsilon strategy used by {@code BlockOverlayRenderer}.
     */
    private static final float HIGHLIGHT_EPSILON = 0.010f;

    /**
     * Draws a translucent filled box + a brighter outline at the waypoint's
     * stored block position. The radius expands only across the horizontal
     * (X/Z) plane centred on the block; the highlight is always exactly one
     * block tall. {@code radius == 0} is a single block; {@code radius == r}
     * is a {@code (2r+1) × (2r+1)} flat slab one block high.
     *
     * <p>The translucent fill retains the existing debug-quad pass. The outline
     * uses Aurora's shared camera-relative GPU-expanded line path at a fixed
     * device-pixel width, so distant edges do not shrink into subpixel slabs.
     */
    private static void drawBlockHighlight(PoseStack matrices, MultiBufferSource consumers,
                                            Waypoint w, Vec3 camPos, int radius,
                                            float r, float g, float b, float a) {
        matrices.pushPose();
        Matrix4f m = matrices.last().pose();

        // Horizontal radius on X/Z; always a single block of height so the
        // overlay marks the floor tile without rising into a column.
        float x1 = (float) (w.x - radius - camPos.x) - HIGHLIGHT_EPSILON;
        float y1 = (float) (w.y - camPos.y) - HIGHLIGHT_EPSILON;
        float z1 = (float) (w.z - radius - camPos.z) - HIGHLIGHT_EPSILON;
        float x2 = (float) (w.x + radius + 1 - camPos.x) + HIGHLIGHT_EPSILON;
        float y2 = (float) (w.y + 1 - camPos.y) + HIGHLIGHT_EPSILON;
        float z2 = (float) (w.z + radius + 1 - camPos.z) + HIGHLIGHT_EPSILON;

        VertexConsumer buf = consumers.getBuffer(RenderTypes.debugQuads());

        // Fill — half-opacity tint, all six faces, double-wound so the
        // highlight reads from inside the block too.
        float fa = a * 0.35f;
        cubeFaces(buf, m, x1, y1, z1, x2, y2, z2, r, g, b, fa, true);

        // Aurora's shared GPU-expanded lines retain a two-device-pixel stroke
        // at any distance; camera subtraction stays in double precision.
        WorldLineRenderer.drawBoxOutline(WorldLineRenderer.getLineBuffer(consumers, false), m, camPos,
                w.x - radius - (double) HIGHLIGHT_EPSILON, w.y - (double) HIGHLIGHT_EPSILON,
                w.z - radius - (double) HIGHLIGHT_EPSILON,
                w.x + radius + 1d + HIGHLIGHT_EPSILON, w.y + 1d + HIGHLIGHT_EPSILON,
                w.z + radius + 1d + HIGHLIGHT_EPSILON, r, g, b, Math.min(1, a + .15f), 2);

        matrices.popPose();
    }

    /** Six-face axis-aligned cuboid; double-wound so it's visible inside-out. */
    private static void cubeFaces(VertexConsumer buf, Matrix4f m,
                                   float x1, float y1, float z1,
                                   float x2, float y2, float z2,
                                   float r, float g, float b, float a,
                                   boolean doubleWind) {
        // -Y / +Y
        quad(buf, m, x1, y1, z2, x2, y1, z2, x2, y1, z1, x1, y1, z1, r, g, b, a);
        quad(buf, m, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2, r, g, b, a);
        // -Z / +Z
        quad(buf, m, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1, r, g, b, a);
        quad(buf, m, x2, y1, z2, x1, y1, z2, x1, y2, z2, x2, y2, z2, r, g, b, a);
        // -X / +X
        quad(buf, m, x1, y1, z2, x1, y1, z1, x1, y2, z1, x1, y2, z2, r, g, b, a);
        quad(buf, m, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
        if (doubleWind) {
            // Reverse winding for inside-out visibility.
            quad(buf, m, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
            quad(buf, m, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, r, g, b, a);
            quad(buf, m, x1, y2, z1, x2, y2, z1, x2, y1, z1, x1, y1, z1, r, g, b, a);
            quad(buf, m, x2, y2, z2, x1, y2, z2, x1, y1, z2, x2, y1, z2, r, g, b, a);
            quad(buf, m, x1, y2, z2, x1, y2, z1, x1, y1, z1, x1, y1, z2, r, g, b, a);
            quad(buf, m, x2, y2, z1, x2, y2, z2, x2, y1, z2, x2, y1, z1, r, g, b, a);
        }
    }

}
