package com.aurora.client.mixin;

import com.aurora.client.util.GuiItemGlint;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GUI Glint Baking (ADR 0009): whether the item's glint was left out of {@code animated}. */
@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateGlintMixin implements GuiItemGlint {
    @Shadow private boolean animated;
    @Unique private boolean aurora$glint;

    @Inject(method = "clear", at = @At("HEAD"))
    private void aurora$resetGlint(CallbackInfo ci) {
        aurora$glint = false;
    }

    @Override public boolean aurora$glint() { return aurora$glint; }

    @Override public boolean aurora$glintOnly() { return aurora$glint && !animated; }

    @Override public void aurora$markGlint() { aurora$glint = true; }
}
