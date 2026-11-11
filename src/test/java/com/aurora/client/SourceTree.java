package com.aurora.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The mod's production sources, for source-contract tests. Since the core module
 * (ADR 0003) they live in two roots, and a test that walks only one misses the other.
 */
public final class SourceTree {
    public static final Path CLIENT = Path.of("src/client/java");
    public static final Path CORE = Path.of("core/src/main/java");
    public static final List<Path> ROOTS = List.of(CLIENT, CORE);
    private static final String PACKAGE = "com/aurora/client/";

    private SourceTree() {}

    /** A file by its path below {@code com/aurora/client/}, from whichever root holds it. */
    public static Path file(String relative) {
        for (Path root : ROOTS) {
            Path candidate = root.resolve(PACKAGE + relative);
            if (Files.exists(candidate)) return candidate;
        }
        throw new AssertionError("no production source " + relative + " in " + ROOTS);
    }

    public static String read(String relative) {
        try {
            return Files.readString(file(relative)).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every production {@code .java} file in both roots. The caller closes the stream. */
    public static Stream<Path> javaFiles() {
        return ROOTS.stream().flatMap(root -> {
            try {
                return Files.walk(root);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).filter(p -> p.toString().endsWith(".java"));
    }

    /** {@code x/Y.java} for a file in either root, with forward slashes. */
    public static String relative(Path file) {
        for (Path root : ROOTS) {
            Path base = root.resolve(PACKAGE);
            if (file.startsWith(base)) return base.relativize(file).toString().replace('\\', '/');
        }
        return file.toString().replace('\\', '/');
    }
}
