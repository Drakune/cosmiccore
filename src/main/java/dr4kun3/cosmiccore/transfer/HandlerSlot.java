package dr4kun3.cosmiccore.transfer;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menü-Slot auf einem {@link ItemHandler} (Ersatz für das ab 26.3 entfernte HandlerSlot). */
public class HandlerSlot extends Slot {
    private static final SimpleContainer EMPTY = new SimpleContainer(0);
    private final ItemHandler handler;
    private final int index;

    /**
     * Legt einen Menü-Slot an, der auf einen Platz der Item-Schnittstelle zeigt.
     *
     * @param handler die Schnittstelle
     * @param index Platz in der Schnittstelle
     * @param x Position x im Fenster
     * @param y Position y im Fenster
     */
    public HandlerSlot(ItemHandler handler, int index, int x, int y) {
        super(EMPTY, index, x, y);
        this.handler = handler;
        this.index = index;
    }

    public ItemHandler getItemHandler() {
        return handler;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !stack.isEmpty() && handler.isItemValid(index, stack);
    }

    @Override
    public ItemStack getItem() {
        return handler.getStackInSlot(index);
    }

    /**
     * Setzt den Inhalt des Platzes.
     *
     * @param stack der neue Stapel
     */
    @Override
    public void set(ItemStack stack) {
        ((ModifiableItemHandler) handler).setStackInSlot(index, stack);
        setChanged();
    }

    @Override
    public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
        set(newStack);
    }

    @Override
    public void setChanged() {}

    @Override
    public int getMaxStackSize() {
        return handler.getSlotLimit(index);
    }

    /**
     * Größte Menge dieses Items im Platz (durch Probe-Einfügen ermittelt).
     *
     * @param stack das Item
     * @return Grenze
     */
    @Override
    public int getMaxStackSize(ItemStack stack) {
        ItemStack max = stack.copyWithCount(stack.getMaxStackSize());
        ItemStack current = handler.getStackInSlot(index);
        if (handler instanceof ModifiableItemHandler m) {
            m.setStackInSlot(index, ItemStack.EMPTY);
            ItemStack rest = m.insertItem(index, max, true);
            m.setStackInSlot(index, current);
            return max.getCount() - rest.getCount();
        }
        ItemStack rest = handler.insertItem(index, max, true);
        return current.getCount() + max.getCount() - rest.getCount();
    }

    @Override
    public boolean mayPickup(Player player) {
        return !handler.extractItem(index, 1, true).isEmpty();
    }

    @Override
    public ItemStack remove(int amount) {
        return handler.extractItem(index, amount, false);
    }
}
