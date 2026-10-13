package dr4kun3.cosmiccore.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Anschluss fuer Trichter und Rohre: nimmt Brennstoff, Stabilisatoren und Module auf und gibt die Asteroiden-Beute ab
 * (leitet alles an das Inventar des Controllers weiter).
 */
public class ItemPortBlockEntity extends PortBlockEntity {
    private final IItemHandler itemView = new View();

    public ItemPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_PORT.get(), pos, state);
    }

    public IItemHandler getItemView() {
        return itemView;
    }

    private final class View implements IItemHandler {
        private IItemHandler target() {
            ControllerBlockEntity c = controller();
            return c == null ? null : c.getItemView();
        }

        @Override
        public int getSlots() {
            return ControllerBlockEntity.INVENTORY_SIZE;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            IItemHandler t = target();
            return t == null ? ItemStack.EMPTY : t.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            IItemHandler t = target();
            return t == null ? stack : t.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            IItemHandler t = target();
            return t == null ? ItemStack.EMPTY : t.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            IItemHandler t = target();
            return t == null ? 64 : t.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            IItemHandler t = target();
            return t != null && t.isItemValid(slot, stack);
        }
    }
}
