package dr4kun3.cosmiccore.transfer;

import net.minecraft.world.item.ItemStack;

/** Inventar, dessen Slots direkt gesetzt werden dürfen (für Menüs). */
public interface ModifiableItemHandler extends ItemHandler {
    /**
     * Setzt den Inhalt eines Platzes direkt.
     *
     * @param slot Slot-Nummer
     * @param stack der neue Stapel
     */
    void setStackInSlot(int slot, ItemStack stack);
}
