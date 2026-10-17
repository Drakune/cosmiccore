package dr4kun3.cosmiccore.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Kosmos-Laser: Vier davon rund um die Deckenmitte (Norden, Osten, Süden, Westen) bilden mit dem Reaktor ein
 * Laser-Array. Es verschmilzt optisch zu einem großen Geschütz, schießt selbstständig Asteroiden ab und speist
 * die Energie in den Puffer. Im Verbund werden die Blöcke zu kreisenden Energiepartikeln (siehe EnergyLaserFx).
 */
public class CosmicLaserBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 5, 15),
            Block.box(5, 5, 5, 11, 11, 11),
            Block.box(7, 11, 7, 9, 16, 9));
    /** Im Verbund: nur eine flache Rune auf der Decke (zum Abbauen anwählbar, ohne Kollision). */
    private static final VoxelShape RUNE = Block.box(3, 0, 3, 13, 1, 13);

    /**
     * Erzeugt den Kosmos-Laser (anfangs nicht verbunden).
     *
     * @param props Blockeigenschaften aus der Registrierung
     */
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
        return state.getValue(ModProperties.LINKED) ? RUNE : SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(ModProperties.LINKED) ? Shapes.empty() : SHAPE;
    }
}
