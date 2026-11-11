package com.aurora.client.diagnostics;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/**
 * Wraps an Aurora callback so its time counts toward {@link AuroraCost}
 * (ADR 0005). Use at registration: {@code HudElementRegistry.addLast(id, Timed.hud(r::render))}.
 * The wrapper is built once, so a timed callback allocates nothing per call.
 */
public final class Timed {
    private Timed() {}

    /** Deferred GPU work is submitted after GUI extraction and needs its own CPU bracket. */
    public static void glass(Runnable body) {
        long t = AuroraCost.begin();
        try { body.run(); } finally { AuroraCost.end(t); }
    }

    public static HudElement hud(HudElement body) {
        return (graphics, delta) -> {
            long t = AuroraCost.begin();
            try { body.extractRenderState(graphics, delta); } finally { AuroraCost.end(t); }
        };
    }

    public static LevelRenderEvents.AfterTranslucentFeatures afterFeatures(LevelRenderEvents.AfterTranslucentFeatures body) {
        return ctx -> {
            long t = AuroraCost.begin();
            try { body.afterTranslucentFeatures(ctx); } finally { AuroraCost.end(t); }
        };
    }

    public static LevelRenderEvents.BeforeBlockOutline beforeBlockOutline(LevelRenderEvents.BeforeBlockOutline body) {
        return (ctx, outline) -> {
            long t = AuroraCost.begin();
            try { return body.beforeBlockOutline(ctx, outline); } finally { AuroraCost.end(t); }
        };
    }

    public static ClientTickEvents.StartTick startTick(ClientTickEvents.StartTick body) {
        return client -> {
            long t = AuroraCost.begin();
            try { body.onStartTick(client); } finally { AuroraCost.end(t); }
        };
    }

    public static ClientTickEvents.EndTick endTick(ClientTickEvents.EndTick body) {
        return client -> {
            long t = AuroraCost.begin();
            try { body.onEndTick(client); } finally { AuroraCost.end(t); }
        };
    }

    public static ClientTickEvents.EndLevelTick endLevelTick(ClientTickEvents.EndLevelTick body) {
        return level -> {
            long t = AuroraCost.begin();
            try { body.onEndTick(level); } finally { AuroraCost.end(t); }
        };
    }
}
