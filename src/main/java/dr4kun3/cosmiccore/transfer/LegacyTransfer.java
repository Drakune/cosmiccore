package dr4kun3.cosmiccore.transfer;

import java.util.Arrays;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Stellt die Sichten des Reaktors (Energie und Items) ueber die Transfer-API ab Minecraft 1.21.9 bereit.
 * Innerhalb einer Transaktion wird nur vorgemerkt und mit Simulation geprueft; ausgefuehrt wird erst, wenn die
 * aeusserste Transaktion abgeschlossen wird. Bricht sie ab, bleibt alles unveraendert.
 */
public final class LegacyTransfer {
    private LegacyTransfer() {}

    /** Eine Huelle je Sicht, damit alle Abfragen in einer Transaktion dieselben Vormerkungen sehen. */
    private static final java.util.Map<Object, Object> CACHE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static EnergyHandler energy(EnergyStore storage) {
        return (EnergyHandler) CACHE.computeIfAbsent(storage, s -> new Energy((EnergyStore) s));
    }

    @SuppressWarnings("unchecked")
    public static ResourceHandler<ItemResource> items(ItemHandler handler) {
        return (ResourceHandler<ItemResource>) CACHE.computeIfAbsent(handler, h -> new Items((ItemHandler) h));
    }

    private static final class Energy extends SnapshotJournal<int[]> implements EnergyHandler {
        private final EnergyStore storage;
        private int pendingIn, pendingOut;

        Energy(EnergyStore storage) {
            this.storage = storage;
        }

        @Override
        public long getAmountAsLong() {
            return (long) storage.getEnergyStored() + pendingIn - pendingOut;
        }

        @Override
        public long getCapacityAsLong() {
            return storage.getMaxEnergyStored();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            if (amount == 0 || pendingOut > 0) return 0;
            int n = Math.clamp(storage.receiveEnergy(pendingIn + amount, true) - pendingIn, 0, amount);
            if (n > 0) {
                updateSnapshots(transaction);
                pendingIn += n;
            }
            return n;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            if (amount == 0 || pendingIn > 0) return 0;
            int n = Math.clamp(storage.extractEnergy(pendingOut + amount, true) - pendingOut, 0, amount);
            if (n > 0) {
                updateSnapshots(transaction);
                pendingOut += n;
            }
            return n;
        }

        @Override
        protected int[] createSnapshot() {
            return new int[]{pendingIn, pendingOut};
        }

        @Override
        protected void revertToSnapshot(int[] snapshot) {
            pendingIn = snapshot[0];
            pendingOut = snapshot[1];
        }

        @Override
        protected void onRootCommit(int[] originalState) {
            if (pendingIn > 0) storage.receiveEnergy(pendingIn, false);
            if (pendingOut > 0) storage.extractEnergy(pendingOut, false);
            pendingIn = pendingOut = 0;
        }
    }

    /** Vorgemerkte Aenderungen je Slot: entweder ein Stapel zum Einfuegen oder eine Menge zum Entnehmen. */
    private record Pending(ItemStack[] insert, int[] extract) {
        Pending copy() {
            ItemStack[] ins = new ItemStack[insert.length];
            for (int i = 0; i < ins.length; i++) ins[i] = insert[i].copy();
            return new Pending(ins, extract.clone());
        }
    }

    private static final class Items extends SnapshotJournal<Pending> implements ResourceHandler<ItemResource> {
        private final ItemHandler handler;
        private Pending pending = new Pending(new ItemStack[0], new int[0]);

        Items(ItemHandler handler) {
            this.handler = handler;
        }

        /** Die Anzahl der Slots kann sich aendern (Struktur zerfaellt) - Vormerkungen wachsen mit. */
        private Pending pending() {
            int n = handler.getSlots();
            if (pending.insert().length < n) {
                ItemStack[] ins = Arrays.copyOf(pending.insert(), n);
                for (int i = pending.insert().length; i < n; i++) ins[i] = ItemStack.EMPTY;
                pending = new Pending(ins, Arrays.copyOf(pending.extract(), n));
            }
            return pending;
        }

        @Override
        public int size() {
            return handler.getSlots();
        }

        @Override
        public ItemResource getResource(int index) {
            ItemStack stack = handler.getStackInSlot(index);
            ItemStack ins = pending().insert()[index];
            return ItemResource.of(stack.isEmpty() ? ins : stack);
        }

        @Override
        public long getAmountAsLong(int index) {
            Pending p = pending();
            return handler.getStackInSlot(index).getCount() + p.insert()[index].getCount() - p.extract()[index];
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return resource.isEmpty() ? handler.getSlotLimit(index) : Math.min(handler.getSlotLimit(index), resource.getMaxStackSize());
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return handler.isItemValid(index, resource.toStack());
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            Pending p = pending();
            if (amount == 0 || index < 0 || index >= handler.getSlots() || p.extract()[index] > 0) return 0;
            ItemStack before = p.insert()[index];
            if (!before.isEmpty() && !resource.matches(before)) return 0;
            int had = before.getCount();
            ItemStack probe = resource.toStack(had + amount);
            int n = Math.clamp(probe.getCount() - handler.insertItem(index, probe, true).getCount() - had, 0, amount);
            if (n > 0) {
                updateSnapshots(transaction);
                pending().insert()[index] = resource.toStack(had + n);
            }
            return n;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            Pending p = pending();
            if (amount == 0 || index < 0 || index >= handler.getSlots() || !p.insert()[index].isEmpty()) return 0;
            if (!resource.matches(handler.getStackInSlot(index))) return 0;
            int had = p.extract()[index];
            int n = Math.clamp(handler.extractItem(index, had + amount, true).getCount() - had, 0, amount);
            if (n > 0) {
                updateSnapshots(transaction);
                pending().extract()[index] = had + n;
            }
            return n;
        }

        @Override
        protected Pending createSnapshot() {
            return pending().copy();
        }

        @Override
        protected void revertToSnapshot(Pending snapshot) {
            pending = snapshot;
        }

        @Override
        protected void onRootCommit(Pending originalState) {
            Pending p = pending();
            int n = Math.min(p.insert().length, handler.getSlots());
            for (int i = 0; i < n; i++) {
                if (!p.insert()[i].isEmpty()) handler.insertItem(i, p.insert()[i], false);
                if (p.extract()[i] > 0) handler.extractItem(i, p.extract()[i], false);
            }
            pending = new Pending(new ItemStack[0], new int[0]);
        }
    }
}
