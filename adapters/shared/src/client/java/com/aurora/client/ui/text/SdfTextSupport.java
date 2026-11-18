package com.aurora.client.ui.text;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.health.MixinHealth;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class SdfTextSupport {
    private static final long WARMUP_NANOS = 2_000_000;
    private static volatile boolean failed;
    private static boolean gui;
    private static long warmupNanos;

    private SdfTextSupport() {}

    public static boolean enabled(AuroraConfig config) {
        return config.textSdf && available();
    }

    public static boolean resolveSdfStyle(AuroraConfig config) {
        return enabled(config) && (RenderSystem.isOnRenderThread() || Minecraft.getInstance() == null);
    }

    public static boolean available() {
        return !failed && healthy("SdfFontManagerMixin") && healthy("SdfGuiTextMixin")
                && healthy("SdfGlyphRenderStateMixin");
    }

    private static boolean healthy(String mixin) {
        return !MixinHealth.failed(mixin) && !MixinHealth.stoodDown(mixin);
    }

    public static boolean beginGui() {
        boolean previous = gui;
        gui = true;
        return previous;
    }

    public static void endGui(boolean previous) { gui = previous; }

    public static void beginFrame() { warmupNanos = 0; }

    static boolean canWarmGlyph() { return warmupNanos < WARMUP_NANOS; }

    static void recordWarmup(long start) { warmupNanos += System.nanoTime() - start; }

    static boolean drawing() {
        return RenderSystem.isOnRenderThread() && gui && enabled(AuroraConfig.get());
    }

    public static Identifier rasterId(Identifier id) {
        String path = id.getPath();
        if (!"aurora".equals(id.getNamespace()) || path.length() <= 4 || !path.endsWith("_sdf")) return null;
        return id.withPath(path.substring(0, path.length() - 4));
    }

    public static void fail(String stage, Throwable failure) {
        if (failed) return;
        failed = true;
        AuroraClient.LOGGER.warn("[Fonts] SDF text disabled ({}); using raster text for this session", stage, failure);
    }
}
