package dr4kun3.cosmiccore.block.entity;

import dr4kun3.cosmiccore.CoreConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class EnergyPortBlockEntity extends PortBlockEntity {
    private final IEnergyStorage energyView = new View();

    public EnergyPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENERGY_PORT.get(), pos, state);
    }

    public IEnergyStorage getEnergyView() {
        return energyView;
    }

    /** Schiebt Energie aktiv in alle angrenzenden Energieempfaenger (Kabel, Maschinen, Akkus). */
    public static void serverTick(Level level, BlockPos pos, BlockState state, EnergyPortBlockEntity be) {
        ControllerBlockEntity c = be.controller();
        if (c == null) return;
        int rate = CoreConfig.PORT_TRANSFER.get();
        for (Direction dir : Direction.values()) {
            if (c.extractEnergy(rate, true) <= 0) return;
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
            if (target == null || !target.canReceive()) continue;
            int offered = c.extractEnergy(rate, true);
            int accepted = target.receiveEnergy(offered, false);
            if (accepted > 0) c.extractEnergy(accepted, false);
        }
    }

    private final class View implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            ControllerBlockEntity c = controller();
            return c == null ? 0 : c.extractEnergy(Math.min(toExtract, CoreConfig.PORT_TRANSFER.get()), simulate);
        }

        @Override
        public int getEnergyStored() {
            ControllerBlockEntity c = controller();
            return c == null ? 0 : (int) Math.min(Integer.MAX_VALUE, c.getEnergy());
        }

        @Override
        public int getMaxEnergyStored() {
            ControllerBlockEntity c = controller();
            return c == null ? 0 : (int) Math.min(Integer.MAX_VALUE, c.getCapacity());
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }
}
