package com.aurora.client.mixin;

import com.aurora.client.feature.impl.TotemPopFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forwards "totem of undying activation" entity events
 * (status byte {@value TotemPopFeature#TOTEM_ACTIVATION_STATUS}) into
 * {@link TotemPopFeature#recordPop(Entity)}.
 *
 * <p>Injected at HEAD so we observe the event regardless of whether
 * vanilla decides to play the sound/particle (e.g. if the entity has
 * already despawned client-side). The packet's
 * {@code getEntity(Level)} returns null for unknown entities, which we
 * tolerate by simply skipping the increment.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerEntityEventMixin {

    @Inject(method = "handleEntityEvent", at = @At("HEAD"), require = 1)
    private void aurora$watchTotemPop(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (packet.getEventId() != TotemPopFeature.TOTEM_ACTIVATION_STATUS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        // handleEntityEvent runs once on the network thread (which reschedules
        // itself onto the game thread via PacketUtils#ensureRunningOnSameThread
        // and then re-enters). Only count on the actual game-thread pass to
        // avoid double-incrementing every pop.
        if (!mc.isSameThread()) return;

        TotemPopFeature feat = TotemPopFeature.get();
        if (feat == null) return;

        ClientLevel level = mc.level;
        if (level == null) return;

        Entity e = packet.getEntity(level);
        if (e != null) feat.recordPop(e);
    }
}
