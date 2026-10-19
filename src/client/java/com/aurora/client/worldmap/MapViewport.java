package com.aurora.client.worldmap;

/** Cursor-anchored eased zoom with continuous world/screen coordinates. */
public final class MapViewport {
    public double x, z;
    private double scale = 1, target = 1, anchorX, anchorZ, mouseX, mouseY;
    private boolean zooming;

    public double scale() { return scale; }
    public double worldX(double screenX, int width) { return x + (screenX - width * .5) / scale; }
    public double worldZ(double screenY, int height) { return z + (screenY - height * .5) / scale; }
    public void zoom(double factor, double sx, double sy, int width, int height) {
        anchorX = worldX(sx, width); anchorZ = worldZ(sy, height);
        mouseX = sx; mouseY = sy;
        target = MapTransform.bounded(target * factor, 1 / 16d, 16);
        zooming = true;
    }
    public void advance(double seconds, int width, int height) {
        if (!zooming) return;
        double amount = 1 - Math.exp(-Math.min(.1, Math.max(0, seconds)) / .07);
        scale = Math.exp(Math.log(scale) + (Math.log(target) - Math.log(scale)) * amount);
        if (Math.abs(scale - target) < .00001) { scale = target; zooming = false; }
        x = anchorX - (mouseX - width * .5) / scale;
        z = anchorZ - (mouseY - height * .5) / scale;
    }
    public void pan(double dx, double dy) {
        x -= dx / scale; z -= dy / scale;
        zooming = false; target = scale;
    }
    public void center(double x, double z) {
        this.x = x; this.z = z; zooming = false; target = scale;
    }
}
