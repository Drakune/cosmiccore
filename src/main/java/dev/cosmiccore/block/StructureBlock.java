package dev.cosmiccore.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Basis fuer alle passiven Strukturbloecke (Gehaeuse, Rahmen, Spule, Kristall). */
public class StructureBlock extends Block {
    public StructureBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(ModProperties.PHASE, CorePhase.DORMANT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ModProperties.PHASE);
    }
}
