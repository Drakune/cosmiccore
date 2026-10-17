package dr4kun3.cosmiccore.multiblock;

import dr4kun3.cosmiccore.block.ModBlocks;
import dr4kun3.cosmiccore.block.Terminal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Das Muster des Kerngenerators für eine Kantenlänge (5, 7 oder 9). Beispiel 7x7x7, y=0 ist der Boden:
 * <pre>
 * y=0 Boden   y=1 / y=5   y=2 / y=4   y=3 Äquator   y=6 Decke
 * HHHCHHH     F.....F     F.....F     F.....F        HHHHHHH
 * HHHHHHH     .aaaaa.     .aaaaa.     .aaaaa.        HGGGGGH
 * HHHHHHH     .aaaaa.     .aaaaa.     .aaaaa.        HGHHHGH
 * CHHHHHC     .aaXaa.     .aaaaa.     .aaKaa.        HGHHHGH
 * HHHHHHH     .aaaaa.     .aaaaa.     .aaaaa.        HGHHHGH
 * HHHHHHH     .aaaaa.     .aaaaa.     .aaaaa.        HGGGGGH
 * HHHCHHH     F.....F     F.....F     F.....F        HHHHHHH
 * (y=1: R statt X = Resonanzschild-Emitter unter dem Kern; y=5: X = Fokuskristall über dem Kern)
 *
 *  H = Hülle (Leerengehäuse, Energie-Port oder Item-Port)
 *  C = wie H, aber hier darf (genau) der Controller sitzen - Mitte jeder Bodenkante
 *  F = Runenrahmen (Eckpfeiler)     G = Fokusglas (Deckenring)
 *  R = Resonanzschild-Emitter (unterer Pol)     X = Fokuskristall (oberer Pol)
 *  K = Singularitätskern (Mitte)   a = Luft (Kammer muss frei sein)   . = egal (offene Seiten)
 * </pre>
 * 11x11x11 (ultimativ): statt K vier Satellitenkerne S auf Kernhöhe, je 2 Blöcke von der Mitte in alle vier
 * Richtungen; die Mitte selbst bleibt frei (FUSED_CORE). Beim Formen verschmelzen die vier zum Kern in der Mitte,
 * den der Controller dort setzt und beim Zerfall wieder entfernt.
 * 5x5x5: die Kammer ist zu eng, Emitter und Kristall sitzen deshalb in der Mitte von Boden bzw. Decke.
 * Alle Muster sind 4-fach drehsymmetrisch; deshalb muss nur der Mittelpunkt aus der Controller-Ausrichtung
 * berechnet werden, nicht das Muster gedreht.
 */
public final class CorePattern {
    public enum Cell { ANY, AIR, HULL, SLOT, FRAME, GLASS, COIL, CRYSTAL, CORE, SATELLITE, FUSED_CORE }

    /** Abstand der vier Satellitenkerne des ultimativen Kerns von der Mitte (auf Kernhöhe, in alle vier Richtungen). */
    public static final int SATELLITE_DISTANCE = 2;

    private final int size;
    private final int half;
    /** Abstand Kern -> Spulen/Kristalle auf den Achsen. */
    private final int focus;
    private final Cell[][][] cells; // [x][y][z]
    private final int blockCount;

    /**
     * Berechnet den Bauplan für eine Kantenlänge (Hülle, Rahmen, Glas, Spulen, Kristalle, Kern, Luft).
     *
     * @param size Kantenlänge in Blöcken
     */
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

