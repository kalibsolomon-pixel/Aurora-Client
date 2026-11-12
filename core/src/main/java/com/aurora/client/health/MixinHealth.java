package com.aurora.client.health;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What happened to each Aurora mixin in this session (ADR 0004).
 *
 * <p>Written by {@code AuroraMixinPlugin} and {@code AuroraMixinErrorHandler}
 * while the game's classes load, on whichever thread loads them, and read
 * from the client thread by {@link FeatureHealth}. Mixin names are relative
 * to the mixin package, exactly as {@code aurora.mixins.json} lists them
 * ({@code "GameRendererMixin"}, {@code "hitreg.ArmorMixin"}).
 *
 * <p>This class is loaded before Minecraft's own classes, so it must not
 * reference any of them, nor anything that does.
 */
public final class MixinHealth {
    private MixinHealth() {}

    /** The package every Aurora mixin lives in, as named in {@code aurora.mixins.json}. */
    public static final String MIXIN_PACKAGE = "com.aurora.client.mixin";

    /** Comma-separated mixin names, as listed in {@code aurora.mixins.json}, to skip. */
    public static final String DISABLE_PROPERTY = "aurora.mixin.disable";

    /** Why a mixin is not doing its job. */
    public enum Cause {
        /** Mixin rejected it: an injection point, shadow or accessor target was not found. */
        APPLY_FAILED("could not be applied"),
        /** Its target class loaded (or does not exist) and the mixin never ran. */
        NOT_APPLIED("was never applied"),
        /** Named in {@code -Daurora.mixin.disable}. */
        DISABLED_BY_OPTION("was turned off with -Daurora.mixin.disable");

        public final String phrase;

        Cause(String phrase) { this.phrase = phrase; }
    }

    /** One mixin that is not working, and why. */
    public record Failure(String mixin, String target, Cause cause, String detail) {}

    private static final Map<String, Failure> FAILURES = new ConcurrentHashMap<>();
    private static final Set<String> APPLIED = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> STOOD_DOWN = new ConcurrentHashMap<>();
    private static final Set<String> TARGETS = ConcurrentHashMap.newKeySet();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static volatile boolean audited;

    /** Changes whenever the set of failures changes; readers cache against it. */
    public static long generation() { return GENERATION.get(); }

    /** True when this mixin is known not to be working. False while still unknown. */
    public static boolean failed(String mixin) { return FAILURES.containsKey(mixin); }

    /** True once Mixin reported this mixin applied to a target without error. */
    public static boolean applied(String mixin) { return APPLIED.contains(mixin); }

    /**
     * True when this mixin was deliberately not applied because another mod
     * owns its area, or the mod it patches is absent (ADR 0009). Not a failure:
     * its feature has nothing to do in this session.
     */
    public static boolean stoodDown(String mixin) { return STOOD_DOWN.containsKey(mixin); }

    /** Mixin name to why it stood down, sorted by mixin name. */
    public static Map<String, String> standDowns() { return new java.util.TreeMap<>(STOOD_DOWN); }

    /** True after {@link MixinAudit} has resolved every mixin one way or the other. */
    public static boolean audited() { return audited; }

    /** The failure recorded for this mixin, or null. */
    public static Failure failure(String mixin) { return FAILURES.get(mixin); }

    /** Every recorded failure, sorted by mixin name. */
    public static List<Failure> failures() {
        List<Failure> out = new ArrayList<>(FAILURES.values());
        out.sort(Comparator.comparing(Failure::mixin));
        return out;
    }

    /** Class names (dotted) that Aurora's mixins target. */
    public static Set<String> targets() { return Set.copyOf(TARGETS); }

    static void addTargets(Set<String> names) {
        for (String n : names) TARGETS.add(n.replace('/', '.'));
    }

    static void noteApplied(String mixin) {
        APPLIED.add(mixin);
        // A mixin the audit gave up on can still apply when its target
        // finally loads; the later, positive report wins.
        Failure f = FAILURES.get(mixin);
        if (f != null && f.cause() == Cause.NOT_APPLIED && FAILURES.remove(mixin, f)) {
            GENERATION.incrementAndGet();
        }
    }

    static void noteStoodDown(String mixin, String reason) {
        if (STOOD_DOWN.put(mixin, reason) == null) GENERATION.incrementAndGet();
    }

    static void noteFailure(String mixin, String target, Cause cause, String detail) {
        Failure next = new Failure(mixin, target, cause, detail == null ? "" : detail);
        Failure prev = FAILURES.put(mixin, next);
        if (!next.equals(prev)) GENERATION.incrementAndGet();
    }

    static void markAudited() { audited = true; }

    /** Test hook: forget everything recorded so far. */
    static void resetForTests() {
        FAILURES.clear();
        APPLIED.clear();
        STOOD_DOWN.clear();
        TARGETS.clear();
        audited = false;
        GENERATION.incrementAndGet();
    }

    /** Mixin class name to the name used in {@code aurora.mixins.json}. */
    static String shortName(String mixinClassName) {
        String prefix = MIXIN_PACKAGE + ".";
        String dotted = mixinClassName.replace('/', '.');
        return dotted.startsWith(prefix) ? dotted.substring(prefix.length()) : dotted;
    }

    /** One line that is safe to show a player: the first line of the cause, bounded. */
    static String summarize(Throwable th) {
        if (th == null) return "";
        Throwable root = th;
        while (root.getCause() != null && root.getCause() != root && root.getMessage() == null) root = root.getCause();
        String msg = root.getMessage();
        if (msg == null || msg.isBlank()) return root.getClass().getSimpleName();
        int nl = msg.indexOf('\n');
        if (nl >= 0) msg = msg.substring(0, nl);
        msg = msg.trim();
        return msg.length() > 300 ? msg.substring(0, 300) + "..." : msg;
    }
}
