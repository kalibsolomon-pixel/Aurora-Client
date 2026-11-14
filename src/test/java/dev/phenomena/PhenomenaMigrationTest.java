package dev.phenomena;

import static org.junit.jupiter.api.Assertions.*;

import dev.phenomena.recording.storage.LegacyDataMigration;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pure legacy-data regressions; standalone mod metadata, commands and ConfigStore are excluded. */
class PhenomenaMigrationTest {




  @Test
  void freshInstallationWritesMarkerWithoutLegacy(@TempDir Path dir) {
    var result = LegacyDataMigration.migrate(dir, null);
    assertTrue(result.ran());
    assertFalse(result.legacyPresent());
    assertTrue(Files.isRegularFile(dir.resolve("phenomena").resolve(LegacyDataMigration.MARKER)));
    assertEquals("Fresh data directory", result.summary());
    // Second startup: no repeat migration.
    assertEquals(false, LegacyDataMigration.migrate(dir, null).ran());
  }

  @Test
  void legacyDataIsCopiedForwardExactlyOnce(@TempDir Path dir) throws Exception {
    Path legacyFights = dir.resolve("kwamster/fights");
    Path legacyState = dir.resolve("kwamster/report-state");
    Files.createDirectories(legacyFights);
    Files.createDirectories(legacyState);
    byte[] fight = "{\"id\":\"a7ee1a04-1111-2222-3333-444455556666\"}".getBytes();
    Files.write(legacyFights.resolve("a7ee1a04-1111-2222-3333-444455556666.json"), fight);
    Files.write(legacyState.resolve("a7ee1a04-1111-2222-3333-444455556666.json"), "{}".getBytes());
    var before = Files.readAllBytes(legacyFights.resolve("a7ee1a04-1111-2222-3333-444455556666.json"));

    var result = LegacyDataMigration.migrate(dir, null);
    assertTrue(result.ran() && result.legacyPresent());
    assertEquals(2, result.copied());
    assertEquals(0, result.conflicts());
    Path migrated =
        dir.resolve("phenomena/fights/a7ee1a04-1111-2222-3333-444455556666.json");
    assertArrayEquals(fight, Files.readAllBytes(migrated));
    // Legacy tree untouched.
    assertArrayEquals(before, Files.readAllBytes(legacyFights.resolve("a7ee1a04-1111-2222-3333-444455556666.json")));

    // Re-running after marker: no further work.
    var second = LegacyDataMigration.migrate(dir, null);
    assertFalse(second.ran());

    // Marker records provenance.
    var marker =
        com.google.gson.JsonParser.parseString(
                Files.readString(dir.resolve("phenomena").resolve(LegacyDataMigration.MARKER)))
            .getAsJsonObject();
    assertEquals(LegacyDataMigration.SCHEMA, marker.get("schema").getAsInt());
    assertEquals("kwamster", marker.get("source").getAsString());
    assertEquals(2, marker.get("copied").getAsInt());
  }

  @Test
  void interruptedMigrationResumesAndNeverOverwritesConflicts(@TempDir Path dir) throws Exception {
    Path legacyFights = dir.resolve("kwamster/fights");
    Files.createDirectories(legacyFights);
    Files.write(legacyFights.resolve("a.json"), "AAAA".getBytes());
    Files.write(legacyFights.resolve("b.json"), "BBBB".getBytes());
    // Simulate a crash after one file: no marker, first file already present.
    Path partialFights = dir.resolve("phenomena/fights");
    Files.createDirectories(partialFights);
    Files.write(partialFights.resolve("a.json"), "AAAA".getBytes());
    // A conflicting file the owner (or a newer install) created: must survive untouched.
    Files.write(partialFights.resolve("b.json"), "DIFFERENT".getBytes());

    var result = LegacyDataMigration.migrate(dir, null);
    assertEquals(0, result.copied());
    assertEquals(1, result.identical());
    assertEquals(1, result.conflicts());
    assertEquals(1, result.conflictNames().size());
    assertTrue(result.conflictNames().get(0).endsWith("b.json"));
    assertEquals("DIFFERENT", Files.readString(partialFights.resolve("b.json")));
    assertEquals("BBBB", Files.readString(legacyFights.resolve("b.json")));
    assertTrue(Files.isRegularFile(dir.resolve("phenomena").resolve(LegacyDataMigration.MARKER)));
  }

  @Test
  void legacyDiagnosticsDirectoryMigratesToo(@TempDir Path dir) throws Exception {
    Path diag = dir.resolve("kwamster/diagnostics");
    Files.createDirectories(diag);
    Files.write(diag.resolve("capture.jsonl"), "{}".getBytes());
    var result = LegacyDataMigration.migrate(dir, null);
    assertEquals(1, result.copied());
    assertTrue(
        Files.isRegularFile(dir.resolve("phenomena/diagnostics/capture.jsonl")));
  }


  @Test
  void legacyExportFolderOwnershipMarkersAreBothAccepted() {
    // Both marker spellings must keep identifying legacy export folders as ours.
    var markers = dev.phenomena.platform.SafePaths.OWNER_MARKERS;
    assertTrue(markers.contains(".phenomena-owner"));
    assertTrue(markers.contains(".kwamster-owner"));
  }
}
