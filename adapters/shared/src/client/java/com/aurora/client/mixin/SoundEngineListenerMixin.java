package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.mojang.blaze3d.audio.ListenerTransform;
import net.minecraft.client.Camera;
import net.minecraft.client.sounds.SoundEngine;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sound Listener Throttle (ADR 0009): vanilla queues a listener update on
 * the sound thread every frame, two OpenAL calls each, even while the camera
 * is still. An update identical to the last one sent is dropped. The memory
 * is cleared whenever the sound library restarts, since that resets the
 * listener.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineListenerMixin {
    @Unique private @Nullable ListenerTransform aurora$lastSent;

    @Inject(method = "updateSource", at = @At("HEAD"), cancellable = true)
    private void aurora$skipUnchanged(Camera camera, CallbackInfo ci) {
        if (!AuroraConfig.get().perfSoundListenerThrottle || !camera.isInitialized()) {
            aurora$lastSent = null;
            return;
        }
        ListenerTransform last = aurora$lastSent;
        if (last != null && last.position().equals(camera.position())
                && aurora$same(last.forward(), camera.forwardVector()) && aurora$same(last.up(), camera.upVector())) {
            ci.cancel();
            return;
        }
        aurora$lastSent = new ListenerTransform(camera.position(),
                new net.minecraft.world.phys.Vec3(camera.forwardVector()), new net.minecraft.world.phys.Vec3(camera.upVector()));
    }

    @Unique
    private static boolean aurora$same(net.minecraft.world.phys.Vec3 sent, org.joml.Vector3fc now) {
        // Vanilla converts the float vector to doubles the same way, so equal floats give equal doubles.
        return sent.x == now.x() && sent.y == now.y() && sent.z == now.z();
    }

    @Inject(method = "loadLibrary", at = @At("HEAD"))
    private void aurora$forgetOnRestart(CallbackInfo ci) {
        aurora$lastSent = null;
    }
}
