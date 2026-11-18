package com.aurora.client.hud;

import com.aurora.client.SourceTree;
import com.aurora.client.config.AuroraConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The opt-in SDF world lines: the role step packed into LineWidth must match the
 * shader's decode, both features must gate on their own field, and an SDF buffer
 * is only requested when the shader is known to work (vanilla lines would draw a
 * role-encoded width more than a thousand pixels wide).
 */
class SdfWorldLinesSourceContractTest {

    private static String read(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n");
    }

    @Test
    void roleStepMatchesShaderDecode() throws IOException {
        String renderer = SourceTree.read("hud/WorldLineRenderer.java");
        assertTrue(renderer.contains("SDF_ROLE = 1024f;"));
        String shader = read(Path.of(SourceTree.ADAPTER, "src/client/resources/assets/aurora/shaders/core/sdf_lines.vsh"));
        assertTrue(shader.contains("float role = floor(LineWidth / 1024.0);"));
        assertTrue(shader.contains("float width = LineWidth - role * 1024.0;"));
    }

    @Test
    void featuresGateOnTheirOwnFieldAndShaderHealth() {
        assertTrue(SourceTree.read("hud/HitboxRenderer.java")
                .contains("boolean sdf = cfg.hitboxSdfLines && WorldLineRenderer.sdfAvailable();"));
        assertTrue(SourceTree.read("hud/BlockOverlayRenderer.java")
                .contains("boolean sdf = cfg.blockOverlaySdfLines && WorldLineRenderer.sdfAvailable();"));
    }

    @Test
    void sdfLinesShipOff() {
        AuroraConfig fresh = new AuroraConfig();
        assertFalse(fresh.hitboxSdfLines);
        assertFalse(fresh.blockOverlaySdfLines);
    }
}
