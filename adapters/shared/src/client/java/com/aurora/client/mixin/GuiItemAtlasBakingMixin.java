package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.GuiItemAnimation;
import com.aurora.client.util.SpriteAnimationClock;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.client.gui.render.DynamicAtlasAllocator;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * GUI Item Baking (ADR 0009): vanilla gives every animated item a slot that
 * is thrown away after the frame, so it is drawn again every frame. An item
 * animated only by its sprites keeps its slot, and is redrawn only when the
 * sprites have advanced since it was drawn ({@link SpriteAnimationClock}).
 * Items with a glint or a special animated model keep vanilla's behaviour.
 */
@Mixin(GuiItemAtlas.class)
public abstract class GuiItemAtlasBakingMixin {
    @Shadow private void drawToSlot(int slotX, int slotY, boolean clear, ItemStackRenderState item) { throw new AssertionError(); }

    /** Model identity to the animation step its slot was last drawn at. */
    @Unique private final Object2LongOpenHashMap<Object> aurora$drawnAt = new Object2LongOpenHashMap<>();

    @WrapOperation(method = "getOrUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/DynamicAtlasAllocator;getOrAllocate(Ljava/lang/Object;Z)Lnet/minecraft/client/gui/render/DynamicAtlasAllocator$Slot;"))
    private DynamicAtlasAllocator.@Nullable Slot aurora$keepSpriteAnimated(DynamicAtlasAllocator<Object> allocator, Object key,
            boolean discardAfterFrame, Operation<DynamicAtlasAllocator.Slot> original,
            @Local(argsOnly = true) TrackingItemStackRenderState item) {
        if (!discardAfterFrame || !AuroraConfig.get().perfGuiItemBaking
                || !(item instanceof GuiItemAnimation animation) || animation.aurora$everyFrame()) {
            return original.call(allocator, key, discardAfterFrame);
        }
        DynamicAtlasAllocator.Slot slot = original.call(allocator, key, false);
        if (slot == null) return null;
        long step = SpriteAnimationClock.ticks();
        // getOrUpdate draws EMPTY and STALE slots itself right after this; a
        // READY slot holds this item's own earlier drawing.
        if (slot.state() == DynamicAtlasAllocator.SlotState.READY && aurora$drawnAt.getOrDefault(key, -1L) != step) {
            drawToSlot(slot.x(), slot.y(), true, item);
        }
        if (aurora$drawnAt.size() > 64 && aurora$drawnAt.size() > 2 * allocator.usedSlotKeys().size()) {
            aurora$drawnAt.keySet().retainAll(allocator.usedSlotKeys());
        }
        aurora$drawnAt.put(key, step);
        return slot;
    }
}
