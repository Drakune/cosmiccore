package dr4kun3.cosmiccore.block;

import net.minecraft.util.StringRepresentable;

/** Zustand der gesamten Struktur - wird auf alle Strukturbloecke gespiegelt (Textur, Licht, Animation). */
public enum CorePhase implements StringRepresentable {
    DORMANT("dormant"),
    FORMED("formed"),
    ACTIVE("active"),
    UNSTABLE("unstable");

    private final String name;

    CorePhase(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static CorePhase byName(String name) {
        for (CorePhase p : values()) {
            if (p.name.equals(name)) return p;
        }
        return DORMANT;
    }
}
