package com.aurora.client.mixin;

import com.mojang.blaze3d.systems.GpuDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Calibrate backend timestamp queries against the CPU clock without raw GL calls. */
@Mixin(GpuDevice.class)
public interface GpuDeviceTimestampAccessor {
    @Invoker("getTimestampNow") long aurora$timestampNow();
}
