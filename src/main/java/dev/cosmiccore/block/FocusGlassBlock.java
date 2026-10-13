package dev.cosmiccore.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class FocusGlassBlock extends TransparentBlock {
    public FocusGlassBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(ModProperties.PHASE, CorePhase.DORMANT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ModProperties.PHASE);
    }
}
