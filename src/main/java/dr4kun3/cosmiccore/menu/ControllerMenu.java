package dr4kun3.cosmiccore.menu;

import dr4kun3.cosmiccore.block.ModBlocks;
import dr4kun3.cosmiccore.block.entity.ControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * GUI des Controllers: Brennstoff- und Stabilisator-Slot (frei befuell- und entnehmbar) plus Spielerinventar.
 * Knoepfe laufen ueber den Vanilla-Mechanismus clickMenuButton - dafuer braucht es kein eigenes Netzwerkpaket.
 */
public class ControllerMenu extends AbstractContainerMenu {
    // Knopf-IDs
    /** Groessenwahl: 100 + Groesse (0..2) + 3 * Trichter + 6 * Laser (eigener Bereich, kollidiert nicht mit den anderen Knoepfen). */
    public static final int BTN_SIZE_FIRST = 100;
    public static final int BTN_SCALE_DOWN = 10;
    public static final int BTN_SCALE_UP = 11;
    public static final int BTN_TOGGLE = 12;
    public static final int BTN_PREVIEW = 13;
    public static final int BTN_BUILD = 14;
    public static final int BTN_OVERDRIVE_UP = 15;
    public static final int BTN_OVERDRIVE_DOWN = 16;
    public static final int BTN_ANTI_ASTEROID = 17;
    /** Lager des Item-Ports oeffnen (beim Schliessen geht es zurueck zum Controller). */
    public static final int BTN_STORAGE = 18;

    // Datenfelder
    public static final int D_ENERGY_LO = 0, D_ENERGY_HI = 1, D_CAPACITY_LO = 2, D_CAPACITY_HI = 3, D_OUTPUT_LO = 4, D_OUTPUT_HI = 5,
            D_INSTABILITY = 6, D_RESONANCE = 7, D_SCALE = 8, D_MAX_SCALE = 9, D_SIZE = 10, D_FLAGS = 11, D_COSMIC = 12,
            D_BURN = 13, D_STAB = 14, D_COLLAPSE = 15, D_ERRORS = 16, D_OVERDRIVE = 17, D_MELTDOWN = 18, D_UNLOCKED = 19,
            D_COLOR_MASK = 20, D_COLOR_FIRST = 21, // Farbsatz: Maske + 8 Farben (RGB555 passt in ein Datenfeld)
            D_FUEL_TIME_LO = 29, D_FUEL_TIME_HI = 30, D_STAB_TIME_LO = 31, D_STAB_TIME_HI = 32;
    public static final int DATA_COUNT = 33;

    public static final int F_FORMED = 1, F_ENABLED = 2, F_PREVIEW = 4, F_SKY = 8, F_BURNING = 16, F_FUNNEL = 32, F_LASER = 64, F_RUNNING = 128, F_FULL = 256, F_HAS_FUEL = 512,
            F_ANTI_INSTALLED = 1024, F_ANTI_ON = 2048, F_COLOR_INSTALLED = 4096,
            F_NO_ENERGY_PORT = 8192, F_NO_ITEM_PORT = 16384;

    // Layout (auch vom Screen benutzt)
    // 5-px-Raster: Karten 5 px vom Rand und voneinander, 5 px Innenabstand (Slot-Rahmen = Slot - 1)
    public static final int FUEL_X = 12, FUEL_Y = 25, STAB_X = 12, STAB_Y = 52;
    public static final int INV_X = 77, INV_Y = 186;
    /**
     * Modul-Tab rechts am Fenster (klappt aus wie bei Mekanism): 2x2 Slots - Effizienz, Stabilitaet,
     * Anti-Asteroiden (Schalter darunter) und Farbe (Farbfeld darunter). Koordinaten relativ zum Fenster.
     */
    public static final int GUI_W = 316, TAB_TOP = 18, MOD_X = GUI_W + 9, MOD_Y = TAB_TOP + 22, MOD_STEP_X = 22, MOD_STEP_Y = 30;

    /** Nur Client: ist der Modul-Tab ausgeklappt? (Der Server laesst die Slots immer zu.) */
    public boolean modulesOpen = true;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    @Nullable
    private final ControllerBlockEntity be;

