package com.aurora.client.mixin;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.module.ScoreboardModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Swaps the vanilla scoreboard sidebar for Aurora's customizable
 * {@link ScoreboardModule}. Two hooks riding vanilla's own sidebar
 * extraction (mirroring its exact team-color -> slot resolution, which
 * differs between the 26.1 and 26.2+ HUD classes):
 *
 * <ol>
 *   <li>{@code extractScoreboardSidebar} HEAD — resolve the frame's sidebar
 *       objective exactly as vanilla does (team-colored slot falling back to
 *       {@code DisplaySlot.SIDEBAR}) and hand it to the shared module. The
 *       module then draws it through the HUD-module layer later in the same
 *       frame, so the sidebar is drawn exactly once.</li>
 *
 *   <li>{@code displayScoreboardSidebar} HEAD (cancellable) — cancel
 *       vanilla's draw while the feature and the module are enabled.</li>
 * </ol>
 *
 * <p>Gated by {@code scoreboardEnabled} (fail-soft: a failed application
 * turns the feature off for the session and vanilla keeps drawing).
 */
@Mixin(Hud.class)
public abstract class ScoreboardSidebarMixin {

    @Inject(method = "extractScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"))
    private void aurora$resolveSidebarObjective(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.scoreboardEnabled) return;
        ScoreboardModule module = aurora$module();
        if (module == null || !module.enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective teamObjective = null;
        PlayerTeam playerTeam = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
        if (playerTeam != null) {
            Optional<TeamColor> teamColor = playerTeam.getColor();
            if (teamColor.isPresent()) {
                teamObjective = scoreboard.getDisplayObjective(teamColor.get().displaySlot());
            }
        }
        module.aurora$noteObjective(teamObjective != null
                ? teamObjective : scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR));
    }

    @Inject(method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void aurora$cancelVanillaSidebar(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.scoreboardEnabled) return;
        ScoreboardModule module = aurora$module();
        if (module == null || !module.enabled) return;
        ci.cancel();
    }

    @Unique
    private static ScoreboardModule aurora$module() {
        var manager = AuroraClient.modules();
        return manager != null && manager.get(ScoreboardModule.ID) instanceof ScoreboardModule m ? m : null;
    }
}
