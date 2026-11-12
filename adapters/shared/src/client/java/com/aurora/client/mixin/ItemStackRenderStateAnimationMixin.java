package com.aurora.client.mixin;

import com.aurora.client.util.GuiItemAnimation;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GUI Item Baking (ADR 0009): records why an item is animated. Every
 * {@code setAnimated()} call marks it as changing each frame, except the one
 * {@code CuboidItemModelAnimationMixin} brackets for animated sprites.
 */
@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateAnimationMixin implements GuiItemAnimation {
    @Unique private boolean aurora$everyFrame;
    @Unique private boolean aurora$inSpriteCall;

    @Inject(method = "clear", at = @At("HEAD"))
    private void aurora$reset(CallbackInfo ci) {
        aurora$everyFrame = false;
        aurora$inSpriteCall = false;
    }

    @Inject(method = "setAnimated", at = @At("HEAD"))
    private void aurora$classify(CallbackInfo ci) {
        if (!aurora$inSpriteCall) aurora$everyFrame = true;
    }

    @Override public boolean aurora$everyFrame() { return aurora$everyFrame; }

    @Override public void aurora$spriteAnimation(boolean inside) { aurora$inSpriteCall = inside; }
}
