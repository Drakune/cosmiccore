package dr4kun3.cosmiccore.transfer;

/** Energiespeicher in RF (Ersatz für das ab 26.3 entfernte EnergyStore). */
public interface EnergyStore {
    /**
     * Speist Energie ein.
     *
     * @param amount angebotene Menge in RF
     * @param simulate nur prüfen
     * @return aufgenommene Menge
     */
    int receiveEnergy(int amount, boolean simulate);

    /**
     * Entnimmt Energie.
     *
     * @param amount gewünschte Menge in RF
     * @param simulate nur prüfen
     * @return entnommene Menge
     */
    int extractEnergy(int amount, boolean simulate);

    /**
     * Gespeicherte Energie.
     *
     * @return Energie in RF
     */
    int getEnergyStored();

    /**
     * Kapazität.
     *
     * @return Kapazität in RF
     */
    int getMaxEnergyStored();

    /**
     * Kann Energie entnommen werden?
     *
     * @return {@code true}, wenn ja
     */
    boolean canExtract();

    /**
     * Kann Energie eingespeist werden?
     *
     * @return {@code true}, wenn ja
     */
    boolean canReceive();
}
