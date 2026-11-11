package com.aurora.client.worldmap;

import com.aurora.client.config.AuroraConfig.Waypoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/** Bounded nearest-first selection shared by all marker presentations. No mutation. */
public final class WaypointQuery {
    private WaypointQuery() {}

    public static double distanceSquared(Waypoint waypoint, double x, double z) {
        double dx = waypoint.x + .5 - x, dz = waypoint.z + .5 - z;
        return dx * dx + dz * dz;
    }

    public static boolean visible(Waypoint w, String dimension, Set<String> hidden) {
        return w != null && w.visible && dimension.equals(w.dimension)
                && (hidden == null || !hidden.contains(w.group));
    }

    public static List<Waypoint> select(List<Waypoint> source, String dimension,
            Set<String> hidden, String selectedId, double x, double z, double radius, int cap) {
        if (source.isEmpty()) return List.of();
        int limit = MapTransform.bounded(cap, 1, 256);
        Comparator<Waypoint> nearest = Comparator
                .comparingInt((Waypoint w) -> w.id != null && w.id.equals(selectedId) ? 0 : 1)
                .thenComparingDouble(w -> distanceSquared(w, x, z))
                .thenComparing(w -> w.id == null ? "" : w.id);
        PriorityQueue<Waypoint> queue = new PriorityQueue<>(limit, nearest.reversed());
        double radiusSquared = radius * radius;
        for (Waypoint w : source) {
            if (!visible(w, dimension, hidden) || distanceSquared(w, x, z) > radiusSquared) continue;
            if (queue.size() < limit) queue.add(w);
            else if (nearest.compare(w, queue.peek()) < 0) { queue.poll(); queue.add(w); }
        }
        List<Waypoint> result = new ArrayList<>(queue);
        result.sort(nearest);
        return List.copyOf(result);
    }
}
