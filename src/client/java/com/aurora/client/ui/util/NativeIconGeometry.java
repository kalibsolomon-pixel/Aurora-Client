package com.aurora.client.ui.util;

/** Integer GUI texture sizes that retain the native raster without resampling. */
public final class NativeIconGeometry {
    private NativeIconGeometry() {}
    public static int canvasSize(int inkPixels, int guiScale) {
        if (inkPixels <= 0 || guiScale <= 0) throw new IllegalArgumentException("Positive raster size and scale required");
        int unit = 2 * guiScale;
        return ((inkPixels + unit - 1) / unit) * unit;
    }
}
