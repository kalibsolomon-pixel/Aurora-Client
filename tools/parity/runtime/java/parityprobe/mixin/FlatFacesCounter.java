package parityprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Counts quads offered inside GUI item atlas drawings and those GUI Flat
 * Faces left out (ADR 0009). Aurora asks {@code skip} for every item quad,
 * with the gate on or off. Also applies the probe's "flat-faces-mode".
 */
@Mixin(com.aurora.client.util.GuiFlatFaces.class)
public abstract class FlatFacesCounter {
    @Unique private static boolean parityprobe$inAtlas;

    @Inject(method = "beginAtlasDraw", at = @At("HEAD"))
    private static void parityprobe$begin(boolean on, CallbackInfo ci) {
        parityprobe$inAtlas = true;
    }

    @Inject(method = "endAtlasDraw", at = @At("HEAD"))
    private static void parityprobe$end(CallbackInfo ci) {
        parityprobe$inAtlas = false;
    }

    @Inject(method = "skip", at = @At("RETURN"), cancellable = true)
    private static void parityprobe$count(com.mojang.blaze3d.vertex.PoseStack.Pose pose, Object quad,
            CallbackInfoReturnable<Boolean> cir) {
        if (!parityprobe$inAtlas) return;
        boolean skipped = cir.getReturnValueZ();
        parityprobe.Counters.ATLAS_QUADS.incrementAndGet();
        if (skipped) parityprobe.Counters.ATLAS_QUADS_SKIPPED.incrementAndGet();
        // "flat-faces-mode": draw only what GUI Flat Faces leaves out, or nothing, to show
        // that the left-out quads alone produce no pixel (gate on only).
        if (!com.aurora.client.config.AuroraConfig.get().perfGuiFlatFaces) return;
        switch (parityprobe.Counters.FLAT_FACES_MODE) {
            case 1 -> cir.setReturnValue(!skipped);
            case 2 -> cir.setReturnValue(true);
            default -> { }
        }
    }
}
