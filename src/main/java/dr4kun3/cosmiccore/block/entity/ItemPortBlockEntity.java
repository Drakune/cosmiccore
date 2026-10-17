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
 * Item-Port: hat ein eigenes Lager so groß wie eine Truhe, in dem die Asteroiden-Beute landet (Rechtsklick öffnet es).
 * Rohre und Trichter füllen über ihn Brennstoff, Stabilisatoren und Module in den Controller und ziehen die Beute ab.
 */
public class ItemPortBlockEntity extends PortBlockEntity implements MenuProvider {
    public static final int STORAGE_SLOTS = 27;

    private final ItemStackHandler storage = new ItemStackHandler(STORAGE_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            // die Stationen zeigen jeden Platz mit Item und Anzahl - bei jeder sichtbaren Änderung neu senden,
            // beim gemeinsamen Lager an allen Item-Ports der Struktur
            if (level == null || level.isClientSide) return;
            ControllerBlockEntity c = controller();
            if (c == null) {
                refreshClients();
            } else {
                for (ItemPortBlockEntity ip : c.itemPorts()) ip.refreshClients();
            }
        }
    };
    private final IItemHandler itemView = new View();
    /** Prüfwert des zuletzt an die Clients gesendeten Lagerinhalts. */
    private int syncedSignature;
    /** Nur Client: belegte Lagerplätze (für das Hologramm). */
    private int clientUsed;
    /** Nur Client: Lagerinhalt laut Server (für die Anzeige auf der Station). */
    private final ItemStackHandler clientView = new ItemStackHandler(STORAGE_SLOTS);

    /**
     * Legt den Blockinhalt des Item-Ports mit seinem Beutelager an.
     *
     * @param pos Position
     * @param state Blockzustand
     */
    public ItemPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_PORT.get(), pos, state);
    }

    public IItemHandler getItemView() {
        return itemView;
    }

    public ItemStackHandler getStorage() {
        return active();
    }

    /**
     * Das Lager, auf das dieser Port gerade zugreift: in einer gebildeten Struktur das gemeinsame Lager aller
     * Item-Ports (es liegt im ersten Port der Struktur), sonst das eigene.
     *
     * @return das Lager
     */
    private ItemStackHandler active() {
        if (level != null && !level.isClientSide) {
            ControllerBlockEntity c = controller();
            ItemPortBlockEntity shared = c == null ? null : c.sharedItemPort();
            if (shared != null && shared != this) return shared.storage;
        }
        return storage;
    }

    /**
     * Gibt den eigenen Lagerinhalt an das gemeinsame Lager ab; was dort keinen Platz hat, fällt am Port heraus.
     *
     * @param shared der Port mit dem gemeinsamen Lager
     */
    void moveInto(ItemPortBlockEntity shared) {
        if (shared == this || level == null) return;
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack st = storage.getStackInSlot(i);
            if (st.isEmpty()) continue;
            storage.setStackInSlot(i, ItemStack.EMPTY);
            ItemStack rest = shared.store(st.copy());
            if (!rest.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, rest);
        }
    }

    /**
     * Schickt den Lagerinhalt an die Clients, wenn er sich seit dem letzten Senden sichtbar geändert hat.
     */
    void refreshClients() {
        if (level == null || level.isClientSide) return;
        int signature = contentSignature();
        if (signature == syncedSignature) return;
        syncedSignature = signature;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /**
     * Legt Beute ins Lager.
     *
     * @param stack einzulagernder Stapel
     * @return was nicht mehr hineinpasst (leer, wenn alles passte)
     */
    public ItemStack store(ItemStack stack) {
        ItemStack rest = stack;
        ItemStackHandler target = active();
        for (int i = 0; i < STORAGE_SLOTS && !rest.isEmpty(); i++) rest = target.insertItem(i, rest, false);
        return rest;
    }

    /**
     * Zählt die belegten Lagerplätze.
     *
     * @return Anzahl 0..27
     */
    public int usedSlots() {
        int n = 0;
        ItemStackHandler source = active();
        for (int i = 0; i < STORAGE_SLOTS; i++) if (!source.getStackInSlot(i).isEmpty()) n++;
        return n;
    }

    public int getClientUsed() {
        return clientUsed;
    }

    /**
     * Bevor der Port verschwindet: Lagerinhalt fallen lassen.
     *
     * @param pos Position
     * @param state bisheriger Blockzustand
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) dropContents(level, pos);
    }

    public ItemStack getClientStack(int slot) {
        return clientView.getStackInSlot(slot);
    }

    /**
     * Prüfwert über Items und Anzahlen aller Lagerplätze (um Änderungen zu erkennen).
     *
     * @return Hashwert
     */
    private int contentSignature() {
        int h = 1;
        ItemStackHandler source = active();
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack st = source.getStackInSlot(i);
            h = h * 31 + (st.isEmpty() ? 0 : st.getItem().hashCode() * 101 + st.getCount());
        }
        return h;
    }

    /**
     * Lässt den ganzen Lagerinhalt als Items fallen und leert das Lager.
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

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cosmiccore.item_port");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ItemPortMenu(id, inv, this, null);
    }

    /**
     * Daten für den Client: Controller-Position und Anzahl belegter Plätze.
     *
     * @param registries Registry-Zugriff
     * @return die zu sendenden Daten
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("used", usedSlots());
        tag.put("view", active().serializeNBT(registries));
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
     * Lädt das Lager (und auf dem Client die Anzahl belegter Plätze).
     *
     * @param tag gespeicherte Daten
     * @param registries Registry-Zugriff
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("storage")) storage.deserializeNBT(registries, tag.getCompoundOrEmpty("storage"));
        if (tag.contains("used")) clientUsed = tag.getIntOr("used", 0);
        if (tag.contains("view")) clientView.deserializeNBT(registries, tag.getCompoundOrEmpty("view"));
        syncedSignature = contentSignature();
    }

    /**
     * Sicht für Rohre: zuerst die Eingänge des Controllers (nur einfügen), dahinter das Lager (nur entnehmen).
     * Ohne gebildete Struktur bleibt das Lager trotzdem leerbar.
     */
    private final class View implements IItemHandler {
        private static final int INPUTS = ControllerBlockEntity.INVENTORY_SIZE;

        /**
         * Item-Eingänge des angeschlossenen Controllers.
         *
         * @return die Schnittstelle oder {@code null} ohne gebildete Struktur
         */
        @Nullable
        private IItemHandler target() {
            ControllerBlockEntity c = controller();
            return c == null ? null : c.getItemView();
        }

        @Override
        public int getSlots() {
            return INPUTS + STORAGE_SLOTS;
        }

        /**
         * Inhalt eines Platzes.
         *
         * @param slot Nummer (erst Eingänge, dann Lager)
         * @return der Stapel
         */
        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot >= INPUTS) return active().getStackInSlot(slot - INPUTS);
            IItemHandler t = target();
            return t == null ? ItemStack.EMPTY : t.getStackInSlot(slot);
        }

        /**
         * Einfügen nur in die Eingänge des Controllers (Brennstoff, Stabilisator).
         *
         * @param slot Nummer
         * @param stack einzufügender Stapel
         * @param simulate nur prüfen
         * @return was nicht eingefügt werden konnte
         */
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot >= INPUTS) return stack;
            IItemHandler t = target();
            return t == null ? stack : t.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= INPUTS ? active().extractItem(slot - INPUTS, amount, simulate) : ItemStack.EMPTY;
        }

        /**
         * Größte Menge je Platz.
         *
         * @param slot Nummer
         * @return Grenze
         */
        @Override
        public int getSlotLimit(int slot) {
            if (slot >= INPUTS) return active().getSlotLimit(slot - INPUTS);
            IItemHandler t = target();
            return t == null ? 64 : t.getSlotLimit(slot);
        }

        /**
         * Darf das Item in diesen Platz (nur Eingänge, nur passende Items)?
         *
         * @param slot Nummer
         * @param stack zu prüfender Stapel
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
