package com.aurora.client.config;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The core cannot see LWJGL, so AuroraConfig spells its default keys as literals.
 * Read from the source because the release-defaults resource overlays a constructed
 * config, which would hide a wrong initializer.
 */
class AuroraConfigKeyDefaultsTest {
    private static final Path SOURCE = Path.of("core/src/main/java/com/aurora/client/config/AuroraConfig.java");

    @Test void literalKeyDefaultsAreTheGlfwCodesTheyName() throws Exception {
        String src = Files.readString(SOURCE);
        assertInitializer(src, "zoomKey", GLFW.GLFW_KEY_C);
        assertInitializer(src, "freeLookKey", GLFW.GLFW_KEY_V);
        assertInitializer(src, "toggleSprintKey", GLFW.GLFW_KEY_J);
        assertInitializer(src, "toggleSneakKey", GLFW.GLFW_KEY_K);
        assertInitializer(src, "hudEditorKey", GLFW.GLFW_KEY_RIGHT_SHIFT);
        assertInitializer(src, "worldMapKey", GLFW.GLFW_KEY_M);
    }

    private static void assertInitializer(String src, String field, int expected) {
        Matcher m = Pattern.compile("public int " + field + "\\s*=\\s*(-?\\d+);").matcher(src);
        assertTrue(m.find(), field + " has no literal initializer");
        assertEquals(expected, Integer.parseInt(m.group(1)), field);
    }
}
