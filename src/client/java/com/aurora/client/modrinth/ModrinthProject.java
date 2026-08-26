package com.aurora.client.modrinth;

/**
 * Lightweight data holder for a single Modrinth search hit (resource pack).
 *
 * <p>Only the fields the browser UI reads are captured. The raw JSON is
 * parsed defensively in {@link ModrinthApi} so missing/null fields default
 * safely rather than throwing.
 */
public final class ModrinthProject {

    /** Modrinth project id (e.g. {@code "AABBCC22"}). Used to fetch versions. */
    public final String projectId;
    /** URL-friendly slug, also accepted by the version endpoint. */
    public final String slug;
    /** Human-readable title. */
    public final String title;
    /** Author display name. */
    public final String author;
    /** Short description (one line). */
    public final String description;
    /** Square icon URL, or {@code null} if the project has no icon. */
    public final String iconUrl;
    /** Total lifetime downloads across all files. */
    public final long downloads;
    /** Number of followers. */
    public final long followers;

    public ModrinthProject(String projectId, String slug, String title, String author,
                           String description, String iconUrl, long downloads, long followers) {
        this.projectId = projectId;
        this.slug = slug;
        this.title = title;
        this.author = author;
        this.description = description;
        this.iconUrl = iconUrl;
        this.downloads = downloads;
        this.followers = followers;
    }
}