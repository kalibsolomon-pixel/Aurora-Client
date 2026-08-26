package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import com.aurora.client.util.AuroraKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;

/**
 * FreeLook feature Ã¢â‚¬â€ hold V to look around independently of player movement.
 *
 * Architecture (adapted from Freelook++ by BloodredX, MIT):
 *  - This class holds the active state plus the desired camera yaw/pitch.
 *  - EntityMixin cancels Entity#changeLookDirection on the local player and
 *    redirects MouseHandler deltas into updateRotation() instead.
 *  - CameraUpdateMixin reads the stored yaw/pitch each frame and re-applies
 *    them via Camera#setRotation, after vanilla's own setRotation call.
 *
 * <p>MouseHandler delta scaling uses the player's vanilla sensitivity setting as a
 * baseline, then multiplies by Aurora's own freelook-specific sensitivity
 * slider so the user can tune freelook independently from in-game look.
 */
public class FreeLookFeature implements Feature {
    public static final String ID = "free_look";

    private static volatile boolean active = false;
    private static volatile boolean firstTime = true;

    private static volatile float cameraYaw = 0f;
    private static volatile float cameraPitch = 0f;

    private static CameraType savedPerspective = null;

    @Override public String id() { return ID; }

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.player == null) return;
        if (!AuroraConfig.get().freeLookEnabled) {
            if (active) deactivate(client);
            return;
        }

        boolean held = AuroraKey.isDown(AuroraConfig.get().freeLookKey);
        if (held && !active) activate(client);
        else if (!held && active) deactivate(client);
    }

    private void activate(Minecraft client) {
        active = true;
        firstTime = true;
        savedPerspective = client.options.getCameraType();
        if (savedPerspective == CameraType.FIRST_PERSON) {
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    private void deactivate(Minecraft client) {
        active = false;
        firstTime = true;
        if (savedPerspective != null) {
            client.options.setCameraType(savedPerspective);
            savedPerspective = null;
        }
    }

    // ===== MouseHandler routing (called by EntityMixin) =====

    /**
     * Apply incoming MouseHandler delta to the freelook camera state. Mirrors
     * vanilla's `cursorDeltaX * 0.15` scaling that Entity#changeLookDirection
     * normally applies before mutating yaw/pitch Ã¢â‚¬â€ then multiplies by the
     * user-configured freelook sensitivity slider.
     */
    public static void updateRotation(double deltaX, double deltaY) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        if (firstTime) {
            cameraYaw = mc.player.getYRot();
            cameraPitch = mc.player.getXRot();
            firstTime = false;
        }

        float vanillaSens = mc.options.sensitivity().get().floatValue() * 0.6f + 0.2f;
        float vanillaScale = vanillaSens * vanillaSens * vanillaSens * 8.0f * 0.15f;

        // Aurora's freelook-specific multiplier on top of vanilla scaling.
        // 1.0 = native vanilla feel, 0.1 = very slow, 3.0 = very fast.
        float auroraMult = (float) AuroraConfig.get().freeLookSensitivity;
        if (auroraMult <= 0f) auroraMult = 0.01f;  // guard against zero
        float scale = vanillaScale * auroraMult;

        cameraYaw += (float) (deltaX * scale);
        cameraPitch += (float) (deltaY * scale);
        cameraPitch = Math.max(-90f, Math.min(90f, cameraPitch));
    }

    // ===== Camera mixin accessors =====

    public static boolean isActive() { return active; }
    public static boolean isFirstTime() { return firstTime; }
    public static void markFirstTime(boolean v) { firstTime = v; }
    public static float getCameraYaw() { return cameraYaw; }
    public static float getCameraPitch() { return cameraPitch; }

    public static void seedFromPlayer(float yaw, float pitch) {
        cameraYaw = yaw;
        cameraPitch = pitch;
    }
}