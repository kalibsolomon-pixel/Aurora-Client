package com.aurora.client.hud;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Unified alert queue for all notification-style features (low durability,
 * low hunger, effect expiry, etc.).
 *
 * <p>Features call {@link #fire} when a threshold is crossed. The most
 * recent alert is stored as the "active" alert; the renderer
 * ({@link AlertRenderer}) fades it in/out over its duration. Alerts are
 * queued FIFO so a burst of different warnings (e.g. armor + hunger
 * simultaneously) don't overwrite each other instantly — each gets its
 * full screen-time unless superseded by a higher-priority alert.
 *
 * <p>Replaces the per-feature static fields that {@code ArmorAlertFeature}
 * previously held, generalising the same popup + fade pattern to any
 * feature that wants to surface a timed on-screen warning.
 */
public final class AlertManager {

    public enum Priority {
        /** Informational (e.g. effect expiring soon). Lowest urgency. */
        INFO(0),
        /** Warning (e.g. low durability, low hunger). */
        WARNING(1),
        /** Critical (e.g. about to break / starve). */
        CRITICAL(2);

        final int level;
        Priority(int level) { this.level = level; }
    }

    public static final class Alert {
        public final String title;
        public final String subtitle;
        public final ItemStack iconStack;
        public final int color;         // ARGB
        public final Priority priority;
        public final long startMs;
        public final long durationMs;

        Alert(String title, String subtitle, ItemStack iconStack,
              int color, Priority priority, long startMs, long durationMs) {
            this.title = title;
            this.subtitle = subtitle;
            this.iconStack = iconStack;
            this.color = color;
            this.priority = priority;
            this.startMs = startMs;
            this.durationMs = durationMs;
        }

        public long ageMs() { return System.currentTimeMillis() - startMs; }
        public boolean isExpired() { return ageMs() >= durationMs; }
    }

    private static final int MAX_QUEUE = 6;

    /** The alert currently being rendered (or most-recently-fired). */
    private static volatile Alert active = null;
    /** FIFO queue of alerts waiting to become active. */
    private static final Deque<Alert> queue = new ArrayDeque<>();

    private AlertManager() {}

    /**
     * Fire a new alert. If there is no active alert or the new one is
     * higher-priority, it becomes active immediately. Otherwise it is
     * queued and shown when the current one expires.
     *
     * <p>Also plays the configured alert sound (shared across all alert
     * sources) if {@link AuroraConfig#armorAlertSound} is enabled.
     */
    public static synchronized void fire(String title, String subtitle,
                                          ItemStack iconStack, int color,
                                          Priority priority, long durationMs) {
        Alert a = new Alert(title, subtitle, iconStack, color, priority,
                System.currentTimeMillis(), durationMs);

        if (active == null || active.isExpired() || priority.level > active.priority.level) {
            active = a;
            queue.clear(); // a higher-priority alert flushes pending lowers
        } else {
            // Avoid queuing an exact duplicate (same title) — spam guard.
            if (queue.size() < MAX_QUEUE) {
                boolean dup = active.title.equals(title);
                if (!dup) {
                    for (Alert q : queue) {
                        if (q.title.equals(title)) { dup = true; break; }
                    }
                }
                if (!dup) queue.addLast(a);
            }
        }

        playAlertSound();
    }

    /**
     * Convenience for firing a warning with the standard 3-second duration.
     */
    public static void fire(String title, String subtitle, ItemStack iconStack, int color) {
        fire(title, subtitle, iconStack, color, Priority.WARNING, 3_000L);
    }

    /**
     * Returns the active alert, or {@code null} if none is showing.
     * Promotes the next queued alert if the current one has expired.
     */
    public static synchronized Alert getActive() {
        if (active != null && active.isExpired()) {
            active = queue.pollFirst();
        }
        return active;
    }

    /** Clears all active and queued alerts. */
    public static synchronized void clear() {
        active = null;
        queue.clear();
    }

    // ===== Sound =====

    private static void playAlertSound() {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.armorAlertSound) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        SoundEvent ev = soundForChoice(cfg.armorAlertSoundChoice);
        if (ev != null) {
            mc.player.playSound(ev, 1.0f, 0.6f);
        }
    }

    /** Shared preview used by the "Test Sound" button in the Alerts UI. */
    public static void playPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        SoundEvent ev = soundForChoice(AuroraConfig.get().armorAlertSoundChoice);
        if (ev != null) mc.player.playSound(ev, 1.0f, 0.6f);
    }

    private static SoundEvent soundForChoice(AuroraConfig.ArmorAlertSoundChoice c) {
        if (c == null) return SoundEvents.NOTE_BLOCK_PLING.value();
        return switch (c) {
            case PLING          -> SoundEvents.NOTE_BLOCK_PLING.value();
            case EXPERIENCE_ORB -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case BELL           -> SoundEvents.BELL_BLOCK;
            case ARMOR_EQUIP    -> SoundEvents.ARMOR_EQUIP_GENERIC.value();
            case ARROW_HIT      -> SoundEvents.ARROW_HIT_PLAYER;
            case ANVIL_LAND     -> SoundEvents.ANVIL_LAND;
            case NOTE_BASS      -> SoundEvents.NOTE_BLOCK_BASS.value();
        };
    }
}