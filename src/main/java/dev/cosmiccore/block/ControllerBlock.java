package dev.cosmiccore.block;

import dev.cosmiccore.block.entity.ControllerBlockEntity;
import dev.cosmiccore.block.entity.ModBlockEntities;
import dev.cosmiccore.data.ModDataMaps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ControllerBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ControllerBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ModProperties.PHASE, CorePhase.DORMANT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ModProperties.PHASE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.CONTROLLER.get()) return null;
        return (lvl, pos, st, be) -> ControllerBlockEntity.serverTick(lvl, pos, st, (ControllerBlockEntity) be);
    }

    /** Direkt nach dem Platzieren oeffnet sich die (einmalige) Wahl der Baugroesse. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof ControllerBlockEntity be && be.getSize() == null) {
            sp.openMenu(be, pos);
        }
    }

    /** Rechtsklick mit Brennstoff oder Stabilisator fuettert den Kern. */
    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hit) {
        boolean feedable = ModDataMaps.fuel(stack) != null || ModDataMaps.stabilizer(stack) != null;
        if (!feedable) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ControllerBlockEntity be) {
            if (be.getSize() == null) {
                player.openMenu(be, pos);
                return ItemInteractionResult.SUCCESS;
            }
            ItemStack remainder = be.insertFromPlayer(stack.copy());
            int moved = stack.getCount() - remainder.getCount();
            if (moved > 0 && !player.getAbilities().instabuild) {
                stack.shrink(moved);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Rechtsklick oeffnet die GUI, Schleichen + Rechtsklick zeigt den Status/ersten Baufehler im Chat. */
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ControllerBlockEntity be) {
            if (player.isShiftKeyDown()) {
                be.sendStatus((ServerLevel) level, player);
            } else {
                player.openMenu(be, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl
                && level.getBlockEntity(pos) instanceof ControllerBlockEntity be) {
            be.onControllerRemoved(sl, state);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
