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
    /**
     * Erzeugt den Item-Port (ruhend, keine Konsole, nichts angedockt).
     *
     * @param props Blockeigenschaften aus der Registrierung
     */
    public ItemPortBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(ModProperties.PHASE, CorePhase.DORMANT)
                .setValue(ModProperties.CONSOLE, ConsoleSide.NONE)
                .setValue(ModProperties.DOCKED, false));
    }

    /**
     * Zustaende: Reaktorphase, Konsolenseite und "angedockt" (Rohr oder Trichter daneben).
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
        return Terminal.portState(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos(), false);
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
        return Terminal.portState(state, level, pos, false);
    }

    /**
     * Der Item-Port hat einen Blockinhalt mit dem Beutelager (27 Plaetze).
     *
     * @param pos Position
     * @param state Blockzustand
     * @return neuer {@link dr4kun3.cosmiccore.block.entity.ItemPortBlockEntity}
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemPortBlockEntity(pos, state);
    }

    /**
     * Rechtsklick oeffnet das Beutelager.
     *
     * @param state Blockzustand
     * @param level Welt
     * @param pos Position des Ports
     * @param player klickender Spieler
     * @param hit getroffene Stelle
     * @return Erfolg
     */
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
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
}
