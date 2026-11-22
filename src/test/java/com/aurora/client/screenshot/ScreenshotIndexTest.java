package com.aurora.client.screenshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScreenshotIndexTest {

    @Test
    void scansSortsNewestFirstAndFilters(@TempDir Path dir) throws Exception {
        Path oldShot = dir.resolve("2026-11-01_10.00.00.png");
        Path newShot = dir.resolve("2026-11-21_22.00.00_end_city.png");
        Path note = dir.resolve("notes.txt");
        Files.write(oldShot, new byte[]{1, 2, 3});
        Files.write(newShot, new byte[]{1, 2, 3, 4, 5});
        Files.writeString(note, "ignore me");
        Files.setLastModifiedTime(oldShot, FileTime.fromMillis(1_000_000L));
        Files.setLastModifiedTime(newShot, FileTime.fromMillis(2_000_000L));

        List<ScreenshotIndex.Entry> all = ScreenshotIndex.scan(dir);
        assertEquals(2, all.size());
        assertEquals("2026-11-21_22.00.00_end_city.png", all.get(0).fileName());
        assertEquals("2026-11-21_22.00.00_end_city", all.get(0).displayName());
        assertEquals("2026-11-01_10.00.00.png", all.get(1).fileName());

        List<ScreenshotIndex.Entry> filtered = ScreenshotIndex.filter(all, "end city");
        assertEquals(1, filtered.size());
        assertEquals(newShot, filtered.get(0).path());
    }

    @Test
    void validatesRenamesDeletesAndComputesCropRects(@TempDir Path dir) throws Exception {
        Path a = dir.resolve("shot_a.png");
        Path b = dir.resolve("shot_b.png");
        Files.write(a, new byte[]{1});
        Files.write(b, new byte[]{2});

        assertNotNull(ScreenshotIndex.validateRename("", dir, a));
        assertNotNull(ScreenshotIndex.validateRename("../escape", dir, a));
        assertNotNull(ScreenshotIndex.validateRename("bad:name", dir, a));
        assertNotNull(ScreenshotIndex.validateRename("shot_b", dir, a));
        assertNull(ScreenshotIndex.validateRename("shot_a", dir, a));
        assertNull(ScreenshotIndex.validateRename("boss_fight", dir, a));

        Path renamed = ScreenshotIndex.rename(a, "boss_fight");
        assertEquals("boss_fight.png", renamed.getFileName().toString());
        assertTrue(Files.exists(renamed));
        assertFalse(Files.exists(a));

        Path crop1 = ScreenshotIndex.croppedOutputPath(renamed);
        assertEquals("boss_fight_crop.png", crop1.getFileName().toString());
        Files.write(crop1, new byte[]{3});
        Path crop2 = ScreenshotIndex.croppedOutputPath(renamed);
        assertEquals("boss_fight_crop_2.png", crop2.getFileName().toString());

        assertTrue(ScreenshotIndex.delete(dir, b));
        assertFalse(Files.exists(b));

        ScreenshotIndex.CropRect r = ScreenshotIndex.computeCropRect(1920, 1080, 0.75f, 0.5f, 0.25f, 0.0f);
        assertEquals(new ScreenshotIndex.CropRect(480, 0, 960, 540), r);
    }
}
