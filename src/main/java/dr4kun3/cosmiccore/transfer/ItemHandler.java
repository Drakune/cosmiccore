package dr4kun3.cosmiccore.transfer;

import net.minecraft.world.item.ItemStack;

/** Inventar mit Slots (Ersatz fuer das ab 26.3 entfernte ItemHandler von NeoForge). */
public interface ItemHandler {
    int getSlots();

    ItemStack getStackInSlot(int slot);

    /** Fuegt ein und liefert den Rest, der nicht gepasst hat. */
    ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

    /** Entnimmt bis zu {@code amount} Items. */
    ItemStack extractItem(int slot, int amount, boolean simulate);

    int getSlotLimit(int slot);

    boolean isItemValid(int slot, ItemStack stack);
}
