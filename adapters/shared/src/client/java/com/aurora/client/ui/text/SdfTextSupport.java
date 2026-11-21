package com.aurora.client.ui.text;

import com.aurora.client.AuroraClient;
import com.aurora.client.health.MixinHealth;
import com.aurora.client.ui.util.ShaderLoad;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.function.BooleanSupplier;

public final class SdfTextSupport {
    private static final long WARMUP_NANOS = 2_000_000;
    private static volatile boolean failed;
    private static boolean gui;
    private static long warmupNanos;
    /** A pipeline look was answered "not yet", so the log can say the shader loader was waited for. */
    private static boolean pipelineWaited;

    private SdfTextSupport() {}

    /**
     * Distance-field text is the only text path (owner, 2026-11-19); raster text remains only
     * as the fallback when the SDF hooks failed, stood down or the atlas could not be built.
     */
    public static boolean enabled() {
        return available();
    }

    public static boolean resolveSdfStyle() {
        return enabled() && (RenderSystem.isOnRenderThread() || Minecraft.getInstance() == null);
    }

    public static boolean available() {
        return !failed && hooksHealthy();
    }

    /**
     * The hooks' health, re-read only when {@link MixinHealth#generation()} moves: text asks every
     * draw. One volatile word (generation shifted left, health in bit 0) so no thread pairs a new
     * generation with an old answer.
     */
    private static volatile long hooksHealth = -1;

    private static boolean hooksHealthy() {
        long generation = MixinHealth.generation(), cached = hooksHealth;
        if (cached >= 0 && cached >>> 1 == generation) return (cached & 1) != 0;
        boolean healthy = healthy("SdfFontManagerMixin") && healthy("SdfGuiTextMixin") && healthy("SdfGlyphRenderStateMixin");
        hooksHealth = generation << 1 | (healthy ? 1 : 0);
        return healthy;
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
        return RenderSystem.isOnRenderThread() && gui && enabled();
    }

    public static Identifier rasterId(Identifier id) {
        return isSdfFont(id) ? id.withPath(id.getPath().substring(0, id.getPath().length() - 4)) : null;
    }

    /** Whether the resolved style names an Aurora distance-field font alias. */
    public static boolean isSdfStyle(Style style) {
        return style != null && style.getFont() instanceof FontDescription.Resource resource
                && isSdfFont(resource.id());
    }

    private static boolean isSdfFont(Identifier id) {
        String path = id.getPath();
        return "aurora".equals(id.getNamespace()) && path.length() > 4 && path.endsWith("_sdf");
    }

    public static void fail(String stage, Throwable failure) {
        if (failed) return;
        failed = true;
        AuroraClient.LOGGER.warn("[Fonts] SDF text disabled ({}); using raster text for this session", stage, failure);
    }

    /**
     * One look at the SDF text pipeline for an adapter, which supplies the compile. Before the Shader Loader has
     * applied, a compile can only fail because Aurora's shader is not visible yet (RRLS lets screens draw during
     * the first reload), so this answers "not yet" without compiling or failing and the glyph keeps its bitmap.
     * After it, a compile that fails is the real thing: raster text for the session.
     */
    static boolean checkPipeline(BooleanSupplier compiles) {
        if (!ShaderLoad.settled()) {
            pipelineWaited = true;
            return false;
        }
        try {
            if (compiles.getAsBoolean()) {
                AuroraClient.LOGGER.info("[Fonts] SDF text pipeline ready{}",
                        pipelineWaited ? " (after waiting for the shader loader)" : "");
                pipelineWaited = false;
                return true;
            }
            fail("shader unavailable", null);
        } catch (RuntimeException | LinkageError failure) {
            fail("shader compilation", failure);
        }
        return false;
    }

    static void resetForTests() {
        failed = false;
        pipelineWaited = false;
    }
}
