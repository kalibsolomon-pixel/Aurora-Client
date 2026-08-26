package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import com.aurora.client.util.AuroraKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Passive per-session combat-stat collector backing the Stats HUD
 * ({@code StatsModule}). Mixin-free: kills are inferred by polling the
 * crosshair target on the attack-key down edge (the same approach
 * {@link ReachTrackerFeature} uses) and watching whether a recently-struck
 * {@link LivingEntity} dies shortly after.
 *
 * <ul>
 *   <li><b>Kills</b> — heuristic. When the player presses attack with a
 *       living entity under the crosshair, that entity is remembered for a
 *       short window; if it dies (or is removed) within the window, a kill
 *       is counted. This can't perfectly attribute assists/ranged finishes
 *       client-side, but it is robust for melee and requires no mixin.</li>
 *   <li><b>Deaths</b> — falling edge on {@link LocalPlayer#isDeadOrDying()},
 *       matching {@link WaypointFeature} / {@link TotemPopFeature}.</li>
 *   <li><b>Totem pops</b> — not stored here; the module reads
 *       {@link TotemPopFeature#selfPops()} directly.</li>
 *   <li><b>Session time</b> — wall-clock since the last reset.</li>
 * </ul>
 */
public class StatsTrackerFeature implements Feature {
    public static final String ID = "stats_tracker";

    /** How long after striking an entity its death still counts as our kill. */
    private static final long KILL_WINDOW_MS = 4000L;

    private static StatsTrackerFeature instance;
    public static StatsTrackerFeature get() { return instance; }

    private int kills = 0;
    private int deaths = 0;
    private long sessionStartMs = System.currentTimeMillis();

    private boolean wasAlive = true;
    private boolean lastAttack = false;
    private final AuroraKey.EdgeDetector resetEdge = new AuroraKey.EdgeDetector();

    /** A living entity we recently attacked, pending a possible kill credit. */
    private static final class PendingHit {
        final LivingEntity target;
        long lastHitMs;
        PendingHit(LivingEntity target, long lastHitMs) {
            this.target = target;
            this.lastHitMs = lastHitMs;
        }
    }

    private final List<PendingHit> pending = new ArrayList<>();

    @Override public String id() { return ID; }

    @Override
    public void onRegister() { instance = this; }

    @Override
    public void onTick(Minecraft client) {
        if (client == null) return;
        AuroraConfig cfg = AuroraConfig.get();

        // Manual reset keybind — always live.
        if (resetEdge.justPressed(cfg.statsResetKey)) {
            resetAll();
        }

        LocalPlayer p = client.player;
        if (p == null) {
            wasAlive = true;
            lastAttack = false;
            pending.clear();
            return;
        }

        long now = System.currentTimeMillis();

        // ----- Kills: register the struck entity on the attack down edge.
        boolean attacking = client.options != null && client.options.keyAttack.isDown();
        if (attacking && !lastAttack) {
            HitResult target = client.hitResult;
            if (target instanceof EntityHitResult ehr
                    && ehr.getEntity() instanceof LivingEntity living
                    && living != p && living.isAlive()) {
                registerHit(living, now);
            }
        }
        lastAttack = attacking;

        // ----- Kills: credit recently-struck entities that have since died,
        // and prune stale entries that never died within the window.
        for (Iterator<PendingHit> it = pending.iterator(); it.hasNext(); ) {
            PendingHit ph = it.next();
            if (!ph.target.isAlive()) {
                kills++;
                it.remove();
            } else if (now - ph.lastHitMs > KILL_WINDOW_MS) {
                it.remove();
            }
        }

        // ----- Deaths: falling edge on the local player's death.
        boolean aliveNow = !p.isDeadOrDying();
        if (wasAlive && !aliveNow) {
            deaths++;
        }
        wasAlive = aliveNow;
    }

    private void registerHit(LivingEntity living, long now) {
        for (PendingHit ph : pending) {
            if (ph.target == living) {
                ph.lastHitMs = now;
                return;
            }
        }
        pending.add(new PendingHit(living, now));
    }

    public int kills()  { return kills; }
    public int deaths() { return deaths; }

    /** Kill/death ratio; deaths floored at 1 so a clean session reads as kills. */
    public double kd() {
        return kills / (double) Math.max(1, deaths);
    }

    /** Milliseconds elapsed since the session began / last reset. */
    public long sessionMs() {
        return System.currentTimeMillis() - sessionStartMs;
    }

    /** Manual reset (config button / keybind). Clears counters and the clock. */
    public void resetAll() {
        kills = 0;
        deaths = 0;
        sessionStartMs = System.currentTimeMillis();
        pending.clear();
    }
}
