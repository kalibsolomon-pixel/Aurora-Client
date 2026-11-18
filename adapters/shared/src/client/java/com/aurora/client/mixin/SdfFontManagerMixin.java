package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.health.MixinHealth;
import com.aurora.client.ui.text.SdfFontSets;
import com.aurora.client.ui.text.SdfTextSupport;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Set;

@Mixin(FontManager.class)
public abstract class SdfFontManagerMixin implements SdfFontSets.Lifecycle {
    @Shadow @Final private Map<Identifier, FontSet> fontSets;
    @Shadow @Final private TextureManager textureManager;
    @Unique private SdfFontSets aurora$sdfSets;

    @Inject(method = "getFontSetRaw", at = @At("HEAD"), cancellable = true, require = 1)
    private void aurora$resolveSdf(Identifier id, CallbackInfoReturnable<FontSet> cir) {
        Identifier rasterId = SdfTextSupport.rasterId(id);
        if (rasterId == null) return;
        FontSet raster = fontSets.get(rasterId);
        if (raster == null) return;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (SdfTextSupport.enabled(AuroraConfig.get()) && minecraft != null && RenderSystem.isOnRenderThread()
                    && !MixinHealth.failed("FontManagerAccessor") && !MixinHealth.failed("FontSetAccessor")) {
                if (aurora$sdfSets == null) aurora$sdfSets = new SdfFontSets(minecraft.getResourceManager());
                FontSet sdf = aurora$sdfSets.resolve(id, raster, textureManager, minecraft.options);
                if (sdf != null) {
                    cir.setReturnValue(sdf);
                    return;
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            SdfTextSupport.fail("font alias initialization", failure);
        }
        cir.setReturnValue(raster);
    }

    @Inject(method = "updateOptions", at = @At("RETURN"), require = 1)
    private void aurora$refreshSdfOptions(Options options, CallbackInfo ci) {
        if (aurora$sdfSets != null && !MixinHealth.failed("FontManagerAccessor")) {
            aurora$refreshSdf(FontManagerAccessor.aurora$fontOptions(options));
        }
    }

    @Override public void aurora$refreshSdf(Set<FontOption> options) {
        if (aurora$sdfSets != null) aurora$sdfSets.refresh(options);
    }

    @Inject(method = "apply", at = @At("HEAD"), require = 1)
    private void aurora$reloadSdf(CallbackInfo ci) { aurora$disposeSdf(); }

    @Inject(method = "close", at = @At("HEAD"), require = 1)
    private void aurora$closeSdf(CallbackInfo ci) { aurora$disposeSdf(); }

    @Unique private void aurora$disposeSdf() {
        if (aurora$sdfSets == null) return;
        aurora$sdfSets.close();
        aurora$sdfSets = null;
    }
}