    /**
     * Bestimmt, was an einer Stelle relativ zur Mitte stehen muss.
     *
     * @param dx Versatz x zur Mitte
     * @param dy Versatz y zur Mitte
     * @param dz Versatz z zur Mitte
     * @return die Zellenart
     */
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
        // Kammer: Kern in der Mitte, Fokuskristall darüber, Resonanzschild-Emitter darunter
        if (dx == 0 && dy == 0 && dz == 0) return size >= 11 ? Cell.FUSED_CORE : Cell.CORE;
        if (size >= 11 && dy == 0 && ((ax == SATELLITE_DISTANCE && dz == 0) || (az == SATELLITE_DISTANCE && dx == 0))) {
            return Cell.SATELLITE;
        }
        if (dx == 0 && dz == 0 && ay == focus) return dy < 0 ? Cell.COIL : Cell.CRYSTAL;
        return Cell.AIR;
    }

    /**
     * Kantenlänge.
     *
     * @return Blöcke
     */
    public int size() {
        return size;
    }

    /**
     * Halbe Kantenlänge.
     *
     * @return Blöcke
     */
    public int half() {
        return half;
    }

    /**
     * Radius der animierten Kammer (gebildete Struktur); Blöcke außerhalb werden zu Phantomen.
     *
     * @return Radius in Blöcken
     */
    public float shellRadius() {
        return half + 0.2F;
    }

    /**
     * Abstand vom Kern zu den Achsen-Spulen und Kristallen.
     *
     * @return Blöcke
     */
    public int focus() {
        return focus;
    }

    /**
     * Anzahl der Strukturblöcke (ohne Luft und "egal").
     *
     * @return Anzahl
     */
    public int blockCount() {
        return blockCount;
    }

    /**
     * Zählt die Zellen einer Art.
     *
     * @param cell die Art
     * @return Anzahl
     */
    public int count(Cell cell) {
        int n = 0;
        for (Cell[][] a : cells) for (Cell[] b : a) for (Cell c : b) if (c == cell) n++;
        return n;
    }

    @FunctionalInterface
    public interface Visitor {
        /**
         * Wird für jede Zelle des Musters aufgerufen.
         *
         * @param pos Weltposition der Zelle
         * @param cell was dort stehen muss
         */
        void visit(BlockPos pos, Cell cell);
    }

    /**
     * Mittelpunkt (Position des Kerns). Der Controller sitzt mittig an der Bodenkante und schaut nach außen.
     *
     * @param controller Position des Controllers
     * @param facing Blickrichtung des Controllers
     * @return Position des Kerns
     */
    public BlockPos center(BlockPos controller, Direction facing) {
        return controller.relative(facing.getOpposite(), half).above(half);
    }

    /**
     * Anzahl der Satellitenkerne (nur beim ultimativen Kern: 4 rund um die Mitte).
     *
     * @return Anzahl
     */
    public int satelliteCount() {
        return count(Cell.SATELLITE);
    }

    public boolean hasFusedCore() {
        return cells[half][half][half] == Cell.FUSED_CORE;
    }

    /**
     * Positionen der vier Satellitenkerne.
     *
     * @param center Position der Mitte
     * @return die Positionen
     */
    public List<BlockPos> satellites(BlockPos center) {
        List<BlockPos> out = new ArrayList<>();
        forEach(center, (pos, cell) -> {
            if (cell == Cell.SATELLITE) out.add(pos.immutable());
        });
        return out;
    }

    /**
     * Besucht jede Zelle; die Ausrichtung spielt keine Rolle (das Muster ist symmetrisch, bleibt für bestehende
     * Aufrufer erhalten).
     *
     * @param center Position der Mitte
     * @param facing Blickrichtung des Controllers (nicht verwendet)
     * @param visitor wird je Zelle aufgerufen
     */
    public void forEach(BlockPos center, Direction facing, Visitor visitor) {
        forEach(center, visitor);
    }

    /**
     * Besucht jede Zelle des Musters.
     *
     * @param center Position der Mitte
     * @param visitor wird je Zelle aufgerufen
     */
    public void forEach(BlockPos center, Visitor visitor) {
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    visitor.visit(center.offset(x - half, y - half, z - half), cells[x][y][z]);
                }
            }
        }
    }

    public static boolean isDock(BlockPos pos, BlockPos controller, Direction facing) {
        return pos.equals(controller.relative(Terminal.right(facing), 2))
                || pos.equals(controller.relative(Terminal.left(facing), 2));
    }

    public static boolean isBlockCell(Cell cell) {
        return cell != Cell.ANY && cell != Cell.AIR;
    }

    public record Report(boolean valid, boolean unloaded, int mismatches, @Nullable BlockPos firstError, @Nullable Component expected) {}

    /**
     * Prüft die gebaute Struktur gegen das Muster.
     *
     * @param level Welt
     * @param controllerPos Position des Controllers
     * @param facing Blickrichtung des Controllers
     * @return Bericht: gültig, nicht geladen, Anzahl Fehler, erster Fehler und was dort erwartet wird
     */
    public Report validate(Level level, BlockPos controllerPos, Direction facing) {
        final int[] mismatches = {0};
        final BlockPos[] first = {null};
        final Component[] expected = {null};
        final boolean[] unloaded = {false};

        forEach(center(controllerPos, facing), facing, (pos, cell) -> {
            if (cell == Cell.ANY || isDock(pos, controllerPos, facing)) return;
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
        if (mismatches[0] == 0) {
            // Pflicht: mindestens ein Energie-Port und ein Item-Port in der Hülle
            boolean[] ports = {false, false};
            forEach(center(controllerPos, facing), (pos, cell) -> {
                if (cell != Cell.HULL && cell != Cell.SLOT) return;
                BlockState s = level.getBlockState(pos);
                if (s.is(ModBlocks.ENERGY_PORT.get())) ports[0] = true;
                if (s.is(ModBlocks.ITEM_PORT.get())) ports[1] = true;
            });
            if (!ports[0]) return new Report(false, false, 1, controllerPos, Component.translatable("message.cosmiccore.missing_energy_port"));
            if (!ports[1]) return new Report(false, false, 1, controllerPos, Component.translatable("message.cosmiccore.missing_item_port"));
        }
        return new Report(mismatches[0] == 0, false, mismatches[0], first[0], expected[0]);
    }

    private static boolean isHull(BlockState s) {
        return s.is(ModBlocks.VOID_CASING.get()) || s.is(ModBlocks.ENERGY_PORT.get()) || s.is(ModBlocks.ITEM_PORT.get());
    }

    /**
     * Passt ein Block an eine Stelle des Musters?
     *
     * @param cell gewünschte Zellenart
     * @param s vorhandener Blockzustand
     * @param isControllerPos {@code true} an der Position des Controllers (dort ist er erlaubt)
     * @return {@code true}, wenn der Block passt
     */
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
            case FUSED_CORE -> s.isAir() || s.is(ModBlocks.SINGULARITY_CORE.get());
        };
    }

    /**
     * Block, der für die Vorschau bzw. den Kreativ-Aufbau an diese Stelle gehört.
     *
     * @param cell die Zellenart
     * @return der Blockzustand oder {@code null} für Luft und "egal"
     */
    @Nullable
    public static BlockState blockFor(Cell cell) {
        return switch (cell) {
            case ANY, AIR, FUSED_CORE -> null;
            case HULL, SLOT -> ModBlocks.VOID_CASING.get().defaultBlockState();
            case FRAME -> ModBlocks.RUNE_FRAME.get().defaultBlockState();
            case GLASS -> ModBlocks.FOCUS_GLASS.get().defaultBlockState();
            case COIL -> ModBlocks.RESONANCE_COIL.get().defaultBlockState();
            case CRYSTAL -> ModBlocks.FOCUS_CRYSTAL.get().defaultBlockState();
            case CORE, SATELLITE -> ModBlocks.SINGULARITY_CORE.get().defaultBlockState();
        };
    }

    /**
     * Beschreibung für Fehlermeldungen ("erwartet: Runenrahmen").
     *
     * @param cell die Zellenart
     * @return übersetzter Text
     */
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
            case FUSED_CORE -> Component.translatable("message.cosmiccore.expected_air");
        };
    }
}
