package dr4kun3.cosmiccore.menu;

import dr4kun3.cosmiccore.block.ModBlocks;
import dr4kun3.cosmiccore.block.entity.ItemPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Lager des Item-Ports: 27 Plaetze Asteroiden-Beute (nur entnehmen) plus Spielerinventar. */
public class ItemPortMenu extends AbstractContainerMenu {
    public static final int W = 176, H = 174;
    public static final int STORE_X = 8, STORE_Y = 20, INV_X = 8, INV_Y = 92;

    private final ContainerLevelAccess access;
    private final BlockPos pos;

    /** Client. */
    public ItemPortMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), new ItemStackHandler(ItemPortBlockEntity.STORAGE_SLOTS));
    }

    /** Server. */
    public ItemPortMenu(int id, Inventory inv, ItemPortBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.getStorage());
    }

    private ItemPortMenu(int id, Inventory inv, BlockPos pos, IItemHandler storage) {
        super(ModMenus.ITEM_PORT.get(), id);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new SlotItemHandler(storage, col + row * 9, STORE_X + col * 18, STORE_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false; // Beute kann nur entnommen werden
                    }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, INV_X + col * 18, INV_Y + 58));
    }

    public BlockPos getPos() {
        return pos;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ITEM_PORT.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int store = ItemPortBlockEntity.STORAGE_SLOTS, end = slots.size();
        if (index < store) {
            if (!moveItemStackTo(stack, store, end, true)) return ItemStack.EMPTY;
        } else {
            int hotbar = end - 9;
            if (index < hotbar) {
                if (!moveItemStackTo(stack, hotbar, end, false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, store, hotbar, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }
}
