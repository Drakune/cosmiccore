package dr4kun3.cosmiccore.client;

import dr4kun3.cosmiccore.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Detailgrad der Effekte (0..1) aus der Client-Config und der Entfernung zur Kamera. Jeder Renderer setzt ihn zu
 * Beginn per {@link #begin(BlockPos)}; die Effekt-Klassen lesen ihn ueber {@link #detail()} (alles im Render-Thread).
 */
public final class RenderQuality {
    /** Nur statischer Zustand - keine Instanzen. */
    private RenderQuality() {}

    private static float detail = 1.0F;

    /**
     * Legt den Detailgrad fuer den naechsten Reaktor fest: Grundwert aus der Client-Einstellung, mit der Entfernung
     * zur Kamera abnehmend.
     *
     * @param pos Position des gezeichneten Reaktors
     */
    public static void begin(BlockPos pos) {
        float base = switch (ClientConfig.QUALITY.get()) {
            case LOW -> 0.35F;
            case MEDIUM -> 0.65F;
            case HIGH -> 1.0F;
        };
        Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
        double dist = cam.distanceTo(Vec3.atCenterOf(pos));
        if (dist > ClientConfig.DETAIL_DISTANCE.get()) base *= 0.5F; // weit weg: vereinfacht
        detail = base;
    }

    /**
     * Aktueller Detailgrad.
     *
     * @return Faktor 0..1 (1 = volle Detailtiefe)
     */
    public static float detail() {
        return detail;
    }

    /**
     * Skaliert eine Anzahl (Linien, Partikel, Segmente) mit dem Detailgrad.
     *
     * @param full Anzahl bei voller Qualitaet
     * @param min Untergrenze
     * @return die angepasste Anzahl, mindestens {@code min}
     */
    public static int scale(int full, int min) {
        return Math.max(min, Math.round(full * detail));
    }
}
