package com.aurora.client.modrinth;

import com.aurora.client.AuroraClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Keyless Modrinth v2 API client for browsing and installing resource packs.
 *
 * <p>All network I/O runs on a dedicated daemon executor so neither the
 * client tick thread nor the render thread ever blocks on the network —
 * consistent with Aurora's "never stall the tick thread" rule (see
 * {@code AuroraConfig.SAVE_EXECUTOR} for the same rationale applied to disk).
 *
 * <h2>Endpoints used</h2>
 * <ul>
 *   <li>{@code GET /v2/search?query=...&facets=[["project_type:resourcepack"]]&limit=40}
 *       — keyless search.</li>
 *   <li>{@code GET /v2/project/{id}/version?game_versions=["1.21.11"]&loaders=["minecraft"]}
 *       — pick the newest version whose primary file is a resource pack for
 *       this MC version.</li>
 *   <li>{@code <download_url>} — direct CDN download (Modrinth serves the
 *       primary file from {@code cdn.modrinth.com}; no auth needed).</li>
 * </ul>
 *
 * <h2>Rate limits</h2>
 * Modrinth's keyless rate limits are generous for a single-user client
 * (search + a handful of version lookups per session). We send a descriptive
 * {@code User-Agent} so Modrinth can contact us if a misbehaving build ever
 * trips a limit.
 */
public final class ModrinthApi {

    private static final String BASE_URL = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "AuroraClient/" + resolveAuroraVersion() + " (minecraft resourcepack browser)";

