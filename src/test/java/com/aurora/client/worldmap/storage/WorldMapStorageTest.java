package com.aurora.client.worldmap.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.io.DataOutputStream;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class WorldMapStorageTest {
    @TempDir Path directory;
    private WorldMapStorage.LoadedRegion load(WorldMapStorage storage) throws Exception {
        var result = new CompletableFuture<WorldMapStorage.LoadedRegion>();
        assertTrue(storage.submitLoad(123, -1, 2, result::complete));
        return result.get(10, TimeUnit.SECONDS);
    }
    @Test void atomicRoundTripAndReplacementRetainExactPixelsAndStamps() throws Exception {
        WorldMapStorage storage = WorldMapStorage.at(directory.resolve("new/nested"));
        long[] stamps = new long[WorldMapStorage.STAMP_COUNT];
        int[] pixels = new int[WorldMapStorage.PIXELS];
        stamps[1023] = 12345678; pixels[0] = 0xFF112233; pixels[pixels.length - 1] = 0x995577EE;
        var saved = new CompletableFuture<Boolean>();
        assertTrue(storage.submitSave(123, -1, 2, stamps, pixels, saved::complete));
        assertTrue(saved.get(10, TimeUnit.SECONDS));
        var loaded = load(storage);
        assertTrue(loaded.valid()); assertEquals(123, loaded.key());
        assertArrayEquals(stamps, loaded.stamps()); assertArrayEquals(pixels, loaded.pixels());
        assertFalse(Files.exists(storage.fileFor(-1, 2).resolveSibling("r.-1.2.wmr.tmp")));
        byte[] before = Files.readAllBytes(storage.fileFor(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> storage.submitSave(0, -1, 2, new long[1], pixels, ok -> {}));
        assertArrayEquals(before, Files.readAllBytes(storage.fileFor(-1, 2)));
    }
    @Test void missingTruncatedUnknownVersionAndBadCrcNeverBecomeWritableData() throws Exception {
        WorldMapStorage storage = WorldMapStorage.at(directory);
        var missing = load(storage);
        assertTrue(missing.valid()); assertEquals(0, missing.pixels()[0]);
        Path file = storage.fileFor(-1, 2);
        for (int version : new int[]{1, 2, 99}) {
            try (var out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
                out.writeInt(WorldMapStorage.MAGIC); out.writeByte(version); out.writeLong(10);
            }
            byte[] original = Files.readAllBytes(file);
            assertFalse(load(storage).valid());
            assertArrayEquals(original, Files.readAllBytes(file));
        }
        Files.write(file, new byte[]{1, 2, 3});
        assertFalse(load(storage).valid());
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file));
    }
    @Test void legacyVersionTwoSurfaceIsRetainedAndTrailingPayloadIsBounded() throws Exception {
        WorldMapStorage storage = WorldMapStorage.at(directory);
        try (var out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(storage.fileFor(-1, 2))))) {
            out.writeInt(WorldMapStorage.MAGIC); out.writeByte(2);
            for (int i = 0; i < WorldMapStorage.STAMP_COUNT; i++) out.writeLong(i);
            for (int i = 0; i < WorldMapStorage.PIXELS; i++) out.writeInt(0xFF443322);
            out.writeByte(0);
        }
        var loaded = load(storage);
        assertTrue(loaded.valid()); assertEquals(0xFF443322, loaded.pixels()[100]);
        assertEquals(1023, loaded.stamps()[1023]);
    }

    @Test void legacyImportMergesOnlyUnexploredChunksAndPreservesOriginals() throws Exception {
        WorldMapStorage old = WorldMapStorage.at(directory.resolve("old"));
        WorldMapStorage current = WorldMapStorage.at(directory.resolve("new"));
        long[] stamps = new long[WorldMapStorage.STAMP_COUNT];
        int[] pixels = new int[WorldMapStorage.PIXELS];
        java.util.Arrays.fill(stamps, 10); java.util.Arrays.fill(pixels, 0xFF112233);
        var saved = new CompletableFuture<Boolean>();
        assertTrue(old.submitSave(0, -1, 2, stamps, pixels, saved::complete));
        assertTrue(saved.get(10, TimeUnit.SECONDS));
        byte[] original = Files.readAllBytes(old.fileFor(-1, 2));
        long[] newerStamps = new long[WorldMapStorage.STAMP_COUNT]; newerStamps[0] = 20;
        int[] newerPixels = new int[WorldMapStorage.PIXELS]; newerPixels[0] = 0xFF445566;
        saved = new CompletableFuture<>();
        assertTrue(current.submitSave(0, -1, 2, newerStamps, newerPixels, saved::complete));
        assertTrue(saved.get(10, TimeUnit.SECONDS));
        for (int pass = 0; pass < 2; pass++) {
            var imported = new CompletableFuture<String>();
            assertTrue(current.importLegacyFrom(directory.resolve("old"), imported::complete));
            assertTrue(imported.get(10, TimeUnit.SECONDS).contains("Imported 1"));
            var merged = load(current);
            assertEquals(20, merged.stamps()[0]); assertEquals(0xFF445566, merged.pixels()[0]);
            assertEquals(10, merged.stamps()[1]); assertEquals(0xFF112233, merged.pixels()[16]);
            assertArrayEquals(original, Files.readAllBytes(old.fileFor(-1, 2)));
        }
    }
}
