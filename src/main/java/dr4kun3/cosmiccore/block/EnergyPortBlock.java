package dr4kun3.cosmiccore.block;

import dr4kun3.cosmiccore.block.entity.EnergyPortBlockEntity;
import dr4kun3.cosmiccore.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

public class EnergyPortBlock extends Block implements EntityBlock {
    /**
     * Erzeugt den Energie-Port (ruhend, keine Konsole, nichts angedockt).
     *
     * @param props Blockeigenschaften aus der Registrierung
     */
    public EnergyPortBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(ModProperties.PHASE, CorePhase.DORMANT)
                .setValue(ModProperties.CONSOLE, ConsoleSide.NONE)
                .setValue(ModProperties.DOCKED, false));
    }

    /**
     * Zustaende: Reaktorphase, Konsolenseite und "angedockt" (Kabel daneben).
     *
     * @param builder Bauplan der Zustaende
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ModProperties.PHASE, ModProperties.CONSOLE, ModProperties.DOCKED);
    }

    /**
     * Beim Setzen pruefen, ob der Port neben einem Controller Teil der Steuerkonsole wird.
     *
     * @param ctx Kontext des Setzens
     * @return der Startzustand
     */
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        return Terminal.portState(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos(), true);
    }

    /**
     * Passt den Zustand an, wenn sich ein Nachbar aendert: Teil der Steuerkonsole und Ausrichtung zum Controller.
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
                                     BlockPos pos, net.minecraft.core.Direction dir, BlockPos neighborPos, BlockState neighbor,
                                     net.minecraft.util.RandomSource random) {
        return Terminal.portState(state, level, pos, true);
    }

    /**
     * Der Energie-Port hat einen Blockinhalt, der Strom an Nachbarn abgibt.
     *
     * @param pos Position
     * @param state Blockzustand
     * @return neuer {@link EnergyPortBlockEntity}
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergyPortBlockEntity(pos, state);
    }

    /**
     * Nur auf dem Server: jeden Tick Energie an angeschlossene Kabel und Speicher abgeben.
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
        if (level.isClientSide || type != ModBlockEntities.ENERGY_PORT.get()) return null;
        return (lvl, pos, st, be) -> EnergyPortBlockEntity.serverTick(lvl, pos, st, (EnergyPortBlockEntity) be);
    }
}
