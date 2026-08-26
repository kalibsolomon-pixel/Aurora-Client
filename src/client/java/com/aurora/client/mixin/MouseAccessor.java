package com.aurora.client.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes private cursor delta fields on MouseHandler so we can read and reset them
 * each render frame for decoupled input sampling.
 */
@Mixin(MouseHandler.class)
public interface MouseAccessor {
    @Accessor("accumulatedDX") double aurora$getCursorDeltaX();
    @Accessor("accumulatedDX") void aurora$setCursorDeltaX(double v);
    @Accessor("accumulatedDY") double aurora$getCursorDeltaY();
    @Accessor("accumulatedDY") void aurora$setCursorDeltaY(double v);
}