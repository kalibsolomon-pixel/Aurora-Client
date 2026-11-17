package com.aurora.client.config.profile;

import java.util.Collection;

/**
 * Pure profile-name validation, shared by {@link ProfileManager}'s CRUD gates
 * and the screens that edit names (the audit's silent-revert fix, 2026-11-17):
 * one place decides what a profile may be called, so the UI can say <i>why</i>
 * a name was refused instead of silently keeping the old one.
 *
 * <p>{@link #problem} returns a short user-facing message (or {@code null}
 * when the name is acceptable). The messages are the canonical English UI
 * strings; screens render them verbatim under the field in the theme's error
 * colour (design language §11.12).
 *
 * <p>File-name collisions: profiles are stored as {@code sanitize(name) +
 * ".json"} (illegal filesystem characters become underscores), so a candidate
 * is "taken" both when an existing profile bears the exact name and when it
 * would sanitize onto another profile's file — and when a rename would
 * sanitize onto the profile's own file (the write-then-delete of a rename to
 * the same path would otherwise destroy the file). File names compare
 * without case, as case-insensitive file systems do.
 */
public final class ProfileNameValidator {
    private ProfileNameValidator() {}

    /** Mirrors {@link ProfileManager}'s acceptance rule; keep the two in lockstep. */
    public static final int MAX_NAME_LENGTH = 64;

    static final String NAME_EMPTY = "Name can't be empty";
    static final String NAME_TAKEN = "Name already used";
    static final String NAME_TOO_LONG = "Name is too long";
    static final String NAME_CASE_ONLY = "Change more than letter case";

    /**
     * @param candidate     the typed name, untrimmed (trailing spaces are not identity).
     * @param existingNames every live profile name.
     * @param currentName   the profile being renamed, or {@code null} when creating;
     *                      it never collides with itself.
     * @return the short refusal message, or {@code null} when the name is valid.
     */
    public static String problem(String candidate, Collection<String> existingNames, String currentName) {
        if (candidate == null) return NAME_EMPTY;
        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) return NAME_EMPTY;
        String file = sanitize(trimmed);
        if (file.isEmpty()) return NAME_EMPTY;
        if (trimmed.length() > MAX_NAME_LENGTH) return NAME_TOO_LONG;
        // File names compare without case: Windows and macOS file systems ignore it, so
        // "PvP.json" is "pvp.json" there and the rename's write-then-delete (or a create)
        // would overwrite or delete that file (2026-11-17).
        if (currentName != null && !trimmed.equals(currentName)) {
            String own = sanitize(currentName.trim());
            if (file.equals(own)) return NAME_TAKEN;
            if (file.equalsIgnoreCase(own)) return NAME_CASE_ONLY;
        }
        for (String other : existingNames) {
            if (other == null || other.equals(currentName)) continue;
            if (other.equals(trimmed) || sanitize(other.trim()).equalsIgnoreCase(file)) return NAME_TAKEN;
        }
        return null;
    }

    /** Replace filesystem-illegal characters with underscore ({@link ProfileManager}'s file naming). */
    public static String sanitize(String name) {
        return name.trim().replaceAll("[<>:\"/\\\\|?*\u0000-\u001f]", "_");
    }
}
