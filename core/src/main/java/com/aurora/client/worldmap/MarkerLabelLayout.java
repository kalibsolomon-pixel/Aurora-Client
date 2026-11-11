package com.aurora.client.worldmap;

/** Reused bounded HUD-label layout. Selected/nearest labels are offered first. */
public final class MarkerLabelLayout {
    private final float[] left = new float[64], top = new float[64], right = new float[64], bottom = new float[64];
    // Hard marker cap (256), player and floating chrome, independently of 64 names.
    private final float[] reservedLeft = new float[260], reservedTop = new float[260], reservedRight = new float[260], reservedBottom = new float[260];
    private int count, reserved;
    private float centerX, bottomY;
    public void reset() { count = 0; reserved = 0; }
    public float centerX() { return centerX; }
    public float bottomY() { return bottomY; }
    /** Fixed chrome exclusion, without relocating it or changing the last placed label. */
    public void reserve(float x, float y, float width, float height) {
        if (reserved >= reservedLeft.length || !Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0) return;
        reservedLeft[reserved] = x; reservedTop[reserved] = y;
        reservedRight[reserved] = x + width; reservedBottom[reserved++] = y + height;
    }
    public boolean place(float x, float y, float width, float height, int screenWidth, int screenHeight) {
        if (count >= left.length || width > screenWidth - 8 || height > screenHeight - 12
                || !Float.isFinite(x) || !Float.isFinite(y)) return false;
        centerX = (float) MapTransform.bounded(x, 4 + width / 2, screenWidth - 4 - width / 2);
        bottomY = (float) MapTransform.bounded(y, height + 4, screenHeight - 8);
        float l = centerX - width / 2, t = bottomY - height, r = centerX + width / 2;
        for (int i = 0; i < reserved; i++)
            if (l < reservedRight[i] + 3 && r > reservedLeft[i] - 3 && t < reservedBottom[i] + 3 && bottomY > reservedTop[i] - 3) return false;
        for (int i = 0; i < count; i++)
            if (l < right[i] + 3 && r > left[i] - 3 && t < bottom[i] + 3 && bottomY > top[i] - 3) return false;
        left[count] = l; top[count] = t; right[count] = r; bottom[count++] = bottomY;
        return true;
    }
}
