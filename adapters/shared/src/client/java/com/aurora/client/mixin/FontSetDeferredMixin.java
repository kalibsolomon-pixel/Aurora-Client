package com.aurora.client.mixin;

import com.aurora.client.ui.util.DeferredFontSet;
import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

/** Vanilla selection, glyph widths and obfuscation are prepared exactly once on use. */
@Mixin(FontSet.class)
public abstract class FontSetDeferredMixin implements DeferredFontSet {
    @Shadow private List<GlyphProvider.Conditional> allProviders;
    @Shadow public abstract void reload(Set<FontOption> options);
    @Unique private Set<FontOption> aurora$pendingOptions;

    @Override public void aurora$defer(List<GlyphProvider.Conditional> providers, Set<FontOption> options) {
        allProviders = providers;
        aurora$pendingOptions = Set.copyOf(options);
    }

    @Override public boolean aurora$pending() { return aurora$pendingOptions != null; }

    @Inject(method = "reload(Ljava/util/Set;)V", at = @At("HEAD"), cancellable = true)
    private void aurora$rememberOptions(Set<FontOption> options, CallbackInfo ci) {
        if (aurora$pendingOptions == null) return;
        aurora$pendingOptions = Set.copyOf(options);
        ci.cancel();
    }

    @Inject(method = {"source", "getRandomGlyph", "whiteGlyph"}, at = @At("HEAD"))
    private void aurora$initialize(CallbackInfoReturnable<?> cir) {
        Set<FontOption> options = aurora$pendingOptions;
        if (options == null) return;
        aurora$pendingOptions = null;
        reload(options);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void aurora$clearPending(CallbackInfo ci) { aurora$pendingOptions = null; }
}
