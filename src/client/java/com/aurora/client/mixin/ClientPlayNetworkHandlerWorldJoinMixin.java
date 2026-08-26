package com.aurora.client.mixin;

import com.aurora.client.feature.impl.EntityMovementSmoother;
import com.aurora.client.feature.impl.HitboxPositionSmoother;
import com.aurora.client.feature.impl.ThrottleDetector;
import com.aurora.client.hud.HitboxRenderer;
import com.aurora.client.util.AttackedPlayerTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Resets Aurora's per-world transient caches on world join and respawn so
 * stale state from a previous server/world/dimension never leaks into the
 * new one.
 *
 * <p>This is the single chokepoint for the "leave the world tidy" contract.
 * Several renderers and feature helpers keep process-global static maps
 * keyed by entity UUID (hitbox position smoother, entity rotation smoother,
 * throttle detector's per-entity movement cache) plus a frame-timing clock
 * in {@link HitboxRenderer}. Because those values outlive any one
 * {@code ClientLevel}, without an explicit reset they carry position /
 * rotation / throttle measurements across disconnects, server switches and
 * dimension changes.
 *
 * <p>The visible symptom of that leakage was block outlines / overlays and
 * especially the <b>targeted hitbox</b> behaving oddly when rejoining or
 * switching worlds (e.g. "launching another instance" / reconnecting): the
 * hitbox position smoother would ease from a stale previous-world position
 * toward the fresh one on the first frames (read as the box sliding or
 * snapping in from nowhere), and the throttle detector's bundling factor
 * could be poisoned by stale per-entity cadence samples -- which in turn
 * scaled the smoothing incorrectly. {@code handleLogin} covers every
 * server/singleplayer join; {@code handleRespawn} covers death-respawn and
 * dimension travel within the same server (where the connection stays up
 * but entities are rebuilt).
 *
 * <p>{@link AttackedPlayerTracker} was already cleared here; the render
 * caches are now cleared from the same spot.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerWorldJoinMixin {

    @Inject(method = "handleLogin", at = @At("HEAD"), require = 1)
    private void aurora$clearOnJoin(ClientboundLoginPacket packet, CallbackInfo ci) {
        aurora$resetRenderCaches();
        AttackedPlayerTracker.clear();
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"), require = 1)
    private void aurora$clearOnRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        aurora$resetRenderCaches();
        AttackedPlayerTracker.clear();
    }

    /**
     * Drop every process-global per-entity render cache so the new world
     * starts from a clean slate. Safe to call repeatedly; all clears are
     * idempotent.
     */
    private static void aurora$resetRenderCaches() {
        // Hitbox position smoother: stale (x,y,z) per UUID would otherwise
        // ease from the old world's location on the first rendered frames.
        HitboxPositionSmoother.clearAll();
        // Entity movement smoother: same reason — drop stale per-entity body
        // positions so entities don't slide in from their old-world location
        // on the first rendered frames after join/respawn/dimension change.
        EntityMovementSmoother.clearAll();
        // Throttle detector: stale per-entity movement samples and the
        // smoothed bundling factor must not bleed across worlds, otherwise
        // the hitbox smoothing strength is wrong on rejoin.
        ThrottleDetector.onWorldChange();
        // Reset the frame-rate-independent easing clock so a stale timestamp
        // from the previous world doesn't poison the first frame's dtSec.
        HitboxRenderer.resetFrameTiming();
    }
}