package dr4kun3.cosmiccore.block;

import net.minecraft.util.StringRepresentable;

/** Zustand der gesamten Struktur - wird auf alle Strukturbloecke gespiegelt (Textur, Licht, Animation). */
public enum CorePhase implements StringRepresentable {
    DORMANT("dormant"),
    FORMED("formed"),
    ACTIVE("active"),
    UNSTABLE("unstable");

    private final String name;

    /**
     * Legt eine Phase mit ihrem Namen im Blockzustand an.
     *
     * @param name Name, unter dem die Phase gespeichert wird (z. B. {@code active})
     */
    CorePhase(String name) {
        this.name = name;
    }

    /**
     * Name der Phase im Blockzustand und in Speicherstaenden.
     *
     * @return der gespeicherte Name
     */
    @Override
    public String getSerializedName() {
        return name;
    }

    /**
     * Findet eine Phase ueber ihren gespeicherten Namen.
     *
     * @param name gespeicherter Name
     * @return die passende Phase oder {@link #DORMANT}, wenn der Name unbekannt ist
     */
    public static CorePhase byName(String name) {
        for (CorePhase p : values()) {
            if (p.name.equals(name)) return p;
        }
        return DORMANT;
    }
}
