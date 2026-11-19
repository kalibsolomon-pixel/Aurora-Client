package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Retro Backwards Walking, for every player this client draws, you included
 * (owner, 2026-11-19).
 *
 * <p>Since 1.12, an entity walking more than 95° away from where it faces
 * aims its body at the opposite of its travel direction, so walking backwards
 * keeps the body square. 1.8 aimed the body along the travel direction and
 * let the head turn up to 75° from it, so the body twisted toward the way it
 * walked. A client computes every player's body yaw itself, from their
 * movement, only to draw the model; the server keeps its own and never
 * receives this one, so movement, hitboxes and what other clients draw are
 * unchanged. Mobs keep vanilla's turn.
 *
 * <p>Runs in {@code LivingEntity.tick}, which the integrated server also
 * runs: the client-side check comes first.
 */
@Mixin(LivingEntity.class)
public abstract class RetroBodyTurnMixin {

    /** 1.8's head-to-body limit (vanilla's is 50°). */
    @Unique private static final float RETRO_MAX_HEAD_TURN = 75.0F;

    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;tickHeadTurn(F)V"))
    private float aurora$retroBodyTarget(float target) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!aurora$retro(self)) return target;
        double dx = self.getX() - self.xo;
        double dz = self.getZ() - self.zo;
        if (dx * dx + dz * dz <= 0.0025000002F) return target;
        float walk = (float) Mth.atan2(dz, dx) * (180.0F / (float) Math.PI) - 90.0F;
        // Undo only vanilla's backwards flip; a swing (body faces the view) and
        // forward or sideways walking keep vanilla's target.
        return Math.abs(Mth.wrapDegrees(target - (walk - 180.0F))) < 0.01F ? walk : target;
    }

    @Inject(method = "getMaxHeadRotationRelativeToBody", at = @At("HEAD"), cancellable = true)
    private void aurora$retroHeadLimit(CallbackInfoReturnable<Float> cir) {
        if (aurora$retro((LivingEntity) (Object) this)) cir.setReturnValue(RETRO_MAX_HEAD_TURN);
    }

    @Unique
    private static boolean aurora$retro(LivingEntity self) {
        if (!(self instanceof Player) || !self.level().isClientSide()) return false;
        AuroraConfig cfg = AuroraConfig.get();
        return cfg.smoothAnimationsEnabled && cfg.animRetroBackwardsWalk;
    }
}
