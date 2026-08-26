package com.aurora.client.modrinth;

/**
 * Minimal data holder for a single Modrinth project version (one
 * downloadable file release).
 *
 * <p>Only the file we install from is captured; the version endpoint returns
 * a lot more, but the browser only ever installs the primary file of the
 * latest release version that matches the running Minecraft version.
 */
public final class ModrinthVersion {

    /** Version name (e.g. {@code "1.2.0"}). Shown in the UI. */
    public final String name;
    /** Version number string. */
    public final String versionNumber;
    /** {@code "release"}, {@code "beta"} or {@code "alpha"}. */
    public final String versionType;
    /** Direct download URL for the primary file. */
    public final String downloadUrl;
    /** Filename of the primary file, e.g. {@code "MyPack-1.2.0.zip"}. */
    public final String fileName;
    /** Size of the primary file in bytes. */
    public final long sizeBytes;

    public ModrinthVersion(String name, String versionNumber, String versionType,
                           String downloadUrl, String fileName, long sizeBytes) {
        this.name = name;
        this.versionNumber = versionNumber;
        this.versionType = versionType;
        this.downloadUrl = downloadUrl;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
    }
}