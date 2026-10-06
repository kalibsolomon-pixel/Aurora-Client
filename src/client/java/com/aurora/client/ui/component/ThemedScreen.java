package com.aurora.client.ui.component;

/**
 * Marker interface for Aurora's own screens.
 *
 * <p>Implementing this opts the screen's vanilla widgets into the shared
 * theming layer — most importantly {@code EditBox}: the mod-wide
 * {@code EditBoxMixin} replaces the vanilla dark-box rendering with the
 * single canonical themed text field (token-driven fill/border, vertically
 * centered text, blinking caret) on any screen marked with this interface.
 * Without the marker, edit boxes on that screen fall back to vanilla
 * rendering and visually break out of the theme.
 *
 * <p>Every custom Aurora screen implements this; vanilla screens that got
 * Aurora search bars via mixins (world selection, multiplayer) are listed
 * explicitly in the mixin instead, since we cannot add interfaces to
 * vanilla classes.
 */
public interface ThemedScreen {
    /** All production Aurora screens use the approved unified material. */
    // An opted-in host must paint its neutral parent face/boundary in the
    // cached overlay for both capture success and decline (RoundedPanel does
    // this automatically). GlassSurface owns only the pre-dim backdrop work.
    default boolean unifiedTheme() { return true; }

    /** Embedded cards in a parent window use cached materials, never another capture. */
    default boolean parentGlassOnly() { return false; }
}
