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

    /**
     * Erzeugt den Controller (Blick nach Norden, ruhend, keine Konsole).
     *
     * @param props Blockeigenschaften aus der Registrierung
     */
    public ControllerBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ModProperties.PHASE, CorePhase.DORMANT)
                .setValue(ModProperties.TERMINAL, false));
    }

    /**
     * Zustaende: Blickrichtung, Reaktorphase und "Steuerkonsole" (Ports links und rechts).
     *
     * @param builder Bauplan der Zustaende
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ModProperties.PHASE, ModProperties.TERMINAL);
    }

    private static final VoxelShape NODE = Block.box(3, 3, 3, 13, 13, 13);

    /**
     * Ruhend ein voller Block; bei gebildeter Struktur schwebt der Controller als Interface-Knoten - nur dieser ist
     * anwaehlbar.
     *
     * @param state Blockzustand
     * @param level Welt
     * @param pos Position
     * @param ctx Kontext der Abfrage
     * @return der Umriss
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(ModProperties.PHASE) == CorePhase.DORMANT ? Shapes.block() : NODE;
    }

    /**
     * Der Controller schaut zum Spieler; stehen die Ports schon daneben, wird er gleich zur Konsole.
     *
     * @param ctx Kontext des Setzens
     * @return der Startzustand
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing)
                .setValue(ModProperties.TERMINAL, Terminal.isTerminal(ctx.getLevel(), ctx.getClickedPos(), facing));
    }

    /**
     * Passt den Zustand an, wenn sich ein Nachbar aendert: Konsolen-Form (Ports direkt neben dem Controller).
     *
     * @param state bisheriger Zustand
     * @param level Welt
     * @param ticks geplante Ticks
     * @param pos Position
     * @param dir Richtung zum Nachbarn
     * @param neighborPos Position des Nachbarn
     * @param neighbor Zustand des Nachbarn
     * @param random Zufall
     * @return der neue Zustand
     */
    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks,
                                     BlockPos pos, Direction dir, BlockPos neighborPos, BlockState neighbor, net.minecraft.util.RandomSource random) {
        return state.setValue(ModProperties.TERMINAL, Terminal.isTerminal(level, pos, state.getValue(FACING)));
    }

    /**
     * Der Controller hat den Blockinhalt mit der ganzen Reaktorlogik.
     *
     * @param pos Position
     * @param state Blockzustand
     * @return neuer {@link ControllerBlockEntity}
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ControllerBlockEntity(pos, state);
    }

    /**
     * Nur auf dem Server: jeden Tick die Reaktorlogik ausfuehren.
     *
     * @param level Welt
     * @param state Blockzustand
     * @param type Typ des Blockinhalts
     * @param <T> Typ des Blockinhalts
     * @return Ticker auf dem Server, auf dem Client {@code null}
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.CONTROLLER.get()) return null;
        return (lvl, pos, st, be) -> ControllerBlockEntity.serverTick(lvl, pos, st, (ControllerBlockEntity) be);
    }

    /**
     * Direkt nach dem Platzieren oeffnet sich die (einmalige) Wahl der Baugroesse.
     *
     * @param level Welt
     * @param pos Position
     * @param state Blockzustand
     * @param placer wer den Block gesetzt hat
     * @param stack das gesetzte Item
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof ControllerBlockEntity be && be.getSize() == null) {
            sp.openMenu(be, pos);
        }
    }

    /**
     * Rechtsklick mit Brennstoff oder Stabilisator fuettert den Kern direkt.
     *
     * @param stack Item in der Hand
     * @param state Blockzustand
     * @param level Welt
     * @param pos Position
     * @param player klickender Spieler
     * @param hand benutzte Hand
     * @param hit getroffene Stelle
     * @return Erfolg, wenn gefuettert wurde, sonst weiter zur normalen Benutzung
     */
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

    /**
     * Rechtsklick oeffnet das Fenster, Schleichen + Rechtsklick zeigt den Status bzw. den ersten Baufehler im Chat.
     *
     * @param state Blockzustand
     * @param level Welt
     * @param pos Position
     * @param player klickender Spieler
     * @param hit getroffene Stelle
     * @return Erfolg
     */
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
