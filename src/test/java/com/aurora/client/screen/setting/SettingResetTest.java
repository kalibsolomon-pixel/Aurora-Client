package com.aurora.client.screen.setting;

import com.aurora.client.SourceTree;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.ConfigDirectory;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.interaction.SemanticFeedback;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class SettingResetTest {
    @TempDir Path temporary;
    enum Choice { FIRST, SECOND }

    @BeforeEach void configDirectory() { ConfigDirectory.set(temporary); }
    @AfterEach void flush() { AuroraConfig.saveBlocking(); FeatureSetting.clearFocus(); }

    private static SemanticActionControl reset(FeatureSetting row) {
        var controls = row.controlsWithReset();
        return controls.getLast();
    }

    @Test void factorySnapshotIsDeeplyIndependentAndContainsReleaseAndMachineDefaults() {
        AuroraConfig a = AuroraConfig.defaults(), b = AuroraConfig.defaults();
        assertEquals(AuroraConfig.FontType.FIRA_SANS, a.activeFont);
        assertEquals(new AuroraConfig().perfDebugEntryCache, a.perfDebugEntryCache);
        a.pulsarSettings.tracking = !a.pulsarSettings.tracking;
        a.themeOrDefault().accent = 123;
        a.moduleLayouts.clear();
        assertEquals(b.pulsarSettings.tracking, AuroraConfig.defaults().pulsarSettings.tracking);
        assertEquals(b.themeOrDefault().accent, AuroraConfig.defaults().themeOrDefault().accent);
        assertEquals(b.moduleLayouts.size(), AuroraConfig.defaults().moduleLayouts.size());
    }

    @Test void booleanDefaultIsReadOnceAndPointerAndKeyboardUseTheSetter() throws Exception {
        var value = new AtomicReference<>(false);
        var calls = new AtomicInteger();
        var defaults = new AtomicInteger();
        var row = new BooleanSetting("Example", value::get, v -> { calls.incrementAndGet(); value.set(v); })
                .resetDefault(() -> { defaults.incrementAndGet(); return true; });
        var control = reset(row);
        assertSame(row.controlsWithReset(), row.controlsWithReset());
        assertEquals("Reset Example to default (On)", control.action().accessibleName().getString());
        assertTrue(row.isModified());
        control.setBounds(10, 20, 18, 18);
        control.setAvailable(true);
        assertTrue(row.resetMouseClicked(11, 21, InputConstants.MOUSE_BUTTON_LEFT));
        assertEquals(1, calls.get());
        assertFalse(row.isModified());
        assertFalse(row.controlVisible(control));
        assertFalse(row.resetMouseClicked(11, 21, InputConstants.MOUSE_BUTTON_LEFT));
        assertNull(control.nextFocusPath(new net.minecraft.client.gui.navigation.FocusNavigationEvent.TabNavigation(true)));
        value.set(false);
        control.setFocused(true);
        assertTrue(row.resetKeyPressed(control, new KeyEvent(InputConstants.KEY_SPACE, 0, 0)));
        assertEquals(2, calls.get());
        assertEquals(1, defaults.get());
        assertTrue(AuroraConfig.saveBlocking());
        assertTrue(Files.exists(temporary.resolve("aurora.json")));
    }

    @Test void disabledAndUnavailableResetsRejectInputAndUnmodifiedRowsHaveNoAction() {
        var value = new AtomicReference<>(false);
        var row = new BooleanSetting("Example", value::get, value::set).resetDefault(() -> true);
        var control = reset(row);
        control.setBounds(0, 0, 18, 18);
        assertFalse(row.resetMouseClicked(1, 1, InputConstants.MOUSE_BUTTON_LEFT));
        control.setAvailable(true);
        row.disabled(() -> true);
        assertFalse(control.action().activate(SemanticFeedback.NONE, null));
        assertFalse(value.get());
        assertFalse(new BooleanSetting("Preview", value::get, value::set).hasResetDefault());
    }

    @Test void enumAndSegmentedDefaultsUseTheirDisplayedLabelsAndExistingSetters() {
        var value = new AtomicReference<>(Choice.SECOND);
        var row = new EnumSetting<>("Choice", Choice.class, value::get, value::set)
                .resetDefault(() -> Choice.FIRST).labels(v -> "Display " + v.name());
        assertEquals("Reset Choice to default (Display FIRST)", reset(row).action().accessibleName().getString());
        assertTrue(reset(row).action().activate(SemanticFeedback.NONE, null));
        assertEquals(Choice.FIRST, value.get());
        value.set(Choice.SECOND);
        var segments = new SegmentedSetting<>("Choice", Choice.class, value::get, value::set, v -> "Segment " + v.name())
                .resetDefault(() -> Choice.FIRST);
        assertEquals(segments.interactionControls().size() + 1, segments.controlsWithReset().size());
        assertEquals("Reset Choice to default (Segment FIRST)", reset(segments).action().accessibleName().getString());
        assertTrue(reset(segments).action().activate(SemanticFeedback.NONE, null));
        assertEquals(Choice.FIRST, value.get());
    }

    @Test void numericDefaultsKeepReadoutFormattingAndThemeResetCallsResponderImmediately() {
        var value = new AtomicReference<>(.7);
        var row = SliderSetting.of("Opacity", value::get, value::set, 0, 1).resetDefault(() -> .33).percent();
        assertEquals("Reset Opacity to default (33%)", reset(row).action().accessibleName().getString());
        assertTrue(reset(row).action().activate(SemanticFeedback.NONE, null));
        assertEquals(.33, value.get(), 1e-9);
        var precise = SliderSetting.of("Exact", value::get, value::set, 0, 1).resetDefault(() -> .333);
        assertTrue(reset(precise).action().activate(SemanticFeedback.NONE, null));
        assertEquals(.333, value.get(), 0, "reset must not quantize a factory default to the interactive step");
        var calls = new AtomicInteger();
        var theme = new ThemeSliderSetting("Blur", value::get, v -> { calls.incrementAndGet(); value.set(v); }, 0, 32, 1, true)
                .resetDefault(() -> 9).valueFormatter(v -> v.intValue() + " px");
        assertEquals("Reset Blur to default (9 px)", reset(theme).action().accessibleName().getString());
        assertTrue(reset(theme).action().activate(SemanticFeedback.NONE, null));
        assertEquals(1, calls.get());
        assertEquals(9., value.get());
    }

    @Test void colourResetRestoresNullInheritanceAndAccentResponderAndKeybindSetterRun() {
        var color = new AtomicReference<Integer>(0xFF123456);
        var row = new ColorSetting("Text", () -> color.get() == null ? 0xFFFFFFFF : color.get(), color::set)
                .resetDefault(color::get, () -> null, () -> 0xFFFFFFFF);
        assertEquals("Reset Text to default (#FFFFFF)", reset(row).action().accessibleName().getString());
        assertTrue(reset(row).action().activate(SemanticFeedback.NONE, null));
        assertNull(color.get());
        assertFalse(row.isModified());
        color.set(0xFF000000);
        var accent = new AccentSetting("Accent", color::get, color::set).resetDefault(() -> 0xFF123456);
        assertTrue(reset(accent).action().activate(SemanticFeedback.NONE, null));
        assertEquals(0xFF123456, color.get());
        var key = new AtomicInteger(42);
        var keyRow = new KeybindSetting("Open", key::get, key::set).resetDefault(() -> -1);
        assertEquals("Reset Open to default (Unbound)", reset(keyRow).action().accessibleName().getString());
        assertTrue(reset(keyRow).action().activate(SemanticFeedback.NONE, null));
        assertEquals(-1, key.get());
    }

    @Test void everySimpleRegistryConstructionDeclaresDefaultOrExplicitDomainExemption() throws Exception {
        String registry = Files.readString(SourceTree.resolve(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/FeatureRegistry.java"));
        var pattern = Pattern.compile("new (?:Boolean|Enum|Segmented|Color|Accent|Keybind|ThemeSlider|ThemeOpacity)Setting(?:<>|<[^>]+>)?\\(|SliderSetting\\.of(?:Int)?\\(");
        var matcher = pattern.matcher(registry);
        int count = 0, exemptions = 0;
        while (matcher.find()) {
            int end = closingParen(registry, matcher.end());
            String suffix = registry.substring(end).stripLeading();
            if (suffix.startsWith(".noReset(")) {
                assertTrue(registry.substring(matcher.end(), end).contains("KeystrokesModule.ID"), "Only the module-layout Scale row is exempt");
                exemptions++;
            } else assertTrue(suffix.startsWith(".resetDefault("), "Missing default at line " + registry.substring(0, matcher.start()).lines().count());
            count++;
        }
        assertTrue(count > 200, "The manifest must scan the full registry");
        assertEquals(1, exemptions);
        // The Hitreg helper forwards field-specific defaults.
        assertTrue(registry.contains("toggle::set).resetDefault(defaultValue::getAsBoolean)"));
    }

    @Test void iconTooltipAndKeybindDescriptionsUseTheCommonSemanticAndLabelPaths() throws Exception {
        String icons = Files.readString(SourceTree.resolve(SourceTree.ADAPTER + "/src/client/resources/assets/aurora/icons/material_symbols.codepoints"));
        assertTrue(icons.contains("restart_alt f053"));
        String base = Files.readString(SourceTree.resolve(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/setting/FeatureSetting.java"));
        assertTrue(base.contains("Component.literal(resetTooltip)"));
        assertTrue(base.contains("resetTooltipSupplier = () -> resetTooltip"));
        assertTrue(base.contains("AuroraFontRenderer.ellipsize(font, text,"));
        String keybind = Files.readString(SourceTree.resolve(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/setting/KeybindSetting.java"));
        assertFalse(keybind.contains("intentionally skip"));
        assertTrue(keybind.contains("else renderWrappedLabel(ctx, label,"));
    }

    private static int closingParen(String text, int start) {
        int depth = 1;
        char quote = 0;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') quote = c;
            else if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i + 1;
        }
        throw new AssertionError("Unclosed setting constructor");
    }
}
