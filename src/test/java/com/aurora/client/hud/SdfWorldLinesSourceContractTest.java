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
    void sdfIsTheOnlyLinePathWhileTheShaderWorks() {
        assertTrue(SourceTree.read("hud/HitboxRenderer.java")
                .contains("boolean sdf = WorldLineRenderer.sdfAvailable();"));
        assertTrue(SourceTree.read("hud/BlockOverlayRenderer.java")
                .contains("boolean sdf = WorldLineRenderer.sdfAvailable();"));
    }

    @Test
    void noSdfSwitchesRemain() {
        for (String removed : new String[]{"hitboxSdfLines", "blockOverlaySdfLines", "textSdf"}) {
            assertFalse(java.util.Arrays.stream(AuroraConfig.class.getFields()).anyMatch(f -> f.getName().equals(removed)),
                    removed + " was removed 2026-11-19: SDF rendering is the only path");
        }
    }
}
