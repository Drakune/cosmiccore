package dev.cosmiccore.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Traegt nur die Animation von Spule und Kristall; der Zustand steckt in der PHASE des Blockstates. */
public class ResonatorBlockEntity extends BlockEntity {
    public ResonatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESONATOR.get(), pos, state);
    }
}
