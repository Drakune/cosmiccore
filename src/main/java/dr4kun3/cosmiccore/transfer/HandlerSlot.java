package dr4kun3.cosmiccore.transfer;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menue-Slot auf einem {@link ItemHandler} (Ersatz fuer das ab 26.3 entfernte HandlerSlot). */
public class HandlerSlot extends Slot {
    private static final SimpleContainer EMPTY = new SimpleContainer(0);
    private final ItemHandler handler;
    private final int index;

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
