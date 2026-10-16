package dr4kun3.cosmiccore.block;

import dr4kun3.cosmiccore.block.entity.ControllerBlockEntity;
import dr4kun3.cosmiccore.block.entity.ModBlockEntities;
import dr4kun3.cosmiccore.data.ModDataMaps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ControllerBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ControllerBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ModProperties.PHASE, CorePhase.DORMANT)
                .setValue(ModProperties.TERMINAL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ModProperties.PHASE, ModProperties.TERMINAL);
    }

    private static final VoxelShape NODE = Block.box(3, 3, 3, 13, 13, 13);

    /** Bei gebildeter Struktur schwebt der Controller als Interface-Knoten - nur dieser ist anwaehlbar. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(ModProperties.PHASE) == CorePhase.DORMANT ? Shapes.block() : NODE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing)
                .setValue(ModProperties.TERMINAL, Terminal.isTerminal(ctx.getLevel(), ctx.getClickedPos(), facing));
    }

    /** Item-Port links und Energie-Port rechts daneben machen den Controller zur Steuerkonsole. */
    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks,
                                     BlockPos pos, Direction dir, BlockPos neighborPos, BlockState neighbor, net.minecraft.util.RandomSource random) {
        return state.setValue(ModProperties.TERMINAL, Terminal.isTerminal(level, pos, state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.CONTROLLER.get()) return null;
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
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hit) {
        boolean feedable = ModDataMaps.fuel(stack) != null || ModDataMaps.stabilizer(stack) != null;
        if (!feedable) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ControllerBlockEntity be) {
            if (be.getSize() == null) {
                player.openMenu(be, pos);
                return InteractionResult.SUCCESS;
            }
            ItemStack remainder = be.insertFromPlayer(stack.copy());
            int moved = stack.getCount() - remainder.getCount();
            if (moved > 0 && !player.getAbilities().instabuild) {
                stack.shrink(moved);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Rechtsklick oeffnet die GUI, Schleichen + Rechtsklick zeigt den Status/ersten Baufehler im Chat. */
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ControllerBlockEntity be) {
            if (player.isShiftKeyDown()) {
                be.sendStatus((ServerLevel) level, player);
            } else {
                player.openMenu(be, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
