package com.aurora.client.mixin;

import com.aurora.client.performance.EntityOcclusion;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Invalidates {@link EntityOcclusion}'s cached leash-holder set the instant a
 * leash link packet arrives, so the table never needs to be rescanned on every
 * render frame while no leashes change.
 */
@Mixin(ClientPacketListener.class)
public abstract class OcclusionLeashMixin {
    @Inject(method = "handleEntityLinkPacket", at = @At("TAIL"))
    private void aurora$occlusionLeashUpdated(ClientboundSetEntityLinkPacket packet, CallbackInfo ci) {
        EntityOcclusion.leashesChanged();
    }
}
