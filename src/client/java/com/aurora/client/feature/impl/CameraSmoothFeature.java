package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import com.aurora.client.util.SmoothingMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Exponential smoothing on the camera's yaw/pitch toward the player's actual
 * rotation — a <b>view-space filter only</b>. Applied each frame in
 * CameraUpdateMixin (FreeLook integration is already there; this feature only
 * applies when FreeLook is OFF). The player's real rotation — movement,
 * outgoing movement packets, crosshair picking — is never touched, so while
 * the view is catching up the center of the screen briefly points away from
 * where you actually aim (vanilla pick uses the player's view vector, not the
 * camera's; see the 2026-11-03 responsiveness audit).
 *
 * <p>Strength 0.05–1.0 maps to a time constant via
 * {@code tau = -(1/60) / ln(1 - strength)} — calibrated so each strength value
 * reproduces the legacy constant per-frame lerp factor at 60 FPS — and the
 * per-frame blend is {@code alpha = 1 - exp(-dt / tau)}, making the response
 * frame-rate INDEPENDENT. (The pre-2026-11-03 code applied a constant
 * per-frame factor, so the same strength settled ~4× faster in wall time at
 * 240 FPS than at 60; its javadoc claimed a dt compensation that was never
 * implemented.) The math lives in {@link SmoothingMath} and is unit-pinned.
 *
 * <p>State re-primes (snap to target, no glide) when the tracked player
 * instance is replaced — respawn/dimension change — and after any hitch
 * longer than 250 ms.
 */
public class CameraSmoothFeature implements Feature {
    public static final String ID = "camera_smooth";

    private static final double DT_MAX_SEC = 0.25;
    private static final double DT_MIN_SEC = 1e-5;

    private static volatile float smoothedYaw = 0f;
    private static volatile float smoothedPitch = 0f;
    private static volatile boolean primed = false;
    /**
     * The player instance the smoothed state tracks. A replacement instance
     * (respawn, dimension change — where there may be no null-player tick to
     * reset {@link #primed}) re-primes instead of gliding from the old
     * world's rotation.
     */
    private static volatile LocalPlayer trackedPlayer = null;
    private static volatile long lastApplyNanos = 0L;

    @Override public String id() { return ID; }
    @Override public void onRegister() {}

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.player == null) {
            primed = false;
            trackedPlayer = null;
        }
    }

    /**
     * Called from CameraUpdateMixin BEFORE FreeLook check. Returns true if
     * smoothing was applied (caller should skip vanilla rotation reading).
     */
    public static boolean apply(net.minecraft.client.Camera camera, float tickDelta) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.smoothCamera) return false;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return false;
        LocalPlayer player = client.player;

        float targetYaw = Mth.rotLerp(tickDelta, player.yRotO, player.getYRot());
        float targetPitch = Mth.lerp(tickDelta, player.xRotO, player.getXRot());

        if (!primed || trackedPlayer != player) {
            smoothedYaw = targetYaw;
            smoothedPitch = targetPitch;
            primed = true;
            trackedPlayer = player;
            lastApplyNanos = System.nanoTime();
            return false;
        }

        double strength = Math.max(0.05, Math.min(1.0, cfg.smoothCameraStrength));
        long now = System.nanoTime();

        if (strength >= 1.0) {
            // Instant: no filtering at all.
            smoothedYaw = targetYaw;
            smoothedPitch = targetPitch;
            lastApplyNanos = now;
            return true;
        }

        double dtSec;
        if (lastApplyNanos == 0L) {
            dtSec = 1.0 / 60.0; // one legacy reference frame
        } else {
            dtSec = (now - lastApplyNanos) / 1_000_000_000.0;
            // A pause/hitch must not over-shoot; a stall so long that easing
            // across it would misrepresent the motion just snaps (alpha ~1).
            if (dtSec > DT_MAX_SEC) dtSec = DT_MAX_SEC;
            if (dtSec < DT_MIN_SEC) dtSec = DT_MIN_SEC;
        }
        lastApplyNanos = now;

        double alpha = SmoothingMath.alpha(strength, dtSec);

        // Shortest-arc yaw lerp.
        float yawDelta = Mth.degreesDifference(smoothedYaw, targetYaw);
        smoothedYaw += yawDelta * (float) alpha;
        smoothedYaw = Mth.wrapDegrees(smoothedYaw);
        smoothedPitch += (targetPitch - smoothedPitch) * (float) alpha;

        return true;
    }

    public static float getSmoothedYaw() { return smoothedYaw; }
    public static float getSmoothedPitch() { return smoothedPitch; }
}
