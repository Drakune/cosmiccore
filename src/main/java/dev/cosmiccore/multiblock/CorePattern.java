package dev.cosmiccore.multiblock;

import dev.cosmiccore.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Das 7x7x7-Muster des Kerngenerators. LAYERS[y][z].charAt(x), y=0 ist der Boden.
 * <pre>
 *  H = Huelle (Leerengehaeuse, Energie-Port oder Item-Port)
 *  C = wie H, aber hier darf (genau) der Controller sitzen - Mitte jeder Bodenkante
 *  F = Runenrahmen (Eckpfeiler)     G = Fokusglas (Deckenring)
 *  R = Resonanzspule (Aequator)     X = Fokuskristall (Pol oben/unten)
 *  K = Singularitaetskern (Mitte)   a = Luft (Kammer muss frei sein)   . = egal (offene Seiten)
 * </pre>
 * Das Muster ist 4-fach drehsymmetrisch; deshalb muss nur der Mittelpunkt aus der Controller-Ausrichtung
 * berechnet werden, nicht das Muster gedreht.
 */
public final class CorePattern {
    public static final int SIZE = 7;
    public static final int HALF = 3;

    public enum Cell { ANY, AIR, HULL, SLOT, FRAME, GLASS, COIL, CRYSTAL, CORE }

    private static final String[][] LAYERS = {
            { // y = 0  Boden
                    "HHHCHHH",
                    "HHHHHHH",
                    "HHHHHHH",
                    "CHHHHHC",
                    "HHHHHHH",
                    "HHHHHHH",
                    "HHHCHHH"},
            { // y = 1
                    "F.....F",
                    ".aaaaa.",
                    ".aaaaa.",
                    ".aaXaa.",
                    ".aaaaa.",
                    ".aaaaa.",
                    "F.....F"},
            { // y = 2
                    "F.....F",
                    ".aaaaa.",
                    ".aaaaa.",
                    ".aaaaa.",
                    ".aaaaa.",
                    ".aaaaa.",
                    "F.....F"},
            { // y = 3  Aequator mit dem Kern in der Mitte
                    "F.....F",
                    ".aaRaa.",
                    ".aaaaa.",
                    ".RaKaR.",
                    ".aaaaa.",
                    ".aaRaa.",
                    "F.....F"},
            { // y = 4
                    "F.....F",
                    ".aaaaa.",
                    ".aaaaa.",
                    ".aaaaa.",
                    ".aaaaa.",
                    ".aaaaa.",
                    "F.....F"},
            { // y = 5
                    "F.....F",
                    ".aaaaa.",
                    ".aaaaa.",
                    ".aaXaa.",
                    ".aaaaa.",
                    ".aaaaa.",
                    "F.....F"},
            { // y = 6  Decke
                    "HHHHHHH",
                    "HGGGGGH",
                    "HGHHHGH",
                    "HGHHHGH",
                    "HGHHHGH",
                    "HGGGGGH",
                    "HHHHHHH"}
    };

    private static final Cell[][][] CELLS = new Cell[SIZE][SIZE][SIZE]; // [x][y][z]

    static {
        for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    CELLS[x][y][z] = parse(LAYERS[y][z].charAt(x));
                }
            }
        }
    }

    private static Cell parse(char c) {
        return switch (c) {
            case 'H' -> Cell.HULL;
            case 'C' -> Cell.SLOT;
            case 'F' -> Cell.FRAME;
            case 'G' -> Cell.GLASS;
            case 'R' -> Cell.COIL;
            case 'X' -> Cell.CRYSTAL;
            case 'K' -> Cell.CORE;
            case 'a' -> Cell.AIR;
            default -> Cell.ANY;
        };
    }

    @FunctionalInterface
    public interface Visitor {
        void visit(BlockPos pos, Cell cell);
    }

    /** Mittelpunkt (Position des Kerns). Der Controller sitzt mittig an der Bodenkante und schaut nach aussen. */
    public static BlockPos center(BlockPos controller, Direction facing) {
        return controller.relative(facing.getOpposite(), HALF).above(HALF);
    }

    public static void forEach(BlockPos center, Visitor visitor) {
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    visitor.visit(center.offset(x - HALF, y - HALF, z - HALF), CELLS[x][y][z]);
                }
            }
        }
    }

    /** Zellen, die tatsaechlich Strukturbloecke sind (also alles ausser ANY und AIR). */
    public static boolean isBlockCell(Cell cell) {
        return cell != Cell.ANY && cell != Cell.AIR;
    }

    public record Report(boolean valid, boolean unloaded, int mismatches, @Nullable BlockPos firstError, @Nullable Component expected) {}

    public static Report validate(Level level, BlockPos controllerPos, Direction facing) {
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

    private static boolean matches(Cell cell, BlockState s, boolean isControllerPos) {
        return switch (cell) {
            case ANY -> true;
            case AIR -> s.isAir();
            case HULL -> isHull(s);
            case SLOT -> isHull(s) || (isControllerPos && s.is(ModBlocks.CONTROLLER.get()));
            case FRAME -> s.is(ModBlocks.RUNE_FRAME.get());
            case GLASS -> s.is(ModBlocks.FOCUS_GLASS.get());
            case COIL -> s.is(ModBlocks.RESONANCE_COIL.get());
            case CRYSTAL -> s.is(ModBlocks.FOCUS_CRYSTAL.get());
            case CORE -> s.is(ModBlocks.SINGULARITY_CORE.get());
        };
    }

    private static Component describe(Cell cell) {
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
        };
    }

    private CorePattern() {}
}
