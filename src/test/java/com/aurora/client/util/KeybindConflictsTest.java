package com.aurora.client.util;

import com.aurora.client.SourceTree;
import com.aurora.client.screen.setting.KeybindSetting;
import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Keybind conflict detection, vanilla Controls augmentation, and UI integration. */
class KeybindConflictsTest {
    private static final int C = 6, K = 14, P = 19, A = 4;

    @Test void exactBindingsConflictWhilePlainAndCombosStayDistinct() {
        List<KeybindConflicts.Entry> all = List.of(
                new KeybindConflicts.Entry("key.aurora.zoom", "Zoom", C, false, true, true),
                new KeybindConflicts.Entry("key.saveToolbarActivator", "Save Hotbar Activator", C, false, false, true),
                new KeybindConflicts.Entry("key.debug.crash", "Crash Game", C, true, false, true),
                new KeybindConflicts.Entry("profile:PvP", "Profile: PvP", KeyCombo.of(C, KeyCombo.CTRL), false, true, false),
                new KeybindConflicts.Entry("key.aurora.free_look", "Free Look", KeyCombo.of(C, KeyCombo.SHIFT), false, true, false),
                new KeybindConflicts.Entry("key.aurora.minimap_toggle", "Toggle Minimap", KeyCombo.UNBOUND, false, true, true)
        );

        assertEquals(List.of("Save Hotbar Activator"),
                KeybindConflicts.forAuroraBinding("key.aurora.zoom", C, all),
                "plain C conflicts with Save Hotbar Activator, not with F3+C debug chord or Ctrl+C / Shift+C combos");

        assertEquals(List.of(),
                KeybindConflicts.forAuroraBinding("profile:PvP", KeyCombo.of(C, KeyCombo.CTRL), all),
                "Ctrl + C does not conflict with plain C or Shift + C");

        assertEquals(List.of(),
                KeybindConflicts.forAuroraBinding("key.aurora.minimap_toggle", KeyCombo.UNBOUND, all),
                "unbound never conflicts");
    }

    @Test void auroraAndProfileDuplicateBindingsIncludeEachOtherAndDeduplicateLabels() {
        int ctrlP = KeyCombo.of(P, KeyCombo.CTRL);
        List<KeybindConflicts.Entry> all = List.of(
                new KeybindConflicts.Entry("key.aurora.pulsar", "Open Pulsar", ctrlP, false, true, false),
                new KeybindConflicts.Entry("key.aurora.totem_reset", "Reset Totem Pops", ctrlP, false, true, false),
                new KeybindConflicts.Entry("profile:PvP", "Profile: PvP", ctrlP, false, true, false),
                new KeybindConflicts.Entry("key.other.dup", "Reset Totem Pops", ctrlP, false, false, false)
        );

        assertEquals(List.of("Reset Totem Pops", "Profile: PvP"),
                KeybindConflicts.forAuroraBinding("key.aurora.pulsar", ctrlP, all));
        assertEquals(List.of("Open Pulsar", "Reset Totem Pops"),
                KeybindConflicts.forAuroraBinding("profile:PvP", ctrlP, all));
    }

    @Test void vanillaControlsExtraConflictsSurfaceProfilesAndDefaultAuroraCollisionsOnly() {
        var zoomDefault = new KeybindConflicts.Entry("key.aurora.zoom", "Zoom", C, false, true, true);
        var saveToolbarDefault = new KeybindConflicts.Entry("key.saveToolbarActivator", "Save Hotbar Activator", C, false, false, true);
        var debugCrash = new KeybindConflicts.Entry("key.debug.crash", "Crash Game", C, true, false, true);
        var profileOnC = new KeybindConflicts.Entry("profile:Clutch", "Profile: Clutch", C, false, true, false);
        var customOnC = new KeybindConflicts.Entry("key.drop", "Drop Selected Item", C, false, false, false);

        List<KeybindConflicts.Entry> all = List.of(zoomDefault, saveToolbarDefault, debugCrash, profileOnC, customOnC);

        assertEquals(List.of("Save Hotbar Activator", "Profile: Clutch"),
                KeybindConflicts.extraVanillaControlsConflicts(zoomDefault, all),
                "Aurora default row gets vanilla default collision + profile hotkey; non-default Drop is already handled by vanilla");

        assertEquals(List.of("Zoom", "Profile: Clutch"),
                KeybindConflicts.extraVanillaControlsConflicts(saveToolbarDefault, all),
                "vanilla default row also sees the colliding Aurora default + profile hotkey");

        assertEquals(List.of("Profile: Clutch"),
                KeybindConflicts.extraVanillaControlsConflicts(customOnC, all),
                "non-default vanilla row only needs profile hotkeys appended; vanilla already flags key mappings");

        assertEquals(List.of(),
                KeybindConflicts.extraVanillaControlsConflicts(debugCrash, all),
                "F3 debug sub-keys never trigger extra standalone conflicts");

        // Vanilla-vs-vanilla default pair (e.g. F3 overlay & modifier, or pick block & spectator hotbar) stays untouched.
        var vanillaA = new KeybindConflicts.Entry("key.debug.overlay", "Toggle Debug Overlay", 60, false, false, true);
        var vanillaB = new KeybindConflicts.Entry("key.debug.modifier", "Debug Modifier", 60, false, false, true);
        assertEquals(List.of(),
                KeybindConflicts.extraVanillaControlsConflicts(vanillaA, List.of(vanillaA, vanillaB)),
                "vanilla-vs-vanilla default pairs must stay untouched");
    }

