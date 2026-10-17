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
    /** Controller, zu dem es beim Schliessen zurueckgeht (oder null bei Rechtsklick auf den Port). */
    @org.jetbrains.annotations.Nullable
    private final BlockPos returnTo;

    /**
     * Client: baut das Menue aus den Daten des Servers (Position, Lager wird gespiegelt, optional Rueckweg).
     *
     * @param id Fensternummer
     * @param inv Inventar des Spielers
     * @param buf Daten vom Server
     */
    public ItemPortMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), new ItemStackHandler(ItemPortBlockEntity.STORAGE_SLOTS), buf.readBoolean() ? buf.readBlockPos() : null);
    }

    /**
     * Server: Menue auf dem echten Lager des Ports.
     *
     * @param id Fensternummer
     * @param inv Inventar des Spielers
     * @param be der Item-Port
     * @param returnTo Controller, zu dem "Zurueck" fuehrt, oder {@code null}
     */
    public ItemPortMenu(int id, Inventory inv, ItemPortBlockEntity be, @org.jetbrains.annotations.Nullable BlockPos returnTo) {
        this(id, inv, be.getBlockPos(), be.getStorage(), returnTo);
    }

    /**
     * Legt die Slots an: 27 Lagerplaetze (nur entnehmen) und das Spielerinventar.
     *
     * @param id Fensternummer
     * @param inv Inventar des Spielers
     * @param pos Position des Ports
     * @param storage Lager (echt oder gespiegelt)
     * @param returnTo Controller fuer den Rueckweg oder {@code null}
     */
    private ItemPortMenu(int id, Inventory inv, BlockPos pos, IItemHandler storage, @org.jetbrains.annotations.Nullable BlockPos returnTo) {
        super(ModMenus.ITEM_PORT.get(), id);
        this.pos = pos;
        this.returnTo = returnTo;
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

    /**
     * Position des Ports.
     *
     * @return die Position
     */
    public BlockPos getPos() {
        return pos;
    }

    /**
     * Controller, zu dem das Schliessen zurueckfuehrt.
     *
     * @return seine Position oder {@code null}, wenn der Port direkt geoeffnet wurde
     */
    @org.jetbrains.annotations.Nullable
    public BlockPos getReturnTo() {
        return returnTo;
    }

    /**
     * Knopf 0: zurueck zur Steuerung des Controllers (ersetzt dieses Menue), sofern man nah genug steht.
     *
     * @param player klickender Spieler
     * @param id Nummer des Knopfs
     * @return {@code true}, wenn das Controller-Menue geoeffnet wurde
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || returnTo == null || !(player.level().getBlockEntity(returnTo) instanceof dr4kun3.cosmiccore.block.entity.ControllerBlockEntity c)) {
            return false;
        }
        if (player.distanceToSqr(returnTo.getX() + 0.5, returnTo.getY() + 0.5, returnTo.getZ() + 0.5) > 64.0) return false;
        BlockPos back = returnTo;
        player.openMenu(c, back);
        return true;
    }

    /**
     * Offen bleibt das Menue, solange der Port existiert und der Spieler nah genug ist.
     *
     * @param player der Spieler
     * @return {@code true}, wenn das Menue weiter gueltig ist
     */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ITEM_PORT.get());
    }

    /**
     * Umschalt-Klick: aus dem Lager ins Inventar (ins Lager kann man nichts legen).
     *
     * @param player der Spieler
     * @param index Nummer des angeklickten Slots
     * @return Kopie des verschobenen Stapels oder leer, wenn nichts ging
     */
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
