package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import com.aurora.client.util.AuroraKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks "totem of undying" activations. The hook is
 * {@link com.aurora.client.mixin.ClientPacketListenerEntityEventMixin},
 * which forwards every entity event with status byte
 * {@link #TOTEM_ACTIVATION_STATUS} into {@link #recordPop(Entity)}.
 *
 * <p>Two parallel counters are maintained:
 * <ul>
 *   <li>{@code selfPops} — your own totem pops since the session began
 *       (or since the last reset, depending on
 *       {@link AuroraConfig#totemResetOnDeath}).</li>
 *   <li>{@code otherPops} — UUID → count for every other player whose
 *       totem we observed pop. Insertion-ordered so the HUD can show
 *       "most recent first".</li>
 * </ul>
 *
 * <p>This is purely informational. No automation, no targeting — read
 * from a packet that every vanilla client already processes for the
 * particle/sound effect.
 */
public class TotemPopFeature implements Feature {
    public static final String ID = "totem_pop";

    /** Vanilla {@code EntityEvent.TOTEM_OF_UNDYING} byte. */
    public static final byte TOTEM_ACTIVATION_STATUS = 35;

    private static TotemPopFeature instance;
    public static TotemPopFeature get() { return instance; }

    private int selfPops = 0;
    /** Display-name cache: UUID → last-seen username. Refreshed on every pop. */
    private final Map<UUID, String> names = new HashMap<>();
    /** UUID → pop count, insertion-ordered for "most recent first" UIs. */
    private final Map<UUID, Integer> otherPops = new LinkedHashMap<>();

    private boolean wasAlive = true;
    private final AuroraKey.EdgeDetector resetEdge = new AuroraKey.EdgeDetector();

    @Override public String id() { return ID; }

    @Override
    public void onRegister() {
        instance = this;
    }

    @Override
    public void onTick(Minecraft client) {
        if (client == null) return;
        AuroraConfig cfg = AuroraConfig.get();

        // Manual reset keybind — works regardless of resetOnDeath setting.
        if (resetEdge.justPressed(cfg.totemResetKey)) {
            resetAll();
        }

        if (!cfg.totemResetOnDeath) return;
        LocalPlayer p = client.player;
        if (p == null) { wasAlive = true; return; }

        boolean aliveNow = !p.isDeadOrDying();
        // Falling edge — player just died this tick. Reset self counter.
        if (wasAlive && !aliveNow) {
            selfPops = 0;
        }
        wasAlive = aliveNow;
    }

    /** Called by the mixin on every received status-35 entity event. */
    public void recordPop(Entity entity) {
        if (entity == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        if (entity.getUUID().equals(mc.player.getUUID())) {
            selfPops++;
            return;
        }
        if (entity instanceof Player other) {
            UUID id = other.getUUID();
            otherPops.merge(id, 1, Integer::sum);
            names.put(id, other.getName().getString());
        }
    }

    public int selfPops() { return selfPops; }
    public Map<UUID, Integer> otherPops() { return otherPops; }
    public String nameOf(UUID id) { return names.getOrDefault(id, id.toString().substring(0, 8)); }

    /** Manual reset (config button / future keybind). */
    public void resetAll() {
        selfPops = 0;
        otherPops.clear();
        names.clear();
    }
}
