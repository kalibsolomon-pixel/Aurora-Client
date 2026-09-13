package com.aurora.client.hud;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;

/**
 * Saturation HUD overlay, ported from AppleSkin
 * (squeek502/AppleSkin, Unlicense / public domain).
 *
 * <p>Draws saturation icons on top of vanilla hunger icons using a sprite
 * atlas at {@code aurora:textures/icons.png}. The atlas has 4 sprite columns
 * of 9x9 each:
 * <pre>
 *   col 0 (u=0):  empty / &lt;25%
 *   col 1 (u=9):  quarter
 *   col 2 (u=18): half
 *   col 3 (u=27): full
 * </pre>
 *
 * <p>ASSET DEPENDENCY: this class is the sole reader of the bundled file
 * {@code src/client/resources/assets/aurora/textures/icons.png} (the reference
 * is the {@link #ICONS} identifier, not a filename literal — reference-audit
 * greps have missed it before, and the 2026-09-11 D9 dead-texture sweep
 * deleted the file, turning every pip into an opaque black square: MC's
 * missing-texture fallback is a 16x16 checkerboard whose top-left quadrant is
 * solid black, and u=0..36 of a 256-wide atlas lands entirely inside it).
 * Don't remove the PNG without removing this overlay.
 *
 * <p>Called from InGameHudMixin with the exact (top, right) that vanilla
 * passed to its own renderFood — so alignment is perfect by construction.
 */
public final class SaturationOverlay {

    public static final Identifier ICONS = Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "textures/icons.png");

    private static final int ICON_SIZE = 9;
    private static final int V_SAT     = 0;  // saturation row in the atlas

    /**
     * Render the saturation overlay. {@code top} and {@code right} are the
     * same values vanilla's renderFood uses internally ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â see InGameHudMixin.
     */
    public static void render(GuiGraphics ctx, Player player, int top, int right) {
        if (!AuroraConfig.get().saturationBarEnabled) return;
        if (player == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) return;
        if (client.options.hideGui) return;
        // Same caveat as HudModuleManager: in 1.21.11 showDebugScreen()
        // returns true whenever ANY debug entry is enabled (e.g. F3+B
        // hitboxes), not just the F3 text overlay. Gate on the actual
        // overlay-visible flag instead so the saturation pips don't
        // disappear the moment the player toggles vanilla hitboxes on.
        if (client.debugEntries != null && client.debugEntries.isOverlayVisible()) return;

        FoodData stats = player.getFoodData();
        float saturationLevel = stats.getSaturationLevel();
        if (saturationLevel <= 0f) return;

        float saturation = Math.max(0f, Math.min(saturationLevel, 20f));
        int endBar = (int) Math.ceil(saturation / 2f);

        // Full white with full alpha ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â the atlas sprites already contain the color.
        int color = 0xFFFFFFFF;

        // Vanilla hunger layout: 10 icons, right-aligned, stepping LEFT by 8px.
        // Icon i (0 = rightmost) is at x = right + offsetX(i), y = top.
        // AppleSkin's offset formula: x = -(i * 8) - 9, y = 0 (no jitter in our port).
        for (int i = 0; i < endBar; i++) {
            int x = right + (-(i * 8) - 9);
            int y = top;

            float satOfBar = (saturation / 2f) - i;
            int u;
            if (satOfBar >= 1f)        u = 3 * ICON_SIZE; // full
            else if (satOfBar > 0.5f)  u = 2 * ICON_SIZE; // between half and full
            else if (satOfBar > 0.25f) u = 1 * ICON_SIZE; // quarter
            else                        u = 0;            // empty (shouldn't reach here since >0 check)

            ctx.blit(
                    RenderPipelines.GUI_TEXTURED,
                    ICONS,
                    x, y,
                    u, V_SAT,
                    ICON_SIZE, ICON_SIZE,
                    256, 256,
                    color
            );
        }
    }
}