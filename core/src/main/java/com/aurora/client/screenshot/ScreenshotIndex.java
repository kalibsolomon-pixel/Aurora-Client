package com.aurora.client.screenshot;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pure, Minecraft-free indexing, search, validation, rename, delete, and crop
 * geometry utilities for Aurora's Screenshot Manager.
 */
public final class ScreenshotIndex {
    public record Entry(Path path, String fileName, String displayName, long modifiedMs, long sizeBytes) {}

    public record CropRect(int x, int y, int width, int height) {}

    private static final String ILLEGAL_CHARS = "\\/:*?\"<>|";

    private ScreenshotIndex() {}

    /** Scans {@code screenshotsDir} for PNG screenshots, newest first. */
    public static List<Entry> scan(Path screenshotsDir) {
        if (screenshotsDir == null || !Files.isDirectory(screenshotsDir)) return List.of();
        List<Entry> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(screenshotsDir, "*.png")) {
            for (Path p : ds) {
                if (!Files.isRegularFile(p)) continue;
                String name = p.getFileName().toString();
                long modified = 0L;
                long size = 0L;
                try {
                    modified = Files.getLastModifiedTime(p).toMillis();
                    size = Files.size(p);
                } catch (IOException ignored) {}
                out.add(new Entry(p, name, stripPng(name), modified, size));
            }
        } catch (IOException ignored) {
            return List.of();
        }
        out.sort(Comparator.comparingLong(Entry::modifiedMs).reversed()
                .thenComparing(Entry::fileName, Comparator.reverseOrder()));
        return List.copyOf(out);
    }

    /** Multi-term case-insensitive filename filter. */
    public static List<Entry> filter(List<Entry> entries, String query) {
        if (entries == null || entries.isEmpty()) return List.of();
        if (query == null || query.isBlank()) return entries;
        String[] terms = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        List<Entry> out = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            String hay = e.fileName().toLowerCase(Locale.ROOT);
            boolean ok = true;
            for (String t : terms) {
                if (!t.isEmpty() && !hay.contains(t)) {
                    ok = false;
                    break;
                }
            }
            if (ok) out.add(e);
        }
        return List.copyOf(out);
    }

    /** Ensures a user-entered screenshot filename ends with {@code .png}. */
    public static String normalizePngName(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(".png")) return trimmed;
        return trimmed + ".png";
    }

    /**
     * Validates a candidate rename inside {@code dir}. Returns a human-readable
     * refusal reason or {@code null} when acceptable.
     */
    public static String validateRename(String rawName, Path dir, Path currentFile) {
        if (rawName == null || rawName.isBlank()) return "Enter a filename";
        String trimmed = rawName.trim();
        String base = stripPng(trimmed);
        if (base.isEmpty() || base.equals(".") || base.equals("..")) return "Invalid filename";
        if (base.length() > 120) return "Filename is too long";
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < 0x20 || ILLEGAL_CHARS.indexOf(c) >= 0) {
                return "Contains invalid character";
            }
        }
        String normalized = normalizePngName(trimmed);
        if (dir != null) {
            Path target = dir.resolve(normalized).normalize();
            if (!target.getParent().equals(dir.normalize())) return "Invalid filename";
            if (currentFile != null && target.equals(currentFile.normalize())) return null;
            if (Files.exists(target)) return "A screenshot with that name already exists";
        }
        return null;
    }

    /** Renames {@code source} inside its parent directory. */
    public static Path rename(Path source, String rawNewName) throws IOException {
        Path dir = source.getParent();
        String problem = validateRename(rawNewName, dir, source);
        if (problem != null) throw new IllegalArgumentException(problem);
        Path target = dir.resolve(normalizePngName(rawNewName)).normalize();
        if (target.equals(source.normalize())) return source;
        return Files.move(source, target);
    }

    /** Deletes {@code file} if it sits directly inside {@code screenshotsDir}. */
    public static boolean delete(Path screenshotsDir, Path file) throws IOException {
        if (screenshotsDir == null || file == null) return false;
        Path normDir = screenshotsDir.toAbsolutePath().normalize();
        Path normFile = file.toAbsolutePath().normalize();
        if (!normDir.equals(normFile.getParent())) {
            throw new IllegalArgumentException("Refusing to delete file outside screenshots directory");
        }
        return Files.deleteIfExists(normFile);
    }

    /** Computes a clamped pixel crop rectangle from normalized [0..1] drag endpoints. */
    public static CropRect computeCropRect(int imgW, int imgH, float nx0, float ny0, float nx1, float ny1) {
        if (imgW <= 0 || imgH <= 0) return new CropRect(0, 0, 0, 0);
        float left = Math.max(0f, Math.min(1f, Math.min(nx0, nx1)));
        float right = Math.max(0f, Math.min(1f, Math.max(nx0, nx1)));
        float top = Math.max(0f, Math.min(1f, Math.min(ny0, ny1)));
        float bottom = Math.max(0f, Math.min(1f, Math.max(ny0, ny1)));

        int x = Math.min(imgW - 1, Math.max(0, Math.round(left * imgW)));
        int y = Math.min(imgH - 1, Math.max(0, Math.round(top * imgH)));
        int x2 = Math.min(imgW, Math.max(x + 1, Math.round(right * imgW)));
        int y2 = Math.min(imgH, Math.max(y + 1, Math.round(bottom * imgH)));
        return new CropRect(x, y, x2 - x, y2 - y);
    }

    /** Writer callback used by {@link #writeCroppedAtomically(Path, PathWriter)}. */
    @FunctionalInterface
    public interface PathWriter {
        void write(Path target) throws IOException;
    }

    private static final Object CROP_PUBLISH_LOCK = new Object();

    /**
     * Writes a cropped screenshot into a unique sibling temporary file first,
     * then atomically publishes it to the first unused {@code _crop*.png}
     * filename without overwriting existing files. On any encoding or I/O
     * failure, the temporary file is removed and no partial output is left at
     * a final screenshot filename.
     */
    public static Path writeCroppedAtomically(Path source, PathWriter writer) throws IOException {
        if (source == null || writer == null) {
            throw new IllegalArgumentException("Source path and writer are required");
        }
        Path dir = source.getParent();
        if (dir == null) {
            throw new IOException("Source screenshot has no parent directory");
        }
        String base = stripPng(source.getFileName().toString());
        Path temp = Files.createTempFile(dir, "." + base + "_crop_", ".tmp");
        boolean published = false;
        try {
            writer.write(temp);
            synchronized (CROP_PUBLISH_LOCK) {
                for (int i = 1; i < 1000; i++) {
                    Path candidate = i == 1
                            ? dir.resolve(base + "_crop.png")
                            : dir.resolve(base + "_crop_" + i + ".png");
                    try {
                        Files.move(temp, candidate);
                        published = true;
                        return candidate;
                    } catch (FileAlreadyExistsException ignored) {
                        // Claim the next suffix.
                    }
                }
                for (int attempt = 0; attempt < 16; attempt++) {
                    Path candidate = dir.resolve(base + "_crop_" + System.currentTimeMillis()
                            + (attempt == 0 ? "" : "_" + attempt) + ".png");
                    try {
                        Files.move(temp, candidate);
                        published = true;
                        return candidate;
                    } catch (FileAlreadyExistsException ignored) {
                        // Retry with a different suffix.
                    }
                }
                throw new IOException("Could not allocate a unique cropped screenshot filename");
            }
        } finally {
            if (!published) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            }
        }
    }

    /** Generates a non-colliding sibling output path for a cropped screenshot. */
    public static Path croppedOutputPath(Path source) {
        Path dir = source.getParent();
        String base = stripPng(source.getFileName().toString());
        Path first = dir.resolve(base + "_crop.png");
        if (!Files.exists(first)) return first;
        for (int i = 2; i < 1000; i++) {
            Path candidate = dir.resolve(base + "_crop_" + i + ".png");
            if (!Files.exists(candidate)) return candidate;
        }
        return dir.resolve(base + "_crop_" + System.currentTimeMillis() + ".png");
    }

    public static String stripPng(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT).endsWith(".png")
                ? name.substring(0, name.length() - 4)
                : name;
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return (bytes / 1024L) + " KB";
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
