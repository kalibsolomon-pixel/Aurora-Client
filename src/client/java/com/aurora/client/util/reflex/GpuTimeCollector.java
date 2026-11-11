package com.aurora.client.util.reflex;

import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderSystem;
import com.aurora.client.mixin.GpuDeviceTimestampAccessor;

import static com.aurora.client.AuroraClient.LOGGER;

public class GpuTimeCollector {
    public Long startTimeSystem = null;
    public Long endTimeSystem = null;
    public Long startTimeGpu = null;
    public Long endTimeGpu = null;
    public Integer startTimeQuery = null;
    public Integer endTimeQuery = null;

    private GpuQueryPool queries;
    private Runnable startCallback = null;
    private Runnable endCallback = null;

    /** Logs the dropped-sample warning once per session; DEBUG afterwards. */
    private static boolean loggedNullStartDrop = false;

    private static com.mojang.blaze3d.systems.GpuDevice calibratedDevice;
    private static long gpuSystemOffset;

    long gpuToSystem(long gpu) {
        var device = RenderSystem.getDevice();
        if (calibratedDevice != device) {
            if (!(device instanceof GpuDeviceTimestampAccessor clock)) {
                throw new IllegalStateException("GPU clock hook unavailable");
            }
            // Vulkan's clock read submits and waits for a query. Calibrate once
            // per device, as vanilla's GPU profiler does, never once per frame.
            long now = nanoseconds(clock.aurora$timestampNow());
            gpuSystemOffset = System.nanoTime() - now;
            calibratedDevice = device;
        }
        return gpu + gpuSystemOffset;
    }

    private long nanoseconds(long timestamp) {
        return (long) (timestamp * (double) RenderSystem.getDevice().getDeviceInfo().timestampPeriod());
    }

    GpuTimeCollector() {
    }

    public void setCallback(Runnable startCallback, Runnable endCallback) {
        this.startCallback = startCallback;
        this.endCallback = endCallback;
    }

    public void startQueryInsert() {
        queries = RenderSystem.getDevice().createTimestampQueryPool(2);
        startTimeQuery = 0;
        RenderSystem.getDevice().createCommandEncoder().writeTimestamp(queries, startTimeQuery);
        startQueryInserted = true;
    }

    public void startQueryCheck() {
        if (!startQueryInserted) {
            LOGGER.error("startQueryInsert() must be called before startQueryCheck()",
                    new IllegalStateException("startQueryInsert() must be called before startQueryCheck()"));
            throw new IllegalStateException("startQueryInsert() must be called before startQueryCheck()");
        }

        // startTimeQuery == null means the start query was already consumed
        // (completed normally, or dropped by endQueryCheck's interrupted-frame
        // path): nothing to poll, startTimeGpu legitimately stays null.
        if (startTimeGpu == null && startTimeQuery != null) {
            var value = queries.getValue(startTimeQuery);
            if (value.isPresent()) {
                startTimeGpu = nanoseconds(value.orElseThrow());
                startTimeQuery = null;

                startTimeSystem = gpuToSystem(startTimeGpu);
                if (startCallback != null) {
                    startCallback.run();
                }
            }
        }
    }

    public boolean startQueryInserted = false;
    public boolean endQueryInserted = false;

    public void endQueryInsert() {
        if (!startQueryInserted) {
            LOGGER.error("startQueryInsert() must be called before endQueryInsert()",
                    new IllegalStateException("startQueryInsert() must be called before endQueryInsert()"));
            throw new IllegalStateException("startQueryInsert() must be called before endQueryInsert()");
        }

        endTimeQuery = 1;
        RenderSystem.getDevice().createCommandEncoder().writeTimestamp(queries, endTimeQuery);

        endQueryInserted = true;
    }

    public boolean endQueryCheck() {
        if (!endQueryInserted) {
            LOGGER.error("endQueryInsert() must be called before endQueryCheck()",
                    new IllegalStateException("endQueryInsert() must be called before endQueryCheck()"));
            throw new IllegalStateException("endQueryInsert() must be called before endQueryCheck()");
        }

        // End query already consumed (completed normally, or dropped below):
        // nothing outstanding, report "done" so the caller retires us.
        if (endTimeQuery == null) {
            return true;
        }

        var value = queries.getValue(endTimeQuery);
        if (value.isPresent()) {
            endTimeGpu = nanoseconds(value.orElseThrow());
            endTimeQuery = null;

            startQueryCheck();
            if (startTimeGpu == null) {
                // An interrupted frame may leave an incomplete pair. Drop that
                // sample without propagating into vanilla's disconnect/render flow.
                startTimeQuery = null;
                if (!loggedNullStartDrop) {
                    loggedNullStartDrop = true;
                    LOGGER.warn("[Reflex] GPU start-timestamp query had no result when the end query "
                            + "completed (interrupted frame — e.g. a disconnect/server transfer — or "
                            + "out-of-order query completion); dropping this frame's GPU timing sample. "
                            + "Further occurrences are logged at DEBUG.");
                } else {
                    LOGGER.debug("[Reflex] dropping GPU timing sample: start query result unavailable");
                }
                return true;
            }

            // Calibrate the pair once; a second clock read would add calibration
            // jitter to the GPU duration used by the pacing policy.
            endTimeSystem = startTimeSystem + (endTimeGpu - startTimeGpu);
            if (endCallback != null) {
                endCallback.run();
            }

            return true;
        }
        return false;
    }

    public void reset() {
        // Release any query objects this collector still owns — abandoned
        // collectors (stale-frame removal, an interrupted frame, the
        // session-disable drain in ReflexScheduler) would otherwise leak them
        // in the driver. Always called on the render thread.
        startTimeQuery = null;
        endTimeQuery = null;
        if (queries != null) {
            queries.close();
            queries = null;
        }
        startTimeSystem = null;
        endTimeSystem = null;
        startTimeGpu = null;
        endTimeGpu = null;
        startQueryInserted = false;
        endQueryInserted = false;
    }
}
