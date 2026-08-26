package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.AuroraConfig.Waypoint;
import com.aurora.client.feature.Feature;
import com.aurora.client.util.AuroraKey;
import com.aurora.client.util.WorldScope;
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
    private final AuroraKey.EdgeDetector dropEdge    = new AuroraKey.EdgeDetector();
    private final AuroraKey.EdgeDetector managerEdge = new AuroraKey.EdgeDetector();

    @Override public String id() { return ID; }

    @Override
    public void onRegister() { instance = this; }

    @Override
    public void onTick(Minecraft client) {
        if (client == null) return;
        AuroraConfig cfg = AuroraConfig.get();

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
        if (p == null) { wasAlive = true; lastLivingPos = null; return; }

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
        if (w == null) return;
        listForCurrentWorld(true).add(w);
        AuroraConfig.save();
    }

    /** Removes the given waypoint instance (identity match) from the current world. */
    public void remove(Waypoint w) {
        List<Waypoint> list = listForCurrentWorld(false);
        if (list == null) return;
        if (list.removeIf(x -> x == w)) {
            AuroraConfig.save();
        }
    }

    /** Persist after an in-place edit (rename / recolor / move). */
    public void touch() { AuroraConfig.save(); }

    /**
     * Returns a live view of the current world's waypoint list — mutations
     * by the manager screen are reflected without any further plumbing.
     * Never null. Empty list when there is no world loaded.
     */
    public List<Waypoint> currentWorldWaypoints() {
        List<Waypoint> list = listForCurrentWorld(false);
        return list == null ? java.util.Collections.emptyList() : list;
    }

    // ===== Internals =====

    private void dropDeathWaypoint(AuroraConfig cfg) {
        if (cfg.deathWaypointReplacesPrevious) {
            List<Waypoint> list = listForCurrentWorld(false);
            if (list != null) {
                for (Iterator<Waypoint> it = list.iterator(); it.hasNext(); ) {
                    if (it.next().death) it.remove();
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
                for (Waypoint x : list) if (x.death) deaths++;
                int excess = deaths - (cap - 1);
                if (excess > 0) {
                    List<Waypoint> deathWps = new ArrayList<>();
                    for (Waypoint x : list) if (x.death) deathWps.add(x);
                    deathWps.sort(java.util.Comparator.comparingLong(x -> x.createdAtMs));
                    for (int i = 0; i < excess && i < deathWps.size(); i++) {
                        list.remove(deathWps.get(i));
                    }
                }
            }
        }

        // Prefer the cached pre-death pos when present; on the very first
        // tick after world join we might not have one yet.
        BlockPos pos = lastLivingPos != null ? lastLivingPos : BlockPos.ZERO;
        String dim = lastLivingDim != null ? lastLivingDim : "minecraft:overworld";
        String label = "Death @ " + LocalTime.now().format(HHMM);
        Waypoint w = new Waypoint(label, pos.getX(), pos.getY(), pos.getZ(), dim, cfg.deathWaypointColor);
        w.death = true;
        listForCurrentWorld(true).add(w);
        AuroraConfig.save();
    }

    private List<Waypoint> listForCurrentWorld(boolean createIfMissing) {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.waypointsByWorld == null) {
            cfg.waypointsByWorld = new java.util.HashMap<>();
        }
        String key = WorldScope.current();
        if ("none".equals(key)) return createIfMissing ? new ArrayList<>() : null;
        List<Waypoint> list = cfg.waypointsByWorld.get(key);
        if (list == null && createIfMissing) {
            list = new ArrayList<>();
            cfg.waypointsByWorld.put(key, list);
        }
        return list;
    }
}
