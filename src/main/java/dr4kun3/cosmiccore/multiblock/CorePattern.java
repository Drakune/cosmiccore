package dr4kun3.cosmiccore.multiblock;

import dr4kun3.cosmiccore.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Das Muster des Kerngenerators fuer eine Kantenlaenge (5, 7 oder 9). Beispiel 7x7x7, y=0 ist der Boden:
 * <pre>
 * y=0 Boden   y=1 / y=5   y=2 / y=4   y=3 Aequator   y=6 Decke
 * HHHCHHH     F.....F     F.....F     F.....F        HHHHHHH
 * HHHHHHH     .aaaaa.     .aaaaa.     .aaaaa.        HGGGGGH
 * HHHHHHH     .aaaaa.     .aaaaa.     .aaaaa.        HGHHHGH
 * CHHHHHC     .aaXaa.     .aaaaa.     .aaKaa.        HGHHHGH
 * HHHHHHH     .aaaaa.     .aaaaa.     .aaaaa.        HGHHHGH
 * HHHHHHH     .aaaaa.     .aaaaa.     .aaaaa.        HGGGGGH
 * HHHCHHH     F.....F     F.....F     F.....F        HHHHHHH
 * (y=1: R statt X = Resonanzschild-Emitter unter dem Kern; y=5: X = Fokuskristall ueber dem Kern)
 *
 *  H = Huelle (Leerengehaeuse, Energie-Port oder Item-Port)
 *  C = wie H, aber hier darf (genau) der Controller sitzen - Mitte jeder Bodenkante
 *  F = Runenrahmen (Eckpfeiler)     G = Fokusglas (Deckenring)
 *  R = Resonanzschild-Emitter (unterer Pol)     X = Fokuskristall (oberer Pol)
 *  K = Singularitaetskern (Mitte)   a = Luft (Kammer muss frei sein)   . = egal (offene Seiten)
 * </pre>
 * 11x11x11 (ultimativ): zusaetzlich S = vier Satellitenkerne auf Kernhoehe, je 2 Bloecke vom Zentrum in alle vier
 * Richtungen - sie verschmelzen beim Formen mit dem Kern.
 * 5x5x5: die Kammer ist zu eng, Emitter und Kristall sitzen deshalb in der Mitte von Boden bzw. Decke.
 * Alle Muster sind 4-fach drehsymmetrisch; deshalb muss nur der Mittelpunkt aus der Controller-Ausrichtung
 * berechnet werden, nicht das Muster gedreht.
 */
public final class CorePattern {
    public enum Cell { ANY, AIR, HULL, SLOT, FRAME, GLASS, COIL, CRYSTAL, CORE, SATELLITE }

    /** Abstand der vier Satellitenkerne des ultimativen Kerns vom Zentrum (auf Kernhoehe, in alle vier Richtungen). */
    public static final int SATELLITE_DISTANCE = 2;

    private final int size;
    private final int half;
    /** Abstand Kern -> Spulen/Kristalle auf den Achsen. */
    private final int focus;
    private final Cell[][][] cells; // [x][y][z]
    private final int blockCount;

