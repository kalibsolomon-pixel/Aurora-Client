package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.util.debug.DebugValueAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla's debug hitbox rendering when Aurora's hitbox feature
 * is enabled, so the two don't overlap and flicker on the targeted entity.
 *
 * <p>1.21.11: {@code EntityRenderDispatcher#renderHitboxes} was removed.
 * Hitbox debug rendering moved into a dedicated
 * {@code DebugRenderer.SimpleDebugRenderer}: {@link EntityHitboxDebugRenderer}.
 * Its public entry point is {@code emitGizmos(...)}. Cancelling at HEAD
 * skips the entire vanilla hitbox emit pass for the frame.
 *
 * <p>Class name kept as {@code EntityRenderDispatcherMixin} for source
 * compatibility with the existing {@code aurora.mixins.json} entry.
 */
@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "emitGizmos", at = @At("HEAD"), cancellable = true, require = 1)
    private void aurora$suppressVanillaHitboxes(double camX, double camY, double camZ,
                                                DebugValueAccess access,
                                                Frustum frustum, float partialTick,
                                                CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.hitboxEnabled || cfg.hitboxTargetEnabled) {
            ci.cancel();
        }
    }
}