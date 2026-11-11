package com.aurora.client.diagnostics;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

/**
 * Wraps an Aurora callback so its time counts toward {@link AuroraCost}
 * (ADR 0005). Use at registration: {@code EVENT.register(Timed.hud(r::render))}.
 * The wrapper is built once, so a timed callback allocates nothing per call.
 */
public final class Timed {
    private Timed() {}

    public static HudRenderCallback hud(HudRenderCallback body) {
        return (graphics, delta) -> {
            long t = AuroraCost.begin();
            try { body.onHudRender(graphics, delta); } finally { AuroraCost.end(t); }
        };
    }

    public static WorldRenderEvents.AfterEntities afterEntities(WorldRenderEvents.AfterEntities body) {
        return ctx -> {
            long t = AuroraCost.begin();
            try { body.afterEntities(ctx); } finally { AuroraCost.end(t); }
        };
    }

    public static WorldRenderEvents.BeforeBlockOutline beforeBlockOutline(WorldRenderEvents.BeforeBlockOutline body) {
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

    public static ClientTickEvents.EndWorldTick endWorldTick(ClientTickEvents.EndWorldTick body) {
        return level -> {
            long t = AuroraCost.begin();
            try { body.onEndTick(level); } finally { AuroraCost.end(t); }
        };
    }
}
