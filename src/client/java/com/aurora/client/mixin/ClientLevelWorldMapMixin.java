package com.aurora.client.mixin;

import com.aurora.client.worldmap.WorldMapClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client block-change funnel for the world map.
 *
 * <p>Fabric exposes no client "block updated" event, but every client-side
 * block change funnels through {@code Level.setBlock} → the virtual
 * {@code setBlocksDirty(BlockPos, BlockState, BlockState)} — verified
 * against the 1.21.11 bytecode. {@code ClientLevel} overrides it, so this
 * single injection point sees chunk packet applications, block updates,
 * piston moves, and everything else that mutates client terrain.
 *
 * <p>The handler is a static no-allocation call into {@link WorldMapClient}
 * which queues the containing chunk for re-capture (deduped by the capture
 * queue). The fast path when the world map is disabled or out-of-context is
 * a single volatile boolean read.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelWorldMapMixin {

    @Inject(method = "setBlocksDirty", at = @At("HEAD"), require = 1)
    private void aurora$onBlocksDirty(BlockPos pos, BlockState oldState, BlockState newState,
                                      CallbackInfo ci) {
        WorldMapClient.onBlockChanged((ClientLevel) (Object) this, pos);
    }
}
