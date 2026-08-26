package com.aurora.client.screen;

import com.aurora.client.AuroraClient;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.ThemedScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Aurora's title screen.
 *
 * <p>The Aurora emblem replaces the previous Component wordmark. Source PNG is
 * 256x256; rendered at 96x96 on screen via the scaling drawTexture overload.
 *
 * <p>Layout is centered around the screen midpoint, scaling cleanly across
 * resolutions. The 6-button stack uses the shared themed {@link ButtonWidget}
 * (the canonical {@code ui.component.Button} under the hood) — Aurora Settings
 * is rendered as the {@code primary} variant for a subtle brand emphasis.
 */
public class AuroraTitleScreen extends Screen implements ThemedScreen {

    // Logo
    private static final Identifier LOGO_TEX =
            Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "textures/gui/logo.png");
    /** Native dimensions of {@code logo.png}. Used as the UV normalization
     *  base in the blit call so the sampler always covers the full texture
     *  regardless of {@link #LOGO_SIZE}. */
    private static final int LOGO_TEX_W = 875;
    private static final int LOGO_TEX_H = 875;
    /**
     * Rendered size of the logo in logical GUI pixels. Scaled down ~15 %
     * from the previous 120 px to 102 px so the new diamond backdrop
     * (see {@link #LOGO_BACKDROP_TEX}) has visible breathing room
     * around the emblem.
     */
    private static final int LOGO_SIZE = 82;

    /**
     * Diamond emblem frame drawn behind {@link #LOGO_TEX}. Native
     * texture is square; on screen we render it as a square AABB whose
     * inscribed dark inner diamond is sized to host the logo with even
     * padding on every side.
     */
    private static final Identifier LOGO_BACKDROP_TEX =
            Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "textures/gui/logo_backdrop.png");
    private static final int LOGO_BACKDROP_TEX_W = 1024;
    private static final int LOGO_BACKDROP_TEX_H = 1024;
    /**
     * On-screen size of the backdrop. The diamond's inner dark region
     * is roughly a 45°-rotated square inscribed in this AABB. Sizing
     * the backdrop to ~1.85× the logo leaves the logo comfortably
     * inside the dark inner zone with the blue frame visible around
     * every side.
     */
    private static final int LOGO_BACKDROP_SIZE = 162;

    /**
     * Starfield background that replaces the previous animated aurora
     * gradient. Stretched to fill the full screen each frame; the source
     * image is a near-black night sky so any aspect-ratio distortion is
     * imperceptible.
     */
    private static final Identifier BG_TEX =
            Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "textures/gui/title_background.png");

    // Button stack
    private static final int BUTTON_W = 220;
    private static final int BUTTON_H = 30;
    private static final int BUTTON_GAP = 6;

    // Layout offsets relative to screen center
    private static final int LOGO_BLOCK_OFFSET = -150;
    /**
     * Vertical nudge of the logo relative to the backdrop's geometric
     * center. Negative = up. The diamond reads more balanced when the
     * emblem sits a few px above true center.
     */
    private static final int LOGO_NUDGE_Y = -5;
    /** Horizontal nudge of the logo relative to the backdrop center. Negative = left. */
    private static final int LOGO_NUDGE_X = 0;
    private static final int BUTTON_STACK_OFFSET = -50;

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
        this.addRenderableWidget(btn);
        return btn;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // Starfield backdrop. Setting (regionW, regionH, texW, texH) all
        // equal to the destination size makes the GPU sample UVs 0..1
        // across the entire texture and stretch it to fill the screen,
        // independent of the PNG's actual native pixel dimensions.
        ctx.blit(
                RenderPipelines.GUI_TEXTURED,
                BG_TEX,
                0, 0,
                0f, 0f,
                this.width, this.height,
                this.width, this.height,
                this.width, this.height
        );

        // Buttons (and other drawable children)
        super.render(ctx, mouseX, mouseY, delta);

        // Logo
        renderLogo(ctx);
    }

    private void renderLogo(GuiGraphics ctx) {
        // Logo center, clamped so it doesn't overflow the top edge on small
        // window heights. Clamp uses the backdrop size since it's the
        // larger of the two and sets the actual top extent.
        int logoCenterY = Math.max(LOGO_BACKDROP_SIZE / 2 + 20,
                this.height / 2 + LOGO_BLOCK_OFFSET);

        // Backdrop diamond — drawn first so the logo composes on top.
        int backdropX = (this.width - LOGO_BACKDROP_SIZE) / 2;
        int backdropY = logoCenterY - LOGO_BACKDROP_SIZE / 2;
        ctx.blit(
                RenderPipelines.GUI_TEXTURED,
                LOGO_BACKDROP_TEX,
                backdropX, backdropY,
                0f, 0f,
                LOGO_BACKDROP_SIZE, LOGO_BACKDROP_SIZE,
                LOGO_BACKDROP_TEX_W, LOGO_BACKDROP_TEX_H,
                LOGO_BACKDROP_TEX_W, LOGO_BACKDROP_TEX_H
        );

        int logoX = (this.width - LOGO_SIZE) / 2 + LOGO_NUDGE_X;
        int logoY = logoCenterY - LOGO_SIZE / 2 + LOGO_NUDGE_Y;

        // Scaling overload of drawTexture: source u/v/regionW/regionH match
        // the texture's full native size, dst width/height set the on-screen
        // size, and the GPU bilinearly samples between them — i.e., it draws
        // the entire texture scaled to fit LOGO_SIZE x LOGO_SIZE.
        //
        // Parameter order: (pipeline, identifier, x, y, u, v, width, height,
        //                   regionWidth, regionHeight, textureWidth, textureHeight)
        // — but with width==regionWidth and height==regionHeight, we get
        // a clean 1:1 scaling of the whole texture into the destination AABB.
        ctx.blit(
                RenderPipelines.GUI_TEXTURED,
                LOGO_TEX,
                logoX, logoY,
                0f, 0f,
                LOGO_SIZE, LOGO_SIZE,
                LOGO_TEX_W, LOGO_TEX_H,
                LOGO_TEX_W, LOGO_TEX_H
        );
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /**
     * Report as a non-pause screen so MC's inactivity-FPS limiter (which
     * kicks in on pause screens after a few seconds of no input) does not
     * throttle the animated backdrop down below 30 fps.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}