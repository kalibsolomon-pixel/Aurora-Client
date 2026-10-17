package com.aurora.client.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class FramePacerLowCpuTest {
    @ParameterizedTest
    @CsvSource({"500,2000,2000,500", "1750,6500,6500,1750", "2000,1000,2100,2000",
                "100,1000,1000,100", "-1,-1,150,50", "0,0,150,50"})
    void hybridUsesAShorterSpinWindowWithoutRewritingThresholds(int spin, int park, int normal, int low) {
        assertEquals(normal, FramePacer.hybridSpinWindowMicros(spin, park, false));
        assertEquals(low, FramePacer.hybridSpinWindowMicros(spin, park, true));
        assertTrue(low <= normal);
        assertEquals(normal, FramePacer.hybridSpinWindowMicros(spin, park, false), "Switching back restores manual policy");
    }

    @Test void lowCpuNeverIncreasesTheAllowanceAcrossSupportedManualValues() {
        for (int spin = 100; spin <= 2000; spin += 25) {
            for (int park = 1000; park <= 8000; park += 100) {
                int normal = FramePacer.hybridSpinWindowMicros(spin, park, false);
                int low = FramePacer.hybridSpinWindowMicros(spin, park, true);
                assertEquals(spin, low);
                assertTrue(low <= normal);
            }
        }
    }

    @Test void parkAndSpinAlgorithmsAndDispatchRemainUnchanged() throws Exception {
        String s = Files.readString(Path.of("src/client/java/com/aurora/client/util/FramePacer.java"))
                .replace("\r\n", "\n");
        assertTrue(s.contains("else waitUntilPark(target, spinTail);"));
        assertTrue(s.contains("case SPIN -> waitUntilSpin(target);"));
        assertTrue(s.contains("if (strat == null) strat = AuroraConfig.PacingStrategy.PARK;"));
        assertTrue(s.contains("case HYBRID -> waitUntilHybrid(target, hybridSpinWindowMicros("));
        assertEquals(1, s.split("cfg.framePacerLowCpuMode", -1).length - 1);
        // Hashes of the complete original methods at audited HEAD, including
        // their bodies. Pin algorithm preservation independently of the helper.
        assertEquals("682728fccccd05cb3dd25e03b6aa5cb10672ba3e57e499953f497937ddbfa1a3", methodHash(s, "waitUntilPark"));
        assertEquals("f25fec16f172d829fe9f80c9198c86b07d1e5fe2631cb3b84c64395ea982c663", methodHash(s, "waitUntilSpin"));
    }

    private static String methodHash(String s, String name) throws Exception {
        int start = s.indexOf("    public static void " + name);
        int end = s.indexOf("\n    }", start) + 6;
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(s.substring(start, end).getBytes(StandardCharsets.UTF_8)));
    }

    @Test void unsupportedControlCombinationsAreExplicitAndTheValueSurvivesSwitches() throws Exception {
        String ui = Files.readString(Path.of("src/client/java/com/aurora/client/screen/FeatureRegistry.java"));
        int start = ui.indexOf("new BooleanSetting(\"Low CPU (Hybrid only)\"");
        String control = ui.substring(start, ui.indexOf("SliderSetting.ofInt", start));
        assertTrue(control.contains("Park and Spin are unchanged"));
        assertTrue(control.contains(".disabled(() -> cfg.framePacingStrategy != AuroraConfig.PacingStrategy.HYBRID)"));
        assertTrue(control.contains("v -> cfg.framePacerLowCpuMode = v"));
        assertFalse(control.contains("framePacingStrategy ="), "Control never switches strategies");
    }
}
