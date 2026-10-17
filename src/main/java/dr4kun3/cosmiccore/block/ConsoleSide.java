package dr4kun3.cosmiccore.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/** Blickrichtung der Steuerkonsole, zu der ein Port gehoert (NONE = einzelner Port). */
public enum ConsoleSide implements StringRepresentable {
    NONE(null), NORTH(Direction.NORTH), EAST(Direction.EAST), SOUTH(Direction.SOUTH), WEST(Direction.WEST);

    @Nullable
    private final Direction direction;

    /**
     * Legt eine Konsolenseite an.
     *
     * @param direction Blickrichtung der Konsole oder {@code null} fuer "keine Konsole"
     */
    ConsoleSide(@Nullable Direction direction) {
        this.direction = direction;
    }

    /**
     * Richtung, in die die Konsole zeigt.
     *
     * @return die Richtung oder {@code null}, wenn der Port zu keiner Konsole gehoert
     */
    @Nullable
    public Direction direction() {
        return direction;
    }

    /**
     * Konsolenseite zu einer waagrechten Richtung.
     *
     * @param d Richtung (oben/unten werden zu Norden)
     * @return die passende Seite
     */
    public static ConsoleSide of(Direction d) {
        return switch (d) {
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    /**
     * Name im Blockzustand.
     *
     * @return der Name in Kleinbuchstaben
     */
    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
