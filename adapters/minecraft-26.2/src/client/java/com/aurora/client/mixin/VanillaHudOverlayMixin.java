package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.VanillaHudLayout;
import com.aurora.client.hud.VanillaHudOverlayHelper;
import com.aurora.client.hud.module.VanillaHudModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Moves, scales or hides the vanilla action bar, title and subtitle from
 * Aurora's HUD editor (Minecraft 26.2 {@link Hud}). Gated by
 * {@code vanillaHudEnabled}.
 */
@Mixin(Hud.class)
public abstract class VanillaHudOverlayMixin {
    @Shadow private Component overlayMessageString;
    @Shadow private int overlayMessageTime;
    @Shadow private int titleTime;
    @Shadow private Component title;
    @Shadow private Component subtitle;
    @Shadow private int titleFadeInTime;
    @Shadow private int titleStayTime;
    @Shadow private int titleFadeOutTime;
    @Shadow public abstract Font getFont();

    @Unique private boolean aurora$actionBarPushed;

    @Inject(method = "extractOverlayMessage(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"), cancellable = true)
    private void aurora$beginActionBar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        aurora$actionBarPushed = false;
        if (!AuroraConfig.get().vanillaHudEnabled) return;
        if (this.overlayMessageString == null || this.overlayMessageTime <= 0) return;
        if (!VanillaHudModule.isVisible(VanillaHudLayout.Element.ACTION_BAR)) {
            ci.cancel();
            return;
        }
        aurora$actionBarPushed = VanillaHudOverlayHelper.beginActionBar(graphics);
    }

    @Inject(method = "extractOverlayMessage(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("RETURN"))
    private void aurora$endActionBar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (aurora$actionBarPushed) {
            aurora$actionBarPushed = false;
            graphics.pose().popMatrix();
        }
    }

    @Inject(method = "extractTitle(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"), cancellable = true)
    private void aurora$controlTitleAndSubtitle(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!AuroraConfig.get().vanillaHudEnabled) return;
        if (VanillaHudOverlayHelper.handleTitleAndSubtitle(graphics, this.getFont(),
                this.title, this.subtitle, this.titleTime,
                this.titleFadeInTime, this.titleStayTime, this.titleFadeOutTime,
                deltaTracker.getGameTimeDeltaPartialTick(false))) {
            ci.cancel();
        }
    }
}
