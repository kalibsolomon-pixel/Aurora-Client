package parityprobe.mixin;

import dev.phenomena.combat.events.CombatEvent;
import dev.phenomena.combat.events.Confidence;
import dev.phenomena.combat.tracking.ClientCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import parityprobe.ReachCheck;

/** Opt-in fixture instrumentation, excluded from Aurora artifacts. */
@Mixin(ClientCapture.class)
abstract class PulsarReachCheck {
    @Inject(
            method = "emit(Ldev/phenomena/combat/events/CombatEvent$Kind;Ljava/lang/String;Ljava/lang/String;"
                    + "Ldev/phenomena/combat/events/Confidence;Ldev/phenomena/combat/events/CombatEvent$Attack;"
                    + "Ljava/lang/String;Ldev/phenomena/combat/events/CombatEvent$Context;)V",
            at = @At("HEAD"))
    private static void observe(CombatEvent.Kind kind, String actor, String target, Confidence c,
            CombatEvent.Attack a, String detail, CombatEvent.Context context, CallbackInfo ci) {
        ReachCheck.observe(kind, a);
    }
}
