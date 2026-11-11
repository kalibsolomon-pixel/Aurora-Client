package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Exponential smoothing on the camera's yaw/pitch toward the player's actual
 * rotation. Applied each frame in CameraUpdateMixin (FreeLook integration is
 * already there; this feature only applies when FreeLook is OFF).
 *
 * <p>Strength = blend factor per frame. 1.0 = no smoothing (instant). Lower
 * values produce smoother but laggier camera. Default 0.5 = noticeable
 * smoothness, ~3-frame settle time. Frame-rate independence is approximated
 * by raising the factor to (1/dt) â€” close enough for typical 60-240 FPS.
 */
public class CameraSmoothFeature implements Feature {
    public static final String ID = "camera_smooth";

    private static volatile float smoothedYaw = 0f;
    private static volatile float smoothedPitch = 0f;
    private static volatile boolean primed = false;

    @Override public String id() { return ID; }
    @Override public void onRegister() {}

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.player == null) {
            primed = false;
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

        float targetYaw = Mth.rotLerp(tickDelta, client.player.yRotO, client.player.getYRot());
        float targetPitch = Mth.lerp(tickDelta, client.player.xRotO, client.player.getXRot());

        if (!primed) {
            smoothedYaw = targetYaw;
            smoothedPitch = targetPitch;
            primed = true;
            return false;
        }

        double strength = Math.max(0.05, Math.min(1.0, cfg.smoothCameraStrength));

        // Shortest-arc yaw lerp.
        float yawDelta = Mth.degreesDifference(smoothedYaw, targetYaw);
        smoothedYaw += yawDelta * (float) strength;
        smoothedYaw = Mth.wrapDegrees(smoothedYaw);
        smoothedPitch += (targetPitch - smoothedPitch) * (float) strength;

        return true;
    }

    public static float getSmoothedYaw() { return smoothedYaw; }
    public static float getSmoothedPitch() { return smoothedPitch; }
}