package com.aurora.client.hitreg;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import com.aurora.client.hitreg.settings.Settings;
import com.aurora.client.hitreg.util.HitTracker;
import com.aurora.client.hitreg.util.MultiVersion;
import com.aurora.client.hitreg.util.OnlyAnimate;
import com.aurora.client.hitreg.util.Scheduler;

import static com.aurora.client.hitreg.Hitreg.*;
import static com.aurora.client.hitreg.Hitreg.alreadyAnimated;
import static com.aurora.client.hitreg.Hitreg.alreadyKnockedBack;
import static com.aurora.client.hitreg.Hitreg.lastTarget;
import static com.aurora.client.hitreg.Hitreg.newTarget;
import static com.aurora.client.hitreg.Hitreg.sprintIsReset;
import static com.aurora.client.hitreg.util.MultiVersion.*;

/**
 * From BetterHitreg by Jass (modrinth.com/mod/betterhitreg), integrated into
 * Aurora with the author's permission. Logic is upstream's, moved verbatim —
 * see {@link com.aurora.client.hitreg.BetterHitreg} for the integration notes.
 */
public class Hit {
    public LivingEntity target;
    public float cooldown;
    public boolean tooEarlyForDamage;
    public boolean tooEarlyForSpecial;
    public boolean hadShield;
    public boolean wasBlocked;
    public boolean wasSprinting;
    public boolean wasMovingFast;
    public boolean wasMovingForward;
    public boolean wasFalling;
    public boolean wasOnGround;
    public boolean wasClimbing;
    public boolean wasTouchingWater;
    public boolean wasInVehicle;
    public boolean wasBlind;
    public boolean wasInvisible;
    public boolean wasHoldingSword;
    public boolean swordHadSharpness;
    public boolean sprintWasReset;

    public boolean shouldAnimate;
    public boolean shouldKnockback;
    public boolean shouldCrit;
    public boolean shouldSweep;
    public boolean shouldPick;
    public boolean shouldFullPick;
    public boolean shouldHalfPick;
    public boolean shouldSpawnSharpnessParticles;

    public SoundEvent expectedSound;
    public HitType type;
    public boolean wasAnimated;
    public boolean wasNewTarget;
    public boolean wasHitByAnother;
    public long timestamp;

    public Hit() {
        timestamp = System.currentTimeMillis();
    }

    //Aurora keeps only the custom hitreg (2026-11-15): the animation, sound and particle toggles
    //upstream read here were removed, so each behaves as its default (off)
    public void updateSettings() {
        shouldAnimate = !tooEarlyForDamage;
        shouldSpawnSharpnessParticles = swordHadSharpness;
    }

    public void load() {
        shouldKnockback = !tooEarlyForSpecial && wasSprinting && sprintWasReset;
        shouldCrit = !tooEarlyForSpecial && !shouldKnockback && wasFalling && !wasOnGround && !wasClimbing && !wasTouchingWater && !wasInVehicle && !wasBlind;
        //version 1.21.1-
        //shouldSweep = !tooEarlyForSpecial && !shouldKnockback && wasHoldingSword && wasOnGround && !wasMovingFast;

        //version 1.21.2+
        shouldSweep = !tooEarlyForSpecial && !shouldKnockback && wasHoldingSword && wasOnGround && !wasMovingFast && !wasMovingForward;
        shouldPick = !shouldKnockback && !shouldCrit && !shouldSweep;
        shouldFullPick = !tooEarlyForSpecial && shouldPick;
        shouldHalfPick = !shouldFullPick && shouldPick;

        type = HitType.of(this);
        if (type == null) return;
        expectedSound = type.getMainSound();

        //decide once at hit time whether the mod replaces the server's feedback, the target's blocking state may change before the server's packets arrive
        boolean handled = Hitreg.isToggled();
        Hitreg.lastSwingHandled = handled;

        if (!tooEarlyForDamage) {
            HitTracker.add(this);
            Hitreg.lastHitHandled = handled;
        }

        if (handled) Scheduler.schedule(Settings.getHitreg(), this::run);
    }

    public void run() {
        if (target == null) return;
        updateSettings();

        if (shouldAnimate) target.handleDamageEvent(new OnlyAnimate(target.damageSources().generic()));

        Vec3 location = getLerpedPosition(target);
        type.playSounds(location);

        if (shouldCrit) playParticles("CRIT", target);
        if (shouldSpawnSharpnessParticles) playParticles("ENCHANTED_HIT", target);
    }
}
