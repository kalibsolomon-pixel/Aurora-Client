package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.TotemPopFeature;
import com.aurora.client.hud.module.PingModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Decorates other players' nametags with a colored ping value and an
 * optional totem-pop counter, by mutating {@link AvatarRenderState#nameTag}
 * after vanilla's {@link AvatarRenderer#extractRenderState} has populated
 * it.
 *
 * <p>This is the simplest possible hook point: vanilla then renders our
 * combined component through its own pipeline (correct background, sneak
 * dimming, distance culling, lighting). The previous approach of issuing
 * a second {@code submitNameTag} call was fragile because the underlying
 * PoseStack state at TAIL of vanilla's submit method differs from what
 * vanilla itself used for the name, leading to mis-positioned text or no
 * text at all.
 *
 * <p>Local player and self-hosted (singleplayer) cases are skipped — no
 * useful ping for the player's own nametag, and the totem counter for
 * the local player is already shown in the corner HUD.
 */
@Mixin(AvatarRenderer.class)
public abstract class PlayerEntityRendererMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"),
            require = 1
    )
    private void aurora$decorateNameTag(Avatar entity, AvatarRenderState state, float partial, CallbackInfo ci) {
        if (state == null || state.nameTag == null) return;
        if (!(entity instanceof Player player)) return;

        AuroraConfig cfg = AuroraConfig.get();
        boolean wantPing  = cfg.nametagPingEnabled;
        boolean wantTotem = cfg.nametagTotemPopsEnabled;
        if (!wantPing && !wantTotem) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        // Skip self — own ping isn't meaningful, and own totem pops are
        // already in the dedicated HUD module.
        if (mc.player != null && player.getUUID().equals(mc.player.getUUID())) return;

        MutableComponent decorated = state.nameTag.copy();
        boolean changed = false;

        if (wantPing && mc.getConnection() != null) {
            PlayerInfo entry = mc.getConnection().getPlayerInfo(player.getUUID());
            if (entry != null) {
                int ms = entry.getLatency();
                String pingText = ms < 0 ? "?" : ms + "ms";
                int color = ms < 0 ? com.aurora.client.theme.HudStatus.OFF : PingModule.pingColor(ms);
                decorated.append(Component.literal("  "))
                         .append(Component.literal(pingText).withColor(color));
                changed = true;
            }
        }

        if (wantTotem) {
            TotemPopFeature feat = TotemPopFeature.get();
            if (feat != null) {
                Integer pops = feat.otherPops().get(player.getUUID());
                if (pops != null && pops > 0) {
                    // Gold accent so the count visually pops without
                    // looking like a mob-health bar.
                    decorated.append(Component.literal("  \u2620"))
                             .append(Component.literal(String.valueOf(pops)).withColor(0xFFFFAA00));
                    changed = true;
                }
            }
        }

        if (changed) state.nameTag = decorated;
    }
}