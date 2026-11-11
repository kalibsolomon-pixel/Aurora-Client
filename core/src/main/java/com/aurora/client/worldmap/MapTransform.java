package com.aurora.client.worldmap;

/** Shared coordinate math. Minecraft yaw zero is south; yaw 90 is west. */
public final class MapTransform {
    private MapTransform() {}

    public static double rotation(float yaw, boolean headingUp) {
        return headingUp ? Math.toRadians(yaw + 180.0) : 0;
    }

    public static double screenX(double dx, double dz, double cos, double sin) {
        return dx * cos + dz * sin;
    }

    public static double screenZ(double dx, double dz, double cos, double sin) {
        return -dx * sin + dz * cos;
    }

    public static double clampScale(double x, double z, double radius, boolean circle) {
        double extent = circle ? Math.hypot(x, z) : Math.max(Math.abs(x), Math.abs(z));
        return extent > radius ? radius / extent : 1;
    }

    public static int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double bounded(double value, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }
}
