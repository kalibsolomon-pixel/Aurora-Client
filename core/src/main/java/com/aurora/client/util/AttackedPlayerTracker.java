package com.aurora.client.util;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks UUIDs of players the local user has attacked in the current
 * session/world. Used by the player health indicator feature when its
 * "only attacked players" toggle is on.
 *
 * <p>State is in-memory only — cleared on world join and dimension
 * change. No persistence needed; the use case is short-term combat
 * tracking, not long-term opponent lists.
 */
public final class AttackedPlayerTracker {
    private static final Set<UUID> ATTACKED = new HashSet<>();

    private AttackedPlayerTracker() {}

    public static void recordAttack(UUID target) {
        if (target != null) ATTACKED.add(target);
    }

    public static boolean wasAttacked(UUID target) {
        return target != null && ATTACKED.contains(target);
    }

    public static void clear() {
        ATTACKED.clear();
    }
}