package dr4kun3.cosmiccore.block;

import dr4kun3.cosmiccore.block.entity.ModBlockEntities;
import dr4kun3.cosmiccore.block.entity.SingularityCoreBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class SingularityCoreBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(4, 4, 4, 12, 12, 12);

    /**
     * Erzeugt den Singularitätskern (ruhend).
     *
     * @param props Blockeigenschaften aus der Registrierung
     */
    public SingularityCoreBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(ModProperties.PHASE, CorePhase.DORMANT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ModProperties.PHASE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /**
     * Der verschmolzene Mittelkern des ultimativen Kerns besteht aus den vier Satelliten und droppt nichts.
     *
     * @param state Blockzustand
     * @param params Parameter der Loot-Berechnung (mit Blockinhalt)
     * @return die Drops, beim verschmolzenen Kern keine
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state,
            LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof SingularityCoreBlockEntity core && core.isFused()) {
            return List.of();
        }
        return super.getDrops(state, params);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SingularityCoreBlockEntity(pos, state);
    }

    /**
     * Nur auf dem Client: Partikel und Klänge des Kerns.
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
        if (!level.isClientSide() || type != ModBlockEntities.CORE.get()) return null;
        return (lvl, pos, st, be) -> SingularityCoreBlockEntity.clientTick(lvl, pos, st, (SingularityCoreBlockEntity) be);
    }
}
