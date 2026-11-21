package com.aurora.client.hud;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.HitboxPositionSmoother;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Draws Aurora's custom hitbox visualization in the world. The Aurora feature
 * toggle is the sole gate; when enabled the renderer always draws and vanilla's
 * own debug hitbox pass is suppressed by {@code EntityRenderDispatcherMixin}.
 *
 * <p><b>Position interpolation (synced with entity model).</b> The hitbox is
 * rendered at the exact same tick-interpolated position
 * ({@code Mth.lerp(tickDelta, xOld, x)}) that vanilla uses for the entity
 * model. This guarantees the hitbox and the entity model are always perfectly
 * in sync — they overlap exactly every frame, which is the core requirement
 * for "hitboxes that feel right".
 *
 * <p>An earlier version routed the position through {@link HitboxPositionSmoother}
 * to smooth throttled/bundled movement packets. That approach was removed
 * because it <b>desynced the hitbox from the entity model</b>: the smoother
 * eased the hitbox toward the interpolated target while the entity model
 * rendered at the raw interpolated target, so the two drifted apart — the
 * hitbox appeared to lag behind or lead ahead of the model, which read as
 * "hitboxes feel off". The vanilla interpolation path is correct because it
 * matches what the entity model uses.
 *
 * <p><b>Camera-relative double precision.</b> Camera position is subtracted in
 * {@code double} precision before casting to {@code float} inside
 * {@link WorldLineRenderer}, preventing float-precision jitter at large world
 * coordinates. The PoseStack is NOT pre-translated by the camera.
 *
 * <p><b>Line rendering (Lunar/NoRisk style).</b> In MC 1.21.11, Mojang's
 * {@code RenderTypes.lines()} uses a GPU-side line expansion shader with
 * per-vertex line width ({@link VertexConsumer#setLineWidth}). This produces
 * clean, crisp wireframe edges at any pixel width — no more thick 3D cuboid
 * bars. All edges (box outline, eye-line box, look-direction line) share the
 * same line technique for a consistent, unified appearance.
 *
 * <p><b>See-through mode.</b> When enabled (default, matching Lunar/NoRisk),
 * lines are drawn via {@link net.minecraft.client.renderer.rendertype.RenderTypes#linesTranslucent()}
 * which renders without depth testing, so the full hitbox wireframe is visible
 * through the entity model — the signature competitive-client look.
 */
public final class HitboxRenderer {

    /**
     * Clears stale per-world smoothing state. Called on world join/respawn
     * (see {@code ClientPlayNetworkHandlerWorldJoinMixin}).
     */
    public static void resetFrameTiming() {
        HitboxPositionSmoother.clearAll();
    }

    /** Blocks around an entity's box still counted as visible (eye and look lines reach past it). */
    private static final double CULL_MARGIN = 1.0;

    public void render(LevelRenderContext ctx) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;

        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.hitboxEnabled && !cfg.hitboxTargetEnabled) return;
        // The Aurora feature toggle is the sole gate -- when enabled, Aurora's
        // hitboxes always draw and EntityRenderDispatcherMixin suppresses the
        // vanilla pass so the two never overlap. F3+B is not consulted.

        Camera cam = client.gameRenderer.mainCamera();
        if (cam == null) return;
        Vec3 camPos = cam.position();

        PoseStack matrices = ctx.poseStack();
        if (matrices == null) return;
        try (WorldGeometryBuffers consumers = WorldGeometryBuffers.begin()) {
            boolean sdf = WorldLineRenderer.sdfAvailable();
            VertexConsumer buf = null;

            // Match vanilla LevelRenderer: use the game-time partial tick (false) so
            // our interpolated AABB lines up with the camera and the entity model.
            float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            Entity activeTarget = resolveTarget(client);
            Matrix4f model = matrices.last().pose();
            Entity cameraEntity = client.getCameraEntity();
            boolean detached = cam.isDetached();
            LocalPlayer player = client.player;

            // Prefer the exact entities that survived main-world culling (vanilla, Sodium,
            // EntityCulling, and Aurora occlusion), matching F3+B; fall back if the hook did not run.
            if (cfg.hitboxEnabled && HitboxVisibleEntities.hasSnapshot()) {
                int n = HitboxVisibleEntities.count();
                for (int i = 0; i < n; i++) {
                    Entity entity = HitboxVisibleEntities.get(i);
                    if (entity == null || entity.isRemoved() || entity.isSpectator()) continue;
                    if (entity == cameraEntity && !detached) continue;
                    if (player != null && entity != player) {
                        if (entity.isInvisible() || entity.isInvisibleTo(player)) continue;
                    }
                    if (!shouldDraw(entity, activeTarget, cfg)) continue;
                    if (buf == null) buf = WorldLineRenderer.getLineBuffer(consumers, sdf);
                    renderCandidate(buf, model, camPos, entity, activeTarget, tickDelta, cfg, sdf);
                }
            } else if (!cfg.hitboxEnabled) {
                if (activeTarget != null && !activeTarget.isRemoved() && !activeTarget.isSpectator()
                        && (activeTarget != cameraEntity || detached)
                        && activeTarget.shouldRender(camPos.x, camPos.y, camPos.z)) {
                    net.minecraft.client.renderer.culling.Frustum frustum = cam.getCullFrustum();
                    if ((frustum == null || frustum.isVisible(activeTarget.getBoundingBox().inflate(CULL_MARGIN)))
                            && (player == null || activeTarget == player
                                    || (!activeTarget.isInvisible() && !activeTarget.isInvisibleTo(player)))) {
                        buf = WorldLineRenderer.getLineBuffer(consumers, sdf);
                        renderCandidate(buf, model, camPos, activeTarget, activeTarget, tickDelta, cfg, sdf);
                    }
                }
            } else {
                net.minecraft.client.renderer.culling.Frustum frustum = cam.getCullFrustum();
                for (Entity entity : client.level.entitiesForRendering()) {
                    if (entity == null || entity.isSpectator()) continue;
                    if (entity == cameraEntity && !detached) continue;
                    if (!entity.shouldRender(camPos.x, camPos.y, camPos.z)) continue;
                    if (frustum != null && !frustum.isVisible(entity.getBoundingBox().inflate(CULL_MARGIN))) continue;
                    if (player != null && entity != player) {
                        if (entity.isInvisible() || entity.isInvisibleTo(player)) continue;
                    }
                    if (!shouldDraw(entity, activeTarget, cfg)) continue;
                    if (buf == null) buf = WorldLineRenderer.getLineBuffer(consumers, sdf);
                    renderCandidate(buf, model, camPos, entity, activeTarget, tickDelta, cfg, sdf);
                }
            }
        }
    }

    private static boolean shouldDraw(Entity entity, Entity activeTarget, AuroraConfig cfg) {
        return (entity == activeTarget) ? cfg.hitboxTargetEnabled : cfg.hitboxEnabled;
    }

    private static void renderCandidate(VertexConsumer buf, Matrix4f model, Vec3 camPos,
                                        Entity entity, Entity activeTarget, float tickDelta,
                                        AuroraConfig cfg, boolean sdf) {
        boolean isTarget = (entity == activeTarget);

        int color;
        float widthPx;
        if (entity instanceof Projectile && cfg.hitboxProjectileColorEnabled) {
            color = cfg.hitboxProjectileColor;
            widthPx = (float) Math.max(0.1, cfg.hitboxProjectileLineWidth);
        } else {
            color = isTarget ? cfg.hitboxTargetColor : cfg.hitboxColor;
            widthPx = (float) Math.max(0.1, cfg.hitboxLineWidth);
        }
        boolean drawEyeLine  = isTarget ? cfg.hitboxTargetEyeLine       : cfg.hitboxEyeLine;
        boolean drawLookLine = isTarget ? cfg.hitboxTargetLookDirection : cfg.hitboxLookDirection;

        drawEntity(buf, model, camPos, entity, tickDelta, color,
                drawEyeLine, drawLookLine, cfg.hitboxLookLength, widthPx, sdf);
    }

    private static Entity resolveTarget(Minecraft client) {
        if (client.player == null || client.level == null) return null;
        if (client.crosshairPickEntity != null) return client.crosshairPickEntity;
        HitResult hr = client.hitResult;
        return hr instanceof EntityHitResult ehr ? ehr.getEntity() : null;
    }

    private static void drawEntity(VertexConsumer buf, Matrix4f model, Vec3 camPos,
                                   Entity e, float tickDelta,
                                   int color, boolean eyeLine, boolean lookLine, double lookLen,
                                   float widthPx, boolean sdf) {
        // Tick-interpolated position — EXACTLY what vanilla entity rendering uses.
        double ix = Mth.lerp((double) tickDelta, e.xOld, e.getX());
        double iy = Mth.lerp((double) tickDelta, e.yOld, e.getY());
        double iz = Mth.lerp((double) tickDelta, e.zOld, e.getZ());

        // Offset raw AABB in primitives to avoid a per-entity AABB allocation each frame.
        AABB raw = e.getBoundingBox();
        double dx = ix - e.getX();
        double dy = iy - e.getY();
        double dz = iz - e.getZ();
        double minX = raw.minX + dx, minY = raw.minY + dy, minZ = raw.minZ + dz;
        double maxX = raw.maxX + dx, maxY = raw.maxY + dy, maxZ = raw.maxZ + dz;

        float a = ((color >>> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8)  & 0xFF) / 255f;
        float b = ( color        & 0xFF) / 255f;

        WorldLineRenderer.drawBoxOutline(buf, model, camPos, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a, widthPx, sdf);

        if (eyeLine) {
            double eyeY = iy + e.getEyeHeight();
            WorldLineRenderer.drawLine(buf, model, camPos, minX, eyeY, minZ, maxX, eyeY, minZ, r, g, b, a, widthPx, sdf);
            WorldLineRenderer.drawLine(buf, model, camPos, maxX, eyeY, minZ, maxX, eyeY, maxZ, r, g, b, a, widthPx, sdf);
            WorldLineRenderer.drawLine(buf, model, camPos, maxX, eyeY, maxZ, minX, eyeY, maxZ, r, g, b, a, widthPx, sdf);
            WorldLineRenderer.drawLine(buf, model, camPos, minX, eyeY, maxZ, minX, eyeY, minZ, r, g, b, a, widthPx, sdf);
        }

        if (lookLine) {
            float lerpYaw   = Mth.rotLerp(tickDelta, e.yRotO,   e.getYRot());
            float lerpPitch = Mth.lerp(tickDelta, e.xRotO, e.getXRot());
            float yawRad = -lerpYaw * Mth.DEG_TO_RAD - (float) Math.PI;
            float pitchRad = -lerpPitch * Mth.DEG_TO_RAD;
            float cosYaw = Mth.cos(yawRad);
            float sinYaw = Mth.sin(yawRad);
            float negCosPitch = -Mth.cos(pitchRad);
            double lookX = (double) (sinYaw * negCosPitch);
            double lookY = (double) Mth.sin(pitchRad);
            double lookZ = (double) (cosYaw * negCosPitch);

            double eyeStartY = iy + e.getEyeHeight();
            WorldLineRenderer.drawLine(buf, model, camPos,
                    ix, eyeStartY, iz,
                    ix + lookX * lookLen, eyeStartY + lookY * lookLen, iz + lookZ * lookLen,
                    r, g, b, a, widthPx, sdf);
        }
    }
}
