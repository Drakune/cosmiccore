package dr4kun3.cosmiccore.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Basis fuer alle passiven Strukturbloecke (Gehaeuse, Rahmen, Spule, Kristall). */
public class StructureBlock extends Block {
    /**
     * Erzeugt einen Strukturblock (ruhend, sichtbar).
     *
     * @param props Blockeigenschaften aus der Registrierung
     */
    public StructureBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(ModProperties.PHASE, CorePhase.DORMANT)
                .setValue(ModProperties.PHANTOM, false));
    }

    /**
     * Zustaende: Reaktorphase und "Phantom" (in der Kammer aufgegangen).
     *
     * @param builder Bauplan der Zustaende
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ModProperties.PHASE, ModProperties.PHANTOM);
    }

    /**
     * Als Phantom ohne Umriss, sonst ein voller Block.
     *
     * @param state Blockzustand
     * @param level Welt
     * @param pos Position
     * @param ctx Kontext der Abfrage
     * @return der Umriss
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(ModProperties.PHANTOM) ? Shapes.empty() : super.getShape(state, level, pos, ctx);
    }

    /**
     * Als Phantom ohne Kollision, sonst ein voller Block.
     *
     * @param state Blockzustand
     * @param level Welt
     * @param pos Position
     * @param ctx Kontext der Abfrage
     * @return die Kollisionsform
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(ModProperties.PHANTOM) ? Shapes.empty() : super.getCollisionShape(state, level, pos, ctx);
    }
}
