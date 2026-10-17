package dr4kun3.cosmiccore.transfer;

import net.minecraft.world.item.ItemStack;

/** Inventar mit Slots (Ersatz fuer das ab 26.3 entfernte ItemHandler von NeoForge). */
public interface ItemHandler {
    /**
     * Anzahl Plaetze.
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
     * Fuegt ein und liefert den Rest, der nicht gepasst hat.
     *
     * @param slot Slot-Nummer
     * @param stack einzufuegender Stapel
     * @param simulate nur pruefen
     * @return der Rest
     */
    ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

    /**
     * Entnimmt bis zu {@code amount} Items.
     *
     * @param slot Slot-Nummer
     * @param amount gewuenschte Menge
     * @param simulate nur pruefen
     * @return entnommener Stapel
     */
    ItemStack extractItem(int slot, int amount, boolean simulate);

    /**
     * Groesste Menge je Platz.
     *
     * @param slot Slot-Nummer
     * @return Grenze
     */
    int getSlotLimit(int slot);

    /**
     * Darf das Item in diesen Platz?
     *
     * @param slot Slot-Nummer
     * @param stack zu pruefender Stapel
     * @return {@code true}, wenn erlaubt
     */
    boolean isItemValid(int slot, ItemStack stack);
}
