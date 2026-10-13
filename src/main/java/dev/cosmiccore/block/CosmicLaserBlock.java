package dev.cosmiccore.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Kosmos-Laser: Vier davon rund um die Deckenmitte (Norden, Osten, Sueden, Westen) bilden mit dem Reaktor ein
 * Laser-Array. Es verschmilzt optisch zu einem grossen Geschuetz, schiesst selbststaendig Asteroiden ab und speist
 * die Energie in den Puffer. Im Verbund ist nur die flache Plattform anfassbar.
 */
public class CosmicLaserBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 5, 15),
            Block.box(5, 5, 5, 11, 11, 11),
            Block.box(7, 11, 7, 9, 16, 9));
    private static final VoxelShape PLATFORM = Block.box(0, 0, 0, 16, 4, 16);

    public CosmicLaserBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(ModProperties.LINKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ModProperties.LINKED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(ModProperties.LINKED) ? PLATFORM : SHAPE;
    }
}
