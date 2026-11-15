package com.aurora.client.hitreg;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import com.aurora.client.hitreg.settings.Toggle;
import com.aurora.client.hitreg.util.*;

/**
 * From BetterHitreg by Jass (modrinth.com/mod/betterhitreg), integrated into
 * Aurora with the author's permission. Logic is upstream's, moved verbatim —
 * see {@link com.aurora.client.hitreg.BetterHitreg} for the integration notes.
 * Aurora keeps only the custom hitreg's timing (2026-11-15): the metronome,
 * sound filters, jump-reset and perfect-hit detection, fight statistics and
 * ping/ground helpers were removed.
 */
public class Hitreg {
    public static Minecraft client;
    public static int lastTarget;
    public static LivingEntity target;
    public static int tick;
    public static long lastAttack;
    public static long lastAttacked;
    public static long lastAnimation;
    public static boolean alreadyAnimated;
    public static boolean alreadyKnockedBack;
    public static boolean wasMovingForward;
    public static boolean sprintIsReset = true;
    public static boolean lastAttackWasBlocked;
    public static boolean lastHitHandled;
    public static boolean lastSwingHandled;
    public static boolean wasGhosted;
    public static boolean newTarget = true;
    public static boolean hitByAnother;
    public static boolean targetHasShield;
    public static boolean targetIsBlocking;
    public static boolean hitWasFarFromPrevious;
    public static Vec3 lastAttackLocation = Vec3.ZERO;
    public static Vec3 targetLocation = Vec3.ZERO;
    public static Vec3 previousTargetLocation = Vec3.ZERO;
    public static boolean bothAlive;
    public static boolean withinFight;
    public static double distance;
    public static int playerId;

    public static void tick() {
        if (client.player == null || client.level == null) return;
        tick++;
        playerId = client.player.getId();

        updateFightState();

        //A hit can briefly toggle the client's sprint state while W remains held. Only a new
        //forward input represents a real sprint reset; otherwise the next hit sounds like a
        //sprint hit even though sprint was never reset.
        boolean movingForward = client.options.keyUp.isDown();
        if (movingForward && !wasMovingForward) sprintIsReset = true;
        wasMovingForward = movingForward;

        //if the fight ended, clear all expected hits to prevent any false ghosts, else remove all unneeded hits naturally
        if (!withinFight) {
            targetLocation = Vec3.ZERO;
            previousTargetLocation = Vec3.ZERO;
            HitTracker.clear();
        } else {
            HitTracker.process();
        }

        //if the target moves backwards, they may be taking knockback
        if (targetTakingKnockback() && !alreadyKnockedBack) {
            alreadyKnockedBack = true;
        }

        if (target != null) {
            previousTargetLocation = targetLocation;
            targetLocation = MultiVersion.getBasePosition(target);
        }
    }

    public static boolean isToggled() {
        if (!Toggle.TOGGLE.toggled()) return false;
        if (!withinFight || targetIsBlocking) return false;
        if (Toggle.SAFE_REGS_ONLY.toggled() && (newTarget || wasGhosted || hitByAnother || hitWasFarFromPrevious)) return false;
        if (Toggle.IGNORE_SHIELD_HOLDERS.toggled() && targetHasShield) return false;
        return true;
    }

    public static void updateFightState() {
        bothAlive = client.player != null && target != null && client.player.isAlive() && target.isAlive() && !client.player.isSpectator() && !target.isSpectator();
        targetHasShield = target != null && Hitreg.target.isHolding(Items.SHIELD);
        //isUsingItem is also true while eating/drinking, isBlocking checks the shield is actually raised
        targetIsBlocking = targetHasShield && Hitreg.target.isBlocking();

        if (bothAlive) {
            distance = distanceToTarget();
            withinFight = distance <= 30;
        } else {
            withinFight = false;
        }
    }

    public static double distanceToTarget() {
        if (client.player == null || target == null) return Double.MAX_VALUE;
        return distanceFrom(MultiVersion.getBasePosition(client.player), MultiVersion.getBasePosition(target));
    }

    public static double distanceFromPlayer(Vec3 position) {
        if (client.player == null) return Double.MAX_VALUE;
        return distanceFrom(MultiVersion.getBasePosition(client.player), position);
    }

    public static double distanceFromTarget(Vec3 position) {
        if (target == null) return Double.MAX_VALUE;
        return distanceFrom(MultiVersion.getBasePosition(target), position);
    }

    public static double distanceFrom(Vec3 a, Vec3 b) {
        if (a == null || b == null) return Double.MAX_VALUE;
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static boolean targetTakingKnockback(double angleThreshold, double minimumSpeed, boolean horizontalOnly) {
        if (client.player == null) return false;

        double mvx = targetLocation.x - previousTargetLocation.x;
        double mvy = targetLocation.y - previousTargetLocation.y;
        double mvz = targetLocation.z - previousTargetLocation.z;

        double pvx = lastAttackLocation.x - targetLocation.x;
        double pvy = lastAttackLocation.y - targetLocation.y;
        double pvz = lastAttackLocation.z - targetLocation.z;

        if (horizontalOnly) {
            mvy = 0;
            pvy = 0;
        }

        //are they moving fast enough
        double mLen = Math.sqrt(mvx*mvx + mvy*mvy + mvz*mvz);
        if (mLen < minimumSpeed) return false;

        //how far away are they
        double pLen = Math.sqrt(pvx*pvx + pvy*pvy + pvz*pvz);
        if (pLen == 0.0) return false;

        //normalized
        double dot = mvx*pvx + mvy*pvy + mvz*pvz;
        double cosAngle = dot / (mLen * pLen);

        //does the movement direction meet our threshold
        double thresholdCos = Math.cos(Math.toRadians(angleThreshold));

        //are they moving away
        return cosAngle < thresholdCos;
    }

    public static boolean targetTakingKnockback() {
        return targetTakingKnockback(120, 1e-3, true);
    }
}
