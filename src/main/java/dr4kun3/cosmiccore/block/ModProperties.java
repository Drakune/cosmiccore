package dr4kun3.cosmiccore.block;

import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public final class ModProperties {
    public static final EnumProperty<CorePhase> PHASE = EnumProperty.create("phase", CorePhase.class);
    /**
     * Hüllenblock liegt bei gebildeter Struktur außerhalb der sichtbaren Kugel: kein Modell, keine Kollision,
     * nicht anwählbar - was man sieht, ist auch das, woran man stößt.
     */
    public static final BooleanProperty PHANTOM = BooleanProperty.create("phantom");
    /** Kosmos-Laser ist Teil eines gebildeten Laser-Arrays (Modell ausgeblendet, das große Geschütz wird gerendert). */
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");

    /** Controller bildet mit Item-Port (links) und Energie-Port (rechts) eine gemeinsame Steuerkonsole. */
    public static final BooleanProperty TERMINAL = BooleanProperty.create("terminal");
    /** Port gehört zu einer Steuerkonsole (Blickrichtung des Controllers) oder steht allein. */
    public static final EnumProperty<ConsoleSide> CONSOLE = EnumProperty.create("console", ConsoleSide.class);

    /** Port der Steuerkonsole: an seiner Außenseite ist etwas angedockt (Rohr, Kabel ...) - erst dann zeigt er die Buchse. */
    public static final BooleanProperty DOCKED = BooleanProperty.create("docked");

    /** Nur statische Blockzustands-Eigenschaften - keine Instanzen. */
    private ModProperties() {}
}
