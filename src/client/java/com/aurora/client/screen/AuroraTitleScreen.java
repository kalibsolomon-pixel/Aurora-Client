package com.aurora.client.screen;

import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.ThemedScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

/** Vanilla panorama with independently blurred neutral action cards.
 * Menu backdrop validity is declared for this frame only; vanilla-backed
 * semantic widgets keep their established input routing.
 */
public class AuroraTitleScreen extends Screen implements ThemedScreen {

    // Button stack
    private static final int BUTTON_W = 220;
    private static final int BUTTON_H = 30;
    private static final int BUTTON_GAP = 6;
    private static final int STACK_HEIGHT = 5 * BUTTON_H + 4 * BUTTON_GAP;
    private static final int BRAND_SIZE = 48;
    private static final int BRAND_GAP = 12;
    private int brandSize() { return Math.max(0, Math.min(BRAND_SIZE, this.height - 16 - STACK_HEIGHT - BRAND_GAP)); }
    private int stackTop() {
        int brandBand = brandSize() == 0 ? 0 : brandSize() + BRAND_GAP;
        return Math.max(8, (this.height - STACK_HEIGHT - brandBand) / 2) + brandBand;
    }

    public AuroraTitleScreen() {
        super(Component.translatable("aurora.title"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2 - BUTTON_W / 2;
        int startY = stackTop();
        int step = BUTTON_H + BUTTON_GAP;

        addBtn(centerX, startY + step * 0, Component.translatable("aurora.button.singleplayer"),
                "Browse and play singleplayer worlds.",
                () -> this.minecraft.setScreen(new SelectWorldScreen(this)),
                false);
        ButtonWidget multiplayerBtn = addBtn(centerX, startY + step * 1, Component.translatable("aurora.button.multiplayer"),
                "Browse multiplayer servers.",
                () -> this.minecraft.setScreen(new JoinMultiplayerScreen(this)),
                false);
        if (this.minecraft != null) {
            multiplayerBtn.active = this.minecraft.allowsMultiplayer();
        }
        addBtn(centerX, startY + step * 2, Component.literal("Aurora Settings"),
                "Open Aurora's settings.",
                () -> this.minecraft.setScreen(AuroraScreen.create()),
                false);
        // "Mods" button retired — duplicated Aurora Settings while no
        // compatible Mod Menu artifact is published for 1.21.11. The
        // reflective shim AuroraModMenuApi stays in place for a future
        // re-enable once Mod Menu ships a stable 1.21.11 release.
        addBtn(centerX, startY + step * 3, Component.translatable("aurora.button.options"),
                "Open Minecraft's options.",
                () -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options)),
                false);
        addBtn(centerX, startY + step * 4, Component.translatable("aurora.button.quit"),
                "Quit Minecraft.",
                () -> this.minecraft.stop(),
                false);
    }

    private ButtonWidget addBtn(int x, int y, Component label, String description,
                                Runnable onPress, boolean primary) {
        // Vanilla-backed semantic button: vanilla keeps the pointer/keyboard/
        // focus routing and the click sound; the semantic action owns only the
        // behavior callback plus narration metadata.
        ButtonWidget btn = ButtonWidget.semantic(x, y, BUTTON_W, BUTTON_H, label, description, onPress);
        // Standard glass treatment: neutral raised for actions, accent-
        // stained for the primary (selected/primary is the only stained
        // scope — §6 convention 3); primary(true) keeps the flat-fallback
        // variant consistent with the stained treatment.
        btn.glassStyle(primary ? Button.GlassStyle.STAINED : Button.GlassStyle.NEUTRAL).primary(primary);
        this.addRenderableWidget(btn);
        return btn;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // Vanilla's rotating panorama via the shared menu-backdrop helper —
        // the same draw + frame-scoped declaration any Aurora screen makes
        // from its renderBackground (CubeMap.render is an eager pass against
        // the main target's color texture, so the panorama is capturable the
        // moment this returns).
        GlassSurface.renderMenuPanorama(this, ctx, delta);

        GlassSurface.beginGlassPass();
        for (var widget : this.children()) {
            if (widget instanceof ButtonWidget button) button.renderGlassPass(ctx);
        }
        GlassSurface.overlayDim(ctx,this.width,this.height);

        // Standalone official mark above the action stack; no surface or blur pass.
        int mark = brandSize();
        if (mark > 0) com.aurora.client.ui.util.AuroraBrand.draw(ctx,
                (this.width - mark) / 2, stackTop() - BRAND_GAP - mark, mark);

        // Buttons (and other drawable children)
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        super.removed();
        MaterialSurface.disposeTemplates();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /**
     * Report as a non-pause screen so MC's inactivity-FPS limiter (which
     * kicks in on pause screens after a few seconds of no input) does not
     * throttle the animated panorama down below 30 fps.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
