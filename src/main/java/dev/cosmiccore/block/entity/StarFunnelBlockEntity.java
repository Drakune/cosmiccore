package dev.cosmiccore.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Traegt nur die Wirbel-Animation des Sternentrichters. */
public class StarFunnelBlockEntity extends BlockEntity {
    public StarFunnelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STAR_FUNNEL.get(), pos, state);
    }
}
