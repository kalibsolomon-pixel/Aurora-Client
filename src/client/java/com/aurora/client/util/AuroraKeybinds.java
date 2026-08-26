package com.aurora.client.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Bridges Aurora's config-as-source-of-truth keybinds (raw GLFW ints
 * stored on {@link com.aurora.client.config.AuroraConfig}) with vanilla
 * Minecraft {@link KeyMapping} entries shown in the vanilla controls
 * menu under category {@code "Aurora Client"}.
 *
 * <p>Aurora still polls GLFW directly through {@link AuroraKey} — the
 * vanilla {@link KeyMapping} is registered <em>only</em> so the binding
 * appears in vanilla's controls UI and so rebinding from either UI
 * stays in sync.
 *
 * <h2>Two-way sync</h2>
 * On every client tick {@link #tick()} reconciles the two sides:
 * <ul>
 *   <li>If the vanilla {@link KeyMapping}'s bound key has changed since
 *       last seen — i.e. the user rebound it in the vanilla controls
 *       screen — the new value is written into the Aurora config via
 *       the registered setter.</li>
 *   <li>Otherwise, if the Aurora config value has changed since last
 *       seen — i.e. the user rebound it via {@link
 *       com.aurora.client.screen.setting.KeybindSetting} inside the
 *       Aurora UI — the new value is pushed back into the
 *       {@link KeyMapping}.</li>
 * </ul>
 * The per-entry {@code lastSeen} watermark prevents an oscillation
 * loop and disambiguates which side initiated the change.
 *
 * <h2>Category translation key</h2>
 * Vanilla looks up the category string as a translation key. We
 * register every Aurora binding under {@link #CATEGORY}, with the
 * "Aurora Client" display string supplied by
 * {@code assets/aurora/lang/en_us.json}.
 */
public final class AuroraKeybinds {

    /**
     * Custom controls-menu category for every Aurora binding. The
     * label is derived from the identifier by vanilla as
     * {@code "key.category." + namespace + "." + path}, so the lang
     * entry to localize is {@code key.category.aurora.client}.
     */
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("aurora", "client"));

    private AuroraKeybinds() {}

    private static final class Entry {
        final KeyMapping mapping;
        final IntSupplier getter;
        final IntConsumer setter;
        int lastSeenGlfw;

        Entry(KeyMapping mapping, IntSupplier getter, IntConsumer setter, int lastSeenGlfw) {
            this.mapping = mapping;
            this.getter = getter;
            this.setter = setter;
            this.lastSeenGlfw = lastSeenGlfw;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    /**
     * Register one Aurora keybind with vanilla. The supplied
     * {@code defaultGlfw} is the factory default used when no
     * options.txt entry exists yet; the actual <em>initial</em> bound
     * key is taken from the config getter so the vanilla UI immediately
     * reflects what's stored on disk.
     *
     * @param translationKey vanilla {@link KeyMapping} translation key
     *                       (e.g. {@code "key.aurora.zoom"}).
     * @param defaultGlfw    factory-default GLFW key code shown in
     *                       vanilla's "RESET" affordance.
     * @param getter         reads the current Aurora config value.
     * @param setter         writes a new GLFW int back into the config.
     */
    public static void register(String translationKey,
                                int defaultGlfw,
                                IntSupplier getter,
                                IntConsumer setter) {
        KeyMapping mapping = new KeyMapping(
                translationKey,
                InputConstants.Type.KEYSYM,
                defaultGlfw,
                CATEGORY);
        KeyBindingHelper.registerKeyBinding(mapping);

        // Force the mapping to match config NOW — KeyBindingHelper /
        // vanilla options.txt may have rebound it to a stale value
        // during options load. Aurora config is the source of truth.
        int cfgGlfw = getter.getAsInt();
        applyToMapping(mapping, cfgGlfw);

        ENTRIES.add(new Entry(mapping, getter, setter, cfgGlfw));
    }

    /**
     * Per-tick reconciler. Call from {@code ClientTickEvents.END_CLIENT_TICK}.
     * No allocation in the steady state; per-entry comparisons are
     * branch-only.
     */
    public static void tick() {
        boolean anyMappingChange = false;
        for (Entry e : ENTRIES) {
            int mappingGlfw = currentGlfw(e.mapping);
            int cfgGlfw = e.getter.getAsInt();

            if (mappingGlfw != e.lastSeenGlfw) {
                // Vanilla controls screen edited the binding → push it
                // into Aurora config. Note this also brings cfgGlfw
                // back in line on the next read.
                e.setter.accept(mappingGlfw);
                e.lastSeenGlfw = mappingGlfw;
            } else if (cfgGlfw != e.lastSeenGlfw) {
                // KeybindSetting (Aurora UI) edited the config → push
                // back into the KeyMapping so the vanilla UI shows the
                // same value.
                applyToMapping(e.mapping, cfgGlfw);
                e.lastSeenGlfw = cfgGlfw;
                anyMappingChange = true;
            }
        }
        if (anyMappingChange) {
            // Rebuild vanilla's key-by-keycode lookup table after any
            // programmatic setKey() — vanilla only does this when its
            // own controls screen closes.
            KeyMapping.resetMapping();
        }
    }

    /**
     * Returns the GLFW key code currently bound to {@code mapping},
     * or {@link AuroraKey#UNBOUND} if it's unbound.
     */
    private static int currentGlfw(KeyMapping mapping) {
        InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(mapping);
        if (key == null || key.equals(InputConstants.UNKNOWN)) return AuroraKey.UNBOUND;
        if (key.getType() != InputConstants.Type.KEYSYM) {
            // Mouse buttons or scancodes — Aurora has no representation
            // for those today, treat as unbound for sync purposes so we
            // don't clobber the config with -1 incorrectly. Returning
            // lastSeen would also work but this is simplest.
            return AuroraKey.UNBOUND;
        }
        return key.getValue();
    }

    /** Pushes a GLFW int into the underlying {@link KeyMapping}. */
    private static void applyToMapping(KeyMapping mapping, int glfw) {
        InputConstants.Key key = (glfw == AuroraKey.UNBOUND)
                ? InputConstants.UNKNOWN
                : InputConstants.Type.KEYSYM.getOrCreate(glfw);
        mapping.setKey(key);
    }
}
