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

    private final float alpha = 0.2f;
    private final AutomaticReflexPolicy automatic = new AutomaticReflexPolicy();
    private boolean capabilitiesChecked;
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
        if (!automatic.acceptGpuSample(gpuTimeNs)) return;
        gpuTimeRingBuffer[ringBufferIndex] = gpuTimeNs;
        ringBufferIndex = (ringBufferIndex + 1) % gpuWindowSize;
        validSamples = Math.min(validSamples + 1, gpuWindowSize);
    }

    public Long getEstimateGpuTime() {
        if (validSamples == 0) return null;

        float weightedSum = 0;
        float weightSum = 0;
        for (int i = 0; i < validSamples; i++) {
            int idx = (ringBufferIndex - 1 - i + gpuWindowSize) % gpuWindowSize;
            weightedSum += gpuTimeRingBuffer[idx] * gpuWeights[i];
            weightSum += gpuWeights[i];
        }
        return (long) (weightedSum / weightSum);
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


            if (waitTime > 0) {
                return waitTime;
            } else {
                return null;
            }
        }
    }

    private static final double NS_TO_SECONDS = 1e-9;

    public void waitBeforeRender() {
        // Measurements determine the pacing budget. No wait in menus, while paused,
        // or out of focus; no sleep until we have a useful GPU-bound queue.
        Long wait = calculateWaitTime(); // retire completed samples even during warmup / CPU-bound frames
        Long gpu = getEstimateGpuTime();
        var client = net.minecraft.client.Minecraft.getInstance();
        if (gpu == null || estimateCpuTime == null || client.player == null
                || client.screen != null || !client.isWindowActive() || client.isPaused()) {
            return;
        }
        long budget = AutomaticReflexPolicy.waitBudget(estimateCpuTime, gpu, validSamples, gpuTimeCollectorDeque.size());
        long deadline = System.nanoTime() + budget;
        while (budget > 0) {
            long remaining = deadline - System.nanoTime();
            if (wait == null || remaining <= 0) break;
            GLFW.glfwWaitEventsTimeout(Math.min(wait, remaining) * NS_TO_SECONDS);
            wait = calculateWaitTime();
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
    // vanilla. The failure latch is session-scoped — the next launch
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
                + "the game (automatic pacing returns on next launch). "
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
            if (!capabilitiesChecked) {
                capabilitiesChecked = true;
                // Blaze3D exposes no backend-neutral GPU timestamp API. Never
                // enter LWJGL's native GL dispatch on a Vulkan-only window.
                if (!com.aurora.client.ui.render.RenderBackend.isOpenGl()) {
                    sessionDisabled = true;
                    LOGGER.info("[Reflex] GPU timestamp pacing unavailable on {}; frame caps remain active",
                            com.mojang.blaze3d.systems.RenderSystem.getDevice().getBackendName());
                    return;
                }
                var caps = org.lwjgl.opengl.GL.getCapabilities();
                if (!caps.OpenGL33 && !caps.GL_ARB_timer_query) {
                    disableForSession(new UnsupportedOperationException("GPU timestamp queries unavailable"));
                    return;
                }
            }
            automatic.observeQueue(gpuTimeCollectorDeque.size());
            if (automatic.failed()) {
                disableForSession(new IllegalStateException("Unreliable GPU timings or persistently stalled queue"));
                return;
            }
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
            cpuTimeCollect.endCollect();
            Long cpuTime = cpuTimeCollect.getCpuTime();
            cpuTimeCollect.reset();
            if (cpuTime != null && cpuTime > 0 && cpuTime < 250_000_000L) {
                updateCpuTime(cpuTime);
            }
        });
    }
}
