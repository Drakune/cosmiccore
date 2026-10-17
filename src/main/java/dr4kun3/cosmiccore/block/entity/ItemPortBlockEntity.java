package dr4kun3.cosmiccore.block.entity;

import dr4kun3.cosmiccore.menu.ItemPortMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Item-Port: hat ein eigenes Lager so gross wie eine Truhe, in dem die Asteroiden-Beute landet (Rechtsklick oeffnet es).
 * Rohre und Trichter fuellen ueber ihn Brennstoff, Stabilisatoren und Module in den Controller und ziehen die Beute ab.
 */
public class ItemPortBlockEntity extends PortBlockEntity implements MenuProvider {
    public static final int STORAGE_SLOTS = 27;

    private final ItemStackHandler storage = new ItemStackHandler(STORAGE_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            int used = usedSlots();
            if (used != syncedUsed && level != null && !level.isClientSide) {
                syncedUsed = used;
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    };
    private final IItemHandler itemView = new View();
    private int syncedUsed;
    /** Nur Client: belegte Lagerplaetze (fuer das Hologramm). */
    private int clientUsed;

    /**
     * Legt den Blockinhalt des Item-Ports mit seinem Beutelager an.
     *
     * @param pos Position
     * @param state Blockzustand
     */
    public ItemPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_PORT.get(), pos, state);
    }

    /**
     * Sicht fuer Rohre: vorn die Eingaenge des Controllers (nur einfuegen), dahinter das Lager (nur entnehmen).
     *
     * @return die Item-Schnittstelle
     */
    public IItemHandler getItemView() {
        return itemView;
    }

    /**
     * Das Beutelager (27 Plaetze).
     *
     * @return das Lager
     */
    public ItemStackHandler getStorage() {
        return storage;
    }

    /**
     * Legt Beute ins Lager.
     *
     * @param stack einzulagernder Stapel
     * @return was nicht mehr hineinpasst (leer, wenn alles passte)
     */
    public ItemStack store(ItemStack stack) {
        ItemStack rest = stack;
        for (int i = 0; i < STORAGE_SLOTS && !rest.isEmpty(); i++) rest = storage.insertItem(i, rest, false);
        return rest;
    }

    /**
     * Zaehlt die belegten Lagerplaetze.
     *
     * @return Anzahl 0..27
     */
    public int usedSlots() {
        int n = 0;
        for (int i = 0; i < STORAGE_SLOTS; i++) if (!storage.getStackInSlot(i).isEmpty()) n++;
        return n;
    }

    /**
     * Client: belegte Plaetze laut Server (fuer die Anzeige auf der Station).
     *
     * @return Anzahl 0..27
     */
    public int getClientUsed() {
        return clientUsed;
    }

    /**
     * Laesst den ganzen Lagerinhalt als Items fallen und leert das Lager.
     *
     * @param level Welt
     * @param pos Position, an der die Items erscheinen
     */
    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack st = storage.getStackInSlot(i);
            if (!st.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, st.copy());
                storage.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /**
     * Fenstertitel des Lagers.
     *
     * @return "Item-Port"
     */
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cosmiccore.item_port");
    }

    /**
     * Oeffnet das Lager direkt (ohne Rueckweg zum Controller).
     *
     * @param id Fensternummer
     * @param inv Inventar des Spielers
     * @param player der Spieler
     * @return das Menue
     */
    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ItemPortMenu(id, inv, this, null);
    }

    /**
     * Daten fuer den Client: Controller-Position und Anzahl belegter Plaetze.
     *
     * @param registries Registry-Zugriff
     * @return die zu sendenden Daten
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("used", usedSlots());
        return tag;
    }

    /**
     * Speichert das Lager.
     *
     * @param tag Ziel der Daten
     * @param registries Registry-Zugriff
     */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("storage", storage.serializeNBT(registries));
    }

    /**
     * Laedt das Lager (und auf dem Client die Anzahl belegter Plaetze).
     *
     * @param tag gespeicherte Daten
     * @param registries Registry-Zugriff
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("storage")) storage.deserializeNBT(registries, tag.getCompound("storage"));
        if (tag.contains("used")) clientUsed = tag.getInt("used");
        syncedUsed = usedSlots();
    }

    /**
     * Sicht fuer Rohre: zuerst die Eingaenge des Controllers (nur einfuegen), dahinter das Lager (nur entnehmen).
     * Ohne gebildete Struktur bleibt das Lager trotzdem leerbar.
     */
    private final class View implements IItemHandler {
        private static final int INPUTS = ControllerBlockEntity.INVENTORY_SIZE;

        /**
         * Item-Eingaenge des angeschlossenen Controllers.
         *
         * @return die Schnittstelle oder {@code null} ohne gebildete Struktur
         */
        @Nullable
        private IItemHandler target() {
            ControllerBlockEntity c = controller();
            return c == null ? null : c.getItemView();
        }

        /**
         * Anzahl Plaetze: Eingaenge des Controllers plus Lager.
         *
         * @return Anzahl
         */
        @Override
        public int getSlots() {
            return INPUTS + STORAGE_SLOTS;
        }

        /**
         * Inhalt eines Platzes.
         *
         * @param slot Nummer (erst Eingaenge, dann Lager)
         * @return der Stapel
         */
        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot >= INPUTS) return storage.getStackInSlot(slot - INPUTS);
            IItemHandler t = target();
            return t == null ? ItemStack.EMPTY : t.getStackInSlot(slot);
        }

        /**
         * Einfuegen nur in die Eingaenge des Controllers (Brennstoff, Stabilisator).
         *
         * @param slot Nummer
         * @param stack einzufuegender Stapel
         * @param simulate nur pruefen
         * @return was nicht eingefuegt werden konnte
         */
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot >= INPUTS) return stack;
            IItemHandler t = target();
            return t == null ? stack : t.insertItem(slot, stack, simulate);
        }

        /**
         * Entnehmen nur aus dem Lager.
         *
         * @param slot Nummer
         * @param amount gewuenschte Menge
         * @param simulate nur pruefen
         * @return entnommener Stapel
         */
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= INPUTS ? storage.extractItem(slot - INPUTS, amount, simulate) : ItemStack.EMPTY;
        }

        /**
         * Groesste Menge je Platz.
         *
         * @param slot Nummer
         * @return Grenze
         */
        @Override
        public int getSlotLimit(int slot) {
            if (slot >= INPUTS) return storage.getSlotLimit(slot - INPUTS);
            IItemHandler t = target();
            return t == null ? 64 : t.getSlotLimit(slot);
        }

        /**
         * Darf das Item in diesen Platz (nur Eingaenge, nur passende Items)?
         *
         * @param slot Nummer
         * @param stack zu pruefender Stapel
         * @return {@code true}, wenn erlaubt
         */
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot >= INPUTS) return false;
            IItemHandler t = target();
            return t != null && t.isItemValid(slot, stack);
        }
    }
}
