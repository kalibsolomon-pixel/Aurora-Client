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
 *   <li><b>Fights</b> — fed by Better Hitreg's fight state machine
 *       ({@code Hitreg.tick} → {@code Settings.addFight} →
 *       {@link #recordFight}): a fight is an exchange of 10 s–10 min with
 *       at least one landed hit, ended by the two players separating by
 *       more than 30 blocks. Per fight it carries the duration and both
 *       players' swing/hit counts (accuracy). Session fight count, session
 *       fight seconds and the last-fight snapshot live here and reset with
 *       the session; the lifetime totals live on {@code AuroraConfig}
 *       ({@code fightStatsTotalFights} / {@code fightStatsPlaytimeSeconds}),
 *       are excluded from profiles like playtime, and are only cleared by
 *       {@link #resetLifetimeFightTotals()}.</li>
 * </ul>
 *
 * <p><b>Overlap policy (merge of BetterHitreg's stats, 2026-09-08):</b> the
 * two systems measure different things and nothing here was replaced.
 * Kills/deaths/K-D have no hitreg counterpart (hitreg tracks fights, hits
 * and accuracy, never kills). "Session time" is wall-clock since reset;
 * hitreg's "fight playtime" is the sum of tracked fight durations only —
 * both are kept, side by side, under distinct labels. Accuracy and fight
 * counts are hitreg-only and were added as new rows. Had a genuine
 * overlap existed, hitreg's computation would have won.
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

    /** Tracked fights since the session began / last reset. */
    private int sessionFights = 0;
    /** Seconds spent in tracked fights since the session began / last reset. */
    private long sessionFightSeconds = 0L;
    /** Snapshot of the last tracked fight, or {@code null} until one completes. */
    private volatile LastFight lastFight = null;

    /** Immutable record of one completed, tracked fight. */
    public record LastFight(long durationSeconds, int yourHits, int yourSwings,
                            int theirHits, int theirSwings, long endedAtMs) {
        /** Your landed-hit percentage, or -1 when no swing was counted. */
        public int yourAccuracyPct() { return pct(yourHits, yourSwings); }
        /** Their landed-hit percentage, or -1 when no swing was counted. */
        public int theirAccuracyPct() { return pct(theirHits, theirSwings); }
        private static int pct(int hits, int swings) {
            // Same rule the original chat summary used: only shown when both
            // counts are non-zero.
            if (hits == 0 || swings == 0) return -1;
            return Math.round(((float) hits / swings) * 100);
        }
    }

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

    public int sessionFights() { return sessionFights; }
    public long sessionFightSeconds() { return sessionFightSeconds; }
    public LastFight lastFight() { return lastFight; }

    /**
     * Called by Better Hitreg's {@code Settings.addFight} on the client
     * thread when a tracked fight ends (after the lifetime counters on
     * {@code AuroraConfig} have been bumped). Updates the session counters
     * and the last-fight snapshot.
     */
    public void recordFight(long durationSeconds, int yourHits, int yourSwings,
                            int theirHits, int theirSwings) {
        sessionFights++;
        sessionFightSeconds += Math.max(0L, durationSeconds);
        lastFight = new LastFight(durationSeconds, yourHits, yourSwings,
                theirHits, theirSwings, System.currentTimeMillis());
    }

    /**
     * Manual reset (config button / keybind). Clears the SESSION counters
     * and the clock — kills, deaths, session fights, session fight time and
     * the last-fight snapshot. Lifetime fight totals are deliberately not
     * touched; see {@link #resetLifetimeFightTotals()}.
     */
    public void resetAll() {
        kills = 0;
        deaths = 0;
        sessionStartMs = System.currentTimeMillis();
        pending.clear();
        sessionFights = 0;
        sessionFightSeconds = 0L;
        lastFight = null;
    }

    /**
     * Zero the persisted lifetime fight totals inherited from BetterHitreg
     * ({@code total_fights} / {@code fight_playtime_(seconds)}). A separate,
     * explicit action from {@link #resetAll()} because these are per-machine
     * telemetry (like playtime) rather than session state; the Stats
     * screen exposes it as its own button.
     */
    public void resetLifetimeFightTotals() {
        AuroraConfig cfg = AuroraConfig.get();
        cfg.fightStatsTotalFights = 0;
        cfg.fightStatsPlaytimeSeconds = 0L;
        AuroraConfig.save();
    }
}
