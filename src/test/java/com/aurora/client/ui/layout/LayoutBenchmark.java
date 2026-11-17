package com.aurora.client.ui.layout;

import com.aurora.client.ui.util.ClipBand;
import java.util.Arrays;

public final class LayoutBenchmark {
    private static volatile int checksum;

    private LayoutBenchmark() {}

    public static void main(String[] args) {
        for (int size : new int[]{40, 200, 1000}) {
            var layout = new ScreenLayout();
            var viewport = new ClipBand(40, 64, 250, 220);
            var keys = new String[size];
            for (int index = 0; index < size; index++) keys[index] = "row/" + index;
            for (int iteration = 0; iteration < 10000; iteration++) arrange(layout, viewport, keys, iteration);
            var samples = new long[10000];
            for (int iteration = 0; iteration < samples.length; iteration++) {
                long start = System.nanoTime();
                arrange(layout, viewport, keys, iteration);
                samples[iteration] = System.nanoTime() - start;
            }
            Arrays.sort(samples);
            double mean = Arrays.stream(samples).average().orElseThrow();
            System.out.printf("%d rows: mean %.2f us, p95 %.2f us%n", size, mean / 1000, samples[9499] / 1000.0);
        }
    }

    private static void arrange(ScreenLayout layout, ClipBand viewport, String[] keys, int iteration) {
        layout.begin(viewport);
        int offset = 0;
        for (int index = 0; index < keys.length; index++) {
            if ((index + iteration) % 8 == 0) continue;
            int height = index % 3 == 0 ? 28 : 18;
            layout.place(keys[index], keys[index], viewport.x, viewport.y + offset, viewport.width, height, true);
            offset += height + 2;
        }
        layout.finish(viewport, offset - 2);
        checksum = layout.focusCount() + (int) layout.maxScroll();
    }
}
