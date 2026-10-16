package dr4kun3.cosmiccore.transfer;

import net.minecraft.world.item.ItemStack;

/** Inventar, dessen Slots direkt gesetzt werden duerfen (fuer Menues). */
public interface ModifiableItemHandler extends ItemHandler {
    void setStackInSlot(int slot, ItemStack stack);
}
