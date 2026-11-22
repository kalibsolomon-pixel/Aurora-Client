package com.aurora.client.mixin;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.resourcepack.PackFolderTree;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.validation.DirectoryValidator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Discovers client resource packs nested inside organizing subfolders under
 * {@code resourcepacks/} (e.g. {@code resourcepacks/PvP/16x/my_pack.zip}).
 *
 * <p>Top-level packs keep their exact vanilla IDs ({@code file/<name>}); nested
 * packs receive unique path-qualified IDs ({@code file/<folder>/<name>}) while
 * displaying their clean filename as title. Datapacks ({@link PackType#SERVER_DATA})
 * are never touched.
 */
@Mixin(FolderRepositorySource.class)
public abstract class FolderRepositorySourceMixin {

    @Shadow @Final private Path folder;
    @Shadow @Final private PackType packType;
    @Shadow @Final private PackSource packSource;
    @Shadow @Final private DirectoryValidator validator;
    @Shadow @Final private static PackSelectionConfig DISCOVERED_PACK_SELECTION_CONFIG;

    @Inject(method = "loadPacks", at = @At("TAIL"))
    private void aurora$loadNestedResourcePacks(Consumer<Pack> consumer, CallbackInfo ci) {
        if (this.packType != PackType.CLIENT_RESOURCES) return;
        if (!AuroraConfig.get().resourcepackFoldersEnabled) return;

        List<Path> subFolders = PackFolderTree.discoverOrganizingFolders(this.folder, PackFolderTree.MAX_DEPTH);
        for (Path subFolder : subFolders) {
            try {
                FolderRepositorySource.discoverPacks(subFolder, this.validator, (packPath, supplier) -> {
                    String rel = PackFolderTree.relativePackPath(this.folder, packPath);
                    if (rel.isEmpty() || !rel.contains("/")) return;
                    String leaf = packPath.getFileName() != null ? packPath.getFileName().toString() : rel;
                    PackLocationInfo info = new PackLocationInfo(
                            "file/" + rel,
                            Component.literal(leaf),
                            this.packSource,
                            Optional.empty());
                    Pack pack = Pack.readMetaAndCreate(info, supplier, this.packType, DISCOVERED_PACK_SELECTION_CONFIG);
                    if (pack != null) {
                        consumer.accept(pack);
                    }
                });
            } catch (IOException e) {
                AuroraClient.LOGGER.warn("[ResourcePacks] failed to scan subfolder {}", subFolder, e);
            }
        }
    }
}
