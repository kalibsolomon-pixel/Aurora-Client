package com.aurora.client.ui.util;

/**
 * Where the custom font applies (owner ruling 2026-11-17): the pure decision
 * behind {@code AuroraConfig.TextRendererMode#AURORA_ONLY}, kept free of
 * Minecraft so its truth table can be tested in the core.
 *
 * <p>"Aurora UI Only" styles <em>only text drawn by an Aurora window's own
 * render</em> — the scope {@code ScreenTextScopeMixin} opens around an
 * Aurora window's screen extraction. It deliberately does not style the
 * vanilla hotbar, chat or scoreboard behind an open window, vanilla screens
 * that carry Aurora accents (search bars, the server-list latency label),
 * or Aurora's in-world HUD — including the HUD previews inside the HUD
 * editor, which must look the way they will in game. "All Text" and "Off"
 * never consult this class: they are decided before any scope is read.
 */
public final class TextScope {
    private TextScope() {}

    /**
     * Whether AURORA_ONLY styles the text being drawn right now.
     *
     * @param drawingHud        an Aurora HUD pass is drawing (HUD modules,
     *                          their editor previews, alerts, waypoint
     *                          labels, the tab-list ping) — never window
     *                          text, even inside a window scope
     * @param insideAuroraWindow the window scope mixin is between enter and
     *                          exit around an Aurora window's own render
     * @param scopeMixinFailed  {@code ScreenTextScopeMixin} is known not to
     *                          be working (ADR 0004 fail-soft)
     * @param auroraScreenOpen  the coarser fallback signal: the currently
     *                          open screen is an Aurora window
     * @return true when the configured font should apply to this draw
     */
    public static boolean auroraOnly(boolean drawingHud, boolean insideAuroraWindow,
                                     boolean scopeMixinFailed, boolean auroraScreenOpen) {
        // The HUD exclusion is absolute: previews inside the HUD editor draw
        // within a window scope and must still match their in-game look.
        if (drawingHud) return false;
        // Fail-soft: without the scope the mode cannot see individual draws,
        // so it falls back to the open-screen check — coarser (everything in
        // a frame with an Aurora screen open), but the setting still does
        // something and FeatureHealth reports the degradation.
        return scopeMixinFailed ? auroraScreenOpen : insideAuroraWindow;
    }
}
