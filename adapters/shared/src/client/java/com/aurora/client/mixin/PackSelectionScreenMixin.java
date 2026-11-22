package com.aurora.client.mixin;

import com.aurora.client.adapter.NativeUi;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.resourcepack.PackFolderTree;
import com.aurora.client.screen.ResourcePackManagerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/**
 * Redirects Minecraft's Options → Resource Packs screen to Aurora's folder-aware
 * {@link ResourcePackManagerScreen} when {@code resourcepackFoldersEnabled} is on.
 * Datapack screens (whose {@code packDir} differs from {@code resourcepacks/})
 * and explicit "Vanilla" requests pass straight through untouched.
 */
@Mixin(PackSelectionScreen.class)
public abstract class PackSelectionScreenMixin extends Screen {

    @Shadow @Final private Path packDir;
    @Shadow @Final private PackSelectionModel model;
    @Shadow private void closeWatcher() {}

    protected PackSelectionScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void aurora$redirectResourcePackManager(CallbackInfo ci) {
        if (!AuroraConfig.get().resourcepackFoldersEnabled) return;
        if (ResourcePackManagerScreen.consumeVanillaBypass()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || this.packDir == null) return;
        if (!PackFolderTree.samePath(this.packDir, mc.getResourcePackDirectory())) return;

        this.closeWatcher();
        NativeUi.setScreen(mc, new ResourcePackManagerScreen(this.model, this.packDir, null));
        ci.cancel();
    }
}