    CorePattern(int size) {
        this.size = size;
        this.half = size / 2;
        this.focus = size <= 5 ? half : half - 1;
        this.cells = new Cell[size][size][size];
        int count = 0;
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    Cell c = compute(x - half, y - half, z - half);
                    cells[x][y][z] = c;
                    if (isBlockCell(c)) count++;
                }
            }
        }
        this.blockCount = count;
    }

    private Cell compute(int dx, int dy, int dz) {
        int ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);

        if (ay == half) {
            // Boden und Decke (5x5x5: Emitter in der Bodenmitte, Kristall in der Deckenmitte)
            if (dx == 0 && dz == 0 && focus == half) return dy < 0 ? Cell.COIL : Cell.CRYSTAL;
            if (dy < 0) {
                return (dx == 0 && az == half) || (dz == 0 && ax == half) ? Cell.SLOT : Cell.HULL;
            }
            return Math.max(ax, az) == half - 1 ? Cell.GLASS : Cell.HULL;
        }
        if (ax == half || az == half) {
            // offene Seiten
            if (ax == half && az == half) return Cell.FRAME;
            return Cell.ANY;
        }
        // Kammer: Kern in der Mitte, Fokuskristall darueber, Resonanzschild-Emitter darunter
        if (dx == 0 && dy == 0 && dz == 0) return Cell.CORE;
        if (size >= 11 && dy == 0 && ((ax == SATELLITE_DISTANCE && dz == 0) || (az == SATELLITE_DISTANCE && dx == 0))) {
            return Cell.SATELLITE;
        }
        if (dx == 0 && dz == 0 && ay == focus) return dy < 0 ? Cell.COIL : Cell.CRYSTAL;
        return Cell.AIR;
    }

    public int size() {
        return size;
    }

    public int half() {
        return half;
    }

    /** Radius der animierten Kammer (gebildete Struktur); Bloecke ausserhalb werden zu Phantomen. */
    public float shellRadius() {
        return half + 0.2F;
    }

    /** Abstand vom Kern zu den Achsen-Spulen und Kristallen (in Bloecken). */
    public int focus() {
        return focus;
    }

    /** Anzahl der Strukturbloecke (ohne Luft und "egal"). */
    public int blockCount() {
        return blockCount;
    }

    public int count(Cell cell) {
        int n = 0;
        for (Cell[][] a : cells) for (Cell[] b : a) for (Cell c : b) if (c == cell) n++;
        return n;
    }

    @FunctionalInterface
    public interface Visitor {
        void visit(BlockPos pos, Cell cell);
    }

    /** Mittelpunkt (Position des Kerns). Der Controller sitzt mittig an der Bodenkante und schaut nach aussen. */
    public BlockPos center(BlockPos controller, Direction facing) {
        return controller.relative(facing.getOpposite(), half).above(half);
    }

    public void forEach(BlockPos center, Visitor visitor) {
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    visitor.visit(center.offset(x - half, y - half, z - half), cells[x][y][z]);
                }
            }
        }
    }

    /** Zellen, die tatsaechlich Strukturbloecke sind (also alles ausser ANY und AIR). */
    public static boolean isBlockCell(Cell cell) {
        return cell != Cell.ANY && cell != Cell.AIR;
    }

    public record Report(boolean valid, boolean unloaded, int mismatches, @Nullable BlockPos firstError, @Nullable Component expected) {}

    public Report validate(Level level, BlockPos controllerPos, Direction facing) {
        final int[] mismatches = {0};
        final BlockPos[] first = {null};
        final Component[] expected = {null};
        final boolean[] unloaded = {false};

        forEach(center(controllerPos, facing), (pos, cell) -> {
            if (cell == Cell.ANY) return;
            if (!level.hasChunkAt(pos)) {
                unloaded[0] = true;
                return;
            }
            if (!matches(cell, level.getBlockState(pos), pos.equals(controllerPos))) {
                mismatches[0]++;
                if (first[0] == null) {
                    first[0] = pos.immutable();
                    expected[0] = describe(cell);
                }
            }
        });

        if (unloaded[0]) return new Report(false, true, 0, null, null);
        return new Report(mismatches[0] == 0, false, mismatches[0], first[0], expected[0]);
    }

    private static boolean isHull(BlockState s) {
        return s.is(ModBlocks.VOID_CASING.get()) || s.is(ModBlocks.ENERGY_PORT.get()) || s.is(ModBlocks.ITEM_PORT.get());
    }

    public static boolean matches(Cell cell, BlockState s, boolean isControllerPos) {
        return switch (cell) {
            case ANY -> true;
            case AIR -> s.isAir();
            case HULL -> isHull(s);
            case SLOT -> isHull(s) || (isControllerPos && s.is(ModBlocks.CONTROLLER.get()));
            case FRAME -> s.is(ModBlocks.RUNE_FRAME.get());
            case GLASS -> s.is(ModBlocks.FOCUS_GLASS.get());
            case COIL -> s.is(ModBlocks.RESONANCE_COIL.get());
            case CRYSTAL -> s.is(ModBlocks.FOCUS_CRYSTAL.get());
            case CORE, SATELLITE -> s.is(ModBlocks.SINGULARITY_CORE.get());
        };
    }

    /** Block, der fuer die Vorschau bzw. den Kreativ-Aufbau an diese Stelle gehoert (null = Luft/egal). */
    @Nullable
    public static BlockState blockFor(Cell cell) {
        return switch (cell) {
            case ANY, AIR -> null;
            case HULL, SLOT -> ModBlocks.VOID_CASING.get().defaultBlockState();
            case FRAME -> ModBlocks.RUNE_FRAME.get().defaultBlockState();
            case GLASS -> ModBlocks.FOCUS_GLASS.get().defaultBlockState();
            case COIL -> ModBlocks.RESONANCE_COIL.get().defaultBlockState();
            case CRYSTAL -> ModBlocks.FOCUS_CRYSTAL.get().defaultBlockState();
            case CORE, SATELLITE -> ModBlocks.SINGULARITY_CORE.get().defaultBlockState();
        };
    }

    public static Component describe(Cell cell) {
        return switch (cell) {
            case ANY -> Component.empty();
            case AIR -> Component.translatable("message.cosmiccore.expected_air");
            case HULL -> Component.translatable("message.cosmiccore.expected_hull");
            case SLOT -> Component.translatable("message.cosmiccore.expected_slot");
            case FRAME -> ModBlocks.RUNE_FRAME.get().getName();
            case GLASS -> ModBlocks.FOCUS_GLASS.get().getName();
            case COIL -> ModBlocks.RESONANCE_COIL.get().getName();
            case CRYSTAL -> ModBlocks.FOCUS_CRYSTAL.get().getName();
            case CORE -> ModBlocks.SINGULARITY_CORE.get().getName();
            case SATELLITE -> Component.translatable("message.cosmiccore.expected_satellite");
        };
    }
}
