package com.aurora.client.util.reflex;

import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;

import org.lwjgl.opengl.GL11C;
import static com.aurora.client.AuroraClient.LOGGER;

public class GpuTimeCollector {
    public Long startTimeSystem = null;
    public Long endTimeSystem = null;
    public Long startTimeGpu = null;
    public Long endTimeGpu = null;
    public Integer startTimeQuery = null;
    public Integer endTimeQuery = null;

    private Runnable startCallback = null;
    private Runnable endCallback = null;

    /** Logs the dropped-sample warning once per session; DEBUG afterwards. */
    private static boolean loggedNullStartDrop = false;

    long gpuToSystem(long gpu) {
        long[] t = new long[1];
        GL33C.glGetInteger64v(GL33C.GL_TIMESTAMP, t);
        long system = System.nanoTime();
        long gpuToSystemOffset = system - t[0];
        return gpu + gpuToSystemOffset;
    }

    GpuTimeCollector() {
    }

    public void setCallback(Runnable startCallback, Runnable endCallback) {
        this.startCallback = startCallback;
        this.endCallback = endCallback;
    }

    public void startQueryInsert() {
        startTimeQuery = GL32C.glGenQueries();
        GL33C.glQueryCounter(startTimeQuery, GL33C.GL_TIMESTAMP);
        startQueryInserted = true;
        if (startTimeQuery == null) {
            throw new RuntimeException("Could not find query insertion time");
        }
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
            if (GL33C.glGetQueryObjecti64(startTimeQuery, GL33C.GL_QUERY_RESULT_AVAILABLE) == GL11C.GL_TRUE) {
                startTimeGpu = GL33C.glGetQueryObjecti64(startTimeQuery, GL33C.GL_QUERY_RESULT);
                GL32C.glDeleteQueries(startTimeQuery);
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

        endTimeQuery = GL32C.glGenQueries();
        GL33C.glQueryCounter(endTimeQuery, GL33C.GL_TIMESTAMP);

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

        if (GL33C.glGetQueryObjecti64(endTimeQuery, GL33C.GL_QUERY_RESULT_AVAILABLE) == GL11C.GL_TRUE) {
            endTimeGpu = GL33C.glGetQueryObjecti64(endTimeQuery, GL33C.GL_QUERY_RESULT);
            GL32C.glDeleteQueries(endTimeQuery);
            endTimeQuery = null;

            startQueryCheck();
            if (startTimeGpu == null) {
                // The end timestamp retired but the start query has no result
                // available — a legal GL state (glQueryCounter results may
                // complete out of order) and the reliable outcome of a frame
                // whose begin→end query cycle was interrupted mid-pair (a
                // server transfer / disconnect runs queued tasks mid-frame,
                // inside runTick and again inside disconnect's own task
                // drain). Threw here until 2026-09-13: the IllegalStateException
                // propagated through the runTick injection into vanilla's
                // disconnect/render flow and crashed a real game. One frame's
                // GPU sample is not crash-worthy — drop it, release the
                // orphaned query, report "done". The end callback must NOT
                // run: endTimeSystem needs startTimeSystem, which is missing.
                if (startTimeQuery != null) {
                    GL32C.glDeleteQueries(startTimeQuery);
                    startTimeQuery = null;
                }
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

            endTimeSystem = gpuToSystem(endTimeGpu);
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
        if (startTimeQuery != null) {
            GL32C.glDeleteQueries(startTimeQuery);
            startTimeQuery = null;
        }
        if (endTimeQuery != null) {
            GL32C.glDeleteQueries(endTimeQuery);
            endTimeQuery = null;
        }
        startTimeSystem = null;
        endTimeSystem = null;
        startTimeGpu = null;
        endTimeGpu = null;
        startQueryInserted = false;
        endQueryInserted = false;
    }
}
