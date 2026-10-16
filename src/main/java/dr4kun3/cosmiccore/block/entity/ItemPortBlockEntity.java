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

    public ItemPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_PORT.get(), pos, state);
    }

    public IItemHandler getItemView() {
        return itemView;
    }

    public ItemStackHandler getStorage() {
        return storage;
    }

    /** Legt Beute ins Lager; zurueck kommt, was nicht mehr passt. */
    public ItemStack store(ItemStack stack) {
        ItemStack rest = stack;
        for (int i = 0; i < STORAGE_SLOTS && !rest.isEmpty(); i++) rest = storage.insertItem(i, rest, false);
        return rest;
    }

    public int usedSlots() {
        int n = 0;
        for (int i = 0; i < STORAGE_SLOTS; i++) if (!storage.getStackInSlot(i).isEmpty()) n++;
        return n;
    }

    /** Client: belegte Plaetze laut Server. */
    public int getClientUsed() {
        return clientUsed;
    }

    /** Beim Abbauen faellt das Lager heraus (ab 1.21.5 ueber die Block-Entity statt Block#onRemove). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) dropContents(level, pos);
    }

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

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("used", usedSlots());
        return tag;
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
        super.saveAdditional(tag);
        storage.serialize(tag.child("storage"));
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        CompoundTag tag = dr4kun3.cosmiccore.data.NbtBridge.read(input);
        if (tag.contains("storage")) storage.deserialize(input.childOrEmpty("storage"));
        if (tag.contains("used")) clientUsed = tag.getIntOr("used", 0);
        syncedUsed = usedSlots();
    }

    /**
     * Sicht fuer Rohre: zuerst die Eingaenge des Controllers (nur einfuegen), dahinter das Lager (nur entnehmen).
     * Ohne gebildete Struktur bleibt das Lager trotzdem leerbar.
     */
    private final class View implements IItemHandler {
        private static final int INPUTS = ControllerBlockEntity.INVENTORY_SIZE;

        @Nullable
        private IItemHandler target() {
            ControllerBlockEntity c = controller();
            return c == null ? null : c.getItemView();
        }

        @Override
        public int getSlots() {
            return INPUTS + STORAGE_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot >= INPUTS) return storage.getStackInSlot(slot - INPUTS);
            IItemHandler t = target();
            return t == null ? ItemStack.EMPTY : t.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot >= INPUTS) return stack;
            IItemHandler t = target();
            return t == null ? stack : t.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= INPUTS ? storage.extractItem(slot - INPUTS, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot >= INPUTS) return storage.getSlotLimit(slot - INPUTS);
            IItemHandler t = target();
            return t == null ? 64 : t.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot >= INPUTS) return false;
            IItemHandler t = target();
            return t != null && t.isItemValid(slot, stack);
        }
    }
}
