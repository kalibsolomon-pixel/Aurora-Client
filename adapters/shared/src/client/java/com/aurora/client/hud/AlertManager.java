package com.aurora.client.hud;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Popup queue for informational notifications (a profile hotkey switch,
 * say), rendered by {@link AlertRenderer}.
 *
 * <p>The warning alert sources no longer route through here: low
 * durability and effect expiry render as their own movable, resizable HUD
 * modules (module ids {@code armor_alert} and {@code potion_alert}, fed by
 * {@link AlertHudState} from {@code ArmorAlertFeature} and
 * {@code StatusAlertFeature}). What remains is {@link #notify} — a silent
 * confirmation popup — plus the shared alert sound helpers.
 *
 * <p>Alerts are queued FIFO so a burst of confirmations don't overwrite
 * each other instantly — each gets its full screen-time unless superseded
 * by a higher-priority alert.
 */
public final class AlertManager {

    public enum Priority {
        /** Informational (e.g. effect expiring soon). Lowest urgency. */
        INFO(0),
        /** Warning (e.g. low durability). */
        WARNING(1),
        /** Critical (e.g. about to break). */
        CRITICAL(2);

        final int level;
        Priority(int level) { this.level = level; }
    }

    public static final class Alert {
        public final String title;
        public final String subtitle;
        public final ItemStack iconStack;
        public final net.minecraft.resources.Identifier iconSprite;
        public final int color;         // ARGB
        public final Priority priority;
        public final long startMs;
        public final long durationMs;

        Alert(String title, String subtitle, ItemStack iconStack,
              int color, Priority priority, long startMs, long durationMs,
              net.minecraft.resources.Identifier iconSprite) {
            this.title = title;
            this.subtitle = subtitle;
            this.iconStack = iconStack;
            this.iconSprite = iconSprite;
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
     * A silent informational popup — a confirmation rather than a warning
     * (a profile hotkey switch, say), so the alert sound does not play.
     */
    public static synchronized void notify(String title, String subtitle, int color, long durationMs) {
        enqueue(new Alert(title, subtitle, ItemStack.EMPTY, color, Priority.INFO,
                System.currentTimeMillis(), durationMs, null));
    }

    private static void enqueue(Alert a) {
        Priority priority = a.priority;
        String title = a.title;

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

    /**
     * Plays the configured alert sound when {@link AuroraConfig#armorAlertSound}
     * is on. The alert features call this directly when a latch fires; the
     * popup path is gone for those sources (they render as HUD modules via
     * {@link AlertHudState}), but the shared sound choice stays.
     */
    public static void playAlertSound() {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.armorAlertSound) return;
        playPreview();
    }

    /** Shared preview used by the "Test Sound" button in the Alerts UI. */
    public static void playPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        SoundEvent ev = soundForChoice(AuroraConfig.get().armorAlertSoundChoice);
        if (ev != null) mc.getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(ev, 1.0f));
    }

    private static SoundEvent soundForChoice(AuroraConfig.ArmorAlertSoundChoice c) {
        if (c == null) return SoundEvents.NOTE_BLOCK_PLING.value();
        return switch (c) {
            case PLING          -> SoundEvents.NOTE_BLOCK_PLING.value();
            case EXPERIENCE_ORB -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case BELL           -> SoundEvents.BELL_BLOCK;
            case ARMOR_EQUIP    -> SoundEvents.ARMOR_EQUIP_GENERIC.value();
            case ANVIL_LAND     -> SoundEvents.ANVIL_LAND;
            case NOTE_BASS      -> SoundEvents.NOTE_BLOCK_BASS.value();
        };
    }
}
