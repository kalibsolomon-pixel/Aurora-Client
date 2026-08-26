package com.aurora.client.hud;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.HitboxPositionSmoother;
import com.aurora.client.feature.impl.ThrottleDetector;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import java.util.function.Predicate;

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
 * matches what the entity model uses. {@link ThrottleDetector} is kept
 * sampling (harmless) for potential future diagnostic use, but its output no
 * longer influences the rendered position.
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

    public void render(WorldRenderContext ctx) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;

        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.hitboxEnabled && !cfg.hitboxTargetEnabled) return;
        // The Aurora feature toggle is the sole gate -- when enabled, Aurora's
        // hitboxes always draw and EntityRenderDispatcherMixin suppresses the
        // vanilla pass so the two never overlap. F3+B is not consulted.

        Camera cam = client.gameRenderer.getMainCamera();
        if (cam == null) return;
        Vec3 camPos = cam.position();

        PoseStack matrices = ctx.matrices();
        if (matrices == null) return;
        MultiBufferSource consumers = ctx.consumers();
        if (consumers == null) return;

        // Use the proper lines() render type with per-vertex line width.
        // See-through mode (default) uses linesTranslucent() for the Lunar look.
        boolean seeThrough = cfg.hitboxSeeThrough;
        VertexConsumer buf = WorldLineRenderer.getLineBuffer(consumers, seeThrough);

        // Match vanilla LevelRenderer: use the game-time partial tick (false) so
        // our interpolated AABB lines up with the camera and the entity model.
        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        // ThrottleDetector is kept sampling (harmless, no-op side effects) for
        // potential future diagnostic use. Its output no longer influences the
        // rendered position — see class javadoc for why smoothing was removed.
        ThrottleDetector.sample(client);

        Entity activeTarget = resolveTarget(client);

        // IMPORTANT: Do NOT translate the pose stack by the camera position.
        // WorldLineRenderer subtracts the camera position in double precision
        // before casting to float, which prevents precision loss at large world
        // coordinates. The matrix here is the raw model-view matrix.
        matrices.pushPose();
        Matrix4f model = matrices.last().pose();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == null || entity.isSpectator()) continue;
            if (entity == client.getCameraEntity() && !cam.isDetached()) continue;

            // Anti-cheat: never reveal entities that are invisible to the local
            // player. Aurora's hitboxes are a visualization aid, not a wallhack --
            // showing the box of an entity under the Invisibility effect (or one
            // the player can't otherwise see) would betray its exact position,
            // size and orientation. The local player is exempt so you can still
            // see your own box in third person / while invisible yourself.
            // Mirrors the isInvisible / isInvisibleToPlayer gate already used by
            // PlayerHealthLabelMixin, but read off the live Entity.
            if (client.player != null && entity != client.player) {
                if (entity.isInvisible() || entity.isInvisibleTo(client.player)) continue;
            }

            boolean isTarget = (entity == activeTarget);
            boolean drawThisOne = isTarget ? cfg.hitboxTargetEnabled : cfg.hitboxEnabled;
            if (!drawThisOne) continue;

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
                    drawEyeLine, drawLookLine, cfg.hitboxLookLength, widthPx);
        }

        matrices.popPose();
    }

    private static Entity resolveTarget(Minecraft client) {
        LocalPlayer p = client.player;
        if (p == null || client.level == null) return null;

        // 1. Try vanilla's active crosshair/hit result target
        HitResult hr = client.hitResult;
        if (hr instanceof EntityHitResult ehr) {
            return ehr.getEntity();
        }

        // 2. Perform a direct raycast to bypass temporary clearing of client.hitResult during swing/attack ticks
        double reach = p.entityInteractionRange();
        if (reach <= 0) reach = 3.0;

        Vec3 eye = p.getEyePosition(1.0f);
        Vec3 look = p.getViewVector(1.0f);
        Vec3 end = eye.add(look.x * reach, look.y * reach, look.z * reach);
        AABB searchBox = p.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);

        Predicate<Entity> filter = e ->
                !e.isSpectator() && e.isPickable() && e != p;

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                p, eye, end, searchBox, filter, reach * reach);

        if (hit != null) {
            return hit.getEntity();
        }
        return null;
    }

    private static void drawEntity(VertexConsumer buf, Matrix4f model, Vec3 camPos,
                                   Entity e, float tickDelta,
                                   int color, boolean eyeLine, boolean lookLine, double lookLen,
                                   float widthPx) {
        // Tick-interpolated position — EXACTLY what vanilla entity rendering uses.
        // This keeps the hitbox perfectly overlaid on the entity model. We do NOT
        // apply any additional smoothing (which would desync the hitbox from the
        // model and make hitboxes "feel off").
        double ix = Mth.lerp((double) tickDelta, e.xOld, e.getX());
        double iy = Mth.lerp((double) tickDelta, e.yOld, e.getY());
        double iz = Mth.lerp((double) tickDelta, e.zOld, e.getZ());

        // Build the AABB at the tick-interpolated render position.
        AABB raw = e.getBoundingBox();
        double dx = ix - e.getX();
        double dy = iy - e.getY();
        double dz = iz - e.getZ();
        AABB box = raw.move(dx, dy, dz);

        float a = ((color >>> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8)  & 0xFF) / 255f;
        float b = ( color        & 0xFF) / 255f;

        // All edges use the same clean line technique — no more cuboid bars.
        // Camera position passed separately for double-precision subtraction.
        WorldLineRenderer.drawBoxOutline(buf, model, camPos, box, r, g, b, a, widthPx);

        if (eyeLine) {
            double eyeY = iy + e.getEyeHeight();
            WorldLineRenderer.drawLine(buf, model, camPos, box.minX, eyeY, box.minZ, box.maxX, eyeY, box.minZ, r, g, b, a, widthPx);
            WorldLineRenderer.drawLine(buf, model, camPos, box.maxX, eyeY, box.minZ, box.maxX, eyeY, box.maxZ, r, g, b, a, widthPx);
            WorldLineRenderer.drawLine(buf, model, camPos, box.maxX, eyeY, box.maxZ, box.minX, eyeY, box.maxZ, r, g, b, a, widthPx);
            WorldLineRenderer.drawLine(buf, model, camPos, box.minX, eyeY, box.maxZ, box.minX, eyeY, box.minZ, r, g, b, a, widthPx);
        }

        if (lookLine) {
            float lerpYaw   = Mth.rotLerp(tickDelta, e.yRotO,   e.getYRot());
            float lerpPitch = Mth.lerp(tickDelta, e.xRotO, e.getXRot());
            Vec3 look = Vec3.directionFromRotation(lerpPitch, lerpYaw);

            double eyeStartX = ix;
            double eyeStartY = iy + e.getEyeHeight();
            double eyeStartZ = iz;

            double endX = eyeStartX + look.x * lookLen;
            double endY = eyeStartY + look.y * lookLen;
            double endZ = eyeStartZ + look.z * lookLen;
            WorldLineRenderer.drawLine(buf, model, camPos, eyeStartX, eyeStartY, eyeStartZ, endX, endY, endZ, r, g, b, a, widthPx);
        }
    }
}