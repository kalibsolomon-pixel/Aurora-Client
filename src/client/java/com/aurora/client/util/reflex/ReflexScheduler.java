package com.aurora.client.util.reflex;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import static com.aurora.client.AuroraClient.LOGGER;

public class ReflexScheduler {
    private static ReflexScheduler instance;

    public static ReflexScheduler getInstance() {
        if (instance == null) {
            instance = new ReflexScheduler();
        }
        return instance;
    }

    private final float alpha = 0.85f;
    private Long estimateCpuTime = null;

    private final int gpuWindowSize = 60;
    private final long[] gpuTimeRingBuffer = new long[gpuWindowSize];
    private int ringBufferIndex = 0;
    private int validSamples = 0;

    public Deque<GpuTimeCollector> gpuTimeCollectorDeque = new ArrayDeque<>();

    ObjectPool.ObjectFactory<GpuTimeCollector> collectorFactory = GpuTimeCollector::new;
    ObjectPool.ObjectResetter<GpuTimeCollector> collectorResetter = GpuTimeCollector::reset;
    private final ObjectPool<GpuTimeCollector> collectorPool = new ObjectPool<>(collectorFactory, collectorResetter);

    private final float weightBase = 1.5f;
    private final float[] gpuWeights;

    public ReflexScheduler() {
        this.gpuWeights = new float[gpuWindowSize];
        float weightSum = 0;

        for (int i = 0; i < gpuWindowSize; i++) {
            gpuWeights[i] = (float) Math.pow(weightBase, gpuWindowSize - 1 - i);
            weightSum += gpuWeights[i];
        }

        for (int i = 0; i < gpuWindowSize; i++) {
            gpuWeights[i] /= weightSum;
        }
    }

    public void updateCpuTime(long cpuTimeNs) {
        if (estimateCpuTime == null) {
            estimateCpuTime = cpuTimeNs;
        } else {
            estimateCpuTime = (long) (alpha * cpuTimeNs + (1 - alpha) * estimateCpuTime);
        }
    }

    public void updateGpuTime(long gpuTimeNs) {
        gpuTimeRingBuffer[ringBufferIndex] = gpuTimeNs;
        ringBufferIndex = (ringBufferIndex + 1) % gpuWindowSize;
        validSamples = Math.min(validSamples + 1, gpuWindowSize);
    }

    public Long getEstimateGpuTime() {
        if (validSamples == 0) return null;

        float weightedSum = 0;
        for (int i = 0; i < validSamples; i++) {
            int idx = (ringBufferIndex - 1 - i + gpuWindowSize) % gpuWindowSize;
            weightedSum += gpuTimeRingBuffer[idx] * gpuWeights[i];
        }
        return (long) weightedSum;
    }

    private Long calculateWaitTime() {

        Iterator<GpuTimeCollector> gpuTimeCollectorIterator = gpuTimeCollectorDeque.iterator();
        while (gpuTimeCollectorIterator.hasNext()) {
            GpuTimeCollector gpuTimeCollector = gpuTimeCollectorIterator.next();
            if (gpuTimeCollector.startQueryInserted && gpuTimeCollector.endQueryInserted) {
                gpuTimeCollector.startQueryCheck();
                if (gpuTimeCollector.endQueryCheck()) {
                    gpuTimeCollectorIterator.remove();
                    collectorPool.returnObject(gpuTimeCollector);
                }
            } else {
                gpuTimeCollectorIterator.remove();
                collectorPool.returnObject(gpuTimeCollector);
            }
        }

        if (gpuTimeCollectorDeque.isEmpty()) {
            return null;
        } else {
            if (getEstimateGpuTime() == null || estimateCpuTime == null) {
                return null;
            }

            long waitTime;
            if (gpuTimeCollectorDeque.getLast().startTimeSystem == null) {
                waitTime = getEstimateGpuTime() * gpuTimeCollectorDeque.size() - estimateCpuTime;
            } else {
                waitTime = gpuTimeCollectorDeque.getLast().startTimeSystem
                        + getEstimateGpuTime() * gpuTimeCollectorDeque.size()
                        - estimateCpuTime - System.nanoTime();
            }

            waitTime -= com.aurora.client.config.AuroraConfig.get().reflexWaitTimeOffset;
            if (waitTime > 0) {
                return waitTime;
            } else {
                return null;
            }
        }
    }

    private static final double NS_TO_SECONDS = 1e-9;

    public void waitBeforeRender() {
        while (true) {
            Long waitTime = calculateWaitTime();
            boolean enabled = com.aurora.client.config.AuroraConfig.get().reflexEnabled;
            if (waitTime != null && enabled) {
                GLFW.glfwWaitEventsTimeout(waitTime * NS_TO_SECONDS);
            } else {
                break;
            }
        }
    }

    private GpuTimeCollector currentOperateGpuTimeCollector = null;
    private RenderQueueAction lastRenderQueueAction = null;

