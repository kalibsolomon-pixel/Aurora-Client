package com.aurora.client.ui.text;

import com.aurora.client.AuroraClient;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicBoolean;

/** Session state and the one GUI pipeline used by the phase-0 SDF spike. */
public final class SdfTextSupport {
    public static final String PROPERTY = "aurora.text.sdf";
    private static final AtomicBoolean FAILED = new AtomicBoolean();
    /** 0 = not queried, 1 = compiled, -1 = unavailable. */
    private static volatile int pipelineState;

    private SdfTextSupport() {}

    private static final class PipelineHolder {
        private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXT_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("aurora", "pipeline/gui_text_sdf"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("aurora", "core/gui_text_sdf"))
                .build();
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY) && !FAILED.get();
    }

    public static boolean failed() { return FAILED.get(); }
    public static RenderPipeline pipeline() { return PipelineHolder.PIPELINE; }

    /** Logs only the first SDF failure; every later glyph takes its raster delegate. */
    public static void fail(String stage, Throwable failure) {
        pipelineState = -1;
        if (FAILED.compareAndSet(false, true)) {
            AuroraClient.LOGGER.warn("[Fonts] SDF text disabled ({}); using raster text for this session",
                    stage, failure);
        }
    }

    /** A successful query is cached, so styled glyphs add no per-frame pipeline lookup. */
    public static boolean pipelineAvailable() {
        if (FAILED.get() || pipelineState < 0) return false;
        if (pipelineState > 0) return true;
        try {
            var compiled = RenderSystem.getCompiledPipelineNullable(pipeline());
            if (compiled == null || compiled.isClosed()) {
                fail("shader unavailable", null);
                return false;
            }
            pipelineState = 1;
            return true;
        } catch (RuntimeException | LinkageError failure) {
            fail("shader compilation", failure);
            return false;
        }
    }
}
