package com.aurora.client.pulsar;

import dev.phenomena.combat.events.CombatEvent;
import dev.phenomena.combat.metrics.Geometry;
import java.util.Optional;

/**
 * Attack reach as vanilla's attack pick measured it.
 *
 * <p>{@code Minecraft.tick} runs {@code pick(1.0F)} immediately before the attack key is handled,
 * so the hit is decided from the attacker's current eye to the target's current, uninterpolated
 * box, and the survival pick accepts it only within 3.0 m (5.0 m in creative). Measuring from
 * frame-interpolated positions instead shifts both ends by each entity's momentum over the leftover
 * partial tick and reports reach the game never allowed.
 */
public final class PulsarReach {
    /** Measurement mode for an attack made in creative, whose range is not comparable. */
    public static final String CREATIVE_MODE = "MELEE_ATTEMPT_CREATIVE";

    private PulsarReach() {}

    /** Reach from the eye and the point the attack ray contacted the target, if it did. */
    public record Reach(double distance, Optional<Geometry.Vec> contact) {}

    /**
     * @param eye attacker eye at partial tick 1
     * @param view unit view vector at partial tick 1
     * @param box target's current collision box inflated by its pick radius
     * @param maxRange attack range for this weapon and game mode
     * @param pickContact vanilla's entity hit location on this target, or null when the attack did
     *     not come from a pick on it (an empty swing)
     */
    public static Reach measure(
            Geometry.Vec eye, Geometry.Vec view, Geometry.Box box, double maxRange, Geometry.Vec pickContact) {
        if (pickContact != null) return new Reach(eye.distance(pickContact), Optional.of(pickContact));
        var contact = Geometry.intersect(eye, view, box, maxRange);
        return new Reach(contact.map(eye::distance).orElse(box.distance(eye)), contact);
    }

    /** The attack's reach when it is comparable with survival reach; NaN for creative attacks. */
    public static double comparable(CombatEvent.Attack attack) {
        var m = attack.measurement();
        return m != null && CREATIVE_MODE.equals(m.mode()) ? Double.NaN : attack.reach();
    }
}
