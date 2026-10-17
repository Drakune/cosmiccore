package dr4kun3.cosmiccore.block;

import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Steuerkonsole aus drei Blöcken: Item-Port links, Controller in der Mitte, Energie-Port rechts (von vorn gesehen).
 * Liegen die drei so nebeneinander, verschmelzen sie optisch zu einer Konsole; Rohre und Kabel docken weiterhin an
 * den äußeren Seiten der Ports an.
 */
public final class Terminal {
    /** Nur statische Hilfsfunktionen - keine Instanzen. */
    private Terminal() {}

    /**
     * Rechts vom Controller (von vorn gesehen) - hier sitzt der Energie-Port.
     *
     * @param facing Blickrichtung des Controllers
     * @return die Richtung nach rechts
     */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    /**
     * Links vom Controller (von vorn gesehen) - hier sitzt der Item-Port.
     *
     * @param facing Blickrichtung des Controllers
     * @return die Richtung nach links
     */
    public static Direction left(Direction facing) {
        return facing.getClockWise();
    }

    public static boolean isTerminal(BlockGetter level, BlockPos controller, Direction facing) {
        return level.getBlockState(controller.relative(right(facing))).is(ModBlocks.ENERGY_PORT.get())
                && level.getBlockState(controller.relative(left(facing))).is(ModBlocks.ITEM_PORT.get());
    }

    /**
     * Außenseite eines Konsolen-Ports (vom Controller weg) - hier docken Kabel und Rohre an.
     *
     * @param console Konsolenseite des Ports
     * @param energy {@code true} für den Energie-Port
     * @return die Richtung nach außen (ohne Konsole: oben)
     */
    public static Direction outer(ConsoleSide console, boolean energy) {
        Direction facing = console.direction();
        if (facing == null) return Direction.UP;
        return energy ? right(facing) : left(facing);
    }

    /**
     * Ist an der Außenseite etwas angedockt: ein Block mit passender Schnittstelle oder ein Block einer anderen Mod?
     *
     * @param level Welt
     * @param port Position des Ports
     * @param console Konsolenseite des Ports
     * @param energy {@code true} für den Energie-Port
     * @return {@code true}, wenn etwas angeschlossen ist
     */
    public static boolean docked(LevelReader level, BlockPos port, ConsoleSide console, boolean energy) {
        if (console == ConsoleSide.NONE) return false;
        Direction out = outer(console, energy);
        BlockPos n = port.relative(out);
        BlockState s = level.getBlockState(n);
        if (s.isAir()) return false;
        String ns = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace();
        if (ns.equals(CosmicCore.MODID)) return false;
        if (!ns.equals("minecraft")) return true;
        if (level instanceof Level l) {
            return energy ? l.getCapability(Capabilities.EnergyStorage.BLOCK, n, out.getOpposite()) != null
                    : l.getCapability(Capabilities.ItemHandler.BLOCK, n, out.getOpposite()) != null;
        }
        return false;
    }

    /**
     * Zustand eines Ports aus seiner Umgebung (Konsolenseite und Andocken).
     *
     * @param state bisheriger Zustand
     * @param level Welt
     * @param pos Position des Ports
     * @param energy {@code true} für den Energie-Port
     * @return der aktualisierte Zustand
     */
    public static BlockState portState(BlockState state, LevelReader level, BlockPos pos, boolean energy) {
        ConsoleSide console = consoleFor(level, pos, energy);
        return state.setValue(ModProperties.CONSOLE, console).setValue(ModProperties.DOCKED, docked(level, pos, console, energy));
    }

    /**
     * Zu welcher Konsole gehört dieser Port (Blickrichtung des Controllers) - oder zu keiner?
     *
     * @param level Welt
     * @param port Position des Ports
     * @param energy {@code true} für den Energie-Port
     * @return die Konsolenseite oder {@link ConsoleSide#NONE}
     */
    public static ConsoleSide consoleFor(BlockGetter level, BlockPos port, boolean energy) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos c = port.relative(d);
            BlockState s = level.getBlockState(c);
            if (!s.is(ModBlocks.CONTROLLER.get())) continue;
            Direction facing = s.getValue(ControllerBlock.FACING);
            BlockPos expected = c.relative(energy ? right(facing) : left(facing));
            if (expected.equals(port) && isTerminal(level, c, facing)) return ConsoleSide.of(facing);
        }
        return ConsoleSide.NONE;
    }
}
