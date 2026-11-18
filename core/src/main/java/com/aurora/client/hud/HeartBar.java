package com.aurora.client.hud;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Vanilla's health bar ({@code Hud.extractHearts}) as sprite placements, for
 * Health Indicators' Hearts mode above entities. Pure layout and timing: the
 * adapter turns each placement into a quad from the GUI atlas.
 *
 * <p>Matches vanilla: containers first, then absorption, the blinking old
 * health and the current health on top; each heart is 2 HP, 10 to a row at
 * 8 px spacing with 9 px sprites; rows stack upward and squeeze together as
 * vanilla's do; hearts jiggle at 4 HP or less. A bar longer than
 * {@link #MAX_CONTAINERS} containers (a boss's) is scaled down to one row of
 * ten so it stays readable above a head.
 */
public final class HeartBar {
    private HeartBar() {}

    public static final int SPRITE_SIZE = 9;
    public static final int SPACING = 8;
    public static final int PER_ROW = 10;
    /** Two vanilla rows (40 HP including absorption); longer bars are scaled to one row. */
    public static final int MAX_CONTAINERS = 20;

    /** Sprite families, in {@code hud/heart/} naming order. */
    public enum Type {
        CONTAINER, NORMAL, ABSORBING, FROZEN;

        private String path(boolean hardcore, boolean half, boolean blink) {
            String suffix = blink ? "_blinking" : "";
            if (this == CONTAINER) return "hud/heart/container" + (hardcore ? "_hardcore" : "") + suffix;
            String prefix = this == NORMAL ? "" : name().toLowerCase(java.util.Locale.ROOT) + "_";
            return "hud/heart/" + prefix + (hardcore ? "hardcore_" : "") + (half ? "half" : "full") + suffix;
        }
    }

    /** Number of distinct sprite ids returned by {@link #sprite}. */
    public static final int SPRITE_COUNT = Type.values().length * 8;

    public static int sprite(Type type, boolean hardcore, boolean half, boolean blink) {
        return type.ordinal() * 8 + (hardcore ? 4 : 0) + (half ? 2 : 0) + (blink ? 1 : 0);
    }

    /** The vanilla GUI-atlas sprite path (in the {@code minecraft} namespace) for a {@link #sprite} id. */
    public static String spritePath(int sprite) {
        Type type = Type.values()[sprite / 8];
        return type.path((sprite & 4) != 0, (sprite & 2) != 0, (sprite & 1) != 0);
    }

    /** Ints per placement in {@link #layout}'s result: x, y, sprite id, layer. */
    public static final int STRIDE = 4;

    /**
     * Lays out one bar. Coordinates are GUI pixels: x from the bar's left edge,
     * y from the top of the bottom row (rows above are negative). Placements come
     * in vanilla's draw order; {@code layer} (0 container, 1 absorption or old
     * health, 2 current health) lets a 3D renderer separate coplanar quads.
     *
     * @param health        current health (vanilla rounds it up)
     * @param maxHealth     maximum health
     * @param absorption    absorption amount (rounded up)
     * @param displayHealth the old health shown while blinking ({@link Animation#displayHealth()})
     * @param blink         whether this frame shows the blink highlight
     * @param type          {@link Type#NORMAL} or {@link Type#FROZEN}
     * @param jiggleSeed    changes every tick; vanilla re-seeds its jiggle per tick
     */
    public static int[] layout(float health, float maxHealth, float absorption, int displayHealth,
                               boolean blink, Type type, boolean hardcore, int jiggleSeed) {
        int current = ceil(health);
        int old = displayHealth;
        float max = Math.max(maxHealth, Math.max(old, current));
        int absorb = ceil(absorption);

        int containers = ceil(max / 2.0f);
        int absorbContainers = ceil(absorb / 2.0f);
        if (containers + absorbContainers > MAX_CONTAINERS) {
            // Scale the whole bar into one row of ten so a boss's bar stays one row.
            float total = Math.max(1.0f, max + absorb);
            float s = 2.0f * PER_ROW / total;
            // Less a hair, so a full bar's 300 * (20 / 300) does not round up to 21.
            current = ceil(current * s - 1e-3f);
            old = ceil(old * s - 1e-3f);
            absorb = ceil(absorb * s - 1e-3f);
            max = Math.max(0, 2 * PER_ROW - absorb);
            containers = ceil(max / 2.0f);
            absorbContainers = ceil(absorb / 2.0f);
        }

        int count = containers + absorbContainers;
        int rows = ceil(count / (float) PER_ROW);
        int rowHeight = Math.max(10 - (rows - 2), 3);
        int maxHalves = containers * 2;
        boolean jiggle = current + absorb <= 4;

        int[] out = new int[count * 3 * STRIDE];
        int n = 0;
        for (int i = count - 1; i >= 0; i--) {
            int x = (i % PER_ROW) * SPACING;
            int y = -(i / PER_ROW) * rowHeight;
            if (jiggle) y += jiggleBit(jiggleSeed, i);

            n = put(out, n, x, y, sprite(Type.CONTAINER, hardcore, false, blink), 0);
            int halves = i * 2;
            if (i >= containers) {
                int absorbHalves = halves - maxHalves;
                if (absorbHalves < absorb) {
                    n = put(out, n, x, y, sprite(Type.ABSORBING, hardcore, absorbHalves + 1 == absorb, false), 1);
                }
            }
            if (blink && halves < old) {
                n = put(out, n, x, y, sprite(type, hardcore, halves + 1 == old, true), 1);
            }
            if (halves < current) {
                n = put(out, n, x, y, sprite(type, hardcore, halves + 1 == current, false), 2);
            }
        }
        return n == out.length ? out : java.util.Arrays.copyOf(out, n);
    }

    /** Width in GUI pixels of a laid-out bar. */
    public static int width(int[] placements) {
        int right = 0;
        for (int i = 0; i < placements.length; i += STRIDE) right = Math.max(right, placements[i] + SPRITE_SIZE);
        return right;
    }

    private static int put(int[] out, int n, int x, int y, int sprite, int layer) {
        out[n] = x;
        out[n + 1] = y;
        out[n + 2] = sprite;
        out[n + 3] = layer;
        return n + STRIDE;
    }

    /** 0 or 1 per heart and tick, like vanilla's {@code random.nextInt(2)} under its per-tick seed. */
    static int jiggleBit(int seed, int index) {
        int h = seed * 312871 + index * 0x9E3779B9;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h >>> 7) & 1;
    }

    private static int ceil(float v) {
        return (int) Math.ceil(v);
    }

    /**
     * One entity's blink timing, vanilla's {@code lastHealth}/{@code displayHealth}/
     * {@code healthBlinkTime}. A drop in health blinks for 20 ticks; a rise blinks for
     * 10 while the entity is still in its hurt animation; the old health stays
     * visible for a second after the last change.
     */
    public static final class Animation {
        private int lastHealth = -1;
        private int displayHealth;
        private long lastHealthMillis;
        private long blinkUntilTick;
        long lastSeenMillis;

        public void update(float health, boolean hurt, long tick, long millis) {
            int current = ceil(health);
            lastSeenMillis = millis;
            if (lastHealth < 0) {
                lastHealth = current;
                displayHealth = current;
                lastHealthMillis = millis;
                return;
            }
            if (current < lastHealth) {
                lastHealthMillis = millis;
                blinkUntilTick = tick + 20;
            } else if (current > lastHealth && hurt) {
                lastHealthMillis = millis;
                blinkUntilTick = tick + 10;
            }
            if (millis - lastHealthMillis > 1000L) {
                displayHealth = current;
                lastHealthMillis = millis;
            }
            lastHealth = current;
        }

        public boolean blink(long tick) {
            return blinkUntilTick > tick && (blinkUntilTick - tick) / 3L % 2L == 1L;
        }

        public int displayHealth() {
            return displayHealth;
        }
    }

    /** Animations per entity, dropped a few seconds after the entity was last drawn. */
    public static final class Animations {
        private static final long FORGET_AFTER_MS = 5_000L;
        private final Map<UUID, Animation> byEntity = new HashMap<>();
        private long lastSweepMillis;

        public Animation get(UUID entity, long millis) {
            if (millis - lastSweepMillis > 1_000L) {
                lastSweepMillis = millis;
                for (Iterator<Animation> it = byEntity.values().iterator(); it.hasNext(); ) {
                    if (millis - it.next().lastSeenMillis > FORGET_AFTER_MS) it.remove();
                }
            }
            return byEntity.computeIfAbsent(entity, k -> new Animation());
        }

        public int size() {
            return byEntity.size();
        }
    }
}
