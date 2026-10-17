package dr4kun3.cosmiccore.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Steuerkonsole aus drei Bloecken: Item-Port links, Controller in der Mitte, Energie-Port rechts (von vorn gesehen).
 * Liegen die drei so nebeneinander, verschmelzen sie optisch zu einer Konsole; Rohre und Kabel docken weiterhin an
 * den aeusseren Seiten der Ports an.
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

    /**
     * Bildet der Controller mit beiden Ports eine Steuerkonsole (Energie-Port rechts, Item-Port links)?
     *
     * @param level Welt
     * @param controller Position des Controllers
     * @param facing Blickrichtung des Controllers
     * @return {@code true}, wenn beide Ports an ihrem Platz sind
     */
    public static boolean isTerminal(BlockGetter level, BlockPos controller, Direction facing) {
        return level.getBlockState(controller.relative(right(facing))).is(ModBlocks.ENERGY_PORT.get())
                && level.getBlockState(controller.relative(left(facing))).is(ModBlocks.ITEM_PORT.get());
    }

    /**
     * Aussenseite eines Konsolen-Ports (vom Controller weg) - hier docken Kabel und Rohre an.
     *
     * @param console Konsolenseite des Ports
     * @param energy {@code true} fuer den Energie-Port
     * @return die Richtung nach aussen (ohne Konsole: oben)
     */
    public static Direction outer(ConsoleSide console, boolean energy) {
        Direction facing = console.direction();
        if (facing == null) return Direction.UP;
        return energy ? right(facing) : left(facing);
    }

    /**
     * Ist an der Aussenseite etwas angedockt: ein Block mit passender Schnittstelle oder ein Block einer anderen Mod?
     *
     * @param level Welt
     * @param port Position des Ports
     * @param console Konsolenseite des Ports
     * @param energy {@code true} fuer den Energie-Port
     * @return {@code true}, wenn etwas angeschlossen ist
     */
    public static boolean docked(net.minecraft.world.level.LevelAccessor level, BlockPos port, ConsoleSide console, boolean energy) {
        if (console == ConsoleSide.NONE) return false;
        Direction out = outer(console, energy);
        BlockPos n = port.relative(out);
        BlockState s = level.getBlockState(n);
        if (s.isAir()) return false;
        String ns = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace();
        if (ns.equals(dr4kun3.cosmiccore.CosmicCore.MODID)) return false;
        if (!ns.equals("minecraft")) return true;
        if (level instanceof net.minecraft.world.level.Level l) {
            return energy ? l.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, n, out.getOpposite()) != null
                    : l.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, n, out.getOpposite()) != null;
        }
        return false;
    }

    /**
     * Zustand eines Ports aus seiner Umgebung (Konsolenseite und Andocken).
     *
     * @param state bisheriger Zustand
     * @param level Welt
     * @param pos Position des Ports
     * @param energy {@code true} fuer den Energie-Port
     * @return der aktualisierte Zustand
     */
    public static BlockState portState(BlockState state, net.minecraft.world.level.LevelAccessor level, BlockPos pos, boolean energy) {
        ConsoleSide console = consoleFor(level, pos, energy);
        return state.setValue(ModProperties.CONSOLE, console).setValue(ModProperties.DOCKED, docked(level, pos, console, energy));
    }

    /**
     * Zu welcher Konsole gehoert dieser Port (Blickrichtung des Controllers) - oder zu keiner?
     *
     * @param level Welt
     * @param port Position des Ports
     * @param energy {@code true} fuer den Energie-Port
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
