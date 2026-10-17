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

    /**
     * Legt einen Menue-Slot an, der auf einen Platz der Item-Schnittstelle zeigt.
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

    /**
     * Die zugrunde liegende Schnittstelle.
     *
     * @return die Schnittstelle
     */
    public ItemHandler getItemHandler() {
        return handler;
    }

    /**
     * Darf das Item hineingelegt werden?
     *
     * @param stack das Item
     * @return {@code true}, wenn erlaubt
     */
    @Override
    public boolean mayPlace(ItemStack stack) {
        return !stack.isEmpty() && handler.isItemValid(index, stack);
    }

    /**
     * Inhalt des Platzes.
     *
     * @return der Stapel
     */
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

    /**
     * Vom Spieler gesetzt - wie {@link #set}.
     *
     * @param newStack neuer Stapel
     * @param oldStack bisheriger Stapel
     */
    @Override
    public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
        set(newStack);
    }

    /** Nichts zu tun - die Schnittstelle meldet Aenderungen selbst. */
    @Override
    public void setChanged() {}

    /**
     * Groesste Menge des Platzes.
     *
     * @return Grenze
     */
    @Override
    public int getMaxStackSize() {
        return handler.getSlotLimit(index);
    }

    /**
     * Groesste Menge dieses Items im Platz (durch Probe-Einfuegen ermittelt).
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

    /**
     * Darf der Spieler etwas herausnehmen?
     *
     * @param player der Spieler
     * @return {@code true}, wenn die Schnittstelle Entnahme erlaubt
     */
    @Override
    public boolean mayPickup(Player player) {
        return !handler.extractItem(index, 1, true).isEmpty();
    }

    /**
     * Nimmt Items heraus.
     *
     * @param amount Menge
     * @return entnommener Stapel
     */
    @Override
    public ItemStack remove(int amount) {
        return handler.extractItem(index, amount, false);
    }
}
