package dr4kun3.cosmiccore.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/** Blickrichtung der Steuerkonsole, zu der ein Port gehoert (NONE = einzelner Port). */
public enum ConsoleSide implements StringRepresentable {
    NONE(null), NORTH(Direction.NORTH), EAST(Direction.EAST), SOUTH(Direction.SOUTH), WEST(Direction.WEST);

    @Nullable
    private final Direction direction;

    ConsoleSide(@Nullable Direction direction) {
        this.direction = direction;
    }

    @Nullable
    public Direction direction() {
        return direction;
    }

    public static ConsoleSide of(Direction d) {
        return switch (d) {
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