    /** Dedicated daemon executor — never lets network I/O touch the tick thread. */
    static final Executor IO_EXECUTOR = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "Aurora-ModrinthIO");
        t.setDaemon(true);
        return t;
    });

    /** Single shared HTTP client. HttpClient is thread-safe and reusable. */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .executor(IO_EXECUTOR)
            .build();

    /** @return the shared HTTP client (used by the icon cache for thumbnails). */
    static HttpClient httpClient() { return HTTP; }

    private ModrinthApi() {}

    // ------------------------------------------------------------------
    //  Search
    // ------------------------------------------------------------------

    /**
     * Search Modrinth for resource packs matching {@code query}.
     *
     * @param query free-text search (can be empty for a popularity feed)
     * @return list of hits (newest-first by relevance/download sort), or a
     *         failed future on network/parse error
     */
    public static CompletableFuture<List<ModrinthProject>> search(String query) {
        return search(query, null);
    }

    /**
     * Search Modrinth for resource packs matching {@code query}, optionally
     * filtered by a Modrinth category slug (e.g. {@code "16x"},
     * {@code "animated"}, {@code "gui"}).
     *
     * @param query    free-text search (can be empty for a popularity feed)
     * @param category Modrinth category slug, or {@code null} for no filter
     * @return list of hits, or a failed future on network/parse error
     */
    public static CompletableFuture<List<ModrinthProject>> search(String query, String category) {
        String q = query == null ? "" : query.trim();
        // Build facets JSON: [["project_type:resourcepack"],["categories:<cat>"]]
        // Multiple top-level arrays are AND'd together by Modrinth.
        String facetsJson;
        if (category != null && !category.isEmpty()) {
            facetsJson = "[[\"project_type:resourcepack\"],[\"categories:" + category + "\"]]";
        } else {
            facetsJson = "[[\"project_type:resourcepack\"]]";
        }
        String facets = java.net.URLEncoder.encode(facetsJson,
                java.nio.charset.StandardCharsets.UTF_8);
        String encQuery = java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8);
        String url = BASE_URL + "/search?query=" + encQuery
                + "&facets=" + facets
                + "&limit=40";

        HttpRequest req = newRequest(url).GET().build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(ModrinthApi::parseSearchResponse, IO_EXECUTOR);
    }

    private static List<ModrinthProject> parseSearchResponse(HttpResponse<String> resp) {
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("Modrinth search returned HTTP " + resp.statusCode());
        }
        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonArray hits = root.getAsJsonArray("hits");
        if (hits == null) return Collections.emptyList();

        List<ModrinthProject> out = new ArrayList<>(hits.size());
        for (JsonElement e : hits) {
            JsonObject h = e.getAsJsonObject();
            out.add(new ModrinthProject(
                    optString(h, "project_id"),
                    optString(h, "slug"),
                    optString(h, "title"),
                    optString(h, "author"),
                    optString(h, "description"),
                    // "icon_url" may be null for icon-less projects
                    h.has("icon_url") && !h.get("icon_url").isJsonNull()
                            ? h.get("icon_url").getAsString() : null,
                    optLong(h, "downloads"),
                    optLong(h, "followers")
            ));
        }
        return out;
    }

    // ------------------------------------------------------------------
    //  Version resolution
    // ------------------------------------------------------------------

    /**
     * Fetch the newest <em>release</em> version of {@code project} that
     * targets the running Minecraft version and the {@code minecraft}
     * "loader" (resource packs use the literal loader id {@code "minecraft"}).
     *
     * @return the chosen version, or empty if no compatible release exists
     */
    public static CompletableFuture<Optional<ModrinthVersion>> bestVersion(ModrinthProject project) {
        String mc = resolveMinecraftVersion();
        // The version endpoint takes game_versions + loaders as URL-encoded JSON arrays.
        String gvs = java.net.URLEncoder.encode("[\"" + mc + "\"]", java.nio.charset.StandardCharsets.UTF_8);
        String lds = java.net.URLEncoder.encode("[\"minecraft\"]", java.nio.charset.StandardCharsets.UTF_8);
        String idOrSlug = project.slug != null && !project.slug.isEmpty() ? project.slug : project.projectId;
        String url = BASE_URL + "/project/" + idOrSlug + "/version"
                + "?game_versions=" + gvs + "&loaders=" + lds;

        HttpRequest req = newRequest(url).GET().build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(ModrinthApi::parseBestVersion, IO_EXECUTOR);
    }

    private static Optional<ModrinthVersion> parseBestVersion(HttpResponse<String> resp) {
        if (resp.statusCode() / 100 != 2) return Optional.empty();
        JsonArray versions = JsonParser.parseString(resp.body()).getAsJsonArray();
        if (versions == null || versions.isEmpty()) return Optional.empty();

        // Prefer release > beta > alpha; Modrinth returns newest-first per
        // version, so the first release we see is the newest release.
        ModrinthVersion fallback = null;
        for (JsonElement e : versions) {
            JsonObject v = e.getAsJsonObject();
            String type = optString(v, "version_type");
            ModrinthVersion mv = extractVersion(v);
            if (mv == null) continue;
            if ("release".equals(type)) return Optional.of(mv);
            if (fallback == null && ("beta".equals(type) || "alpha".equals(type))) {
                fallback = mv;
            }
        }
        return Optional.ofNullable(fallback);
    }

    private static ModrinthVersion extractVersion(JsonObject v) {
        JsonArray files = v.getAsJsonArray("files");
        if (files == null || files.isEmpty()) return null;
        // Primary file is conventionally files[0] and is_primary == true.
        JsonObject primary = files.get(0).getAsJsonObject();
        for (JsonElement f : files) {
            JsonObject fo = f.getAsJsonObject();
            if (fo.has("primary") && fo.get("primary").getAsBoolean()) {
                primary = fo;
                break;
            }
        }
        return new ModrinthVersion(
                optString(v, "name"),
                optString(v, "version_number"),
                optString(v, "version_type"),
                optString(primary, "url"),
                optString(primary, "filename"),
                optLong(primary, "size")
        );
    }

    // ------------------------------------------------------------------
    //  Download + install
    // ------------------------------------------------------------------

    /** Result of an install attempt. */
    public enum InstallStatus { DOWNLOADED, ALREADY_INSTALLED, FAILED }

    /**
     * Download {@code version} into the game's {@code resourcepacks/} folder.
     *
     * <p>Short-circuit semantics: if a file with the same name already
     * exists in the folder we treat it as already installed and skip the
     * download. The caller is
     * responsible for telling the user to actually enable the pack in the
     * vanilla resource-pack picker — we deliberately do not force-enable packs,
     * because doing so silently would be surprising.
     */
    public static CompletableFuture<InstallStatus> install(ModrinthVersion version) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path packsDir = resourcePacksDir();
                Files.createDirectories(packsDir);
                Path target = packsDir.resolve(sanitizeFileName(version.fileName));
                if (Files.exists(target)) {
                    return InstallStatus.ALREADY_INSTALLED;
                }
                HttpRequest req = newRequest(version.downloadUrl).GET().build();
                HttpResponse<java.io.InputStream> resp = HTTP.send(req,
                        HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() / 100 != 2) {
                    throw new RuntimeException("Download failed: HTTP " + resp.statusCode());
                }
                // Stream to a .part temp file then atomically move, so a
                // half-downloaded file is never left visible to the game.
                Path tmp = Files.createTempFile(packsDir, "aurora-dl-", ".part");
                try (java.io.InputStream in = resp.body()) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                return InstallStatus.DOWNLOADED;
            } catch (Throwable t) {
                AuroraClient.LOGGER.error("Failed to install resource pack {}", version.fileName, t);
                return InstallStatus.FAILED;
            }
        }, IO_EXECUTOR);
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static HttpRequest.Builder newRequest(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json");
    }

    private static Path resourcePacksDir() {
        return FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
    }

    private static String resolveAuroraVersion() {
        try {
            return FabricLoader.getInstance().getModContainer("aurora")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /**
     * Resolve the running Minecraft version string (e.g. {@code "1.21.11"})
     * via the Fabric loader's mod container for {@code minecraft}. This is
     * cross-version safe — unlike {@code SharedConstants.getCurrentVersion()}
     * whose sub-methods have changed across MC versions.
     */
    private static String resolveMinecraftVersion() {
        try {
            return FabricLoader.getInstance().getModContainer("minecraft")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("1.21.11");
        } catch (Throwable t) {
            return "1.21.11";
        }
    }

    /** Strip path separators / control chars so a hostile filename can't escape the folder. */
    private static String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) return "aurora-pack.zip";
        String base = name.replace('/', '_').replace('\\', '_').replace("..", "_");
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }

    private static String optString(JsonObject o, String key) {
        if (o.has(key) && !o.get(key).isJsonNull()) return o.get(key).getAsString();
        return "";
    }

    private static long optLong(JsonObject o, String key) {
        if (o.has(key) && !o.get(key).isJsonNull()) {
            JsonElement e = o.get(key);
            return e.isJsonPrimitive() ? e.getAsLong() : 0L;
        }
        return 0L;
    }
}