    @Test void summaryTooltipAndMergeFormatting() {
        assertNull(KeybindConflicts.summary(List.of()));
        assertNull(KeybindConflicts.tooltip(List.of()));

        assertEquals("Conflicts with Sprint", KeybindConflicts.summary(List.of("Sprint")));
        assertEquals("Conflicts with Sprint, Zoom", KeybindConflicts.summary(List.of("Sprint", "Zoom")));
        assertEquals("Conflicts with Sprint, Zoom +2 more",
                KeybindConflicts.summary(List.of("Sprint", "Zoom", "Profile: PvP", "Drop")));

        assertEquals("Conflicts with: Sprint, Zoom, Profile: PvP",
                KeybindConflicts.tooltip(List.of("Sprint", "Zoom", "Profile: PvP")));

        assertEquals(List.of("Sprint", "Zoom", "Profile: PvP"),
                KeybindConflicts.mergeLabels(List.of("Sprint", "Zoom"), List.of("Zoom", "Profile: PvP")));
    }

    @Test void keybindSettingNarrationReflectsConflictsAndClearsWhenResolved() throws Exception {
        AtomicInteger binding = new AtomicInteger(K);
        AtomicReference<List<String>> conflicts = new AtomicReference<>(List.of("Sprint", "Profile: PvP"));
        KeybindSetting row = new KeybindSetting("Toggle Sprint Key", binding::get, binding::set);
        var seam = KeybindSetting.class.getDeclaredMethod("conflictSupplier", java.util.function.Supplier.class);
        seam.setAccessible(true);
        seam.invoke(row, (java.util.function.Supplier<List<String>>) conflicts::get);

        String conflicted = row.interactionControl().action().state().getString();
        assertTrue(conflicted.contains("Conflicts with: Sprint, Profile: PvP"),
                "conflicted keybind row narrates its conflicts: " + conflicted);

        conflicts.set(List.of());
        String clean = row.interactionControl().action().state().getString();
        assertFalse(clean.contains("Conflicts with"),
                "resolved keybind row drops conflict narration: " + clean);
    }

    @Test void everyRegistryKeybindDeclaresKeyIdAndVanillaMixinIsWiredAcrossAllLines() throws Exception {
        String registry = SourceTree.read("screen/FeatureRegistry.java");
        int totalKeybinds = registry.split("new KeybindSetting", -1).length - 1;
        Matcher m = Pattern.compile("\\.keyId\\(\"key\\.aurora\\.[a-z_]+\"\\)").matcher(registry);
        int tagged = 0;
        while (m.find()) tagged++;
        assertEquals(totalKeybinds, tagged,
                "every production KeybindSetting in FeatureRegistry must declare its .keyId(\"key.aurora.*\")");

        String mixin = SourceTree.read("mixin/KeyBindsListEntryConflictMixin.java");
        assertTrue(mixin.contains("NativeUi.screen(mc)"), "must use NativeUi.screen for 26.1/26.2/26.3 parity");
        assertTrue(mixin.contains("KeybindConflictLookup.extraVanillaControlsConflicts(this.key)"));

        for (String v : new String[]{"26.1", "26.2", "26.3"}) {
            String manifest = Files.readString(Path.of("adapters/minecraft-" + v + "/src/client/resources/aurora.mixins.json"));
            assertTrue(manifest.contains("\"KeyBindsListEntryConflictMixin\""),
                    "mc " + v + " manifest must include KeyBindsListEntryConflictMixin");
        }

        String keybindSrc = SourceTree.read("screen/setting/KeybindSetting.java");
        assertTrue(keybindSrc.contains("ThemeToken.STATUS_WARNING"));
        assertTrue(keybindSrc.contains("AuroraFontRenderer.ellipsize(font, summary, maxW, 0)"),
                "inline conflict summary must respect overflow rules");
    }
}
