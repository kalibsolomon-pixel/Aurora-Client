package com.aurora.client.worldmap;

import com.aurora.client.config.AuroraConfig.Waypoint;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class WaypointQueryTest {
    private static Waypoint waypoint(int x, String dim) {
        return new Waypoint("same name", x, 64, 0, dim, 0xFF123456);
    }
    @Test void filtersAllVisibilityRulesAndKeepsSelectedTargetFirst() {
        Waypoint near = waypoint(1, "minecraft:overworld");
        Waypoint target = waypoint(99, "minecraft:overworld");
        Waypoint hidden = waypoint(0, "minecraft:overworld"); hidden.visible = false;
        Waypoint group = waypoint(0, "minecraft:overworld"); group.group = "mines";
        Waypoint other = waypoint(0, "minecraft:the_nether");
        List<Waypoint> result = WaypointQuery.select(List.of(near, target, hidden, group, other),
                "minecraft:overworld", Set.of("mines"), target.id, 0, 0, 1000, 2);
        assertEquals(List.of(target, near), result);
        assertTrue(WaypointQuery.select(List.of(target), "minecraft:overworld", Set.of(), target.id, 0, 0, 5, 2).isEmpty());
    }
    @Test void largeCollectionsStayBoundedAndNamesAreNotIdentity() {
        List<Waypoint> waypoints = new ArrayList<>();
        for (int i = 9999; i >= 0; i--) waypoints.add(waypoint(i, "test:world"));
        List<Waypoint> selected = WaypointQuery.select(waypoints, "test:world", Set.of(), null, 0, 0, 20000, 64);
        assertEquals(64, selected.size());
        for (int i = 0; i < selected.size(); i++) assertEquals(i, selected.get(i).x);
        assertEquals(10000, waypoints.stream().map(w -> w.id).distinct().count());
        assertEquals(256, WaypointQuery.select(waypoints, "test:world", Set.of(), null, 0, 0, 20000, Integer.MAX_VALUE).size());
    }
    @Test void olderJsonGainsStableIdAndDefaultsWithoutLosingCoordinates() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        Waypoint waypoint = gson.fromJson("{\"name\":\"Old\",\"x\":-513,\"y\":70,\"z\":1025}", Waypoint.class);
        assertNotNull(waypoint.id); assertTrue(waypoint.visible);
        assertEquals(-513, waypoint.x);
        Waypoint reloaded = gson.fromJson(gson.toJson(waypoint), Waypoint.class);
        assertEquals(waypoint.id, reloaded.id);
    }
}
