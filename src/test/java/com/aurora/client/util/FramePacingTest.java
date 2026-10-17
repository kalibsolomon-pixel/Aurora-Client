package com.aurora.client.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.Files;
import java.nio.file.Path;
import static com.aurora.client.util.FramePacing.Owner.*;
import static org.junit.jupiter.api.Assertions.*;

class FramePacingTest {
    @Test void sharedHookInstanceIsInitializedBeforeTheFirstFrame() {
        var off = FramePacing.resolve(260, 0, 60, false, true, false);
        assertEquals(0, FramePacing.INSTANCE.beginFrame(off, 1));
        assertFalse(FramePacing.INSTANCE.postSwap(off, 1).cancelNative());
    }

    @ParameterizedTest(name = "native={0}, global={1}, GUI={2}/{3}, pacer={4}, adaptive={5} -> {6}/{7}")
    @CsvSource({
            "260, 0, 60, false, true, false, NONE, 0",
            "260, 0, 60, false, true, true, NONE, 0",
            "260, 144, 60, false, true, false, FRAME_HEAD, 144",
            "260, 1000, 60, false, true, true, FRAME_HEAD, 1000",
            "60, 144, 60, false, true, true, FRAME_HEAD, 60",
            "144, 60, 60, false, true, false, FRAME_HEAD, 60",
            "10, 144, 60, false, true, true, FRAME_HEAD, 10",
            "30, 0, 60, false, true, true, FRAME_HEAD, 30",
            "10, 0, 60, false, true, true, FRAME_HEAD, 10",
            "60, 0, 60, false, true, true, FRAME_HEAD, 60",
            "30, 0, 60, true, true, true, POST_SWAP, 30",
            "144, 0, 60, true, true, true, POST_SWAP, 60",
            "144, 0, 60, false, true, false, POST_SWAP, 144",
            "144, 0, 60, false, false, true, NATIVE, 144",
            "260, 60, 60, false, false, true, FRAME_HEAD, 60",
            "144, 30, 60, false, false, false, FRAME_HEAD, 30",
            "144, 0, 60, true, false, true, POST_SWAP, 60",
            "144, 30, 60, true, false, true, FRAME_HEAD, 30",
            "144, 0, 1000, true, true, true, POST_SWAP, 144",
            "260, 0, 60, true, true, true, NONE, 0",
            "260, 144, 60, true, true, true, FRAME_HEAD, 144",
            "144, 0, 0, true, false, true, NATIVE, 144",
            "0, 0, 60, false, true, true, NONE, 0",
            "-1, -1, 60, false, true, true, NONE, 0",
            "0, 144, 60, false, false, false, FRAME_HEAD, 144"
    })
    void effectiveLimitsAndOwners(int nativeFps, int global, int gui, boolean auroraGui,
                                  boolean pacer, boolean adaptive, FramePacing.Owner owner, int fps) {
        assertEquals(new FramePacing.Decision(owner, fps),
                FramePacing.resolve(nativeFps, global, gui, auroraGui, pacer, adaptive));
    }

    @Test void oneHeadWaitSuppressesTheCorrespondingPostSwapWait() {
        var pacing = new FramePacing();
        var limit = FramePacing.resolve(120, 60, 60, false, true, false);
        assertEquals(0, pacing.beginFrame(limit, 1));
        assertFalse(pacing.postSwap(limit, 1.001).cancelNative(), "Arming is not an enforced wait");
        assertEquals(1 + 1.0 / 60, pacing.beginFrame(limit, 1.005), 1e-10);
        var after = pacing.postSwap(limit, 1.020);
        assertTrue(after.cancelNative());
        assertEquals(0, after.waitTarget(), "Never apply a second full-period wait");
    }

