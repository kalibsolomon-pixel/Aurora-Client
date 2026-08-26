package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.AuroraItemPhysicsSnapshots;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the {@code ItemEntity}'s ground-contact flag and current
 * motion length-squared during state extraction so
 * {@link ItemEntityRendererSubmitMixin} can adjust the bob translate
 * and spin quaternion later in {@code submit}. The post-1.21.5 render
 * path only hands the renderer an {@link ItemEntityRenderState}
 * snapshot, so we stash what we need in a weak-keyed side map.
 *
 * <p>Hooked at TAIL of
 * {@code extractRenderState(ItemEntity, ItemEntityRenderState, float)}.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererExtractMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL"),
            require = 0)
    private void aurora$capturePhysics(ItemEntity entity,
                                       ItemEntityRenderState state,
                                       float partialTick,
                                       CallbackInfo ci) {
        if (entity == null || state == null) return;
        if (!AuroraConfig.get().itemPhysicsEnabled) return;
        Vec3 v = entity.getDeltaMovement();
        boolean grounded = entity.onGround();

        // Carry forward the previous frame's groundT so the transition is
        // smooth instead of stepping 0->1 the moment the entity touches.
        AuroraItemPhysicsSnapshots.Snapshot prev = AuroraItemPhysicsSnapshots.get(state);
        float prevT = prev == null ? (grounded ? 1f : 0f) : prev.groundT;
        float target = grounded ? 1f : 0f;
        float delta = target - prevT;
        // Asymmetric easing:
        //   - LANDING (target = 1, delta > 0): step at 0.15 so the
        //     flat-on-ground rotation snap is hidden across ~7 frames.
        //   - LIFTOFF (target = 0, delta < 0): snap instantly. Once the
        //     item has left the ground it's already accelerating
        //     downward, and any easing of groundT toward 0 visibly
        //     lifts/tilts the item mid-air instead of letting it fall
        //     straight, which reads as glitching. Falling items must
        //     return to airborne tumble on the very next frame.
        if (delta > 0f) {
            float step = 0.15f;
            if (delta > step) delta = step;
        }
        // (delta <= 0 → applied in full → groundT goes to 0 immediately.)
        float groundT = prevT + delta;
        if (groundT < 0f) groundT = 0f;
        else if (groundT > 1f) groundT = 1f;

        // Sticky settle flag: latch once the item has FULLY come to rest
        // (groundT == 1), never clearing afterwards. We deliberately wait
        // for full settle rather than first contact so the fresh-drop
        // landing slerp still plays; only items that genuinely rested are
        // marked. The submit mixin reads this to keep settled-then-falling
        // items flat instead of letting them tumble (the lerp glitch).
        boolean settled = (prev != null && prev.settled) || groundT >= 1f;

        AuroraItemPhysicsSnapshots.put(state,
                new AuroraItemPhysicsSnapshots.Snapshot(grounded, v.lengthSqr(), groundT, settled));
    }
}
