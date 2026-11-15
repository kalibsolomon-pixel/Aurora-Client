package com.aurora.client.pulsar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Pulsar controls are announced by the label shown beside them, never by an internal id. */
class PulsarNarrationContractTest {
    private static final Path GUI = Path.of("adapters/shared/src/client/java/dev/phenomena/gui");

    @Test
    void settingRowsNarrateTheirVisibleLabel() throws Exception {
        String content = Files.readString(GUI.resolve("screens/Content.java"));
        assertTrue(content.contains("if (control instanceof KSlider slider) slider.narrate(label);"));
        assertTrue(content.contains("else if (control instanceof KToggle toggle) toggle.narrate(label);"));
    }

    @Test
    void slidersAndTogglesSpeakTheNarrationLabel() throws Exception {
        String slider = Files.readString(GUI.resolve("components/KSlider.java"));
        assertTrue(slider.contains("setMessage(Component.literal(spoken + \": \" + format.apply(current())));"));
        assertFalse(slider.contains("setMessage(Component.literal(id + "), "slider narrates its id");
        String toggle = Files.readString(GUI.resolve("components/KToggle.java"));
        assertTrue(toggle.contains("setMessage(Component.literal(spoken + (state ? \": On\" : \": Off\")));"));
        assertFalse(toggle.contains("setMessage(Component.literal(id + "), "toggle narrates its id");
    }
}
