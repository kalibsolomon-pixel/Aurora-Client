package com.aurora.client.hud;

import java.util.Arrays;

/**
 * The union of axis-aligned rectangles as rectangles that never overlap. An
 * inverting blend (the crosshair's Vanilla Blending) flips a pixel back each
 * time it is covered again, so a shape drawn that way must cover each pixel
 * once.
 */
public final class RectUnion {
    private RectUnion() {}

    /**
     * @param rects {@code x0, y0, x1, y1} per rectangle, half-open; empty ones are ignored
     * @return the same coverage as disjoint rectangles in the same layout, top to bottom
     */
    public static int[] of(int[] rects) {
        int n = rects.length / 4;
        int[] ys = new int[n * 2];
        for (int i = 0; i < n; i++) {
            ys[i * 2] = rects[i * 4 + 1];
            ys[i * 2 + 1] = rects[i * 4 + 3];
        }
        Arrays.sort(ys);
        int[] out = new int[0];
        int count = 0;
        int[] spans = new int[n * 2];
        for (int b = 0; b + 1 < ys.length; b++) {
            int top = ys[b], bottom = ys[b + 1];
            if (top == bottom) continue;
            int s = 0;
            for (int i = 0; i < n; i++) {
                int x0 = rects[i * 4], y0 = rects[i * 4 + 1], x1 = rects[i * 4 + 2], y1 = rects[i * 4 + 3];
                if (x0 < x1 && y0 <= top && y1 >= bottom) {
                    spans[s++] = x0;
                    spans[s++] = x1;
                }
            }
            sortPairs(spans, s);
            for (int i = 0; i < s; ) {
                int x0 = spans[i], x1 = spans[i + 1];
                i += 2;
                while (i < s && spans[i] <= x1) {
                    x1 = Math.max(x1, spans[i + 1]);
                    i += 2;
                }
                if (count + 4 > out.length) out = Arrays.copyOf(out, Math.max(16, out.length * 2));
                out[count++] = x0;
                out[count++] = top;
                out[count++] = x1;
                out[count++] = bottom;
            }
        }
        return Arrays.copyOf(out, count);
    }

    /** Insertion sort of (start, end) pairs by start; the inputs here are a handful of spans. */
    private static void sortPairs(int[] spans, int length) {
        for (int i = 2; i < length; i += 2) {
            int a = spans[i], b = spans[i + 1];
            int j = i - 2;
            while (j >= 0 && spans[j] > a) {
                spans[j + 2] = spans[j];
                spans[j + 3] = spans[j + 1];
                j -= 2;
            }
            spans[j + 2] = a;
            spans[j + 3] = b;
        }
    }
}
