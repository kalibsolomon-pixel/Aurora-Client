package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.screen.AuroraScreen;
import com.aurora.client.screen.AuroraTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Title screen behavior:
 * <ul>
 *   <li>Custom title enabled: redirect to {@link AuroraTitleScreen} on init.</li>
 *   <li>Custom title disabled: leave vanilla's button stack alone and add an
 *       "Aurora Settings" button overlaid on the screen Ã¢â‚¬â€ placed at a fixed
 *       safe position (lower-right corner) so it never collides with vanilla
 *       buttons or modloader-added buttons (Mods button from Fabric API,
 *       etc.).</li>
 * </ul>
 *
 * <p>The previous version of this mixin tried to inject into the middle of
 * the vanilla button stack and shift things around, which broke when other
 * mods (Fabric API's Mods button) were also present, or when window
 * dimensions placed buttons outside the assumed bounds. The current version
 * just adds a small button at a corner, which is layout-stable across
 * window sizes and play nice with other mods.
 *
 * <p>The redirect is unconditional, gated only by the config flag. There is
 * no re-entrancy / infinite-loop risk: {@link AuroraTitleScreen} never
 * navigates back to the vanilla {@link TitleScreen} (all of its buttons pass
 * {@code this} as the parent, so "back" returns to the Aurora screen), so the
 * mixin cannot fire again until a brand-new {@code TitleScreen} instance is
 * shown (game start, disconnecting from a world, etc.) — at which point we
 * <em>want</em> to redirect again.
 *
 * <p>The previous implementation used a {@code static boolean aurora$redirecting}
 * one-shot flag to guard against a (non-existent) loop. That flag was never
 * cleared by the custom screen, so it leaked across visits and caused the
 * mixin to skip the redirect on every <em>second</em> arrival at the title
 * screen — i.e. the custom screen appeared only "every other time". Removing
 * the flag fixes the alternation.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void aurora$redirect(CallbackInfo ci) {
        if (!AuroraConfig.get().customTitleScreen) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null) return;

        client.setScreen(new AuroraTitleScreen());
        ci.cancel();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void aurora$injectAuroraButton(CallbackInfo ci) {
        if (AuroraConfig.get().customTitleScreen) return; // we'd have redirected

        // Place the Aurora Settings button in the lower-right corner of the
        // screen, above the typical Quit/Realms placement so it doesn't
        // overlap. Width 100 keeps it compact.
        int btnWidth = 100;
        int btnHeight = 20;
        int padding = 4;
        int x = this.width - btnWidth - padding;
        int y = this.height - btnHeight - padding;

        Button auroraButton = Button.builder(
                        Component.literal("Aurora Settings"),
                        btn -> Minecraft.getInstance().setScreen(AuroraScreen.create()))
                .bounds(x, y, btnWidth, btnHeight)
                .build();
        this.addRenderableWidget(auroraButton);
    }
}