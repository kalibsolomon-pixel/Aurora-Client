package com.aurora.client.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Bridges Aurora's config-as-source-of-truth keybinds (raw SDL ints
 * stored on {@link com.aurora.client.config.AuroraConfig}) with vanilla
 * Minecraft {@link KeyMapping} entries shown in the vanilla controls
 * menu under category {@code "Aurora Client"}.
 *
 * <p>Aurora still polls SDL directly through {@link AuroraKey} — the
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
 *
 * <h2>Combos</h2>
 * A config value is a {@link KeyCombo} binding (key plus Ctrl / Shift /
 * Alt). The {@link KeyMapping} holds the plain key and the modifiers ride on
 * it through {@link KeyComboHolder}, so both sides compare whole bindings and
 * a combo set in either UI syncs like a plain key.
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
        int lastSeenScancode;

        Entry(KeyMapping mapping, IntSupplier getter, IntConsumer setter, int lastSeenScancode) {
            this.mapping = mapping;
            this.getter = getter;
            this.setter = setter;
            this.lastSeenScancode = lastSeenScancode;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    /** Minecraft's own mappings get their saved modifiers once, after options.txt has loaded. */
    private static boolean vanillaModifiersLoaded;

    /** Toggle-style mapping actions consume queued presses so taps between client
     * ticks are retained. Call even while a screen is open to discard stale input. */
    public static boolean consumePress(String translationKey) {
        for (Entry entry : ENTRIES) {
            if (!entry.mapping.getName().equals(translationKey)) continue;
            boolean pressed = false;
            while (entry.mapping.consumeClick()) pressed = true;
            return pressed;
        }
        return false;
    }

    /**
     * Register one Aurora keybind with vanilla. The supplied
     * {@code defaultNativeKey} is the host's factory key constant used when no
     * options.txt entry exists yet; the actual <em>initial</em> bound
     * key is taken from the config getter so the vanilla UI immediately
     * reflects what's stored on disk.
     *
     * @param translationKey vanilla {@link KeyMapping} translation key
     *                       (e.g. {@code "key.aurora.zoom"}).
     * @param defaultNativeKey   factory-default native key code shown in
     *                       vanilla's "RESET" affordance.
     * @param getter         reads the current Aurora config value.
     * @param setter         writes a new SDL int back into the config.
     */
    public static void register(String translationKey,
                                int defaultNativeKey,
                                IntSupplier getter,
                                IntConsumer setter) {
        KeyMapping mapping = new KeyMapping(
                translationKey,
                com.aurora.client.adapter.NativeKeyboard.type(),
                defaultNativeKey,
                CATEGORY);
        KeyMappingHelper.registerKeyMapping(mapping);

        // Force the mapping to match config NOW — KeyMappingHelper /
        // vanilla options.txt may have rebound it to a stale value
        // during options load. Aurora config is the source of truth.
        int cfgScancode = getter.getAsInt();
        applyToMapping(mapping, cfgScancode);

        ENTRIES.add(new Entry(mapping, getter, setter, cfgScancode));
    }

    /**
     * Per-tick reconciler. Call from {@code ClientTickEvents.END_CLIENT_TICK}.
     * No allocation in the steady state; per-entry comparisons are
     * branch-only.
     */
    public static void tick() {
        if (!vanillaModifiersLoaded) {
            vanillaModifiersLoaded = true;
            VanillaKeyCombos.load();
        }
        boolean anyMappingChange = false;
        boolean pushedFromConfig = false;
        for (Entry e : ENTRIES) {
            int mappingScancode = currentScancode(e.mapping);
            int cfgScancode = e.getter.getAsInt();

            if (mappingScancode != e.lastSeenScancode) {
                // Vanilla controls screen edited the binding → push it
                // into Aurora config. Note this also brings cfgScancode
                // back in line on the next read.
                e.setter.accept(mappingScancode);
                e.lastSeenScancode = mappingScancode;
            } else if (cfgScancode != e.lastSeenScancode) {
                // KeybindSetting (Aurora UI) edited the config → push
                // back into the KeyMapping so the vanilla UI shows the
                // same value.
                applyToMapping(e.mapping, cfgScancode);
                e.lastSeenScancode = cfgScancode;
                anyMappingChange = true;
                pushedFromConfig = true;
            }
        }
        if (anyMappingChange) {
            // Rebuild vanilla's key-by-keycode lookup table after any
            // programmatic setKey() — vanilla only does this when its
            // own controls screen closes.
            KeyMapping.resetMapping();
        }
        if (pushedFromConfig) {
            // Persist the vanilla side too. options.txt is loaded before this
            // reconciler runs at boot, so an Aurora-UI rebind that only
            // reached aurora.json was overwritten by the stale options.txt
            // binding on the next launch (found by the 2026-10-19 relaunch
            // acceptance; CONFIG_PERSISTENCE_RELIABILITY_REPORT.md). Vanilla's
            // own Controls screen saves options the same way.
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.options != null) mc.options.save();
        }
        // Every live binding, so a plain key can tell when a combo on it outranks it.
        VanillaKeyCombos.rebuildRegistry(com.aurora.client.config.profile.ProfileManager.getInstance().hotkeys());
    }

    /**
     * Returns the binding currently on {@code mapping} — its SDL key code
     * with the mapping's modifiers ({@link KeyCombo}) — or
     * {@link AuroraKey#UNBOUND} if it's unbound. Mouse buttons have no
     * representation in Aurora today and also read as unbound.
     */
    private static int currentScancode(KeyMapping mapping) {
        return VanillaKeyCombos.binding(mapping);
    }

    /** Pushes a binding into the underlying {@link KeyMapping}: the plain key, then the modifiers. */
    private static void applyToMapping(KeyMapping mapping, int binding) {
        int scancode = KeyCombo.key(binding);
        InputConstants.Key key = (scancode == AuroraKey.UNBOUND)
                ? InputConstants.UNKNOWN
                : com.aurora.client.adapter.NativeKeyboard.key(scancode);
        mapping.setKey(key);
        VanillaKeyCombos.setModifiers(mapping, KeyCombo.modifiers(binding));
    }
}
