package com.aurora.client.resourcepack;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure core folder-tree model and path utilities for nested resource packs
 * inside {@code resourcepacks/}.
 *
 * <p>A directory inside {@code resourcepacks/} is treated as a resource pack
 * when it contains a {@code pack.mcmeta} file directly inside it; otherwise it
 * is treated as an organizing folder that may contain {@code .zip} packs,
 * unpacked pack directories, and deeper organizing folders (up to
 * {@link #MAX_DEPTH}).
 */
public final class PackFolderTree {

    /** Maximum subfolder nesting depth scanned under {@code resourcepacks/}. */
    public static final int MAX_DEPTH = 6;
    private static final String FILE_PREFIX = "file/";

    private PackFolderTree() {}

    /** One clickable breadcrumb segment (root has {@code path == ""}). */
    public record Breadcrumb(String label, String path) {}

    /** A direct child folder shown in the Available pane. */
    public record FolderEntry(String name, String path, int packCount) {}

    /** Minimal metadata for one resource pack used during folder/search partitioning. */
    public record PackItem(String id, String title, String description, String folderPath, int originalIndex) {}

    /** Partitioned view of the Available pane for a folder and optional search query. */
    public record FolderView(
            String currentFolder,
            boolean searching,
            List<Breadcrumb> breadcrumbs,
            List<FolderEntry> folders,
            List<PackItem> packs) {}

    /** Normalizes a folder path to clean forward-slash relative form ({@code ""} for root). */
    public static String normalizeFolder(String folder) {
        if (folder == null || folder.isBlank()) return "";
        String cleaned = folder.replace('\\', '/').trim();
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        while (cleaned.endsWith("/")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        if (cleaned.isEmpty()) return "";
        String[] parts = cleaned.split("/");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) continue;
            if (sb.length() > 0) sb.append('/');
            sb.append(part);
        }
        return sb.toString();
    }

    /**
     * Returns the relative forward-slash path of {@code packPath} under {@code root}
     * (e.g. {@code "PvP/16x/my_pack.zip"}), or the file name when unrelated.
     */
    public static String relativePackPath(Path root, Path packPath) {
        if (packPath == null) return "";
        if (root == null) return packPath.getFileName() != null ? packPath.getFileName().toString() : "";
        try {
            Path rel = root.toAbsolutePath().normalize().relativize(packPath.toAbsolutePath().normalize());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rel.getNameCount(); i++) {
                if (i > 0) sb.append('/');
                sb.append(rel.getName(i).toString());
            }
            return sb.toString();
        } catch (IllegalArgumentException e) {
            return packPath.getFileName() != null ? packPath.getFileName().toString() : "";
        }
    }

    /**
     * Extracts the organizing folder path for a Minecraft pack ID.
     * {@code "file/PvP/16x/pack.zip"} → {@code "PvP/16x"};
     * {@code "file/pack.zip"} or {@code "vanilla"} → {@code ""}.
     */
    public static String folderOfPackId(String packId) {
        if (packId == null || !packId.startsWith(FILE_PREFIX)) return "";
        String rest = packId.substring(FILE_PREFIX.length()).replace('\\', '/');
        int slash = rest.lastIndexOf('/');
        if (slash <= 0) return "";
        return normalizeFolder(rest.substring(0, slash));
    }

    /** Returns the leaf filename portion of a pack ID (or the raw ID if not a file pack). */
    public static String leafNameOfPackId(String packId) {
        if (packId == null) return "";
        String rest = packId.startsWith(FILE_PREFIX) ? packId.substring(FILE_PREFIX.length()) : packId;
        rest = rest.replace('\\', '/');
        int slash = rest.lastIndexOf('/');
        return slash >= 0 ? rest.substring(slash + 1) : rest;
    }

    /** Returns the parent folder of {@code folder} ({@code ""} for root or top-level folders). */
    public static String parentFolder(String folder) {
        String norm = normalizeFolder(folder);
        int slash = norm.lastIndexOf('/');
        return slash <= 0 ? "" : norm.substring(0, slash);
    }

    /** Builds clickable breadcrumbs from {@code Root} down to {@code currentFolder}. */
    public static List<Breadcrumb> breadcrumbs(String currentFolder) {
        String norm = normalizeFolder(currentFolder);
        List<Breadcrumb> out = new ArrayList<>();
        out.add(new Breadcrumb("Root", ""));
        if (norm.isEmpty()) return out;
        String[] parts = norm.split("/");
        StringBuilder acc = new StringBuilder();
        for (String part : parts) {
            if (acc.length() > 0) acc.append('/');
            acc.append(part);
            out.add(new Breadcrumb(part, acc.toString()));
        }
        return out;
    }

    /** True when {@code dir} is an unpacked resource pack directory (contains {@code pack.mcmeta}). */
    public static boolean isPackDirectory(Path dir) {
        return dir != null && Files.isDirectory(dir) && Files.isRegularFile(dir.resolve("pack.mcmeta"));
    }

    /** Safely compares two filesystem paths for equality, handling normalized and real paths. */
    public static boolean samePath(Path a, Path b) {
        if (a == null || b == null) return false;
        Path na = a.toAbsolutePath().normalize();
        Path nb = b.toAbsolutePath().normalize();
        if (na.equals(nb)) return true;
        try {
            return Files.exists(na) && Files.exists(nb) && Files.isSameFile(na, nb);
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * Resolves a normalized relative folder path safely inside {@code root}.
     * Falls back to {@code root} if empty or if resolution would escape {@code root}.
     */
    public static Path resolveSubfolder(Path root, String folder) {
        if (root == null) return null;
        String norm = normalizeFolder(folder);
        if (norm.isEmpty()) return root;
        Path base = root.toAbsolutePath().normalize();
        Path candidate = base;
        for (String segment : norm.split("/")) {
            candidate = candidate.resolve(segment);
        }
        candidate = candidate.normalize();
        return candidate.startsWith(base) ? candidate : root;
    }

    /**
     * Recursively discovers all non-pack organizing subdirectories under {@code root}
     * (excluding {@code root} itself, unpacked resource packs containing {@code pack.mcmeta},
     * hidden directories starting with {@code .}, and symlink cycles).
     */
    public static List<Path> discoverOrganizingFolders(Path root, int maxDepth) {
        if (root == null || !Files.isDirectory(root) || maxDepth <= 0) return List.of();
        List<Path> out = new ArrayList<>();
        Set<Path> visited = new HashSet<>();
        try {
            visited.add(root.toRealPath());
        } catch (IOException ignored) {
            visited.add(root.toAbsolutePath().normalize());
        }
        scanFolders(root, 1, maxDepth, visited, out);
        out.sort(Comparator.comparing(p -> relativePackPath(root, p).toLowerCase(Locale.ROOT)));
        return out;
    }

    private static void scanFolders(Path dir, int depth, int maxDepth, Set<Path> visited, List<Path> out) {
        if (depth > maxDepth) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) continue;
                Path fileName = entry.getFileName();
                if (fileName == null || fileName.toString().startsWith(".")) continue;
                if (isPackDirectory(entry)) continue;
                Path canonical;
                try {
                    canonical = entry.toRealPath();
                } catch (IOException ignored) {
                    continue;
                }
                if (!visited.add(canonical)) continue;
                out.add(entry);
                scanFolders(entry, depth + 1, maxDepth, visited, out);
            }
        } catch (IOException ignored) {
            // Unreadable directory — skip gracefully.
        }
    }

    /** Returns normalized relative folder paths (e.g. {@code "PvP"}, {@code "PvP/16x"}) discovered on disk. */
    public static Set<String> discoverOrganizingFolderPaths(Path root) {
        if (root == null || !Files.isDirectory(root)) return Set.of();
        Set<String> paths = new LinkedHashSet<>();
        for (Path dir : discoverOrganizingFolders(root, MAX_DEPTH)) {
            String rel = normalizeFolder(relativePackPath(root, dir));
            if (!rel.isEmpty()) paths.add(rel);
        }
        return paths;
    }

    /**
     * Partitions {@code availablePacks} into direct child folders and direct packs
     * for {@code currentFolder}, or returns a flat recursive search result when
     * {@code searchQuery} is non-blank.
     */
    public static FolderView buildView(
            List<PackItem> availablePacks,
            Set<String> diskFolders,
            String currentFolder,
            String searchQuery) {
        String normCurrent = normalizeFolder(currentFolder);
        List<Breadcrumb> crumbs = breadcrumbs(normCurrent);
        String trimmedQuery = searchQuery == null ? "" : searchQuery.trim();
        if (!trimmedQuery.isEmpty()) {
            String[] terms = trimmedQuery.toLowerCase(Locale.ROOT).split("\\s+");
            List<PackItem> matches = new ArrayList<>();
            for (PackItem pack : availablePacks) {
                if (matchesAllTerms(pack, terms)) matches.add(pack);
            }
            matches.sort(Comparator.comparing((PackItem p) -> p.title().toLowerCase(Locale.ROOT))
                    .thenComparing(p -> p.folderPath().toLowerCase(Locale.ROOT)));
            return new FolderView(normCurrent, true, crumbs, List.of(), matches);
        }

        // Direct child folder name -> recursive pack count inside that child folder.
        Map<String, Integer> childCounts = new HashMap<>();
        String prefix = normCurrent.isEmpty() ? "" : normCurrent + "/";

        if (diskFolders != null) {
            for (String rawFolder : diskFolders) {
                String f = normalizeFolder(rawFolder);
                if (f.isEmpty()) continue;
                String child = directChildName(f, prefix);
                if (child != null) childCounts.putIfAbsent(child, 0);
            }
        }

        List<PackItem> directPacks = new ArrayList<>();
        for (PackItem pack : availablePacks) {
            String pf = normalizeFolder(pack.folderPath());
            if (pf.equals(normCurrent)) {
                directPacks.add(pack);
            } else {
                String child = directChildName(pf, prefix);
                if (child != null) {
                    childCounts.merge(child, 1, Integer::sum);
                }
            }
        }

        List<FolderEntry> folders = new ArrayList<>(childCounts.size());
        for (Map.Entry<String, Integer> e : childCounts.entrySet()) {
            String path = normCurrent.isEmpty() ? e.getKey() : normCurrent + "/" + e.getKey();
            folders.add(new FolderEntry(e.getKey(), path, e.getValue()));
        }
        folders.sort(Comparator.comparing(f -> f.name().toLowerCase(Locale.ROOT)));
        directPacks.sort(Comparator.comparing(p -> p.title().toLowerCase(Locale.ROOT)));

        return new FolderView(normCurrent, false, crumbs, Collections.unmodifiableList(folders),
                Collections.unmodifiableList(directPacks));
    }

    private static String directChildName(String candidateFolder, String prefix) {
        if (prefix.isEmpty()) {
            int slash = candidateFolder.indexOf('/');
            return slash < 0 ? candidateFolder : candidateFolder.substring(0, slash);
        }
        if (!candidateFolder.startsWith(prefix)) return null;
        String rest = candidateFolder.substring(prefix.length());
        if (rest.isEmpty()) return null;
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    private static boolean matchesAllTerms(PackItem pack, String[] terms) {
        String haystack = (pack.title() + " " + pack.description() + " " + pack.folderPath() + " " + pack.id())
                .toLowerCase(Locale.ROOT);
        for (String term : terms) {
            if (!term.isEmpty() && !haystack.contains(term)) return false;
        }
        return true;
    }
}
