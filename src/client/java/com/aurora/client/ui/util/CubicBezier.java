package com.aurora.client.ui.util;

/** CSS timing: invert X(u)=elapsed, then evaluate Y(u). No overshoot. */
public record CubicBezier(double x1, double y1, double x2, double y2) {
    public static final CubicBezier EASE = new CubicBezier(.25, .1, .25, 1);
    public static final CubicBezier EASE_OUT = new CubicBezier(0, 0, .58, 1);
    public static final CubicBezier EMPHASIZED = new CubicBezier(.2, .8, .2, 1);

    public double at(double t) {
        if (!(t > 0)) return 0;
        if (t >= 1) return 1;
        double u = t, low = 0, high = 1;
        for (int i = 0; i < 24; i++) {
            double error = coordinate(u, x1, x2) - t;
            if (Math.abs(error) < 1e-9) break;
            if (error > 0) high = u; else low = u;
            double d = 3*(1-u)*(1-u)*x1 + 6*(1-u)*u*(x2-x1) + 3*u*u*(1-x2);
            double next = d > 1e-7 ? u - error/d : Double.NaN;
            u = next > low && next < high ? next : (low + high)/2;
        }
        return coordinate(u, y1, y2);
    }
    private static double coordinate(double u, double a, double b) {
        return 3*(1-u)*(1-u)*u*a + 3*(1-u)*u*u*b + u*u*u;
    }
}
