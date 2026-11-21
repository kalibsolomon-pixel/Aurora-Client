package com.aurora.client.performance;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;

import java.util.Set;

/** The renderer's view of a terrain section, for {@link EntityOcclusion}. 26.3 binding. */
final class OcclusionTerrain {
    private static final Set<BlockEntityType<?>> CONTAINED = Set.of(
            BlockEntityTypes.CHEST, BlockEntityTypes.TRAPPED_CHEST, BlockEntityTypes.ENDER_CHEST,
            BlockEntityTypes.SHULKER_BOX, BlockEntityTypes.SIGN, BlockEntityTypes.HANGING_SIGN,
            BlockEntityTypes.SKULL, BlockEntityTypes.DECORATED_POT, BlockEntityTypes.CAMPFIRE, BlockEntityTypes.LECTERN,
            BlockEntityTypes.ENCHANTING_TABLE, BlockEntityTypes.BELL, BlockEntityTypes.BRUSHABLE_BLOCK,
            BlockEntityTypes.SHELF);

    private OcclusionTerrain() {}

    /** True for vanilla block entities whose renderer stays inside the block's 3³ neighbourhood. */
    static boolean contained(BlockEntityType<?> type) {
        return CONTAINED.contains(type);
    }

    /** How long a newly built section fades in, in milliseconds. */
    static long fadeMillis(Minecraft mc) {
        return Math.max(0L, (long) Math.ceil(mc.options.chunkSectionFadeInTime().get() * 1000.0));
    }

    /** The section holding {@code pos} has a mesh and has faded in far enough that vanilla draws its entities. */
    static boolean drawn(Minecraft mc, BlockPos pos, long fadeMillis) {
        return mc.levelRenderer.isSectionCompiledAndVisible(pos, fadeMillis);
    }
}
