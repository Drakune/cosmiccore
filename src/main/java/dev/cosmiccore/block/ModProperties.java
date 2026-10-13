package dev.cosmiccore.block;

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

    private ModProperties() {}
}
