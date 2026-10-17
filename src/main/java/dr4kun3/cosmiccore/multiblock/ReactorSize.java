package dr4kun3.cosmiccore.multiblock;

import dr4kun3.cosmiccore.CoreConfig;
import dr4kun3.cosmiccore.ModItems;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * Baugrößen des Kerngenerators. Größere Reaktoren erlauben höhere Leistungsstufen, sind pro Stufe
 * stabiler (mehr Spulen verteilen die Last) und haben einen größeren Energiepuffer.
 */
public enum ReactorSize {
    // Leistungsstufen 4 / 6 / 8 / 10; größere Reaktoren liefern mehr pro Stufe und sind stabiler
    SMALL(5, 4, 0.6, 0.5, 1.0, 1.0, 1),
    MEDIUM(7, 6, 0.85, 1.0, 0.8, 1.25, 2),
    LARGE(9, 8, 1.1, 2.5, 0.6, 1.6, 3),
    /** Ultimativer Kern: Stufen 1-10, stärkster und stabilster Reaktor, erzeugt zusätzlich Vakuum-Energie. */
    ULTIMATE(11, 10, 1.3, 4.0, 0.45, 2.0, 3);

    private final int size;
    private final int maxScale;
    private final double outputFactor;
    private final double capacityFactor;
    private final double instabilityFactor;
    private final double stabilizerFactor;
    private final int maxOverdrive;
    private final CorePattern pattern;

    /**
     * Legt eine Baugröße mit ihren Kennwerten an.
     *
     * @param size Kantenlänge in Blöcken
     * @param maxScale höchste Leistungsstufe
     * @param outputFactor Leistungsfaktor je Stufe
     * @param capacityFactor Faktor für den Puffer (ältere Berechnung)
     * @param instabilityFactor wie schnell die Instabilität steigt
     * @param stabilizerFactor wie stark Stabilisatoren wirken
     * @param maxOverdrive höchste Overdrive-Stufe ohne Ultimate-Modus
     */
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

    /**
     * RF-Puffer dieser Baustufe: 1G je Stufe (5er 1G, 7er 2G, 9er 3G), der ultimative Kern 5G - unabhängig von der
     * Leistungsstufe (Werte aus der Konfiguration).
     *
     * @return Kapazität in RF
     */
    public long buffer() {
        if (this == ULTIMATE) return CoreConfig.ULTIMATE_BUFFER.get();
        return CoreConfig.BUFFER_PER_TIER.get() * (ordinal() + 1);
    }

    /**
     * Item, das je Leistungsstufe einmalig verbraucht wird, um sie dauerhaft freizuschalten.
     *
     * @return Sternenstaub, verdichteter Sternenstaub, Neutronenstern bzw. Quasar-Essenz
     */
    public Item unlockItem() {
        return switch (this) {
            case SMALL -> ModItems.STAR_DUST.get();
            case MEDIUM -> ModItems.COMPRESSED_STAR_DUST.get();
            case LARGE -> ModItems.NEUTRON_FRAGMENT.get();
            case ULTIMATE -> ModItems.QUASAR_ESSENCE.get();
        };
    }

    /**
     * Kantenlänge der Struktur.
     *
     * @return 5, 7, 9 oder 11 Blöcke
     */
    public int size() {
        return size;
    }

    /**
     * Halbe Kantenlänge (Abstand Mitte -> Wand).
     *
     * @return Blöcke
     */
    public int half() {
        return size / 2;
    }

    /**
     * Höchste einstellbare Leistungsstufe für diese Baugröße.
     *
     * @return 4, 6, 8 bzw. 10
     */
    public int maxScale() {
        return maxScale;
    }

    /**
     * Leistungsfaktor der Baugröße (wirkt auf jede Stufe).
     *
     * @return Faktor
     */
    public double outputFactor() {
        return outputFactor;
    }

    /**
     * Faktor für die Kapazität.
     *
     * @return Faktor
     */
    public double capacityFactor() {
        return capacityFactor;
    }

    /**
     * Wie schnell die Instabilität bei dieser Größe steigt.
     *
     * @return Faktor
     */
    public double instabilityFactor() {
        return instabilityFactor;
    }

    /**
     * Wie stark Stabilisatoren bei dieser Größe kühlen.
     *
     * @return Faktor
     */
    public double stabilizerFactor() {
        return stabilizerFactor;
    }

    /**
     * Höchste Overdrive-Stufe inklusive Ultimate-Modus (nur der ultimative Kern hat eine Stufe mehr).
     *
     * @return höchste Stufe
     */
    public int maxOverdriveTotal() {
        return isUltimate() ? maxOverdrive() + 1 : maxOverdrive();
    }

    /**
     * Wie viele Overdrive-Partikel diese Baugröße aufnehmen kann (ohne Ultimate-Modus).
     *
     * @return höchste Overdrive-Stufe
     */
    public int maxOverdrive() {
        return maxOverdrive;
    }

    /**
     * Bauplan der Struktur.
     *
     * @return das Muster aller Blöcke
     */
    public CorePattern pattern() {
        return pattern;
    }

    public boolean isUltimate() {
        return this == ULTIMATE;
    }

    /**
     * Baugröße zu einer Kantenlänge.
     *
     * @param size Kantenlänge in Blöcken
     * @return die Baugröße oder {@code null}, wenn es keine gibt
     */
    @Nullable
    public static ReactorSize bySize(int size) {
        for (ReactorSize s : values()) {
            if (s.size == size) return s;
        }
        return null;
    }
}
