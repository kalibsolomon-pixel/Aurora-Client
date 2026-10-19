package com.aurora.client.worldmap;

/** Reused bounded HUD-label layout. Selected/nearest labels are offered first. */
public final class MarkerLabelLayout {
    private final float[] left = new float[64], top = new float[64], right = new float[64], bottom = new float[64];
    private int count;
    private float centerX, bottomY;
    public void reset() { count = 0; }
    public float centerX() { return centerX; }
    public float bottomY() { return bottomY; }
    public boolean place(float x, float y, float width, float height, int screenWidth, int screenHeight) {
        if (count >= left.length || width > screenWidth - 8 || height > screenHeight - 12
                || !Float.isFinite(x) || !Float.isFinite(y)) return false;
        centerX = (float) MapTransform.bounded(x, 4 + width / 2, screenWidth - 4 - width / 2);
        bottomY = (float) MapTransform.bounded(y, height + 4, screenHeight - 8);
        float l = centerX - width / 2, t = bottomY - height, r = centerX + width / 2;
        for (int i = 0; i < count; i++)
            if (l < right[i] + 3 && r > left[i] - 3 && t < bottom[i] + 3 && bottomY > top[i] - 3) return false;
        left[count] = l; top[count] = t; right[count] = r; bottom[count++] = bottomY;
        return true;
    }
}
