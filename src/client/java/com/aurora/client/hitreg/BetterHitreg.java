package com.aurora.client.hitreg;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hitreg.util.Render;
import com.aurora.client.screen.FeatureDetailScreen;
import com.aurora.client.screen.FeatureMetadata;
import com.aurora.client.screen.FeatureRegistry;
import com.aurora.client.util.AuroraKey;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;

import static com.aurora.client.hitreg.Hitreg.*;

/**
 * Better Hitreg — client-side hit registration feedback, adapted from
 * <b>BetterHitreg by Jass</b> (modrinth.com/mod/betterhitreg), integrated
 * into Aurora with the author's explicit permission. The timing core
 * ({@link Hitreg}, {@link Hit}, {@link HitType}, the {@code util} package
 * and the {@code mixin.hitreg} mixins) is Jass's code moved verbatim; this
 * class is what remains of the original {@code ClientModInitializer} once
 * its command tree, chat alerts and hand-drawn menu were retired in favor
 * of Aurora's own settings UI.
 *
 * <p>Wiring, in the original entrypoint's order:
 * <ol>
 *   <li>{@link Hitreg#client} is set <em>first</em> — every class in the
 *       package reads that static, so nothing may touch {@code Hitreg},
 *       {@code Settings} or {@code Render} before it.</li>
 *   <li>{@link Render#updateColors()} primes the overlay color cache.</li>
 *   <li>{@code START_CLIENT_TICK} → {@link Hitreg#tick()} (the fight-state
 *       machine).</li>
 *   <li>{@code WorldRenderEvents.END_MAIN} → {@link Render#render} (target
 *       hitbox/cross/reach rings/floor grid).</li>
 *   <li>A {@link HudRenderCallback} drawing the practice scoreboard
 *       ({@code "Score: L - R"}) while either side is non-zero.</li>
 *   <li>{@code END_CLIENT_TICK} keybind dispatch: open settings, swap main
 *       hand, and the four scoreboard keys. The bindings themselves live on
 *       {@link AuroraConfig} and are registered with vanilla by
 *       {@code AuroraClient.registerAuroraKeybinds()} (two-way synced,
 *       unbound by default).</li>
 * </ol>
 *
 * <p>The scoreboard is a manual, user-triggered utility (press a key to
 * bump/send/reset a practice score) — not an alert — so it survives the
 * no-notifications rule. Its keys and HUD line are gated on the feature's
 * master enable.
 */
public final class BetterHitreg {
    private BetterHitreg() {}

    /** Ticks between accepted hand swaps (original: 5). */
    public static int handSwitchCooldown;
    /** Ticks between accepted scoreboard key presses (original: 5). */
    public static int scoreCooldown;
    public static int leftScore;
    public static int rightScore;

    private static final AuroraKey.EdgeDetector settingsEdge   = new AuroraKey.EdgeDetector();
    private static final AuroraKey.EdgeDetector handEdge       = new AuroraKey.EdgeDetector();
    private static final AuroraKey.EdgeDetector leftEdge       = new AuroraKey.EdgeDetector();
    private static final AuroraKey.EdgeDetector rightEdge      = new AuroraKey.EdgeDetector();
    private static final AuroraKey.EdgeDetector sendEdge       = new AuroraKey.EdgeDetector();
    private static final AuroraKey.EdgeDetector resetEdge      = new AuroraKey.EdgeDetector();

    /** Registry id of the feature card / detail screen. */
    public static final String FEATURE_ID = "better_hitreg";

    private static boolean initialized;

    /**
     * Wire the hitreg runtime into the client. Called once from
     * {@code AuroraClient.onInitializeClient()} after the config and active
     * profile are loaded (the settings layer reads {@link AuroraConfig}).
     */
    public static void initialize() {
        if (initialized) return;
        initialized = true;

        // Ordering constraint: must be the first statement — every class in
        // the package reads this static.
        client = Minecraft.getInstance();
        Render.updateColors();

        ClientTickEvents.START_CLIENT_TICK.register(mc -> tick());

        WorldRenderEvents.END_MAIN.register(context -> {
            Render.render(context.gameRenderer().getMainCamera());
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (!AuroraConfig.get().hitregEnabled) return;
            if (client.level == null || client.font == null || (leftScore == 0 && rightScore == 0)) return;
            String scoreText = "Score: " + leftScore + " - " + rightScore;
            context.drawString(client.font, scoreText, 10, 10, 0xFFFFFFFF);
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> dispatchKeys(mc));
    }

    /**
     * Keybind dispatch — the original END_CLIENT_TICK body with vanilla
     * {@code KeyMapping.consumeClick()} replaced by Aurora's GLFW edge
     * detectors (which already carry the "no screen open" guard the
     * original applied by hand). Cooldowns are kept as-is.
     */
    private static void dispatchKeys(Minecraft mc) {
        AuroraConfig cfg = AuroraConfig.get();
        boolean enabled = cfg.hitregEnabled;

        if (settingsEdge.justPressed(cfg.hitregSettingsKey) && enabled && mc.screen == null) {
            openSettings(mc);
        }

        if (handEdge.justPressed(cfg.hitregSwitchHandKey) && enabled && handSwitchCooldown == 0 && mc.player != null) {
            mc.options.mainHand().set(mc.options.mainHand().get().getOpposite());
            mc.player.setMainArm(mc.options.mainHand().get());
            mc.options.broadcastOptions();
            handSwitchCooldown = 5;
        }

        // Poll every scoreboard edge each tick so a press during the cooldown
        // is consumed rather than replayed on the next free tick.
        boolean left  = leftEdge.justPressed(cfg.hitregScoreLeftKey);
        boolean right = rightEdge.justPressed(cfg.hitregScoreRightKey);
        boolean send  = sendEdge.justPressed(cfg.hitregScoreSendKey);
        boolean reset = resetEdge.justPressed(cfg.hitregScoreResetKey);

        if (scoreCooldown == 0 && enabled && (left || right || send || reset)) {
            if (left) leftScore++;
            if (right) rightScore++;
            if (send && (leftScore > 0 || rightScore > 0) && mc.getConnection() != null) mc.getConnection().sendChat(leftScore + "-" + rightScore);
            if (reset) {
                leftScore = 0;
                rightScore = 0;
            }

            scoreCooldown = 5;
        }

        if (handSwitchCooldown > 0) handSwitchCooldown--;
        if (scoreCooldown > 0) scoreCooldown--;
    }

    /** Opens the Better Hitreg detail screen (falls back to the main Aurora screen). */
    public static void openSettings(Minecraft mc) {
        for (FeatureMetadata fm : FeatureRegistry.modules()) {
            if (FEATURE_ID.equals(fm.id)) {
                mc.setScreen(new FeatureDetailScreen(null, fm));
                return;
            }
        }
        mc.setScreen(com.aurora.client.screen.AuroraScreen.create());
    }
}
