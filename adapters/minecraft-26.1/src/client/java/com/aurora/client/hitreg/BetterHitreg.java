package com.aurora.client.hitreg;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import static com.aurora.client.hitreg.Hitreg.*;

/**
 * Better Hitreg — client-side hit registration feedback, adapted from
 * <b>BetterHitreg by Jass</b> (modrinth.com/mod/betterhitreg), integrated
 * into Aurora with the author's explicit permission. The timing core
 * ({@link Hitreg}, {@link Hit}, {@link HitType}, the {@code util} package
 * and the {@code mixin.hitreg} mixins) is Jass's code; this class is what
 * remains of the original {@code ClientModInitializer} once its command
 * tree, chat alerts and hand-drawn menu were retired in favor of Aurora's
 * own settings UI.
 *
 * <p>Since 2026-11-15 Aurora keeps only the custom client-side hitreg (the
 * feedback delay and its two safety switches). The overlays, practice
 * arena, practice scoreboard, keybinds, sound filters and hit statistics
 * were removed, so the wiring is two steps:
 * <ol>
 *   <li>{@link Hitreg#client} is set <em>first</em> — every class in the
 *       package reads that static, so nothing may touch {@code Hitreg} or
 *       {@code Settings} before it.</li>
 *   <li>{@code START_CLIENT_TICK} → {@link Hitreg#tick()} (the fight-state
 *       machine).</li>
 * </ol>
 */
public final class BetterHitreg {
    private BetterHitreg() {}

    /** Registry id of the feature card / detail screen. */
    public static final String FEATURE_ID = "better_hitreg";

    private static boolean initialized;

    /**
     * Wire the hitreg runtime into the client. Called once from
     * {@code AuroraClient.onInitializeClient()} after the config and active
     * profile are loaded (the settings layer reads
     * {@link com.aurora.client.config.AuroraConfig}).
     */
    public static void initialize() {
        if (initialized) return;
        initialized = true;

        // Ordering constraint: must be the first statement — every class in
        // the package reads this static.
        client = Minecraft.getInstance();

        ClientTickEvents.START_CLIENT_TICK.register(mc -> tick());
    }
}
