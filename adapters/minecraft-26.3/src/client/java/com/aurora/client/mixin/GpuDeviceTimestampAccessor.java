package com.aurora.client.mixin;

import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Calibrate backend timestamp queries against the CPU clock without raw GL calls. */
@Mixin(FrontendGpuDevice.class)
public interface GpuDeviceTimestampAccessor {
    @Invoker("getTimestampCalibrationOffset") long aurora$timestampCalibrationOffset();
}
