package dr4kun3.cosmiccore.block;

import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public final class ModProperties {
    public static final EnumProperty<CorePhase> PHASE = EnumProperty.create("phase", CorePhase.class);
    /**
     * Huellenblock liegt bei gebildeter Struktur ausserhalb der sichtbaren Kugel: kein Modell, keine Kollision,
     * nicht anwaehlbar - was man sieht, ist auch das, woran man stoesst.
     */
    public static final BooleanProperty PHANTOM = BooleanProperty.create("phantom");
    /** Kosmos-Laser ist Teil eines gebildeten Laser-Arrays (Modell ausgeblendet, das grosse Geschuetz wird gerendert). */
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");

    /** Controller bildet mit Item-Port (links) und Energie-Port (rechts) eine gemeinsame Steuerkonsole. */
    public static final BooleanProperty TERMINAL = BooleanProperty.create("terminal");
    /** Port gehoert zu einer Steuerkonsole (Blickrichtung des Controllers) oder steht allein. */
    public static final EnumProperty<ConsoleSide> CONSOLE = EnumProperty.create("console", ConsoleSide.class);

    /** Port der Steuerkonsole: an seiner Aussenseite ist etwas angedockt (Rohr, Kabel ...) - erst dann zeigt er die Buchse. */
    public static final BooleanProperty DOCKED = BooleanProperty.create("docked");

    private ModProperties() {}
}
