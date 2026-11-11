package com.aurora.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The mod's production sources, for source-contract tests. Since the core module
 * (ADR 0003) they span core, ports and the version adapter; a test must scan all three.
 */
public final class SourceTree {
    public static final String ADAPTER = System.getProperty("aurora.adapterRoot", "adapters/minecraft-26.3");
    public static final Path CLIENT = Path.of(ADAPTER, "src/client/java");
    public static final Path CORE = Path.of("core/src/main/java");
    public static final List<Path> ROOTS = List.of(CLIENT, CORE, Path.of("ports/src/main/java"));
    private static final String PACKAGE = "com/aurora/client/";

    private SourceTree() {}

    /** Resolve maintained source paths while allowing pure classes to move between layers. */
    public static Path resolve(java.net.URI uri) { return Path.of(uri); }

    public static Path resolve(String first, String... rest) {
        Path candidate = Path.of(first, rest);
        String path = candidate.toString().replace('\\', '/');
        if (path.startsWith("src/client/")) candidate = Path.of(ADAPTER).resolve(candidate);
        String marker = "src/client/java/" + PACKAGE;
        int at = path.indexOf(marker);
        if (at >= 0 && path.endsWith(".java")) return file(path.substring(at + marker.length()));
        return candidate;
    }

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

    /** Every production {@code .java} file in all production roots. The caller closes the stream. */
    public static Stream<Path> javaFiles() {
        return ROOTS.stream().flatMap(root -> {
            try {
                return Files.walk(root);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).filter(p -> p.toString().endsWith(".java"));
    }

    /** {@code x/Y.java} for a file in any production root, with forward slashes. */
    public static String relative(Path file) {
        for (Path root : ROOTS) {
            Path base = root.resolve(PACKAGE);
            if (file.startsWith(base)) return base.relativize(file).toString().replace('\\', '/');
        }
        return file.toString().replace('\\', '/');
    }
}
