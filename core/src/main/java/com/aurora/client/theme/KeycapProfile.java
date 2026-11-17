package com.aurora.client.theme;

/**
 * Pure Cherry-profile keycap geometry and upper-left lighting. Geometry instances are device-space
 * bake inputs; the static top-surface helpers use the same logical units as the HUD layout.
 * No renderer or Minecraft types cross this core boundary.
 */
public final class KeycapProfile {
    public static final float BACK_WALL_SHARE = .08f;
    public static final float FRONT_WALL_SHARE = .18f;
    public static final float SIDE_WALL_SHARE = .12f;
    public static final float DEFAULT_RADIUS = 2f;
    public static final float PRESS_TRAVEL = 1f;
    public static final float CAP_HEIGHT = 3f;
    public static final float DISH_DEPTH = .35f;

    private KeycapProfile() {}

    public static float topX(float width) { return width * SIDE_WALL_SHARE; }
    public static float topWidth(float width) { return Math.max(0, width * (1 - 2 * SIDE_WALL_SHARE)); }
    public static float topY(float height, float press) {
        return height * BACK_WALL_SHARE + PRESS_TRAVEL * Math.clamp(press, 0, 1);
    }
    public static float topHeight(float height) {
        return Math.max(0, height * (1 - BACK_WALL_SHARE - FRONT_WALL_SHARE));
    }
    public static float backWall(float height, float press) {
        return height * BACK_WALL_SHARE + PRESS_TRAVEL * Math.clamp(press, 0, 1);
    }
    public static float frontWall(float height, float press) {
        return Math.max(0, height * FRONT_WALL_SHARE - PRESS_TRAVEL * Math.clamp(press, 0, 1));
    }

    /** One immutable endpoint bake. Pixel samples call no allocating helpers. */
    public static final class Geometry {
        private final float width, height, radius, scale;
        private final boolean pressed;
        private final float left, top, topWidth, topHeight, topRadius;
        private final float backWall, frontWall;

        public Geometry(float width, float height, float radius, float scale, boolean pressed) {
            if (width <= 0 || height <= 0 || scale <= 0)
                throw new IllegalArgumentException("Positive keycap dimensions and scale required");
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.pressed = pressed;
            this.radius = Math.clamp(radius, 0, Math.min(width, height) * .5f);
            left = KeycapProfile.topX(width / scale) * scale;
            topWidth = KeycapProfile.topWidth(width / scale) * scale;
            top = KeycapProfile.topY(height / scale, pressed ? 1 : 0) * scale;
            topHeight = KeycapProfile.topHeight(height / scale) * scale;
            backWall = KeycapProfile.backWall(height / scale, pressed ? 1 : 0) * scale;
            frontWall = KeycapProfile.frontWall(height / scale, pressed ? 1 : 0) * scale;
            // Concentric enough to read as one cap, without letting wide walls consume the top roll.
            topRadius = Math.clamp(this.radius - Math.min(width * SIDE_WALL_SHARE,
                    height * BACK_WALL_SHARE) * .5f, 0, Math.min(topWidth, topHeight) * .5f);
        }

        public float topX() { return left; }
        public float topY() { return top; }
        public float topWidth() { return topWidth; }
        public float topHeight() { return topHeight; }
        public float topRadius() { return topRadius; }
        public float backWall() { return backWall; }
        public float frontWall() { return frontWall; }
        public float sideWall() { return left; }
        /** Label bounds are exactly the dished top surface, never the sloped skirt. */
        public float labelX() { return left; }
        public float labelY() { return top; }
        public float labelWidth() { return topWidth; }
        public float labelHeight() { return topHeight; }

        public float shadowElevation() { return pressed ? .75f : 1.5f; }
        public float shadowSoftness() { return pressed ? 1.5f : 3f; }
        public float shadowAlpha() { return pressed ? .16f : .24f; }

        public float outerCoverage(float x, float y) {
            double d = MaterialLighting.distance(x, y, width, height, radius);
            return (float) Math.clamp(.5 - d, 0, 1);
        }

        /** HSL lightness points under the same upper-left light as the material depth pass. */
        public double lightingDelta(float x, float y) {
            double topDistance = roundedDistance(x, y);
            return topDistance <= 0 ? dishDelta(x) : wallDelta(x, y, topDistance);
        }

        /** A concave cylinder rises toward both X edges; its lit sides oppose a convex dome. */
        private double dishDelta(float x) {
            if (topWidth <= 0) return 0;
            double u = Math.clamp((x - left) / topWidth, 0, 1) * 2 - 1;
            double derivative = 4 * DISH_DEPTH * scale * u / topWidth;
            double invLength = 1 / Math.sqrt(derivative * derivative + 1);
            double nx = -derivative * invLength, nz = invLength;
            double dot = nx * MaterialLighting.LIGHT_X + nz * MaterialLighting.LIGHT_Z;
            return MaterialLighting.RIM_GAIN * (dot - MaterialLighting.LIGHT_Z);
        }

        private double wallDelta(float x, float y, double topDistance) {
            double cx = left + topWidth * .5, cy = top + topHeight * .5;
            double dx = x - cx, dy = y - cy;
            double qx = Math.abs(dx) - (topWidth * .5 - topRadius);
            double qy = Math.abs(dy) - (topHeight * .5 - topRadius);
            double ox = Math.max(qx, 0), oy = Math.max(qy, 0), length = Math.hypot(ox, oy);
            double gx, gy;
            if (length > 0) {
                gx = Math.copySign(ox / length, dx);
                gy = Math.copySign(oy / length, dy);
            } else if (qx > qy) {
                gx = Math.signum(dx);
                gy = 0;
            } else {
                gx = 0;
                gy = Math.signum(dy);
            }
            double verticalWall = gy < 0 ? backWall : frontWall;
            double sideWall = Math.max(1e-6, left);
            double wall = 1 / Math.sqrt(gx * gx / (sideWall * sideWall)
                    + gy * gy / Math.max(1e-6, verticalWall * verticalWall));
            // Every wall reaches the same cap height: the narrower back is steeper than the front.
            double slope = CAP_HEIGHT * scale / Math.max(1e-6, wall);
            double invLength = 1 / Math.sqrt(slope * slope + 1);
            double nx = gx * slope * invLength, ny = gy * slope * invLength, nz = invLength;
            double dot = nx * MaterialLighting.LIGHT_X + ny * MaterialLighting.LIGHT_Y
                    + nz * MaterialLighting.LIGHT_Z;
            return MaterialLighting.RIM_GAIN * (dot - MaterialLighting.LIGHT_Z);
        }

        private double roundedDistance(float x, float y) {
            double cx = left + topWidth * .5, cy = top + topHeight * .5;
            double qx = Math.abs(x - cx) - (topWidth * .5 - topRadius);
            double qy = Math.abs(y - cy) - (topHeight * .5 - topRadius);
            return Math.hypot(Math.max(qx, 0), Math.max(qy, 0))
                    + Math.min(Math.max(qx, qy), 0) - topRadius;
        }

        /** Shade one endpoint pixel and apply the outer skirt's anti-aliased coverage. */
        public int shade(int argb, float x, float y) {
            float coverage = outerCoverage(x, y);
            if (coverage <= 0 || (argb >>> 24) == 0) return 0;
            int lit = MaterialLighting.lightness(argb, (float) lightingDelta(x, y));
            return (lit & 0x00ffffff) | (Math.round((argb >>> 24) * coverage) << 24);
        }
    }
}
