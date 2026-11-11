package com.aurora.client.health;

import com.aurora.client.feature.FeatureCatalog;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a feature off for the session when one of its mixins is not working,
 * and says why (ADR 0004).
 *
 * <p>{@link MixinHealth} knows which mixins failed; {@link FeatureCatalog}
 * knows which features and which config switches ("gates") each mixin serves.
 * This class joins the two:
 *
 * <ul>
 *   <li>{@link #enforce} holds every gate of a failed mixin at {@code false}
 *       in the live config, so the rest of that feature does not keep running
 *       half-attached;</li>
 *   <li>the value the player had is remembered and written back whenever the
 *       config or a profile is saved ({@link #persistedValue}), so a broken
 *       session never overwrites a saved preference;</li>
 *   <li>{@link #noteFor} gives the sentence the UI shows on the feature.</li>
 * </ul>
 *
 * <p>With no failed mixin every method returns after one comparison.
 * No Minecraft imports: the config object is reached by reflection so this
 * class can be tested on its own.
 */
public final class FeatureHealth {
    private FeatureHealth() {}

    /** One thing that is not working, attributed to a mixin. */
    public record Issue(String mixin, String provides, MixinHealth.Cause cause, String detail) {}

    private static long seenGeneration = -1;
    private static Set<String> blockedGates = Set.of();
    private static Map<String, List<Issue>> issuesByFeature = Map.of();
    private static List<Issue> generalIssues = List.of();

    /** Gate name to the value the player had before this session forced it off. */
    private static final Map<String, Boolean> REMEMBERED = new ConcurrentHashMap<>();
    private static final Map<String, Field> FIELD_CACHE = new HashMap<>();
    private static Class<?> fieldCacheOwner;

    private static synchronized void refresh() {
        long gen = MixinHealth.generation();
        if (gen == seenGeneration) return;
        Set<String> gates = new LinkedHashSet<>();
        Map<String, List<Issue>> byFeature = new LinkedHashMap<>();
        List<Issue> general = new ArrayList<>();
        for (MixinHealth.Failure failure : MixinHealth.failures()) {
            FeatureCatalog.MixinBinding binding = FeatureCatalog.mixin(failure.mixin());
            String provides = binding == null ? "an Aurora hook" : binding.provides();
            Issue issue = new Issue(failure.mixin(), provides, failure.cause(), failure.detail());
            if (binding == null || binding.features().isEmpty()) {
                general.add(issue);
            } else {
                for (String feature : binding.features()) {
                    byFeature.computeIfAbsent(feature, k -> new ArrayList<>()).add(issue);
                }
            }
            if (binding != null) gates.addAll(binding.gates());
        }
        blockedGates = gates;
        issuesByFeature = byFeature;
        generalIssues = general;
        seenGeneration = gen;
    }

    /** True when at least one Aurora mixin is not working. */
    public static boolean anyIssues() {
        refresh();
        return !issuesByFeature.isEmpty() || !generalIssues.isEmpty();
    }

    /** What is not working for this feature; empty when it is healthy. */
    public static List<Issue> issuesFor(String featureId) {
        refresh();
        return issuesByFeature.getOrDefault(featureId, List.of());
    }

    /** Ids of the features with at least one issue, in the order the failures were found. */
    public static List<String> affectedFeatures() {
        refresh();
        return new ArrayList<>(issuesByFeature.keySet());
    }

    /** Issues in mixins that serve Aurora as a whole rather than one feature. */
    public static List<Issue> generalIssues() {
        refresh();
        return generalIssues;
    }

    /** Config switches currently held off. */
    public static Set<String> blockedGates() {
        refresh();
        return blockedGates;
    }

    /**
     * The sentence shown on a feature that is not fully working, or null when
     * it is healthy. Example: "Not working in this session: zooming the view.
     * Aurora could not attach to this game or mod set."
     */
    public static String noteFor(String featureId) {
        List<Issue> issues = issuesFor(featureId);
        if (issues.isEmpty()) return null;
        Set<String> lost = new LinkedHashSet<>();
        boolean byOption = true;
        for (Issue issue : issues) {
            lost.add(issue.provides());
            byOption &= issue.cause() == MixinHealth.Cause.DISABLED_BY_OPTION;
        }
        return "Not working in this session: " + String.join("; ", lost) + ". "
                + (byOption
                    ? "Turned off with -D" + AuroraMixinPlugin.DISABLE_PROPERTY + "."
                    : "Aurora could not attach to this game or mod set.");
    }

    /** The first sentence of {@link #noteFor}, for places with one short line. */
    public static String headlineFor(String featureId) {
        String note = noteFor(featureId);
        if (note == null) return null;
        int end = note.indexOf(". ");
        return end < 0 ? note : note.substring(0, end);
    }

    /**
     * Hold every blocked gate at {@code false} on {@code config}, and give
     * back any gate that is no longer blocked. Cheap enough to call every
     * tick: with nothing blocked it is one comparison.
     */
    public static synchronized void enforce(Object config) {
        refresh();
        if (blockedGates.isEmpty() && REMEMBERED.isEmpty()) return;
        if (config == null) return;
        for (String gate : blockedGates) {
            Field f = booleanField(config, gate);
            if (f == null) continue;
            try {
                if (f.getBoolean(config)) {
                    REMEMBERED.put(gate, Boolean.TRUE);
                    f.setBoolean(config, false);
                }
            } catch (IllegalAccessException ignored) {}
        }
        for (var it = REMEMBERED.entrySet().iterator(); it.hasNext();) {
            var e = it.next();
            if (blockedGates.contains(e.getKey())) continue;
            Field f = booleanField(config, e.getKey());
            try {
                if (f != null) f.setBoolean(config, e.getValue());
            } catch (IllegalAccessException ignored) {}
            it.remove();
        }
    }

    /**
     * The value to write to disk for a config field: the player's own value
     * for a gate this session is holding off, otherwise {@code liveValue}.
     */
    public static Object persistedValue(String fieldName, Object liveValue) {
        if (REMEMBERED.isEmpty()) return liveValue;
        Boolean kept = REMEMBERED.get(fieldName);
        return kept != null ? kept : liveValue;
    }

    /** Gate name to remembered player value, for whole-config serialization. */
    public static Map<String, Boolean> rememberedValues() {
        return REMEMBERED.isEmpty() ? Map.of() : Map.copyOf(REMEMBERED);
    }

    /**
     * The player's value for this field was just replaced on purpose (a
     * feature reset); what was remembered for it no longer applies.
     */
    public static void forget(String fieldName) {
        if (!REMEMBERED.isEmpty()) REMEMBERED.remove(fieldName);
    }

    private static Field booleanField(Object config, String name) {
        Class<?> owner = config.getClass();
        if (owner != fieldCacheOwner) {
            FIELD_CACHE.clear();
            fieldCacheOwner = owner;
        }
        if (FIELD_CACHE.containsKey(name)) return FIELD_CACHE.get(name);
        Field found = null;
        try {
            Field f = owner.getField(name);
            if (f.getType() == boolean.class) found = f;
        } catch (NoSuchFieldException ignored) {}
        FIELD_CACHE.put(name, found);
        return found;
    }

    /** Test hook. */
    static synchronized void resetForTests() {
        REMEMBERED.clear();
        FIELD_CACHE.clear();
        fieldCacheOwner = null;
        seenGeneration = -1;
    }
}
