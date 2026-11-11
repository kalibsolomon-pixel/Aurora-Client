package com.aurora.client.feature.impl;

import com.aurora.client.feature.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Tracks the distance between the player and whatever entity they most
 * recently attacked. Rather than mixing into attack logic, we poll the
 * crosshair target on the attack key press edge ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â close enough for a
 * display module and mixin-free.
 *
 * <p>Misses (attacking air, blocks, or no target) don't update state, so
 * the module keeps displaying the previous valid reach through its fade.
 */
public class ReachTrackerFeature implements Feature {
    public static final String ID = "reach_tracker";

    public static volatile double lastReach = 0.0;
    public static volatile long   lastReachTime = 0L;

    private boolean lastAttack = false;

    @Override public String id() { return ID; }

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.player == null || client.options == null) return;

        boolean attacking = client.options.keyAttack.isDown();
        if (attacking && !lastAttack) {
            HitResult target = client.hitResult;
            if (target instanceof EntityHitResult ehr && ehr.getEntity() != null) {
                double d = client.player.getEyePosition().distanceTo(ehr.getLocation());
                lastReach = d;
                lastReachTime = System.currentTimeMillis();
            }
            // If there's no entity target, leave previous values intact so
            // the current display fade can finish cleanly.
        }
        lastAttack = attacking;
    }
}