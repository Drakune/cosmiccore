package dr4kun3.cosmiccore.block.entity;

import dr4kun3.cosmiccore.CoreConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class EnergyPortBlockEntity extends PortBlockEntity {
    /** Messfenster fuer den Durchfluss in Ticks (zugleich Abstand der Aktualisierungen an die Clients). */
    private static final int WINDOW = 10;

    private final IEnergyStorage energyView = new View();
    /** Im laufenden Messfenster abgegebene Energie (Server). */
    private long moved;
    private int windowTick;
    private int syncedFlow = -1;
    private long syncedStored = -1, syncedCapacity = -1;
    /** Vom Server gemeldete Werte fuer die Anzeige (Client). */
    private int clientFlow;
    private long clientStored, clientCapacity;

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
    public IEnergyStorage getEnergyView() {
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
        if (c != null) {
            int rate = CoreConfig.PORT_TRANSFER.get();
            for (Direction dir : Direction.values()) {
                if (c.extractEnergy(rate, true) <= 0) break;
                IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
                if (target == null || !target.canReceive()) continue;
                int offered = c.extractEnergy(rate, true);
                int accepted = target.receiveEnergy(offered, false);
                if (accepted > 0) be.moved += c.extractEnergy(accepted, false);
            }
        }
        if (++be.windowTick >= WINDOW) {
            be.windowTick = 0;
            be.publish(c);
        }
    }

    /**
     * Schliesst ein Messfenster ab und meldet Durchfluss und Pufferstand an die Clients - nur bei sichtbarer
     * Aenderung (Durchfluss anders oder Puffer um mehr als 0,1 % verschoben).
     *
     * @param c der verbundene Controller oder {@code null}
     */
    private void publish(ControllerBlockEntity c) {
        int flow = (int) Math.min(Integer.MAX_VALUE, moved / WINDOW);
        moved = 0;
        long stored = c == null ? 0 : c.getEnergy(), capacity = c == null ? 0 : c.getCapacity();
        boolean changed = flow != syncedFlow || capacity != syncedCapacity
                || Math.abs(stored - syncedStored) > Math.max(1L, capacity / 1000L);
        if (!changed || level == null) return;
        syncedFlow = flow;
        syncedStored = stored;
        syncedCapacity = capacity;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /**
     * Client: abgegebene Leistung laut Server (Mittel ueber eine halbe Sekunde).
     *
     * @return RF pro Tick
     */
    public int getClientFlow() {
        return clientFlow;
    }

    /**
     * Client: gespeicherte Energie des Reaktors laut Server.
     *
     * @return Energie in RF
     */
    public long getClientStored() {
        return clientStored;
    }

    /**
     * Client: Kapazitaet des Puffers laut Server.
     *
     * @return Kapazitaet in RF
     */
    public long getClientCapacity() {
        return clientCapacity;
    }

    /**
     * Daten fuer den Client: Controller-Position, Durchfluss und Pufferstand.
     *
     * @param registries Registry-Zugriff
     * @return die zu sendenden Daten
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("flow", Math.max(0, syncedFlow));
        tag.putLong("stored", Math.max(0, syncedStored));
        tag.putLong("capacity", Math.max(0, syncedCapacity));
        return tag;
    }

    /**
     * Laedt die Controller-Position und auf dem Client die Anzeigewerte.
     *
     * @param input gespeicherte bzw. empfangene Daten
     */
    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        clientFlow = input.getIntOr("flow", clientFlow);
        clientStored = input.getLongOr("stored", clientStored);
        clientCapacity = input.getLongOr("capacity", clientCapacity);
    }

    private final class View implements IEnergyStorage {
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
            if (c == null) return 0;
            int out = c.extractEnergy(Math.min(toExtract, CoreConfig.PORT_TRANSFER.get()), simulate);
            if (!simulate) moved += out;
            return out;
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