    @Test void nativeThrottleAppearingDuringSwapMustStillRunNatively() {
        var pacing = new FramePacing();
        var foreground = FramePacing.resolve(120, 0, 60, false, true, true);
        var minimized = FramePacing.resolve(10, 0, 60, false, true, true);
        pacing.beginFrame(foreground, 1);
        assertTrue(pacing.beginFrame(foreground, 1.001) > 0);
        assertFalse(pacing.postSwap(minimized, 1.010).cancelNative());
        assertEquals(0, pacing.beginFrame(minimized, 1.110));
        assertFalse(pacing.postSwap(minimized, 1.111).cancelNative());
        assertEquals(1.210, pacing.beginFrame(minimized, 1.115), 1e-10);
        assertTrue(pacing.postSwap(minimized, 1.215).cancelNative());
    }

    @Test void fpsChangesFocusRecoveryAndOwnershipChangesDiscardOldDeadlines() {
        var pacing = new FramePacing();
        var head30 = new FramePacing.Decision(FRAME_HEAD, 30);
        var head144 = new FramePacing.Decision(FRAME_HEAD, 144);
        var post144 = new FramePacing.Decision(POST_SWAP, 144);
        pacing.beginFrame(head30, 1);
        assertEquals(0, pacing.beginFrame(head144, 1.001));
        assertEquals(1.001 + 1.0 / 144, pacing.beginFrame(head144, 1.002), 1e-10);
        assertEquals(0, pacing.beginFrame(post144, 1.010));
        assertFalse(pacing.postSwap(post144, 1.011).cancelNative());
        pacing.beginFrame(post144, 1.012);
        assertEquals(1.011 + 1.0 / 144, pacing.postSwap(post144, 1.013).waitTarget(), 1e-10);
        assertEquals(0, pacing.beginFrame(head144, 1.020));
        assertEquals(1.020 + 1.0 / 144, pacing.beginFrame(head144, 1.021), 1e-10);
    }

    @Test void unlimitedDisabledAndLateIntervalsDoNotLeaveStaleDeadlines() {
        var pacing = new FramePacing();
        var post = new FramePacing.Decision(POST_SWAP, 60);
        var off = new FramePacing.Decision(NONE, 0);
        var vanilla = new FramePacing.Decision(NATIVE, 60);
        pacing.beginFrame(post, 1);
        pacing.postSwap(post, 1.001);
        pacing.beginFrame(off, 1.002); // no post-swap hook at Unlimited
        pacing.beginFrame(post, 1.003);
        assertFalse(pacing.postSwap(post, 1.004).cancelNative());
        pacing.beginFrame(vanilla, 1.005);
        assertFalse(pacing.postSwap(vanilla, 1.006).cancelNative());
        pacing.beginFrame(post, 1.007);
        assertFalse(pacing.postSwap(post, 1.008).cancelNative());
        pacing.beginFrame(post, 2);
        assertFalse(pacing.postSwap(post, 2.001).cancelNative(), "Late frames reanchor as before");
        pacing.beginFrame(post, 2.002);
        assertEquals(2.001 + 1.0 / 60, pacing.postSwap(post, 2.003).waitTarget(), 1e-10);
    }

    @Test void hooksUseTheTrackerAndOnlyCancelAfterTheOwnershipCheck() throws Exception {
        Path root = Path.of("src/client/java/com/aurora/client/mixin");
        String head = Files.readString(root.resolve("MinecraftClientRenderMixin.java"));
        String post = Files.readString(root.resolve("RenderSystemMixin.java"));
        assertTrue(head.contains("getFramerateLimitTracker().getFramerateLimit()"));
        assertFalse(head.contains("options.framerateLimit()"));
        assertTrue(head.contains("FramePacing.INSTANCE.beginFrame"));
        assertTrue(post.contains("FramePacing.INSTANCE.postSwap"));
        assertEquals(1, post.split("ci.cancel\\(\\)", -1).length - 1);
        assertTrue(post.indexOf("if (!wait.cancelNative()) return;") < post.indexOf("ci.cancel()"));
        assertTrue(post.contains("require = 1"), "A missing limiter interception must fail visibly");
    }
}
