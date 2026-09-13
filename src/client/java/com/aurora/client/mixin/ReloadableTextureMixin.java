package com.aurora.client.mixin;

import com.aurora.client.util.GlintColor;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enchant Glint Recolor — the single injection point.
 *
 * <p>Targeted at {@link ReloadableTexture} (not its file-backed subclass
 * {@code SimpleTexture}, whose mixin would need to shadow an inherited
 * method Mixin cannot resolve) because this is where both the resource id
 * and the upload live: {@code apply(TextureContents)} is the one concrete
 * method every reloadable texture funnels through — its caller has already
 * decoded the PNG into {@code TextureContents(NativeImage, metadata)} and
 * will upload the image and close it inside this very call. HEAD of
 * {@code apply} is therefore the last (and only) CPU-side moment the pixels
 * can be recolored.
 *
 * <p>When {@code GlintColor.applyToLoadedContents} declines — feature off,
 * armor sub-toggle off for the armor texture, or the texture isn't one of
 * the two glint textures — the contents pass through untouched: the
 * "disabled" state is vanilla's own file load, with no hand-written restore
 * constant anywhere (the HitColor d84298c bug class cannot occur by
 * construction).
 *
 * <p>Fires once per texture per resource reload, plus GlintColor's forced
 * reloads of the two glint textures on config changes — a couple of
 * reference comparisons for the vast majority of calls.
 */
@Mixin(ReloadableTexture.class)
public abstract class ReloadableTextureMixin {

    /** The resource id this texture was registered under. */
    @Shadow
    public abstract Identifier resourceId();

    @Inject(method = "apply", at = @At("HEAD"))
    private void aurora$tintGlintTexture(TextureContents contents, CallbackInfo ci) {
        GlintColor.applyToLoadedContents(this.resourceId(), contents);
    }
}
