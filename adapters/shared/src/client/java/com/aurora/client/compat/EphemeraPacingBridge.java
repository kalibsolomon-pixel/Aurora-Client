package com.aurora.client.compat;

import com.aurora.client.AuroraClient;
import com.aurora.client.adapter.NativeUi;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.EphemeraPacingHook;
import com.aurora.client.util.FramePacing;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * Zero-compile-coupling bridge to Ephemera's {@code ephemera:frame_pacer} Fabric ObjectShare
 * channel. Subscribes via {@code ObjectShare#whenAvailable} so mod initialization order never
 * matters and steady-state frame boundaries pay zero lookup overhead.
 *
 * <p>When both Aurora Client and Ephemera are installed:
 * <ul>
 *   <li>Ephemera reads Aurora's effective cap and VSync preference when it owns frame pacing.
 *       Older engines receive Aurora's wait reports and frame deadlines.</li>
 *   <li>Aurora queries whether Ephemera's asynchronous Vulkan swapchain presenter is active so
 *       Aurora's Reflex scheduler does not stack redundant stalls on top of overlapped present.</li>
 *   <li>Both pacers share OS park-overshoot measurements so wake reserves adapt faster.</li>
 * </ul>
 */
public final class EphemeraPacingBridge implements EphemeraPacingHook.Bridge {
    private static final String SHARE_KEY = "ephemera:frame_pacer";
    private static final boolean EPHEMERA_PRESENT = detectPresent();

    private static volatile boolean bound;
    private static volatile Supplier<String> statusLineSupplier;

    private final MethodHandle reportAuroraHandle;
    private final MethodHandle beforeWaitHandle;
    private final MethodHandle afterParkHandle;
    private final BooleanSupplier vulkanAsyncPresent;
    private final IntSupplier recommendedOvershootMicros;
    private final BooleanSupplier ownsFramePacing;

    static {
        if (EPHEMERA_PRESENT) {
            try {
                FabricLoader.getInstance().getObjectShare()
                        .whenAvailable(SHARE_KEY, (key, value) -> onPacerAvailable(value));
            } catch (Throwable t) {
                AuroraClient.LOGGER.warn("[Ephemera] Failed to subscribe to frame-pacer bridge: {}", t.toString());
            }
        }
    }

    private EphemeraPacingBridge(MethodHandle reportAuroraHandle,
                                 MethodHandle beforeWaitHandle,
                                 MethodHandle afterParkHandle,
                                 BooleanSupplier vulkanAsyncPresent,
                                 IntSupplier recommendedOvershootMicros,
                                 BooleanSupplier ownsFramePacing) {
        this.reportAuroraHandle = reportAuroraHandle;
        this.beforeWaitHandle = beforeWaitHandle;
        this.afterParkHandle = afterParkHandle;
        this.vulkanAsyncPresent = vulkanAsyncPresent;
        this.recommendedOvershootMicros = recommendedOvershootMicros;
        this.ownsFramePacing = ownsFramePacing;
    }

    private static boolean detectPresent() {
        try {
            return FabricLoader.getInstance().isModLoaded("ephemera");
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void onPacerAvailable(Object raw) {
        if (bound || !(raw instanceof Map<?, ?> map)) {
            return;
        }
        MethodHandle report = map.get("reportAurora") instanceof MethodHandle h ? h : null;
        MethodHandle before = map.get("beforeWait") instanceof MethodHandle h ? h : null;
        MethodHandle after = map.get("afterPark") instanceof MethodHandle h ? h : null;
        BooleanSupplier vkAsync = map.get("isVulkanAsyncPresentActive") instanceof BooleanSupplier s ? s : () -> false;
        IntSupplier overshoot = map.get("recommendedOvershootMicros") instanceof IntSupplier s ? s : () -> 0;
        if (map.get("statusLine") instanceof Supplier<?> s) {
            statusLineSupplier = (Supplier<String>) s;
        }
        if (report != null) {
            BooleanSupplier owner = registerFrameLimits(map);
            EphemeraPacingHook.install(new EphemeraPacingBridge(report, before, after, vkAsync, overshoot, owner));
            bound = true;
            AuroraClient.LOGGER.info("[Ephemera] Connected cooperative frame-pacer bridge");
        }
    }

    @SuppressWarnings("unchecked")
    private static BooleanSupplier registerFrameLimits(Map<?, ?> map) {
        if (map.get("setAuroraLimits") instanceof BiConsumer<?, ?> register
                && map.get("ownsAuroraPacing") instanceof BooleanSupplier owner) {
            try {
                ((BiConsumer<IntUnaryOperator, BooleanSupplier>) register).accept(
                        EphemeraPacingBridge::effectiveCap, EphemeraPacingBridge::forceVsyncOff);
                return owner;
            } catch (RuntimeException e) {
                AuroraClient.LOGGER.warn("[Ephemera] Could not hand off frame pacing", e);
            }
        }
        return () -> false;
    }

    private static int effectiveCap(int nativeFps) {
        Minecraft client = Minecraft.getInstance();
        var screen = client == null ? null : NativeUi.screen(client);
        boolean auroraGui = screen != null && screen.getClass().getName().startsWith("com.aurora.client.screen");
        AuroraConfig cfg = AuroraConfig.get();
        return FramePacing.effectiveFps(nativeFps, cfg.frameCapFps, cfg.guiFpsLimit, auroraGui);
    }

    private static boolean forceVsyncOff() {
        AuroraConfig cfg = AuroraConfig.get();
        return cfg.lowLatencyRender && cfg.disableVSync;
    }

    /** Triggers class initialization so {@code whenAvailable} subscribes; zero work after bind. */
    public static void ensureBound() {
        if (!EPHEMERA_PRESENT || bound) {
            return;
        }
        try {
            Object raw = FabricLoader.getInstance().getObjectShare().get(SHARE_KEY);
            if (raw != null) {
                onPacerAvailable(raw);
            }
        } catch (Throwable ignored) {}
    }

    /** True once Ephemera's frame-pacer bridge is connected. */
    public static boolean isBound() {
        return bound;
    }

    /** Ephemera's live pacer status line for diagnostics, or empty when disconnected. */
    public static String statusLine() {
        Supplier<String> supplier = statusLineSupplier;
        if (!bound || supplier == null) return "";
        try {
            String s = supplier.get();
            return s != null ? s : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    @Override
    public void reportAurora(boolean auroraOwnsWait, int effectiveTargetFps, long lastWaitNanos) {
        try {
            reportAuroraHandle.invokeExact(auroraOwnsWait, effectiveTargetFps, lastWaitNanos);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onBeforeWait(long plannedWaitNanos) {
        if (beforeWaitHandle == null) return;
        try {
            beforeWaitHandle.invokeExact(plannedWaitNanos);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onAfterPark(long requestedNanos, long actualNanos) {
        if (afterParkHandle == null) return;
        try {
            afterParkHandle.invokeExact(requestedNanos, actualNanos);
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isVulkanAsyncPresentActive() {
        try {
            return vulkanAsyncPresent.getAsBoolean();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public int recommendedOvershootMicros() {
        try {
            return recommendedOvershootMicros.getAsInt();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @Override
    public boolean ownsFramePacing() {
        return ownsFramePacing.getAsBoolean();
    }
}
