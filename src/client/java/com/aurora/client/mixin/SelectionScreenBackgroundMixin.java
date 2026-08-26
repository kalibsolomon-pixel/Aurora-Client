package com.aurora.client.mixin;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class SelectionScreenBackgroundMixin {

    @Unique
    private static final Identifier AURORA_BG_TEX = Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "textures/gui/title_background.png");

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void aurora$renderStarryBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!AuroraConfig.get().customTitleScreen) return;
        
        Object self = this;
        if (self instanceof JoinMultiplayerScreen || self instanceof SelectWorldScreen) {
            Screen screen = (Screen) self;
            
            // Starfield backdrop
            ctx.blit(
                    RenderPipelines.GUI_TEXTURED,
                    AURORA_BG_TEX,
                    0, 0,
                    0f, 0f,
                    screen.width, screen.height,
                    screen.width, screen.height,
                    screen.width, screen.height
            );
            ci.cancel();
        }
    }
}