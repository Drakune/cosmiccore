package dr4kun3.cosmiccore.transfer;

/** Energiespeicher in RF (Ersatz fuer das ab 26.3 entfernte EnergyStore). */
public interface EnergyStore {
    int receiveEnergy(int amount, boolean simulate);

    int extractEnergy(int amount, boolean simulate);

    int getEnergyStored();

    int getMaxEnergyStored();

    boolean canExtract();

    boolean canReceive();
}
