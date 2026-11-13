package com.aurora.client.hud;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Row state the alert HUD modules render, fed by the alert features each
 * tick. Replaces the shared popup card for its two sources:
 *
 * <ul>
 *   <li><b>Armor rows</b> — {@code ArmorAlertFeature}'s durability latch
 *       output: one row per latched item fingerprint (label taken at fire
 *       time, remaining percent, icon, and how many stacks share the
 *       fingerprint).</li>
 *   <li><b>Effect rows</b> — {@code StatusAlertFeature}'s latched
 *       effect-expiry output: one row per effect currently at or below its
 *       threshold (vanilla status sprite, localized name, live seconds
 *       left).</li>
 * </ul>
 *
 * <p>Both lists are swapped wholesale each tick (a volatile reference to a
 * fresh immutable list) so the render thread always reads a consistent
 * snapshot. A module renders while its list is non-empty; the popup card
 * never sees these sources — {@link AlertManager#notify} informational
 * popups (profile hotkey switches) are unaffected.
 */
public final class AlertHudState {

    /** One low-durability row: item fingerprint, fire-time label, icon, remaining %. */
    public record ArmorRow(DurabilityFingerprint fingerprint, String label,
                           ItemStack stack, int remainingPct, int count) {
        /** Fingerprint identity from the pure latch (core, no Minecraft types). */
        public record DurabilityFingerprint(String itemId, int maxDamage, int damage) {
            public static DurabilityFingerprint of(com.aurora.client.alerts.DurabilityLatch.Fingerprint fp) {
                return new DurabilityFingerprint(fp.itemId(), fp.maxDamage(), fp.damage());
            }
        }
    }

    /** One effect-expiry row: registry id, vanilla sprite, localized name, live seconds left. */
    public record EffectRow(String effectId, Identifier sprite, String name, int secondsLeft) {}

    private static volatile List<ArmorRow> armorRows = List.of();
    private static volatile List<EffectRow> effectRows = List.of();

    private AlertHudState() {}

    public static List<ArmorRow> armor() { return armorRows; }
    public static List<EffectRow> effects() { return effectRows; }

    public static void setArmorRows(List<ArmorRow> rows) {
        armorRows = rows == null || rows.isEmpty() ? List.of() : List.copyOf(rows);
    }

    public static void setEffectRows(List<EffectRow> rows) {
        effectRows = rows == null || rows.isEmpty() ? List.of() : List.copyOf(rows);
    }

    /** Clears both row lists (world join / respawn / feature reset). */
    public static void clearAll() {
        armorRows = List.of();
        effectRows = List.of();
    }
}
