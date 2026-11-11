package com.aurora.client.port;

/** Measures a plain string in the active font's logical pixels. */
@FunctionalInterface
public interface TextMetrics {
    int width(String text);
}
