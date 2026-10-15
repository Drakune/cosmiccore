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
    private Terminal() {}

    /** Rechts vom Controller (von vorn gesehen) - hier sitzt der Energie-Port. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** Links vom Controller (von vorn gesehen) - hier sitzt der Item-Port. */
    public static Direction left(Direction facing) {
        return facing.getClockWise();
    }

    public static boolean isTerminal(BlockGetter level, BlockPos controller, Direction facing) {
        return level.getBlockState(controller.relative(right(facing))).is(ModBlocks.ENERGY_PORT.get())
                && level.getBlockState(controller.relative(left(facing))).is(ModBlocks.ITEM_PORT.get());
    }

    /** Zu welcher Konsole gehoert dieser Port (Blickrichtung des Controllers) - oder keiner. */
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
