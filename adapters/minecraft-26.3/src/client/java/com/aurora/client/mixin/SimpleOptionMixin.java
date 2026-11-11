package com.aurora.client.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes OptionInstance's private 'value' field so we can write values that
 * would otherwise be rejected by the option's validator (such as gamma > 1.0).
 */
@Mixin(OptionInstance.class)
public interface SimpleOptionMixin<T> {

    @Accessor("value")
    void aurora$forceSetValue(T value);
}