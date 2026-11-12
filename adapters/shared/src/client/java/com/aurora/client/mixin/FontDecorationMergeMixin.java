package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.DecorationRun;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Merge Text Decorations (ADR 0009): vanilla draws an underline or
 * strikethrough as one quad per character. Consecutive characters whose
 * line continues exactly (same height, colors and shadow, the next one
 * starting where the last ended) are drawn as one quad. The merged run is
 * emitted before the text's bounds are read or its quads visited, so both
 * see the same geometry vanilla would produce.
 */
@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public abstract class FontDecorationMergeMixin {
    @Shadow private void addEffect(TextRenderable effect) { throw new AssertionError(); }

    /** Index 0 is the strikethrough run, 1 the underline run (their order in {@code accept}). */
    @Unique private final DecorationRun[] aurora$runs = {new DecorationRun(), new DecorationRun()};

    @WrapOperation(method = "accept(ILnet/minecraft/network/chat/Style;Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;)Z",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/gui/font/glyphs/EffectGlyph;createEffect(FFFFFIIF)Lnet/minecraft/client/gui/font/TextRenderable;"))
    private @Nullable TextRenderable aurora$strikethrough(EffectGlyph glyph, float x0, float y0, float x1, float y1, float depth,
                                                          int color, int shadowColor, float shadowOffset, Operation<TextRenderable> original) {
        return aurora$extend(0, glyph, x0, y0, x1, y1, depth, color, shadowColor, shadowOffset, original);
    }

    @WrapOperation(method = "accept(ILnet/minecraft/network/chat/Style;Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;)Z",
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lnet/minecraft/client/gui/font/glyphs/EffectGlyph;createEffect(FFFFFIIF)Lnet/minecraft/client/gui/font/TextRenderable;"))
    private @Nullable TextRenderable aurora$underline(EffectGlyph glyph, float x0, float y0, float x1, float y1, float depth,
                                                      int color, int shadowColor, float shadowOffset, Operation<TextRenderable> original) {
        return aurora$extend(1, glyph, x0, y0, x1, y1, depth, color, shadowColor, shadowOffset, original);
    }

    /** A null effect means "merged into the pending run". */
    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    private void aurora$skipMerged(@Nullable TextRenderable effect, CallbackInfo ci) {
        if (effect == null) ci.cancel();
    }

    @Unique
    private @Nullable TextRenderable aurora$extend(int kind, EffectGlyph glyph, float x0, float y0, float x1, float y1, float depth,
                                                   int color, int shadowColor, float shadowOffset, Operation<TextRenderable> original) {
        DecorationRun run = aurora$runs[kind];
        if (!AuroraConfig.get().perfMergeTextDecorations) {
            aurora$flush();
            return original.call(glyph, x0, y0, x1, y1, depth, color, shadowColor, shadowOffset);
        }
        if (run.continuesWith(glyph, x0, y0, y1, depth, color, shadowColor, shadowOffset)) {
            run.x1 = x1;
            return null;
        }
        if (run.active) addEffect(run.create());
        run.start(glyph, x0, y0, x1, y1, depth, color, shadowColor, shadowOffset);
        return null;
    }

    @Unique
    private void aurora$flush() {
        for (DecorationRun run : aurora$runs) {
            if (run.active) addEffect(run.create());
        }
    }

    @Inject(method = "visit", at = @At("HEAD"))
    private void aurora$flushBeforeVisit(CallbackInfo ci) {
        aurora$flush();
    }

    @Inject(method = "bounds", at = @At("HEAD"))
    private void aurora$flushBeforeBounds(CallbackInfoReturnable<?> cir) {
        aurora$flush();
    }

    /** 26.2 and later drop the effects of outline text; 26.1 has no such method, hence optional. */
    @Inject(method = "discardEffects", at = @At("HEAD"), require = 0)
    private void aurora$discardRuns(CallbackInfo ci) {
        for (DecorationRun run : aurora$runs) run.active = false;
    }
}
