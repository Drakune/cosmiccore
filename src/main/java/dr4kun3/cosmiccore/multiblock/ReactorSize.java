package dr4kun3.cosmiccore.multiblock;

import org.jetbrains.annotations.Nullable;

/**
 * Baugroessen des Kerngenerators. Groessere Reaktoren erlauben hoehere Leistungsstufen, sind pro Stufe
 * stabiler (mehr Spulen verteilen die Last) und haben einen groesseren Energiepuffer.
 */
public enum ReactorSize {
    // Leistungsstufen 4 / 6 / 8 / 10; groessere Reaktoren liefern mehr pro Stufe und sind stabiler
    SMALL(5, 4, 0.6, 0.5, 1.0, 1.0, 1),
    MEDIUM(7, 6, 0.85, 1.0, 0.8, 1.25, 2),
    LARGE(9, 8, 1.1, 2.5, 0.6, 1.6, 3),
    /** Ultimativer Kern: Stufen 1-10, staerkster und stabilster Reaktor, erzeugt zusaetzlich Vakuum-Energie. */
    ULTIMATE(11, 10, 1.3, 4.0, 0.45, 2.0, 3);

    private final int size;
    private final int maxScale;
    private final double outputFactor;
    private final double capacityFactor;
    private final double instabilityFactor;
    private final double stabilizerFactor;
    private final int maxOverdrive;
    private final CorePattern pattern;

    ReactorSize(int size, int maxScale, double outputFactor, double capacityFactor, double instabilityFactor, double stabilizerFactor, int maxOverdrive) {
        this.size = size;
        this.maxScale = maxScale;
        this.outputFactor = outputFactor;
        this.capacityFactor = capacityFactor;
        this.instabilityFactor = instabilityFactor;
        this.stabilizerFactor = stabilizerFactor;
        this.maxOverdrive = maxOverdrive;
        this.pattern = new CorePattern(size);
    }

    /** RF-Puffer dieser Baustufe: 1G je Stufe (5er 1G, 7er 2G, 9er 3G), der ultimative Kern 5G - unabhaengig vom Power-Level. */
    public long buffer() {
        if (this == ULTIMATE) return dr4kun3.cosmiccore.CoreConfig.ULTIMATE_BUFFER.get();
        return dr4kun3.cosmiccore.CoreConfig.BUFFER_PER_TIER.get() * (ordinal() + 1);
    }

    /** Item, das je Leistungsstufe einmalig verbraucht wird, um sie dauerhaft freizuschalten. */
    public net.minecraft.world.item.Item unlockItem() {
        return switch (this) {
            case SMALL -> dr4kun3.cosmiccore.ModItems.STAR_DUST.get();
            case MEDIUM -> dr4kun3.cosmiccore.ModItems.COMPRESSED_STAR_DUST.get();
            case LARGE -> dr4kun3.cosmiccore.ModItems.NEUTRON_FRAGMENT.get();
            case ULTIMATE -> dr4kun3.cosmiccore.ModItems.QUASAR_ESSENCE.get();
        };
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

    /** Leistungsfaktor der Baugroesse (auf jede Stufe). */
    public double outputFactor() {
        return outputFactor;
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
    /** Hoechste Overdrive-Stufe inklusive Ultimate-Modus (nur der ultimative Kern hat Stufe 4). */
    public int maxOverdriveTotal() {
        return isUltimate() ? maxOverdrive() + 1 : maxOverdrive();
    }

    public int maxOverdrive() {
        return maxOverdrive;
    }

    public CorePattern pattern() {
        return pattern;
    }

    public boolean isUltimate() {
        return this == ULTIMATE;
    }

    @Nullable
    public static ReactorSize bySize(int size) {
        for (ReactorSize s : values()) {
            if (s.size == size) return s;
        }
        return null;
    }
}
