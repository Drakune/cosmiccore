package dev.cosmiccore.multiblock;

import org.jetbrains.annotations.Nullable;

/**
 * Baugroessen des Kerngenerators. Groessere Reaktoren erlauben hoehere Leistungsstufen, sind pro Stufe
 * stabiler (mehr Spulen verteilen die Last) und haben einen groesseren Energiepuffer.
 */
public enum ReactorSize {
    SMALL(5, 3, 0.5, 1.0, 1.0, 1),
    MEDIUM(7, 5, 1.0, 0.8, 1.25, 2),
    LARGE(9, 8, 2.5, 0.6, 1.6, 3);

    private final int size;
    private final int maxScale;
    private final double capacityFactor;
    private final double instabilityFactor;
    private final double stabilizerFactor;
    private final int maxOverdrive;
    private final CorePattern pattern;

    ReactorSize(int size, int maxScale, double capacityFactor, double instabilityFactor, double stabilizerFactor, int maxOverdrive) {
        this.size = size;
        this.maxScale = maxScale;
        this.capacityFactor = capacityFactor;
        this.instabilityFactor = instabilityFactor;
        this.stabilizerFactor = stabilizerFactor;
        this.maxOverdrive = maxOverdrive;
        this.pattern = new CorePattern(size);
    }

    /** Kantenlaenge in Bloecken (5, 7 oder 9). */
    public int size() {
        return size;
    }

    public int half() {
        return size / 2;
    }

    /** Hoechste einstellbare Leistungsstufe fuer diese Baugroesse. */
    public int maxScale() {
        return maxScale;
    }

    public double capacityFactor() {
        return capacityFactor;
    }

    public double instabilityFactor() {
        return instabilityFactor;
    }

    public double stabilizerFactor() {
        return stabilizerFactor;
    }

    /** Wie viele Overdrive-Partikel diese Baugroesse aufnehmen kann. */
    public int maxOverdrive() {
        return maxOverdrive;
    }

    public CorePattern pattern() {
        return pattern;
    }

    @Nullable
    public static ReactorSize bySize(int size) {
        for (ReactorSize s : values()) {
            if (s.size == size) return s;
        }
        return null;
    }
}
