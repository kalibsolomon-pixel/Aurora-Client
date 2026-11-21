package com.aurora.client.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure keybind conflict detection and formatting (Minecraft-free core, ADR 0003).
 *
 * <p>Two bindings conflict when they share the exact same non-unbound
 * {@link KeyCombo} int (physical key plus Ctrl/Shift/Alt modifiers). A plain
 * key and a combo on the same key (e.g. {@code C} and {@code Ctrl + C}) do not
 * conflict because {@link KeyCombo#resolves} dispatches them separately.
 *
 * <p>F3 debug sub-keys ({@code debugChord == true}, i.e. Minecraft's
 * {@code Category.DEBUG} bindings other than the F3 overlay/modifier itself)
 * only trigger while F3 is held and therefore do not conflict with ordinary
 * bindings.
 */
public final class KeybindConflicts {
    private KeybindConflicts() {}

    /**
     * One live keybind participating in conflict checks.
     *
     * @param id           stable identifier used for self-exclusion (translation key or {@code "profile:<name>"})
     * @param label        human-readable action name shown in warnings
     * @param binding      encoded {@link KeyCombo} int, or {@link KeyCombo#UNBOUND}
     * @param debugChord   true for F3+&lt;key&gt; debug chords that never fire standalone
     * @param auroraOwned  true for Aurora keybinds and profile hotkeys
     * @param defaultBound true when the binding is currently at its factory default
     */
    public record Entry(String id, String label, int binding,
                        boolean debugChord, boolean auroraOwned, boolean defaultBound) {}

    /**
     * Returns the deduplicated display names of every entry in {@code all} that
     * conflicts with an Aurora or profile binding ({@code selfId} bound to
     * {@code binding}). Returns an empty list when {@code binding} is unbound.
     */
    public static List<String> forAuroraBinding(String selfId, int binding, Collection<Entry> all) {
        if (binding < 0 || all == null || all.isEmpty()) return List.of();
        Set<String> seen = null;
        for (Entry other : all) {
            if (other == null || other.binding() < 0 || other.binding() != binding) continue;
            if (other.debugChord()) continue;
            if (selfId != null && selfId.equals(other.id())) continue;
            String name = other.label();
            if (name == null || name.isBlank()) continue;
            if (seen == null) seen = new LinkedHashSet<>();
            seen.add(name);
        }
        return seen == null ? List.of() : List.copyOf(seen);
    }

    /**
     * Additional conflicts for Minecraft's vanilla Controls screen that vanilla's
     * own loop misses:
     * <ul>
     *   <li>Profile hotkeys (not registered as vanilla {@code KeyMapping}s).</li>
     *   <li>Default-vs-default collisions involving at least one Aurora binding
     *       (vanilla skips all default-vs-default pairs to hide its own F3 debug
     *       chords, which also hid Aurora defaults that collide with vanilla).</li>
     * </ul>
     *
     * <p>Non-Aurora default-vs-default pairs stay untouched so vanilla's own
     * intentional defaults (such as Pick Block / Spectator Hotbar or F3 overlay /
     * modifier) are not spuriously flagged.
     */
    public static List<String> extraVanillaControlsConflicts(Entry self, Collection<Entry> all) {
        if (self == null || self.binding() < 0 || self.debugChord() || all == null || all.isEmpty()) {
            return List.of();
        }
        Set<String> extra = null;
        for (Entry other : all) {
            if (other == null || other.binding() < 0 || other.binding() != self.binding()) continue;
            if (other.debugChord()) continue;
            if (self.id() != null && self.id().equals(other.id())) continue;
            boolean isProfile = other.id() != null && other.id().startsWith("profile:");
            boolean hiddenAuroraDefault = !isProfile
                    && self.defaultBound() && other.defaultBound()
                    && (self.auroraOwned() || other.auroraOwned());
            if (!isProfile && !hiddenAuroraDefault) continue;
            String name = other.label();
            if (name == null || name.isBlank()) continue;
            if (extra == null) extra = new LinkedHashSet<>();
            extra.add(name);
        }
        return extra == null ? List.of() : List.copyOf(extra);
    }

    /**
     * Compact single-line summary for inline row subtitles and toasts, or
     * {@code null} when there are no conflicts.
     */
    public static String summary(List<String> conflicts) {
        if (conflicts == null || conflicts.isEmpty()) return null;
        int n = conflicts.size();
        if (n == 1) return "Conflicts with " + conflicts.get(0);
        if (n == 2) return "Conflicts with " + conflicts.get(0) + ", " + conflicts.get(1);
        return "Conflicts with " + conflicts.get(0) + ", " + conflicts.get(1) + " +" + (n - 2) + " more";
    }

    /**
     * Full conflict description for tooltips and accessibility narration, or
     * {@code null} when there are no conflicts.
     */
    public static String tooltip(List<String> conflicts) {
        if (conflicts == null || conflicts.isEmpty()) return null;
        return "Conflicts with: " + String.join(", ", conflicts);
    }

    /**
     * Merges vanilla's already-detected conflict labels with Aurora's extra
     * conflict labels, preserving order without duplicates.
     */
    public static List<String> mergeLabels(List<String> base, List<String> extra) {
        if (extra == null || extra.isEmpty()) return base == null ? List.of() : base;
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (base != null) {
            for (String s : base) if (s != null && !s.isBlank()) merged.add(s);
        }
        for (String s : extra) if (s != null && !s.isBlank()) merged.add(s);
        return new ArrayList<>(merged);
    }
}
