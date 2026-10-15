package dr4kun3.cosmiccore.block;

import dr4kun3.cosmiccore.block.entity.ItemPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

public class ItemPortBlock extends Block implements EntityBlock {
    public ItemPortBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(ModProperties.PHASE, CorePhase.DORMANT)
                .setValue(ModProperties.CONSOLE, ConsoleSide.NONE)
                .setValue(ModProperties.DOCKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ModProperties.PHASE, ModProperties.CONSOLE, ModProperties.DOCKED);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        return Terminal.portState(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos(), false);
    }

    /** Nachbar geaendert: gehoert der Port (noch) zu einer Steuerkonsole? */
    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.core.Direction dir, BlockState neighbor,
                                     net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return Terminal.portState(state, level, pos, false);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemPortBlockEntity(pos, state);
    }

    /** Rechtsklick oeffnet das Beutelager. */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                                                                   net.minecraft.world.entity.player.Player player,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof dr4kun3.cosmiccore.block.entity.ItemPortBlockEntity be) {
            player.openMenu(be, buf -> {
                buf.writeBlockPos(pos);
                buf.writeBoolean(false);
            });
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Beim Abbauen faellt das Lager heraus. */
    @Override
    protected void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof dr4kun3.cosmiccore.block.entity.ItemPortBlockEntity be) {
            be.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