    /** Client: Daten kommen vom Server. */
    public ControllerMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(id, playerInv, buf.readBlockPos(), new ControllerBlockEntity.CoreInventory(() -> {}),
                new SimpleContainerData(DATA_COUNT), null);
    }

    /** Server. */
    public ControllerMenu(int id, Inventory playerInv, ControllerBlockEntity be, ContainerData data) {
        this(id, playerInv, be.getBlockPos(), be.getInventory(), data, be);
    }

    private ControllerMenu(int id, Inventory playerInv, BlockPos pos, IItemHandler machine, ContainerData data,
                           @Nullable ControllerBlockEntity be) {
        super(ModMenus.CONTROLLER.get(), id);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(playerInv.player.level(), pos);
        this.data = data;
        this.be = be;

        addSlot(new MachineSlot(machine, ControllerBlockEntity.SLOT_FUEL, FUEL_X, FUEL_Y));
        addSlot(new MachineSlot(machine, ControllerBlockEntity.SLOT_STAB, STAB_X, STAB_Y));
        // Asteroiden-Beute liegt jetzt im Lager des Item-Ports - der Controller zeigt keine Beute-Slots mehr
        addSlot(new ModuleSlot(machine, ControllerBlockEntity.SLOT_EFFICIENCY, MOD_X, MOD_Y));
        addSlot(new ModuleSlot(machine, ControllerBlockEntity.SLOT_STABILITY, MOD_X + MOD_STEP_X, MOD_Y));
        addSlot(new ModuleSlot(machine, ControllerBlockEntity.SLOT_ANTI, MOD_X, MOD_Y + MOD_STEP_Y));
        addSlot(new ModuleSlot(machine, ControllerBlockEntity.SLOT_COLOR, MOD_X + MOD_STEP_X, MOD_Y + MOD_STEP_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new PlayerSlot(playerInv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new PlayerSlot(playerInv, col, INV_X + col * 18, INV_Y + 58));
        }
        addDataSlots(data);
    }

    // ------------------------------------------------------------------ Datenzugriff fuer den Screen

    /** Anzahl der Maschinen-Slots im Menue (Brennstoff, Stabilisator, vier Module). */
    public static final int MACHINE_SLOTS = 6;

    /** Menue-Slot zu einem Slot des Controller-Inventars (null = nicht im Menue, z. B. der Beute-Puffer). */
    @Nullable
    public Slot machineSlot(int inventoryIndex) {
        for (int i = 0; i < MACHINE_SLOTS; i++) if (slots.get(i).getSlotIndex() == inventoryIndex) return slots.get(i);
        return null;
    }

    public BlockPos getPos() {
        return pos;
    }

    private int wide(int lo, int hi) {
        return (data.get(hi) << 16) | (data.get(lo) & 0xFFFF);
    }

    /** Energie in RF (uebertragen in kRF). */
    public long energy() {
        return (wide(D_ENERGY_LO, D_ENERGY_HI) & 0xFFFFFFFFL) * 1000L;
    }

    public long capacity() {
        return (wide(D_CAPACITY_LO, D_CAPACITY_HI) & 0xFFFFFFFFL) * 1000L;
    }

    /** Restlaufzeit des Brennstoffs in Sekunden (oder ControllerBlockEntity.TIME_*). */
    public int fuelSeconds() {
        return wide(D_FUEL_TIME_LO, D_FUEL_TIME_HI);
    }

    public int stabSeconds() {
        return wide(D_STAB_TIME_LO, D_STAB_TIME_HI);
    }

    public int output() {
        return wide(D_OUTPUT_LO, D_OUTPUT_HI);
    }

    public float instability() {
        return data.get(D_INSTABILITY) / 100f;
    }

    public int resonance() {
        return data.get(D_RESONANCE);
    }

    public int scale() {
        return data.get(D_SCALE);
    }

    public int maxScale() {
        return data.get(D_MAX_SCALE);
    }

    public int size() {
        return data.get(D_SIZE);
    }

    public boolean sizeChosen() {
        return size() > 0;
    }

    public boolean flag(int f) {
        return (data.get(D_FLAGS) & f) != 0;
    }

    public float cosmic() {
        return data.get(D_COSMIC) / 100f;
    }

    public float burnProgress() {
        return data.get(D_BURN) / 1000f;
    }

    public float stabProgress() {
        return data.get(D_STAB) / 1000f;
    }

    public int collapseSeconds() {
        return data.get(D_COLLAPSE);
    }

    public int errors() {
        return data.get(D_ERRORS);
    }

    /** Sekunden bis zum Kollaps (0 = kein Countdown). */
    public int meltdown() {
        return data.get(D_MELTDOWN);
    }

    /** Hoechste dauerhaft freigeschaltete Leistungsstufe. */
    public int unlocked() {
        return Math.max(1, data.get(D_UNLOCKED));
    }

    /** Aktueller Farbsatz des Controllers. */
    public dr4kun3.cosmiccore.color.CoreColors.Config colorConfig() {
        int[] c = new int[dr4kun3.cosmiccore.color.CoreColors.CHANNELS];
        for (int i = 0; i < c.length; i++) c[i] = data.get(D_COLOR_FIRST + i);
        return new dr4kun3.cosmiccore.color.CoreColors.Config(data.get(D_COLOR_MASK), c);
    }

    public int overdrive() {
        return data.get(D_OVERDRIVE);
    }

    // ------------------------------------------------------------------ Logik

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return be != null && be.handleButton(player, id);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.CONTROLLER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        // Slots 0-1: Brennstoff, Stabilisator | 2-5: Module | danach Spielerinventar
        final int machineEnd = MACHINE_SLOTS, invEnd = slots.size();
        if (index < machineEnd) {
            if (!moveItemStackTo(stack, machineEnd, invEnd, true)) return ItemStack.EMPTY;
        } else {
            // erst in den passenden Maschinenslot, sonst zwischen Inventar und Hotbar wechseln
            if (!moveItemStackTo(stack, 0, machineEnd, false)) {
                int hotbarStart = invEnd - 9;
                if (index < hotbarStart) {
                    if (!moveItemStackTo(stack, hotbarStart, invEnd, false)) return ItemStack.EMPTY;
                } else if (!moveItemStackTo(stack, machineEnd, hotbarStart, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    /** Solange die Baugroesse nicht gewaehlt ist, zeigt der Screen nur die Auswahl - Slots sind dann aus. */
    /** Modul-Slot im Seiten-Tab: auf dem Client nur sichtbar und klickbar, solange der Tab ausgeklappt ist. */
    private final class ModuleSlot extends SlotItemHandler {
        ModuleSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean isActive() {
            return sizeChosen() && modulesOpen;
        }
    }

    private final class MachineSlot extends SlotItemHandler {
        MachineSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean isActive() {
            return sizeChosen();
        }
    }

    private final class PlayerSlot extends Slot {
        PlayerSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return sizeChosen();
        }
    }
}
