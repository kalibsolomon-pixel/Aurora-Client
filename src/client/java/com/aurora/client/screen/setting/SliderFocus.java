package com.aurora.client.screen.setting;

/**
 * Tracks the most-recently-clicked slider. The slider claims focus on
 * mouseClicked, holds it across frames, and screens forward scroll
 * wheel + arrow key input here. Click any other slider, or click off
 * a slider entirely, releases focus.
 *
 * <p>Item 1: scroll wheel and arrow keys adjust the focused slider by
 * one step (0.01 for double sliders, 1 for int sliders). Shift x10.
 */
public final class SliderFocus {
    private SliderFocus() {}

    public interface ScrollableSlider {
        /** Adjust the slider by N steps. Positive = up, negative = down. */
        void adjustBySteps(int steps);
    }

    private static ScrollableSlider focused = null;

    public static void claim(ScrollableSlider s) { focused = s; }
    public static void release(ScrollableSlider s) { if (focused == s) focused = null; }
    public static void releaseAll() { focused = null; }
    public static ScrollableSlider current() { return focused; }
}