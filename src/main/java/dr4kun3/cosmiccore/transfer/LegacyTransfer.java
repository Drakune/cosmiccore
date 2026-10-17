package dr4kun3.cosmiccore.transfer;

import java.util.Arrays;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
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
@SuppressWarnings("removal")
public final class LegacyTransfer {
    /** Nur statische Hilfsfunktionen - keine Instanzen. */
    private LegacyTransfer() {}

    /** Eine Huelle je Sicht, damit alle Abfragen in einer Transaktion dieselben Vormerkungen sehen. */
    private static final java.util.Map<Object, Object> CACHE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * Energiespeicher als Transfer-Schnittstelle (eine Huelle je Speicher).
     *
     * @param storage der Speicher
     * @return die Huelle
     */
    public static EnergyHandler energy(IEnergyStorage storage) {
        return (EnergyHandler) CACHE.computeIfAbsent(storage, s -> new Energy((IEnergyStorage) s));
    }

    /**
     * Item-Schnittstelle als Transfer-Schnittstelle (eine Huelle je Schnittstelle).
     *
     * @param handler die Schnittstelle
     * @return die Huelle
     */
    @SuppressWarnings("unchecked")
    public static ResourceHandler<ItemResource> items(IItemHandler handler) {
        return (ResourceHandler<ItemResource>) CACHE.computeIfAbsent(handler, h -> new Items((IItemHandler) h));
    }

    private static final class Energy extends SnapshotJournal<int[]> implements EnergyHandler {
        private final IEnergyStorage storage;
        private int pendingIn, pendingOut;

        /**
         * Huelle um einen alten Energiespeicher.
         *
         * @param storage der Speicher
         */
        Energy(IEnergyStorage storage) {
            this.storage = storage;
        }

        /**
         * Gespeicherte Energie einschliesslich der Vormerkungen.
         *
         * @return Energie in RF
         */
        @Override
        public long getAmountAsLong() {
            return (long) storage.getEnergyStored() + pendingIn - pendingOut;
        }

        /**
         * Kapazitaet.
         *
         * @return Kapazitaet in RF
         */
        @Override
        public long getCapacityAsLong() {
            return storage.getMaxEnergyStored();
        }

        /**
         * Merkt eine Einspeisung vor (ausgefuehrt erst beim Abschluss der Transaktion).
         *
         * @param amount Menge in RF
         * @param transaction laufende Transaktion
         * @return vorgemerkte Menge
         */
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

        /**
         * Merkt eine Entnahme vor (ausgefuehrt erst beim Abschluss der Transaktion).
         *
         * @param amount Menge in RF
         * @param transaction laufende Transaktion
         * @return vorgemerkte Menge
         */
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

        /**
         * Sichert die Vormerkungen.
         *
         * @return {eingespeist, entnommen}
         */
        @Override
        protected int[] createSnapshot() {
            return new int[]{pendingIn, pendingOut};
        }

        /**
         * Stellt die Vormerkungen wieder her (Transaktion abgebrochen).
         *
         * @param snapshot gesicherter Zustand
         */
        @Override
        protected void revertToSnapshot(int[] snapshot) {
            pendingIn = snapshot[0];
            pendingOut = snapshot[1];
        }

        /**
         * Transaktion abgeschlossen: Vormerkungen wirklich ausfuehren.
         *
         * @param originalState Zustand zu Beginn
         */
        @Override
        protected void onRootCommit(int[] originalState) {
            if (pendingIn > 0) storage.receiveEnergy(pendingIn, false);
            if (pendingOut > 0) storage.extractEnergy(pendingOut, false);
            pendingIn = pendingOut = 0;
        }
    }

    /** Vorgemerkte Aenderungen je Slot: entweder ein Stapel zum Einfuegen oder eine Menge zum Entnehmen. */
    private record Pending(ItemStack[] insert, int[] extract) {
        /**
         * Tiefe Kopie der Vormerkungen.
         *
         * @return die Kopie
         */
        Pending copy() {
            ItemStack[] ins = new ItemStack[insert.length];
            for (int i = 0; i < ins.length; i++) ins[i] = insert[i].copy();
            return new Pending(ins, extract.clone());
        }
    }

    private static final class Items extends SnapshotJournal<Pending> implements ResourceHandler<ItemResource> {
        private final IItemHandler handler;
        private Pending pending = new Pending(new ItemStack[0], new int[0]);

        /**
         * Huelle um eine alte Item-Schnittstelle.
         *
         * @param handler die Schnittstelle
         */
        Items(IItemHandler handler) {
            this.handler = handler;
        }

        /**
         * Die Vormerkungen; die Anzahl der Slots kann sich aendern (Struktur zerfaellt) - sie wachsen mit.
         *
         * @return die Vormerkungen
         */
        private Pending pending() {
            int n = handler.getSlots();
            if (pending.insert().length < n) {
                ItemStack[] ins = Arrays.copyOf(pending.insert(), n);
                for (int i = pending.insert().length; i < n; i++) ins[i] = ItemStack.EMPTY;
                pending = new Pending(ins, Arrays.copyOf(pending.extract(), n));
            }
            return pending;
        }

        /**
         * Anzahl Plaetze.
         *
         * @return Anzahl
         */
        @Override
        public int size() {
            return handler.getSlots();
        }

        /**
         * Item im Platz (oder das vorgemerkte, wenn der Platz leer ist).
         *
         * @param index Platz
         * @return das Item
         */
        @Override
        public ItemResource getResource(int index) {
            ItemStack stack = handler.getStackInSlot(index);
            ItemStack ins = pending().insert()[index];
            return ItemResource.of(stack.isEmpty() ? ins : stack);
        }

        /**
         * Menge im Platz einschliesslich der Vormerkungen.
         *
         * @param index Platz
         * @return Menge
         */
        @Override
        public long getAmountAsLong(int index) {
            Pending p = pending();
            return handler.getStackInSlot(index).getCount() + p.insert()[index].getCount() - p.extract()[index];
        }

        /**
         * Groesste Menge des Items im Platz.
         *
         * @param index Platz
         * @param resource welches Item (leer = beliebig)
         * @return Grenze
         */
        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return resource.isEmpty() ? handler.getSlotLimit(index) : Math.min(handler.getSlotLimit(index), resource.getMaxStackSize());
        }

        /**
         * Darf das Item in diesen Platz?
         *
         * @param index Platz
         * @param resource welches Item
         * @return {@code true}, wenn erlaubt
         */
        @Override
        public boolean isValid(int index, ItemResource resource) {
            return handler.isItemValid(index, resource.toStack());
        }

        /**
         * Merkt ein Einfuegen vor.
         *
         * @param index Platz
         * @param resource welches Item
         * @param amount Menge
         * @param transaction laufende Transaktion
         * @return vorgemerkte Menge
         */
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

        /**
         * Merkt eine Entnahme vor.
         *
         * @param index Platz
         * @param resource welches Item
         * @param amount Menge
         * @param transaction laufende Transaktion
         * @return vorgemerkte Menge
         */
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

        /**
         * Sichert die Vormerkungen.
         *
         * @return Kopie der Vormerkungen
         */
        @Override
        protected Pending createSnapshot() {
            return pending().copy();
        }

        /**
         * Stellt die Vormerkungen wieder her (Transaktion abgebrochen).
         *
         * @param snapshot gesicherter Zustand
         */
        @Override
        protected void revertToSnapshot(Pending snapshot) {
            pending = snapshot;
        }

        /**
         * Transaktion abgeschlossen: Vormerkungen wirklich ausfuehren.
         *
         * @param originalState Zustand zu Beginn
         */
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
