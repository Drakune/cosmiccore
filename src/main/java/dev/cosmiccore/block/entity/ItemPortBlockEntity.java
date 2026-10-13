package dev.cosmiccore.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/** Nimmt Brennstoff/Stabilisatoren per Trichter oder Rohr an (nur einfuegen, nie entnehmen). */
public class ItemPortBlockEntity extends PortBlockEntity {
    private final IItemHandler itemView = new View();

    public ItemPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_PORT.get(), pos, state);
    }

    public IItemHandler getItemView() {
        return itemView;
    }

    private final class View implements IItemHandler {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            ControllerBlockEntity c = controller();
            return c == null ? ItemStack.EMPTY : c.getInventory().getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            ControllerBlockEntity c = controller();
            return c == null ? stack : c.getInventory().insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            ControllerBlockEntity c = controller();
            return c != null && c.getInventory().isItemValid(slot, stack);
        }
    }
}