    public void renderQueueAdd() {
        if (lastRenderQueueAction != RenderQueueAction.END_INSERT && lastRenderQueueAction != null) {
            gpuTimeCollectorDeque.remove(currentOperateGpuTimeCollector);
            collectorPool.returnObject(currentOperateGpuTimeCollector);
            currentOperateGpuTimeCollector = null;
            lastRenderQueueAction = null;
        }

        GpuTimeCollector gpuTimeCollector = collectorPool.borrow();
        gpuTimeCollector.setCallback(
                null, () -> {
                    updateGpuTime(gpuTimeCollector.endTimeSystem - gpuTimeCollector.startTimeSystem);
                });
        gpuTimeCollector.startQueryInsert();
        gpuTimeCollectorDeque.addFirst(gpuTimeCollector);
        currentOperateGpuTimeCollector = gpuTimeCollector;
        lastRenderQueueAction = RenderQueueAction.ADD;
    }

    public void renderQueueEndInsert() {
        if (lastRenderQueueAction != RenderQueueAction.ADD) {
            gpuTimeCollectorDeque.remove(currentOperateGpuTimeCollector);
            collectorPool.returnObject(currentOperateGpuTimeCollector);
            currentOperateGpuTimeCollector = null;
            lastRenderQueueAction = null;
            return;
        }
        currentOperateGpuTimeCollector.endQueryInsert();
        lastRenderQueueAction = RenderQueueAction.END_INSERT;
    }

    enum RenderQueueAction {
        ADD,
        END_INSERT
    }

    // --- Exception safety (2026-09-13, from a real gameplay crash) ----------
    //
    // Reflex hooks directly into Minecraft's frame loop (three injections in
    // ReflexMinecraftMixin, delegating here), so any exception escaping those
    // hooks propagates into vanilla's own critical paths — that is exactly
    // how the 2026-09-13 crash died: a server-transfer disconnect ran queued
    // tasks mid-frame, left a GPU query pair half-processed, and the next
    // hook's IllegalStateException("startTimeGpu is null") interrupted
    // Minecraft.disconnect mid-teardown; the half-torn state NPE'd
    // GameRenderer.renderHand the following frame. Reflex is a non-essential
    // latency optimization, so it takes the same defense-in-depth posture as
    // BlurPanelRenderer's permanentlyDisabled latch: on ANY unexpected
    // failure inside a render hook, log once with the stack and disable
    // pacing for the rest of the session instead of ever throwing into
    // vanilla. The reflexEnabled setting is untouched — the next launch
    // retries.

    private boolean sessionDisabled = false;
    private final CpuTimeCollector cpuTimeCollect = new CpuTimeCollector();

    public boolean isSessionDisabled() {
        return sessionDisabled;
    }

    /**
     * Runs one render-hook body. Latched off after the first failure, so a
     * broken Reflex costs its pacing (and nothing else) for the session.
     * Public because it is the exact guard the frame hooks run under — the
     * dev harness drives it directly to verify the catch-and-latch behavior.
     */
    public void runGuarded(Runnable body) {
        if (sessionDisabled) {
            return;
        }
        try {
            body.run();
        } catch (Throwable t) {
            disableForSession(t);
        }
    }

    private void disableForSession(Throwable cause) {
        sessionDisabled = true;
        LOGGER.error("[Reflex] unexpected exception inside the Reflex render hook — Reflex latency "
                + "pacing is now disabled for the rest of this session so it cannot interfere with "
                + "the game (the reflexEnabled setting is untouched; pacing returns on next launch). "
                + "Skipping pacing changes nothing else about gameplay. Cause:", cause);
        try {
            // Drain half-processed collectors; reset() releases their outstanding
            // GL query objects. Cleanup must never re-throw — this runs inside
            // the failure handler on the render thread.
            for (GpuTimeCollector c : gpuTimeCollectorDeque) {
                collectorPool.returnObject(c);
            }
            gpuTimeCollectorDeque.clear();
        } catch (Throwable cleanup) {
            LOGGER.warn("[Reflex] collector cleanup after session-disable failed (ignored)", cleanup);
        }
        currentOperateGpuTimeCollector = null;
        lastRenderQueueAction = null;
        cpuTimeCollect.reset();
    }

    /** Frame head (runTick HEAD): pace, then open a new GPU timing window. */
    public void beginFrame() {
        runGuarded(() -> {
            waitBeforeRender();
            cpuTimeCollect.startCollect();
            renderQueueAdd();
        });
    }

    /** Just before the swapchain present (Window.updateDisplay). */
    public void beforeFlush() {
        runGuarded(this::renderQueueEndInsert);
    }

    /** Just after GameRenderer.render: harvest this frame's CPU timing. */
    public void endFrame() {
        runGuarded(() -> {
            Long cpuTime = null;
            if (!gpuTimeCollectorDeque.isEmpty()) {
                gpuTimeCollectorDeque.getFirst().startQueryCheck();
            }
            if (!gpuTimeCollectorDeque.isEmpty() && gpuTimeCollectorDeque.getFirst().startTimeSystem != null) {
                if (cpuTimeCollect.startTime != null) {
                    cpuTime = gpuTimeCollectorDeque.getFirst().startTimeSystem - cpuTimeCollect.startTime;
                }
            } else {
                cpuTimeCollect.endCollect();
                cpuTime = cpuTimeCollect.getCpuTime();
            }
            cpuTimeCollect.reset();
            if (cpuTime != null) {
                updateCpuTime(cpuTime);
            }
        });
    }
}
