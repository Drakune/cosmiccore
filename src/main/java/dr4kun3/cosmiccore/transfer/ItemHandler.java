package dr4kun3.cosmiccore.transfer;

import net.minecraft.world.item.ItemStack;

/** Inventar mit Slots (Ersatz für das ab 26.3 entfernte ItemHandler von NeoForge). */
public interface ItemHandler {
    /**
     * Anzahl Plätze.
     *
     * @return Anzahl
     */
    int getSlots();

    /**
     * Inhalt eines Platzes.
     *
     * @param slot Slot-Nummer
     * @return der Stapel
     */
    ItemStack getStackInSlot(int slot);

    /**
     * Fügt ein und liefert den Rest, der nicht gepasst hat.
     *
     * @param slot Slot-Nummer
     * @param stack einzufügender Stapel
     * @param simulate nur prüfen
     * @return der Rest
     */
    ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

    /**
     * Entnimmt bis zu {@code amount} Items.
     *
     * @param slot Slot-Nummer
     * @param amount gewünschte Menge
     * @param simulate nur prüfen
     * @return entnommener Stapel
     */
    ItemStack extractItem(int slot, int amount, boolean simulate);

    /**
     * Größte Menge je Platz.
     *
     * @param slot Slot-Nummer
     * @return Grenze
     */
    int getSlotLimit(int slot);

    /**
     * Darf das Item in diesen Platz?
     *
     * @param slot Slot-Nummer
     * @param stack zu prüfender Stapel
     * @return {@code true}, wenn erlaubt
     */
    boolean isItemValid(int slot, ItemStack stack);
}
