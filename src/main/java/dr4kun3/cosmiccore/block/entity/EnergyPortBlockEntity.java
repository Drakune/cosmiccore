package dr4kun3.cosmiccore.block.entity;

import dr4kun3.cosmiccore.transfer.*;

import dr4kun3.cosmiccore.CoreConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

public class EnergyPortBlockEntity extends PortBlockEntity {
    private final EnergyStore energyView = new View();

    /**
     * Legt den Blockinhalt des Energie-Ports an.
     *
     * @param pos Position
     * @param state Blockzustand
     */
    public EnergyPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENERGY_PORT.get(), pos, state);
    }

    /**
     * Energie-Schnittstelle fuer Kabel: gibt aus dem Puffer des Controllers ab, nimmt nichts an.
     *
     * @return die Sicht auf den Energiespeicher
     */
    public EnergyStore getEnergyView() {
        return energyView;
    }

    /**
     * Schiebt Energie aktiv in alle angrenzenden Energieempfaenger (Kabel, Maschinen, Akkus) - je Tick hoechstens die
     * eingestellte Port-Rate.
     *
     * @param level Server-Welt
     * @param pos Position des Ports
     * @param state Blockzustand
     * @param be der Port
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, EnergyPortBlockEntity be) {
        ControllerBlockEntity c = be.controller();
        if (c == null) return;
        int rate = CoreConfig.PORT_TRANSFER.get();
        for (Direction dir : Direction.values()) {
            if (c.extractEnergy(rate, true) <= 0) return;
            var handler = level.getCapability(Capabilities.Energy.BLOCK, pos.relative(dir), dir.getOpposite());
            if (handler == null) continue;
            int offered = c.extractEnergy(rate, true);
            int accepted;
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                accepted = handler.insert(offered, tx);
                tx.commit();
            }
            if (accepted > 0) c.extractEnergy(accepted, false);
        }
    }

    private final class View implements EnergyStore {
        /**
         * Der Port nimmt keine Energie an.
         *
         * @param toReceive angebotene Menge
         * @param simulate nur pruefen
         * @return immer 0
         */
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return 0;
        }

        /**
         * Entnimmt Energie aus dem Puffer des Controllers (hoechstens die Port-Rate).
         *
         * @param toExtract gewuenschte Menge in RF
         * @param simulate nur pruefen, nichts veraendern
         * @return tatsaechlich (oder moeglich) entnommene Menge
         */
        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            ControllerBlockEntity c = controller();
            return c == null ? 0 : c.extractEnergy(Math.min(toExtract, CoreConfig.PORT_TRANSFER.get()), simulate);
        }

        /**
         * Fuellstand des Controller-Puffers.
         *
         * @return RF (auf int begrenzt)
         */
        @Override
        public int getEnergyStored() {
            ControllerBlockEntity c = controller();
            return c == null ? 0 : (int) Math.min(Integer.MAX_VALUE, c.getEnergy());
        }

        /**
         * Kapazitaet des Controller-Puffers.
         *
         * @return RF (auf int begrenzt)
         */
        @Override
        public int getMaxEnergyStored() {
            ControllerBlockEntity c = controller();
            return c == null ? 0 : (int) Math.min(Integer.MAX_VALUE, c.getCapacity());
        }

        /**
         * Energie kann entnommen werden.
         *
         * @return immer {@code true}
         */
        @Override
        public boolean canExtract() {
            return true;
        }

        /**
         * Energie kann nicht eingespeist werden.
         *
         * @return immer {@code false}
         */
        @Override
        public boolean canReceive() {
            return false;
        }
    }
}
