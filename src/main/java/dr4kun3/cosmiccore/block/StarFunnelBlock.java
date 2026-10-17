package dr4kun3.cosmiccore.block;

import dr4kun3.cosmiccore.block.entity.ModBlockEntities;
import dr4kun3.cosmiccore.block.entity.StarFunnelBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Sternentrichter: wird auf die Deckenmitte des Reaktors gesetzt. Mit freiem Himmel darueber verdoppelt er den
 * Himmelsbonus und saugt sichtbar kosmische Energie aus dem All (Wirbel siehe StarFunnelRenderer).
 */
public class StarFunnelBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(4, 0, 4, 12, 4, 12),
            Block.box(2, 4, 2, 14, 9, 14),
            Block.box(0, 9, 0, 16, 13, 16));

    /**
     * Erzeugt den Sternentrichter (anfangs nicht mit einem Reaktor verbunden).
     *
     * @param props Blockeigenschaften aus der Registrierung
     */
    public StarFunnelBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(ModProperties.LINKED, false));
    }

    /**
     * Zustand LINKED: Teil eines gebildeten Reaktors - Modell ausgeblendet, der animierte Trichter wird gerendert.
     *
     * @param builder Bauplan der Zustaende
     */
    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ModProperties.LINKED);
    }

    /**
     * Umriss des Trichters (immer gleich).
     *
     * @param state Blockzustand
     * @param level Welt
     * @param pos Position
     * @param ctx Kontext der Abfrage
     * @return der Umriss
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /**
     * Der Trichter hat einen Blockinhalt fuer den Partikelsog und die Animation.
     *
     * @param pos Position des Trichters
     * @param state Blockzustand
     * @return neuer {@link StarFunnelBlockEntity}
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StarFunnelBlockEntity(pos, state);
    }

    /**
     * Nur auf dem Client: jeden Tick den Partikelsog ueber dem Trichter erzeugen.
     *
     * @param level Welt
     * @param state Blockzustand
     * @param type Typ des Blockinhalts
     * @param <T> Typ des Blockinhalts
     * @return Ticker auf dem Client, auf dem Server {@code null}
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() || type != ModBlockEntities.STAR_FUNNEL.get()) return null;
        return (lvl, pos, st, be) -> StarFunnelBlockEntity.clientTick(lvl, pos);
    }
}
