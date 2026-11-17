package parityprobe.mixin;

import com.aurora.client.ui.util.UiLayerCache;
import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import parityprobe.UiCacheBenchmark;

@Mixin(UiLayerCache.class)
abstract class LayerCacheCounters {
    @Shadow private NativeImage image;
    @Shadow private int w;
    @Shadow private int h;
    @Unique private long ui$commitStart;

    @Inject(method = "ensureSize", at = @At("RETURN"))
    private void sized(int width, int height, CallbackInfo ci) {
        UiCacheBenchmark.observe(this, image, w, h);
    }

    // Absent on the baseline; identity tracking avoids counting delegation twice.
    @Inject(method = "ensureRegion", at = @At("RETURN"), require = 0)
    private void region(int x, int y, int width, int height, CallbackInfo ci) {
        UiCacheBenchmark.observe(this, image, w, h);
    }

    @Inject(method = "commit", at = @At("HEAD"))
    private void beforeCommit(long version, CallbackInfo ci) { ui$commitStart = System.nanoTime(); }

    @Inject(method = "commit", at = @At("RETURN"))
    private void afterCommit(long version, CallbackInfo ci) {
        if (image != null) UiCacheBenchmark.commit(System.nanoTime() - ui$commitStart, w, h);
    }

    @Inject(method = "dispose", at = @At("RETURN"))
    private void disposed(CallbackInfo ci) { UiCacheBenchmark.dispose(this); }
}
