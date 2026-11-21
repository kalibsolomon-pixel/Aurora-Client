package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.VanillaHudLayout;
import com.aurora.client.hud.VanillaHudOverlayHelper;
import com.aurora.client.hud.module.VanillaHudModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

/**
 * Moves, scales or hides Minecraft's boss bar from Aurora's HUD editor.
 * Gated by {@code vanillaHudEnabled} (fail-soft: when disabled or unapplied,
 * vanilla draws untouched).
 */
@Mixin(BossHealthOverlay.class)
public abstract class VanillaBossBarMixin {
    @Shadow @Final private Map<UUID, LerpingBossEvent> events;
    @Unique private boolean aurora$pushed;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At("HEAD"), cancellable = true)
    private void aurora$beginBossBar(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        aurora$pushed = false;
        if (!AuroraConfig.get().vanillaHudEnabled) return;
        if (!VanillaHudModule.isVisible(VanillaHudLayout.Element.BOSS_BAR)) {
            ci.cancel();
            return;
        }
        if (this.events.isEmpty()) return;
        aurora$pushed = VanillaHudOverlayHelper.beginBossBar(graphics);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At("RETURN"))
    private void aurora$endBossBar(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (aurora$pushed) {
            aurora$pushed = false;
            graphics.pose().popMatrix();
        }
    }
}
