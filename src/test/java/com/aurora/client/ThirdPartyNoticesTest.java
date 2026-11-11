package com.aurora.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * THIRD_PARTY_NOTICES.md must cover everything Aurora contains from other
 * projects. When this fails, update tools/gen_third_party_notices.py (and
 * tools/licenses/), then run it.
 */
class ThirdPartyNoticesTest {
    private static final Path NOTICES = Path.of("THIRD_PARTY_NOTICES.md");
    private static final Path FONTS = Path.of("src/client/resources/assets/aurora/font");
    private static final List<String> PROJECTS = List.of("BetterHitreg", "Freelook++", "HitColor", "AppleSkin");

    private static String read(Path p) throws Exception {
        return Files.readString(p).replace("\r\n", "\n");
    }

    @Test void everyBundledFontIsListedWithItsLicense() throws Exception {
        String notices = read(NOTICES);
        String folderLicense = read(FONTS.resolve("license.txt"));
        try (Stream<Path> files = Files.list(FONTS)) {
            for (Path font : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".ttf"))::iterator) {
                String name = font.getFileName().toString();
                assertTrue(notices.contains(name), name + " is not in THIRD_PARTY_NOTICES.md");
                assertTrue(folderLicense.contains(name), name + " is not in the font folder's license.txt");
            }
        }
        assertTrue(folderLicense.contains("SIL OPEN FONT LICENSE Version 1.1"));
    }

    @Test void everyLicenseTextIsReproducedVerbatim() throws Exception {
        String notices = read(NOTICES);
        try (Stream<Path> files = Files.list(Path.of("tools/licenses"))) {
            for (Path license : (Iterable<Path>) files::iterator) {
                String body = read(license).strip();
                assertTrue(notices.contains(body), license.getFileName() + " is not reproduced in THIRD_PARTY_NOTICES.md");
            }
        }
    }

    @Test void everyBundledJarIsCovered() throws Exception {
        String notices = read(NOTICES);
        String build = read(Path.of("build.gradle"));
        // Jar-in-jar libraries are the names in a list whose .each block runs `include`.
        Matcher lists = Pattern.compile("\\[([^\\]]*)\\]\\.each \\{\\s*include").matcher(build);
        List<String> jars = new ArrayList<>();
        while (lists.find()) {
            Matcher names = Pattern.compile("\"([\\w.-]+)\"").matcher(lists.group(1));
            while (names.find()) jars.add(names.group(1));
        }
        assertFalse(jars.isEmpty(), "found no bundled jars in build.gradle; the pattern is out of date");
        for (String jar : jars) assertTrue(notices.contains("`" + jar + "`"), jar + " is not in THIRD_PARTY_NOTICES.md");
        assertTrue(build.contains("from(\"THIRD_PARTY_NOTICES.md\")"), "the notices must ship in the mod jar");
    }

    @Test void everyCreditInTheSourceNamesAListedProject() throws Exception {
        String notices = read(NOTICES);
        for (String project : PROJECTS) assertTrue(notices.contains(project), project);
        // A credit may wrap onto the next comment line and sit in a <b> tag.
        Pattern credit = Pattern.compile("(?i)(adapted from|ported from|borrowed from|port of)[\\s*]+(?:<b>)?(\\S+)");
        try (Stream<Path> files = SourceTree.javaFiles()) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Matcher m = credit.matcher(read(file));
                while (m.find()) {
                    String source = m.group(2);
                    // Aurora's own code: the minimap pipeline, the launcher's palette policy.
                    if (source.equals("the") || source.startsWith("appearance.rs")) continue;
                    boolean listed = PROJECTS.stream().anyMatch(p -> m.group(0).contains(p)
                            || source.toLowerCase().contains(p.toLowerCase().replace("++", "")));
                    assertTrue(listed, SourceTree.relative(file) + " credits \"" + m.group(0) + "\", not in the notices");
                }
            }
        }
    }
}
