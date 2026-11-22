package com.aurora.client.resourcepack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PackFolderTreeTest {

    @Test
    void folderAndLeafExtractionFromPackIds() {
        assertEquals("", PackFolderTree.folderOfPackId("vanilla"));
        assertEquals("", PackFolderTree.folderOfPackId("file/simple.zip"));
        assertEquals("PvP", PackFolderTree.folderOfPackId("file/PvP/sword16x.zip"));
        assertEquals("PvP/16x", PackFolderTree.folderOfPackId("file/PvP/16x/overlay.zip"));
        assertEquals("PvP/16x", PackFolderTree.folderOfPackId("file/PvP\\16x\\overlay.zip"));

        assertEquals("overlay.zip", PackFolderTree.leafNameOfPackId("file/PvP/16x/overlay.zip"));
        assertEquals("simple.zip", PackFolderTree.leafNameOfPackId("file/simple.zip"));
        assertEquals("vanilla", PackFolderTree.leafNameOfPackId("vanilla"));
    }

    @Test
    void breadcrumbsAndParentNavigation() {
        var crumbs = PackFolderTree.breadcrumbs("PvP/16x/Skies");
        assertEquals(4, crumbs.size());
        assertEquals(new PackFolderTree.Breadcrumb("Root", ""), crumbs.get(0));
        assertEquals(new PackFolderTree.Breadcrumb("PvP", "PvP"), crumbs.get(1));
        assertEquals(new PackFolderTree.Breadcrumb("16x", "PvP/16x"), crumbs.get(2));
        assertEquals(new PackFolderTree.Breadcrumb("Skies", "PvP/16x/Skies"), crumbs.get(3));

        assertEquals("PvP/16x", PackFolderTree.parentFolder("PvP/16x/Skies"));
        assertEquals("PvP", PackFolderTree.parentFolder("PvP/16x"));
        assertEquals("", PackFolderTree.parentFolder("PvP"));
        assertEquals("", PackFolderTree.parentFolder(""));
    }

    @Test
    void discoverOrganizingFoldersSkipsUnpackedPacksAndHiddenDirs(@TempDir Path root) throws Exception {
        // Real unpacked pack at root — must NOT be treated as an organizing folder.
        Path unpackedRoot = root.resolve("MyUnpackedPack");
        Files.createDirectories(unpackedRoot.resolve("assets/minecraft/textures"));
        Files.writeString(unpackedRoot.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"test\"}}");

        // Organizing folder with nested folder and an unpacked pack inside.
        Path pvp = root.resolve("PvP");
        Path pvp16 = pvp.resolve("16x");
        Path nestedPack = pvp16.resolve("CleanSword");
        Files.createDirectories(nestedPack.resolve("assets"));
        Files.writeString(nestedPack.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"nested\"}}");

        // Empty organizing folder + hidden folder.
        Files.createDirectories(root.resolve("EmptyFolder"));
        Files.createDirectories(root.resolve(".hidden"));

        Set<String> discovered = PackFolderTree.discoverOrganizingFolderPaths(root);
        assertEquals(Set.of("EmptyFolder", "PvP", "PvP/16x"), discovered);
    }

    @Test
    void buildViewPartitionsFoldersPacksAndRecursiveSearch() {
        List<PackFolderTree.PackItem> packs = List.of(
                new PackFolderTree.PackItem("file/RootPack.zip", "Root Pack", "Default tweaks", "", 0),
                new PackFolderTree.PackItem("file/PvP/16x/Sword.zip", "Sword 16x", "Short swords", "PvP/16x", 1),
                new PackFolderTree.PackItem("file/PvP/Sky.zip", "Night Sky", "Custom sky", "PvP", 2),
                new PackFolderTree.PackItem("file/SMP/Cozy.zip", "Cozy Vanilla", "Warm tones", "SMP", 3));

        // Root view shows PvP (2 packs), SMP (1 pack), Empty (0 packs), and RootPack directly.
        var rootView = PackFolderTree.buildView(packs, Set.of("PvP", "PvP/16x", "SMP", "Empty"), "", "");
        assertFalse(rootView.searching());
        assertEquals(3, rootView.folders().size());
        assertEquals(new PackFolderTree.FolderEntry("Empty", "Empty", 0), rootView.folders().get(0));
        assertEquals(new PackFolderTree.FolderEntry("PvP", "PvP", 2), rootView.folders().get(1));
        assertEquals(new PackFolderTree.FolderEntry("SMP", "SMP", 1), rootView.folders().get(2));
        assertEquals(1, rootView.packs().size());
        assertEquals("Root Pack", rootView.packs().get(0).title());

        // Inside PvP: shows 16x subfolder (1 pack) and Night Sky directly.
        var pvpView = PackFolderTree.buildView(packs, Set.of("PvP", "PvP/16x", "SMP"), "PvP", "");
        assertEquals(1, pvpView.folders().size());
        assertEquals(new PackFolderTree.FolderEntry("16x", "PvP/16x", 1), pvpView.folders().get(0));
        assertEquals(1, pvpView.packs().size());
        assertEquals("Night Sky", pvpView.packs().get(0).title());

        // Global search finds nested packs across all folders by title or folder name.
        var searchView = PackFolderTree.buildView(packs, Set.of("PvP", "PvP/16x", "SMP"), "", "pvp sword");
        assertTrue(searchView.searching());
        assertTrue(searchView.folders().isEmpty());
        assertEquals(1, searchView.packs().size());
        assertEquals("Sword 16x", searchView.packs().get(0).title());
        assertEquals("PvP/16x", searchView.packs().get(0).folderPath());
    }

    @Test
    void resolveSubfolderBlocksTraversal(@TempDir Path root) {
        Path safe = PackFolderTree.resolveSubfolder(root, "PvP/16x");
        assertEquals(root.toAbsolutePath().normalize().resolve("PvP").resolve("16x"), safe);
        // Dot-dot segments are stripped cleanly during normalization.
        Path cleaned = PackFolderTree.resolveSubfolder(root, "../outside");
        assertTrue(cleaned.startsWith(root.toAbsolutePath().normalize()));
    }
}
