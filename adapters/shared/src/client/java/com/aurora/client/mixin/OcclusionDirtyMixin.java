package com.aurora.client.mixin;

import com.aurora.client.performance.EntityOcclusion;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every client terrain change the renderer will rebuild a section for, so
 * {@link EntityOcclusion} trusts a section only after it has settled. These
 * {@code ClientLevel} methods hand the change to the level renderer (vanilla)
 * or its replacement (Sodium overwrites the receiving side, not these).
 */
@Mixin(ClientLevel.class)
public abstract class OcclusionDirtyMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void aurora$occlusionBlockUpdated(BlockPos pos, BlockState old, BlockState current, int flags, CallbackInfo ci) {
        EntityOcclusion.blockChanged((ClientLevel) (Object) this, pos);
    }

    @Inject(method = "setBlocksDirty", at = @At("HEAD"))
    private void aurora$occlusionBlocksDirty(BlockPos pos, BlockState old, BlockState current, CallbackInfo ci) {
        EntityOcclusion.blockChanged((ClientLevel) (Object) this, pos);
    }

    @Inject(method = "setSectionDirtyWithNeighbors", at = @At("HEAD"))
    private void aurora$occlusionSectionDirty(int x, int y, int z, CallbackInfo ci) {
        EntityOcclusion.sectionsChanged((ClientLevel) (Object) this, x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
    }

    @Inject(method = "setSectionRangeDirty", at = @At("HEAD"))
    private void aurora$occlusionSectionRange(int x0, int y0, int z0, int x1, int y1, int z1, CallbackInfo ci) {
        EntityOcclusion.sectionsChanged((ClientLevel) (Object) this, x0, y0, z0, x1, y1, z1);
    }
}
