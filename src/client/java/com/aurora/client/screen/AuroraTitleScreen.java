package com.aurora.client.screen;

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

/**
 * Aurora's title screen: vanilla's own panorama underneath, Aurora's glass
 * buttons floating over it.
 *
 * <p>The screen draws NO custom branding — the former emblem, diamond
 * backdrop and starfield blits were removed outright (their PNGs deleted;
 * the starfield's own survival on the two selection screens ended
 * 2026-09-12, when {@code SelectionScreenBackgroundMixin} was removed and
 * those screens went back to the vanilla backdrop — their themed buttons
 * and search fields stay, via {@code AbstractButtonMixin}/{@code EditBoxMixin}).
 * The backdrop is vanilla's rotating title panorama, drawn by
 * {@code Screen.renderPanorama} exactly as vanilla's own title screen draws
 * it (vanilla {@code TitleScreen.renderBackground} is empty and
 * {@code render} calls {@code renderPanorama} directly — mirrored here
 * rather than going through {@code renderBackground}, which would stack the
 * menu-background texture and the accessibility blur on top of it).
 *
 * <p><b>Glass over the panorama.</b> On 1.21.11 {@code CubeMap.render}
 * issues an eager Blaze3D render pass straight into the main render
 * target's color texture — the same texture the glass pipeline's world
 * reader wraps — so right after {@code renderPanorama} returns, the
 * panorama pixels are genuinely capturable. Since 2026-09-12 the screen
 * says so through the shared helper —
 * {@link GlassSurface#renderMenuPanorama}, which draws the panorama and
 * stamps {@code BlurPanelRenderer.noteMenuBackdropDrawn()}, a
 * frame-scoped declaration that is the only exemption from the renderer's
 * menu-context guard (see that method for why the exemption cannot leak to
 * any other caller; {@code AuroraScreen} declares the same way from its
 * own {@code renderBackground} when no world is loaded). The buttons are
 * then ordinary shared
 * {@link ButtonWidget}s on the standard glass treatment — chrome-only
 * depth, like {@code ColorPickerScreen}/{@code HudEditorScreen}: no
 * window, no dim, just glass controls over the live panorama, with the
 * complete flat look whenever glass declines (Transparent style, F2
 * suppression, a frame the panorama did not draw).
 *
 * <p>The 5-button stack uses the shared themed {@link ButtonWidget}
 * (the canonical {@code ui.component.Button} under the hood) — Aurora
 * Settings is the {@code primary} variant, which on glass is the
 * accent-STAINED treatment (the ColorPicker Apply convention).
 */
public class AuroraTitleScreen extends Screen implements ThemedScreen {

    // Button stack
    private static final int BUTTON_W = 220;
    private static final int BUTTON_H = 30;
    private static final int BUTTON_GAP = 6;
    /**
     * Vertical offset of the stack's top from screen center. The 5-button
     * stack is 5*30 + 4*6 = 174 px tall, so -87 centers it exactly — the
     * logo block is gone and nothing else anchors the layout.
     */
    private static final int BUTTON_STACK_OFFSET = -87;

    public AuroraTitleScreen() {
        super(Component.translatable("aurora.title"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2 - BUTTON_W / 2;
        int startY = this.height / 2 + BUTTON_STACK_OFFSET;
        int step = BUTTON_H + BUTTON_GAP;

        addBtn(centerX, startY + step * 0, Component.translatable("aurora.button.singleplayer"),
                () -> this.minecraft.setScreen(new SelectWorldScreen(this)),
                false);
        ButtonWidget multiplayerBtn = addBtn(centerX, startY + step * 1, Component.translatable("aurora.button.multiplayer"),
                () -> this.minecraft.setScreen(new JoinMultiplayerScreen(this)),
                false);
        if (this.minecraft != null) {
            multiplayerBtn.active = this.minecraft.allowsMultiplayer();
        }
        addBtn(centerX, startY + step * 2, Component.literal("Aurora Settings"),
                () -> this.minecraft.setScreen(AuroraScreen.create()),
                true);
        // "Mods" button retired — duplicated Aurora Settings while no
        // compatible Mod Menu artifact is published for 1.21.11. The
        // reflective shim AuroraModMenuApi stays in place for a future
        // re-enable once Mod Menu ships a stable 1.21.11 release.
        addBtn(centerX, startY + step * 3, Component.translatable("aurora.button.options"),
                () -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options)),
                false);
        addBtn(centerX, startY + step * 4, Component.translatable("aurora.button.quit"),
                () -> this.minecraft.stop(),
                false);
    }

    private ButtonWidget addBtn(int x, int y, Component label, Runnable onPress, boolean primary) {
        ButtonWidget btn = new ButtonWidget(x, y, BUTTON_W, BUTTON_H, label, onPress, primary);
        // Standard glass treatment: neutral raised for actions, accent-
        // stained for the primary (selected/primary is the only stained
        // scope — §6 convention 3).
        btn.glassStyle(primary ? Button.GlassStyle.STAINED : Button.GlassStyle.NEUTRAL);
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

        // Buttons (and other drawable children)
        super.render(ctx, mouseX, mouseY, delta);
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
