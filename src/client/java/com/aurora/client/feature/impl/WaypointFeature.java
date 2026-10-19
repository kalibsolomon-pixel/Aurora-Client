package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.AuroraConfig.Waypoint;
import com.aurora.client.feature.Feature;
import com.aurora.client.util.AuroraKey;
import com.aurora.client.util.WorldScope;
import com.aurora.client.worldmap.MapIdentity;
import com.aurora.client.worldmap.MapTransform;
import com.aurora.client.worldmap.WaypointQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Owns the user-managed waypoint list and the death-waypoint auto-drop.
 *
 * <p>Lists are scoped per-world via {@link WorldScope}. The renderer
 * ({@code WaypointRenderer}) consumes {@link #currentWorldWaypoints()} on
 * every frame; this feature handles mutation, persistence, and the
 * death-edge detection.
 *
 * <p>Death detection uses a simple falling-edge on
 * {@link LocalPlayer#isDeadOrDying()}. The player's last known living
 * block position is remembered so that, on the death frame, the waypoint
 * is dropped exactly where the player stood instead of wherever the
 * client thinks they are after the death tick (which is usually the
 * same, but the explicit cache costs nothing).
 */
public class WaypointFeature implements Feature {
    public static final String ID = "waypoints";

    private static final DateTimeFormatter HHMM =
            DateTimeFormatter.ofPattern("HH:mm");

    private static WaypointFeature instance;
    public static WaypointFeature get() { return instance; }

    private boolean wasAlive = true;
    private BlockPos lastLivingPos = null;
    private String   lastLivingDim = "minecraft:overworld";
    private String lastScope;
    private List<Waypoint> nearby = List.of();
    private List<Waypoint> normalizedList;
    private long revision;
    private int visibilityFingerprint;
    public long revision() { return revision; }
    public List<Waypoint> nearbyWaypoints() { return nearby; }
    private final AuroraKey.EdgeDetector dropEdge    = new AuroraKey.EdgeDetector();
    private final AuroraKey.EdgeDetector managerEdge = new AuroraKey.EdgeDetector();

    @Override public String id() { return ID; }

    @Override
    public void onRegister() { instance = this; }

    @Override
    public void onTick(Minecraft client) {
        if (client == null) return;
        AuroraConfig cfg = AuroraConfig.get();
        int fingerprint = java.util.Objects.hash(cfg.waypointMaxVisible, cfg.waypointMaxDistance,
                cfg.waypointHiddenGroups);
        if (fingerprint != visibilityFingerprint) { visibilityFingerprint = fingerprint; revision++; }

        // Manual drop keybind — always live, independent of master enable
        // toggle so users can bind it once and never worry about the tile
        // being off.
        if (dropEdge.justPressed(cfg.waypointDropKey)) {
            dropAtPlayer("Waypoint", 0xFFFFAA00, false);
        }

        // Manager-open keybind. Only fires when no other screen is
        // currently open so it can't steal focus from chat / inventory.
        if (managerEdge.justPressed(cfg.waypointManagerKey) && client.screen == null) {
            client.setScreen(new com.aurora.client.screen.WaypointManagerScreen(null));
        }

        LocalPlayer p = client.player;
        String scope = scopeKey();
        if (!java.util.Objects.equals(scope, lastScope)) {
            wasAlive = true; lastLivingPos = null; lastScope = scope;
            normalizedList = null; revision++;
        }
        if (p == null || scope == null) { wasAlive = true; lastLivingPos = null; nearby = List.of(); return; }

        nearby = WaypointQuery.select(currentWorldWaypoints(),
                client.level.dimension().identifier().toString(), cfg.waypointHiddenGroups,
                navigationId(), p.getX(), p.getZ(),
                MapTransform.bounded(cfg.waypointMaxDistance, 16, 30_000_000), cfg.waypointMaxVisible);

        boolean aliveNow = !p.isDeadOrDying();

        if (aliveNow) {
            // Cache the last-known living spot so a death-frame drop uses
            // exactly the tile the player was standing on at end-of-tick.
            // Store the block the player is standing on top of (Y-1) so
            // death-respawn waypoints mark the floor rather than the
            // empty tile that holds the player's feet.
            lastLivingPos = p.blockPosition().below();
            lastLivingDim = client.level == null
                    ? "minecraft:overworld"
                    : client.level.dimension().identifier().toString();
        }

        // Falling edge — just died this tick.
        if (wasAlive && !aliveNow && cfg.deathWaypointEnabled) {
            dropDeathWaypoint(cfg);
        }

        wasAlive = aliveNow;
    }

    // ===== Public API used by the manager screen / keybind =====

    /**
     * Drops a waypoint on the block the local player is standing on top
     * of (one tile below {@link net.minecraft.world.entity.Entity#blockPosition}),
     * not the tile occupied by the player's feet — this matches what
     * users mean when they say "mark this spot".
     */
    public Waypoint dropAtPlayer(String name, int color, boolean death) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return null;
        BlockPos pos = mc.player.blockPosition().below();
        String dim = mc.level.dimension().identifier().toString();
        Waypoint w = new Waypoint(name, pos.getX(), pos.getY(), pos.getZ(), dim, color);
        w.death = death;
        addWaypoint(w);
        return w;
    }

    /** Appends a waypoint to the current world's list and persists. */
    public void addWaypoint(Waypoint w) {
        if (w == null || scopeKey() == null) return;
        w.id = java.util.UUID.randomUUID().toString();
        listForCurrentWorld(true).add(w);
        touch();
    }

    /** Removes the given waypoint instance (identity match) from the current world. */
    public void remove(Waypoint w) {
        List<Waypoint> list = listForCurrentWorld(false);
        if (list == null) return;
        if (list.removeIf(x -> x == w || (x != null && x.id != null && x.id.equals(w.id)))) {
            if (w.id.equals(navigationId())) navigateTo(null);
            touch();
        }
    }

    /** Persist after an in-place edit (rename / recolor / move). */
    public void touch() { revision++; AuroraConfig.save(); }

    public static String scopeKey() {
        MapIdentity identity = MapIdentity.current();
        return identity == null ? null : identity.key();
    }

    public String navigationId() {
        var map = AuroraConfig.get().waypointNavigationByWorld;
        return map == null ? null : map.get(scopeKey());
    }

    public void navigateTo(Waypoint waypoint) {
        String scope = scopeKey();
        if (scope == null) return;
        var cfg = AuroraConfig.get();
        if (cfg.waypointNavigationByWorld == null) cfg.waypointNavigationByWorld = new java.util.HashMap<>();
        if (waypoint == null) cfg.waypointNavigationByWorld.remove(scope);
        else cfg.waypointNavigationByWorld.put(scope, waypoint.id);
        touch();
    }

    /** User-requested copy from an ambiguous old display-name bucket. Original retained. */
    public int importLegacy() {
        MapIdentity identity = MapIdentity.current();
        if (identity == null) return 0;
        var old = AuroraConfig.get().waypointsByWorld.get(identity.legacyScope());
        if (old == null) return 0;
        List<Waypoint> target = listForCurrentWorld(true);
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Waypoint w : target) if (w != null) ids.add(w.id);
        int count = 0;
        com.google.gson.Gson gson = new com.google.gson.Gson();
        for (int index = 0; index < old.size(); index++) {
            Waypoint original = old.get(index);
            if (original == null) continue;
            // Deterministic legacy ID makes repeating the copy idempotent, including old files without IDs.
            String id = java.util.UUID.nameUUIDFromBytes((identity.legacyScope() + "|" +
                    index + "|" + original.name + "|" + original.createdAtMs)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            if (!ids.add(id)) continue;
            Waypoint copy = gson.fromJson(gson.toJson(original), Waypoint.class);
            copy.id = id; target.add(copy); count++;
        }
        touch();
        return count;
    }

    /**
     * Returns a live view of the current world's waypoint list — mutations
     * by the manager screen are reflected without any further plumbing.
     * Never null. Empty list when there is no world loaded.
     */
    public List<Waypoint> currentWorldWaypoints() {
        List<Waypoint> list = listForCurrentWorld(false);
        if (list == null) return List.of();
        if (normalizedList != list) {
            normalizedList = list;
            revision++;
            java.util.Set<String> ids = new java.util.HashSet<>();
            boolean changed = false;
            for (Waypoint w : list) {
                if (w == null) continue;
                if (w.id == null || w.id.isBlank() || !ids.add(w.id)) {
                    w.id = java.util.UUID.randomUUID().toString(); ids.add(w.id); changed = true;
                }
                if (w.dimension == null) { w.dimension = "minecraft:overworld"; changed = true; }
                if (w.name == null) { w.name = "Waypoint"; changed = true; }
                if (w.group == null) { w.group = ""; changed = true; }
                if (w.description == null) { w.description = ""; changed = true; }
                if (w.marker == null) { w.marker = AuroraConfig.WaypointMarker.DIAMOND; changed = true; }
            }
            if (changed) touch();
        }
        return list;
    }

    // ===== Internals =====

    private void dropDeathWaypoint(AuroraConfig cfg) {
        if (cfg.deathWaypointReplacesPrevious) {
            List<Waypoint> list = listForCurrentWorld(false);
            if (list != null) {
                for (Iterator<Waypoint> it = list.iterator(); it.hasNext(); ) {
                    Waypoint marker = it.next();
                    if (marker != null && marker.death) it.remove();
                }
            }
        }

        // Cap the number of retained death markers (oldest auto-pruned by
        // creation time) so a long-running world can't accumulate them
        // unbounded. Pruning mutates this same live list — the single
        // source of truth — and persists with the same save call below.
        if (!cfg.deathWaypointReplacesPrevious) {
            List<Waypoint> list = listForCurrentWorld(false);
            if (list != null) {
                int cap = Math.max(1, cfg.deathWaypointMaxCount);
                int deaths = 0;
                for (Waypoint x : list) if (x != null && x.death) deaths++;
                int excess = deaths - (cap - 1);
                if (excess > 0) {
                    List<Waypoint> deathWps = new ArrayList<>();
                    for (Waypoint x : list) if (x != null && x.death) deathWps.add(x);
                    deathWps.sort(java.util.Comparator.comparingLong(x -> x.createdAtMs));
                    for (int i = 0; i < excess && i < deathWps.size(); i++) {
                        list.remove(deathWps.get(i));
                    }
                }
            }
        }

        // Prefer the cached pre-death pos when present; on the very first
        // tick after world join we might not have one yet.
        if (lastLivingPos == null) return;
        BlockPos pos = lastLivingPos;
        String dim = lastLivingDim != null ? lastLivingDim : "minecraft:overworld";
        String label = "Death @ " + LocalTime.now().format(HHMM);
        Waypoint w = new Waypoint(label, pos.getX(), pos.getY(), pos.getZ(), dim, cfg.deathWaypointColor);
        w.death = true;
        listForCurrentWorld(true).add(w);
        touch();
    }

    private List<Waypoint> listForCurrentWorld(boolean createIfMissing) {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.waypointsByWorld == null) {
            cfg.waypointsByWorld = new java.util.HashMap<>();
        }
        String key = scopeKey();
        if (key == null) return null;
        List<Waypoint> list = cfg.waypointsByWorld.get(key);
        if (list == null && createIfMissing) {
            list = new ArrayList<>();
            cfg.waypointsByWorld.put(key, list);
        }
        return list;
    }
}
