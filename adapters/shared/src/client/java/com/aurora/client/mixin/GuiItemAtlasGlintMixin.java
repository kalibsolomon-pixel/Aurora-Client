package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.GlintBakeTracker;
import com.aurora.client.util.GuiItemGlint;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.DynamicAtlasAllocator;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GUI Glint Baking (ADR 0009): a glint-only item ({@code CuboidItemModelGlintMixin})
 * keeps its atlas slot like a static item, and is drawn again only when the
 * glint transform or strength has changed since it was drawn
 * ({@link GlintBakeTracker}). Vanilla redraws it every frame.
 */
@Mixin(GuiItemAtlas.class)
public abstract class GuiItemAtlasGlintMixin {
    @Shadow private void drawToSlot(int slotX, int slotY, boolean clear, ItemStackRenderState item) { throw new AssertionError(); }
    @Shadow @Final private DynamicAtlasAllocator<Object> allocator;

    @Unique private final GlintBakeTracker aurora$glints = new GlintBakeTracker();
    @Unique private @Nullable Object aurora$pendingItem;
    @Unique private @Nullable Matrix4fc aurora$pendingBefore;
    @Unique private boolean aurora$invisible;

    /** What a drawing depends on now: the item glint's texture transform, unless the glint is invisible. */
    @Unique
    private @Nullable Matrix4fc aurora$glintKey() {
        if (aurora$invisible) return GlintBakeTracker.INVISIBLE;
        return (Object) TextureTransform.GLINT_TEXTURING instanceof TextureTransformAccessor transform
                ? transform.aurora$supplier().get() : null;
    }

    @WrapOperation(method = "getOrUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/DynamicAtlasAllocator;getOrAllocate(Ljava/lang/Object;Z)Lnet/minecraft/client/gui/render/DynamicAtlasAllocator$Slot;"))
    private DynamicAtlasAllocator.@Nullable Slot aurora$redrawMovedGlint(DynamicAtlasAllocator<Object> allocator, Object key,
            boolean discardAfterFrame, Operation<DynamicAtlasAllocator.Slot> original,
            @Local(argsOnly = true) TrackingItemStackRenderState item) {
        DynamicAtlasAllocator.Slot slot = original.call(allocator, key, discardAfterFrame);
        if (slot == null || discardAfterFrame || !AuroraConfig.get().perfGuiGlintBaking
                || !(item instanceof GuiItemGlint glint) || !glint.aurora$glintOnly()
                || !aurora$glints.firstThisFrame(key)) {
            return slot;
        }
        AuroraConfig cfg = AuroraConfig.get();
        double strength = Minecraft.getInstance().options.glintStrength().get();
        // Glint Color picks the glint render type (GlintRenderTypesMixin) without a texture reload.
        aurora$glints.inputs(strength, (cfg.glintColorEnabled ? 1 : 0) | (cfg.glintColorAlternative ? 2 : 0));
        aurora$invisible = strength == 0.0;
        Matrix4fc before = aurora$glintKey();
        if (slot.state() != DynamicAtlasAllocator.SlotState.READY) {
            // getOrUpdate draws this slot right after; aurora$recordDrawn notes it.
            aurora$pendingItem = key;
            aurora$pendingBefore = before;
        } else if (aurora$glints.stale(key, before)) {
            drawToSlot(slot.x(), slot.y(), true, item);
            aurora$glints.drawn(key, before, aurora$glintKey());
        }
        return slot;
    }

    @Inject(method = "getOrUpdate", at = @At("RETURN"))
    private void aurora$recordDrawn(TrackingItemStackRenderState item, CallbackInfoReturnable<GuiItemAtlas.SlotView> cir) {
        if (aurora$pendingItem == null) return;
        aurora$glints.drawn(aurora$pendingItem, aurora$pendingBefore, aurora$glintKey());
        aurora$pendingItem = null;
        aurora$pendingBefore = null;
    }

    @Inject(method = "endFrame", at = @At("HEAD"))
    private void aurora$endGlintFrame(CallbackInfo ci) {
        aurora$glints.endFrame(allocator.usedSlotKeys());
    }
}
