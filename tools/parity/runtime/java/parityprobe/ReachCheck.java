package parityprobe;

import dev.phenomena.combat.events.CombatEvent;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;

/**
 * "pulsar-reach": compares each recorded Pulsar attack reach with the vanilla pick distance that
 * Reach Display shows, and with the frame-interpolated formula Pulsar used before the fix.
 */
public final class ReachCheck {
    static volatile boolean active;
    private static boolean expectCreative;
    private static int attacks, picked, mismatches, legacyOver, creative;
    private static double maxRecorded, maxEmpty, maxVanilla, maxLegacy;

    private ReachCheck() {}

    static void start(boolean creativeMode) {
        expectCreative = creativeMode;
        attacks = picked = mismatches = legacyOver = creative = 0;
        maxRecorded = maxEmpty = maxVanilla = maxLegacy = 0;
        active = true;
    }

    /** Called at the head of ClientCapture.emit, on the client thread, inside Minecraft.tick. */
    public static void observe(CombatEvent.Kind kind, CombatEvent.Attack attack) {
        if (!active || kind != CombatEvent.Kind.ATTACK || attack == null || attack.measurement() == null) return;
        var mc = Minecraft.getInstance();
        var player = mc.player;
        attacks++;
        if (!"MELEE_ATTEMPT".equals(attack.measurement().mode())) creative++;
        if (!(mc.hitResult instanceof EntityHitResult hit)) {
            // An empty swing: a miss distance, which may exceed the attack range.
            maxEmpty = Math.max(maxEmpty, attack.reach());
            return;
        }
        picked++;
        maxRecorded = Math.max(maxRecorded, attack.reach());
        double vanilla = player.getEyePosition().distanceTo(hit.getLocation());
        maxVanilla = Math.max(maxVanilla, vanilla);
        if (Math.abs(vanilla - attack.reach()) > 1e-9) mismatches++;
        // The pre-fix measurement: interpolated eye to the interpolated target box.
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        var target = hit.getEntity();
        var eye = player.getEyePosition(partial);
        var box = target.getBoundingBox().move(target.getPosition(partial).subtract(target.position()))
                .inflate(target.getPickRadius());
        var end = eye.add(player.getViewVector(partial).scale(3));
        double legacy = box.clip(eye, end).map(eye::distanceTo).orElse(Math.sqrt(box.distanceToSqr(eye)));
        maxLegacy = Math.max(maxLegacy, legacy);
        if (legacy > 3 + 1e-9) legacyOver++;
    }

    static void report() {
        active = false;
        // Creative attack range is 5.0; those attacks must all be tagged instead of capped.
        boolean pass = attacks > 0 && picked > 0 && mismatches == 0
                && (expectCreative ? creative == attacks : creative == 0 && maxRecorded <= 3 + 1e-9);
        System.out.println(String.format(Locale.ROOT,
                "[parity-probe] pulsar-reach %s attacks=%d picked=%d maxRecorded=%.4f maxVanilla=%.4f"
                        + " mismatches=%d creativeTagged=%d legacyMax=%.4f legacyOver3=%d emptySwingMax=%.4f",
                pass ? "PASS" : "FAIL", attacks, picked, maxRecorded, maxVanilla, mismatches, creative,
                maxLegacy, legacyOver, maxEmpty));
    }

    /** The saved recording's hit statistics, which feed Average and Maximum reach. */
    static String stats(dev.phenomena.combat.fights.FightRecord fight, boolean creativeMode) {
        var s = fight.statistics();
        var a = dev.phenomena.analytics.FightAnalytics.of(fight);
        if (creativeMode) {
            if (s.hits == 0) throw new AssertionError("No creative hits in " + fight.id());
            if (s.reachCount != 0 || a.reach.eligible() != 0)
                throw new AssertionError("Creative hits entered reach statistics: " + s.reachCount);
            return "pulsar-reach-stats PASS creative hits=" + s.hits + " reachSamples=0";
        }
        if (s.reachCount == 0 || a.reach.eligible() == 0) throw new AssertionError("No reach samples in " + fight.id());
        if (s.maxReach > 3 + 1e-9 || a.reach.max() > 3 + 1e-9)
            throw new AssertionError("Recorded hit reach above 3.0: " + s.maxReach + " / " + a.reach.max());
        return String.format(Locale.ROOT, "pulsar-reach-stats PASS hits=%d reachSamples=%d maxReach=%.4f analyticsMax=%.4f mean=%.4f",
                s.hits, s.reachCount, s.maxReach, a.reach.max(), a.reach.mean());
    }
}
