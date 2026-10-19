package com.aurora.client.config.profile;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.theme.ThemeDefinition;
import com.aurora.client.theme.ThemeMigrator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Owns the lifecycle of configuration profiles.
 *
 * <p>A profile is a named, full snapshot of every profile-scoped field on
 * {@link AuroraConfig} (module enables + all setting values + keybinds +
 * theme + HUD positions + particle/item-scale/waypoint maps). Profiles are
 * stored as one JSON file each under {@code <config dir>/profiles/}.
 *
 * <p><b>Scope.</b> Per design, everything is profile-scoped except the small
 * exclusion set in {@link ProfileFieldSet} (the {@code activeProfile}
 * pointer and the playtime telemetry accumulators).
 *
 * <p><b>Capture / restore.</b> Reflection walks {@link AuroraConfig}'s
 * public, non-static, non-final instance fields — the same approach the
 * existing {@code resetByPrefix} uses. Capture serializes each value to a
 * {@link JsonElement} via Gson using the field's generic type (so maps and
 * nested POJOs round-trip correctly). Restore first resets every
 * profile-scoped field to its factory default (so a setting missing from
 * the profile falls back to default), then overlays the profile's values,
 * skipping any field that no longer exists or fails to deserialize with a
 * logged warning — never crashing.
 *
 * <p><b>Persistence.</b> Every profile file write is atomic: serialize to a
 * sibling {@code .tmp} file on the calling thread, then {@code ATOMIC_MOVE}
 * it into place on a dedicated daemon thread. A crash mid-write therefore
 * can never corrupt or truncate an existing profile. This mirrors
 * {@code AuroraConfig.writeJsonAtomic}.
 *
 * <p><b>Threading.</b> Reflection capture happens on the calling thread (it
 * reads a consistent snapshot of the live config and is fast); only the
 * disk write is offloaded. Live-config mutation during {@link #applyProfile}
 * is synchronous so a runtime switch takes effect immediately.
 */
public class ProfileManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PROFILES_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("profiles");

    /** Factory-default snapshot built once from a fresh AuroraConfig instance. */
    private static final AuroraConfig DEFAULTS = new AuroraConfig();

    /**
     * Dedicated single-thread daemon executor for profile disk writes.
     * Mirrors {@code AuroraConfig.WRITER}'s rationale: disk I/O is
     * slow/unpredictable and must not stall the client tick thread.
     */
    private static final com.aurora.client.config.AtomicJsonWriter WRITER =
            new com.aurora.client.config.AtomicJsonWriter("Aurora-ProfileSave");

    private static final ProfileManager INSTANCE = new ProfileManager();

    public static ProfileManager getInstance() { return INSTANCE; }

    /** Name of the profile currently applied to the live config. */
    private String currentProfileName = "default";

    /** Cached profile-name list; invalidated on any mutating op or load. */
    private List<String> cachedNames = null;

    /**
     * Listeners invoked on the calling thread after a profile is applied to
     * the live config (at load and on every {@link #switchTo}). Use these to
     * refresh any in-memory state derived from {@link AuroraConfig} fields —
     * notably {@code AuroraClient.applyLayouts} to reposition HUD modules
     * after a switch rewrites {@code moduleLayouts}.
     */
    private final List<Consumer<String>> switchListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private ProfileManager() {}

    /**
     * Register a listener fired after a profile is applied. Receives the new
     * active profile name. Safe to call from any thread; listeners run on the
     * thread that triggered the switch.
     */
    public void onProfileApplied(Consumer<String> listener) {
        if (listener != null) switchListeners.add(listener);
    }

    /** Fire the switch listeners. */
    private void fireApplied(String name) {
        for (Consumer<String> l : switchListeners) {
            try { l.accept(name); }
            catch (Throwable t) {
                AuroraClient.LOGGER.warn("ProfileManager: switch listener threw", t);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Startup / load
    // ------------------------------------------------------------------

    /**
     * Initialize on client startup. Ensures the profiles directory exists,
     * creates a {@code default} profile from the current (just-loaded) live
     * config if none exist, then loads + applies the profile named by
     * {@code AuroraConfig.activeProfile} (falling back to {@code default}).
     *
     * <p>Must be called AFTER {@link AuroraConfig#load()}.
     */
    public void load() {
        try { Files.createDirectories(PROFILES_DIR); }
        catch (IOException e) {
            AuroraClient.LOGGER.error("ProfileManager: could not create profiles dir", e);
        }
        // Temp files left by a crash mid-write never affect the real profile; tidy them.
        for (String name : scanNames()) com.aurora.client.config.AtomicJsonWriter.deleteStaleTemps(pathFor(name));

        ensureDefaultProfileExists();

        String target = AuroraConfig.get().activeProfile;
        if (target == null || target.isEmpty() || !nameExists(target)) {
            target = "default";
        }
        applyProfileByName(target);
        invalidateCache();
    }

    /**
     * If no profile files exist, capture the current live config (which, at
     * first run, is factory defaults) into a {@code default} profile so
     * there is always a fallback.
     */
    private void ensureDefaultProfileExists() {
        if (hasAnyProfileFile()) return;
        Profile p = captureCurrent();
        p.name = "default";
        p.createdAtMs = System.currentTimeMillis();
        writeProfileFile(p);
        AuroraClient.LOGGER.info("ProfileManager: created default profile");
    }

    private boolean hasAnyProfileFile() {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(PROFILES_DIR, "*.json")) {
            return ds.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    //  GUI hooks
    // ------------------------------------------------------------------

    /** @return the display names of all profiles, sorted alphabetically. */
    public List<String> listProfileNames() {
        if (cachedNames == null) cachedNames = scanNames();
        return new ArrayList<>(cachedNames);
    }

    /** @return the name of the profile currently applied to the live config. */
    public String currentProfile() {
        return currentProfileName;
    }

    // ------------------------------------------------------------------
    //  CRUD
    // ------------------------------------------------------------------

    /**
     * Create a new profile from the current live config state.
     *
     * @return {@code true} if created; {@code false} if the name already
     *         exists or is invalid.
     */
    public boolean create(String name) {
        if (!isValidName(name) || nameExists(name)) return false;
        Profile p = captureCurrent();
        p.name = name;
        p.createdAtMs = System.currentTimeMillis();
        writeProfileFile(p);
        invalidateCache();
        return true;
    }

    /**
     * Rename a profile (keeping its contents). The active profile's pointer
     * is updated if it was the renamed one.
     *
     * @return {@code true} on success; {@code false} if {@code oldName} is
     *         missing, {@code newName} is taken/invalid, or they're equal.
     */
    public boolean rename(String oldName, String newName) {
        if (!isValidName(newName) || oldName.equals(newName)) return false;
        if (!nameExists(oldName) || nameExists(newName)) return false;
        Profile p = readProfileFile(oldName);
        if (p == null) return false;
        p.name = newName;
        Path oldFile = pathFor(oldName);
        writeProfileFile(p);
        WRITER.delete(oldFile); // ordered after any queued write for the old name
        if (currentProfileName.equals(oldName)) {
            currentProfileName = newName;
            AuroraConfig.get().activeProfile = newName;
            AuroraConfig.save();
        }
        invalidateCache();
        return true;
    }

    /**
     * Delete a profile file. The {@code default} profile cannot be deleted
     * (it is the fallback). The active profile cannot be deleted either.
     *
     * @return {@code true} on success.
     */
    public boolean delete(String name) {
        if ("default".equals(name) || !nameExists(name)) return false;
        if (currentProfileName.equals(name)) return false; // never delete active
        // Ordered through the writer: a still-queued save for this profile can
        // no longer land after the delete and resurrect it.
        WRITER.delete(pathFor(name));
        invalidateCache();
        return true;
    }

    /**
     * Duplicate an existing profile under a new name.
     *
     * @return {@code true} on success; {@code false} if source is missing or
     *         the target name is taken/invalid.
     */
    public boolean duplicate(String sourceName, String newName) {
        if (!isValidName(newName) || nameExists(newName)) return false;
        Profile src = readProfileFile(sourceName);
        if (src == null) return false;
        src.name = newName;
        src.createdAtMs = System.currentTimeMillis();
        writeProfileFile(src);
        invalidateCache();
        return true;
    }

    // ------------------------------------------------------------------
    //  Switch / save
    // ------------------------------------------------------------------

    /**
     * Switch the active profile at runtime, without restarting the client.
     *
     * <p>First persists the current live config back into the outgoing
     * profile (so changes aren't lost), then loads + applies the target
     * profile to the live config, updates the {@code activeProfile} pointer,
     * and persists the pointer.
     *
     * @return {@code true} on success; {@code false} if the target doesn't
     *         exist or equals the current profile.
     */
    public boolean switchTo(String name) {
        if (name == null || name.equals(currentProfileName) || !nameExists(name)) return false;
        // Persist outgoing state so the user doesn't lose edits.
        saveCurrent();
        // Apply incoming.
        applyProfileByName(name);
        // Persist the pointer.
        AuroraConfig.get().activeProfile = currentProfileName;
        AuroraConfig.save();
        return true;
    }

    /**
     * Capture the current live config and persist it into the active
     * profile's file (async, atomic). Safe to call frequently (e.g. from the
     * config save path) — the reflection capture is on the calling thread
     * and the disk write is offloaded.
     */
    public void saveCurrent() {
        Profile p = captureCurrent();
        p.name = currentProfileName;
        // Preserve the stored createdAtMs if the file already exists.
        Profile existing = readProfileFile(currentProfileName);
        p.createdAtMs = (existing != null) ? existing.createdAtMs : System.currentTimeMillis();
        writeProfileFile(p);
    }

    /**
     * Blocking variant for use on JVM shutdown — the async save executor is a
     * daemon thread the JVM will not wait for on exit, so a final flush must
     * run inline to guarantee it lands on disk. Mirrors the rationale of
     * {@code AuroraConfig.saveBlocking()}.
     */
    public void saveCurrentBlocking() {
        Profile p = captureCurrent();
        p.name = currentProfileName;
        Profile existing = readProfileFile(currentProfileName);
        p.createdAtMs = (existing != null) ? existing.createdAtMs : System.currentTimeMillis();
        writeProfileFile(p);
        // Through the SAME ordered writer as the async path, then a bounded
        // wait: the old inline write raced a queued async write for the same
        // fixed .tmp path, and that older snapshot could land afterwards.
        if (!WRITER.flush(com.aurora.client.config.AuroraConfig.SHUTDOWN_FLUSH_MS))
            AuroraClient.LOGGER.error("ProfileManager: profile '{}' was NOT confirmed saved; the previous file is intact", p.name);
    }

    // ------------------------------------------------------------------
    //  Capture / restore (reflection)
    // ------------------------------------------------------------------

    /**
     * Snapshot every profile-scoped field of the live {@link AuroraConfig}
     * into a new {@link Profile}.
     */
    Profile captureCurrent() {
        Profile p = new Profile();
        p.name = currentProfileName;
        AuroraConfig live = AuroraConfig.get();
        for (Field f : profileFields()) {
            try {
                Object value = f.get(live);
                JsonElement el = GSON.toJsonTree(value, f.getGenericType());
                p.fields.put(f.getName(), el);
            } catch (Throwable t) {
                AuroraClient.LOGGER.warn(
                        "ProfileManager: skipped field '{}' during capture", f.getName(), t);
            }
        }
        return p;
    }

    /**
     * Apply a profile to the live config: reset every profile-scoped field
     * to its factory default, then overlay the profile's values. Fields that
     * no longer exist or fail to deserialize are skipped with a warning —
     * never throws. Synchronous so a runtime switch is immediately visible.
     */
    void applyProfile(Profile p) {
        AuroraConfig live = AuroraConfig.get();
        // Pre-pass: fold legacy themePrimary/themeSecondary entries into the
        // structured "theme" object so old profiles keep their customization.
        if (p != null) {
            ThemeMigrator.migrateProfileFields(p.fields, AuroraClient.LOGGER);
        }
        // 1. Reset all profile-scoped fields to factory defaults so any
        //    setting absent from the profile (e.g. a newly-added setting)
        //    lands on its default value. Mutable collections are COPIED,
        //    not aliased — a profile saved before the field existed would
        //    otherwise hand the live config the DEFAULTS snapshot's Set/Map
        //    object and every later edit would mutate the factory default.
        for (Field f : profileFields()) {
            try {
                Object def = f.get(DEFAULTS);
                if (def instanceof java.util.Map<?,?> m) def = new java.util.HashMap<>(m);
                else if (def instanceof java.util.Set<?> s) def = new java.util.HashSet<>(s);
                else if (def instanceof java.util.List<?> l) def = new java.util.ArrayList<>(l);
                f.set(live, def);
            } catch (Throwable t) {
                AuroraClient.LOGGER.warn(
                        "ProfileManager: could not reset field '{}' to default", f.getName(), t);
            }
        }
        // 2. Overlay the profile's captured values. Skip anything that no
        //    longer maps to a live field, with a warning.
        Set<String> liveNames = profileFieldNames();
        for (Map.Entry<String, JsonElement> e : p.fields.entrySet()) {
            String fieldName = e.getKey();
            if (!liveNames.contains(fieldName) || !ProfileFieldSet.isProfileScoped(fieldName)) {
                AuroraClient.LOGGER.warn(
                        "ProfileManager: profile '{}' references field '{}' which no longer exists; skipped",
                        p.name, fieldName);
                continue;
            }
            try {
                Field f = AuroraConfig.class.getField(fieldName);
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isFinal(mod)) continue;
                Type t = f.getGenericType();
                Object value = GSON.fromJson(e.getValue(), t);
                f.set(live, value);
            } catch (NoSuchFieldException nsfe) {
                AuroraClient.LOGGER.warn(
                        "ProfileManager: field '{}' removed since profile was saved; skipped", fieldName);
            } catch (Throwable t) {
                AuroraClient.LOGGER.warn(
                        "ProfileManager: failed to restore field '{}'; skipped", fieldName, t);
            }
        }

        // The reset step above assigns DEFAULTS.theme by reference; give the
        // live config its own instance so profile edits can never leak into
        // the factory snapshot (or vice versa).
        live.theme = live.theme != null
                ? ThemeDefinition.copyOf(live.theme)
                : ThemeDefinition.defaults();
    }

    private void applyProfileByName(String name) {
        Profile p = readProfileFile(name);
        if (p == null) {
            AuroraClient.LOGGER.warn(
                    "ProfileManager: profile '{}' missing; falling back to defaults only", name);
            p = new Profile(name); // empty → live config stays at defaults after reset
        }
        applyProfile(p);
        currentProfileName = name;
        // Notify listeners (e.g. HUD module re-layout) that the live config
        // was just rewritten from this profile.
        fireApplied(name);
    }

    // ------------------------------------------------------------------
    //  Reflection helpers (cached)
    // ------------------------------------------------------------------

    private static volatile Field[] PROFILE_FIELDS_CACHE;
    private static volatile Set<String> PROFILE_FIELD_NAMES_CACHE;

    private static Field[] profileFields() {
        Field[] cache = PROFILE_FIELDS_CACHE;
        if (cache != null) return cache;
        List<Field> out = new ArrayList<>();
        for (Field f : AuroraConfig.class.getFields()) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod) || Modifier.isFinal(mod)) continue;
            if (!ProfileFieldSet.isProfileScoped(f.getName())) continue;
            f.setAccessible(true);
            out.add(f);
        }
        Field[] arr = out.toArray(new Field[0]);
        PROFILE_FIELDS_CACHE = arr;
        PROFILE_FIELD_NAMES_CACHE = Collections.unmodifiableSet(
                new LinkedHashSet<>() {{ for (Field f : arr) add(f.getName()); }});
        return arr;
    }

    private static Set<String> profileFieldNames() {
        if (PROFILE_FIELD_NAMES_CACHE == null) profileFields();
        return PROFILE_FIELD_NAMES_CACHE;
    }

    // ------------------------------------------------------------------
    //  File I/O
    // ------------------------------------------------------------------

    private Path pathFor(String name) {
        return PROFILES_DIR.resolve(sanitizeName(name) + ".json");
    }

    private Profile readProfileFile(String name) {
        Path file = pathFor(name);
        // Read-your-writes: a queued/in-flight write (or delete) is newer than
        // the disk. Reading the disk here let create→switchTo fail (the new
        // file was not written yet) and could apply a stale profile.
        String pending = WRITER.latest(file);
        if (pending == com.aurora.client.config.AtomicJsonWriter.DELETED) return null;
        if (pending == null && !Files.exists(file)) return null;
        try {
            String json = pending != null ? pending : Files.readString(file);
            Profile p = GSON.fromJson(json, Profile.class);
            if (p == null) return null;
            if (p.fields == null) p.fields = new LinkedHashMap<>();
            if (p.name == null || p.name.isEmpty()) p.name = name;
            return p;
        } catch (IOException | JsonParseException e) {
            AuroraClient.LOGGER.error("ProfileManager: failed to read profile '{}'", name, e);
            return null;
        }
    }

    /**
     * Atomic <b>async</b> write: serialize on the calling thread, submit the
     * temp-file + {@code ATOMIC_MOVE} rename to the save executor.
     */
    private void writeProfileFile(Profile p) {
        final String json;
        try {
            json = GSON.toJson(p);
        } catch (Throwable t) {
            AuroraClient.LOGGER.error("ProfileManager: failed to serialize profile '{}'", p.name, t);
            return;
        }
        WRITER.submit(pathFor(p.name), json);
    }

    // ------------------------------------------------------------------
    //  Name helpers
    // ------------------------------------------------------------------

    private List<String> scanNames() {
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(PROFILES_DIR, "*.json")) {
            for (Path p : ds) {
                try {
                    String json = Files.readString(p);
                    Profile prof = GSON.fromJson(json, Profile.class);
                    String n = (prof != null && prof.name != null && !prof.name.isEmpty())
                            ? prof.name
                            : stripJsonExt(p.getFileName().toString());
                    names.add(n);
                } catch (Exception e) {
                    AuroraClient.LOGGER.warn("ProfileManager: skipping unreadable profile {}", p, e);
                }
            }
        } catch (IOException e) {
            AuroraClient.LOGGER.error("ProfileManager: failed to scan profiles dir", e);
        }
        // Overlay queued writes/deletes so a just-created profile exists and a
        // just-deleted one does not, before the disk catches up.
        for (var e : WRITER.latestAll().entrySet()) {
            if (!PROFILES_DIR.equals(e.getKey().getParent())) continue;
            String fileName = e.getKey().getFileName().toString();
            names.removeIf(n -> pathFor(n).getFileName().toString().equals(fileName));
            if (e.getValue() == com.aurora.client.config.AtomicJsonWriter.DELETED) continue;
            try {
                Profile prof = GSON.fromJson(e.getValue(), Profile.class);
                names.add(prof != null && prof.name != null && !prof.name.isEmpty() ? prof.name : stripJsonExt(fileName));
            } catch (RuntimeException ignored) { }
        }
        Collections.sort(names);
        return names;
    }

    private void invalidateCache() { cachedNames = null; }

    private boolean nameExists(String name) {
        return listProfileNames().contains(name);
    }

    private static boolean isValidName(String name) {
        return name != null && !name.isEmpty()
                && !name.trim().isEmpty()
                && name.length() <= 64
                && !sanitizeName(name).isEmpty();
    }

    /** Replace filesystem-illegal characters with underscore. */
    private static String sanitizeName(String name) {
        return name.trim().replaceAll("[<>:\"/\\\\|?*\u0000-\u001f]", "_");
    }

    private static String stripJsonExt(String fn) {
        return fn.endsWith(".json") ? fn.substring(0, fn.length() - 5) : fn;
    }
}