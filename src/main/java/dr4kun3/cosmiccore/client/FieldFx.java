package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.cube;
import static dr4kun3.cosmiccore.client.RenderUtil.flatCircle;
import static dr4kun3.cosmiccore.client.RenderUtil.hash;
import static dr4kun3.cosmiccore.client.RenderUtil.lerp;
import static dr4kun3.cosmiccore.client.RenderUtil.quadFade;
import static dr4kun3.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dr4kun3.cosmiccore.color.CoreColors;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Das Eindämmungsfeld des Resonanzschild-Emitters: Vom Emitter unter dem Kern wölben sich Feldlinien um das
 * kosmische Loch bis zum Fokuskristall darüber (wie die Linien eines Magnetfelds). Zwischen ihnen liegt eine
 * zarte Membran, Lichtpulse und Ringe steigen am Feld empor.
 * <ul>
 *   <li>Die Stärke zeigt den Stabilisator-Vorrat - wird er knapp, reißen einzelne Feldlinien weg.</li>
 *   <li>Instabilität verzerrt das Feld, färbt es rot und lässt Entladungen zwischen den Linien springen.</li>
 * </ul>
 */
public final class FieldFx {
    /** Nur statische Zeichenroutinen - keine Instanzen. */
    private FieldFx() {}

    private static final float[] BLUE_DEF = {0.35F, 0.75F, 1.0F};
    private static float[] BLUE = BLUE_DEF;
    private static final float[] VIOLET_DEF = {0.6F, 0.4F, 1.0F};
    private static float[] VIOLET = VIOLET_DEF;
    private static final float[] CRITICAL = {1.0F, 0.12F, 0.12F};
    private static final float[][] OVERDRIVE = {{0.4F, 0.85F, 1.0F}, {1.0F, 0.75F, 0.2F}, {0.95F, 0.6F, 1.0F}};
    private static final int FULL_LINES = 12, FULL_STEPS = 28;

    /**
     * Zeichnet das Eindämmungsfeld: Feldlinien vom Emitter unter dem Kern um das Loch bis zum Kristall darüber, die
     * Membran dazwischen und aufsteigende Lichtpulse. Mit sinkendem Stabilisator reißen Linien weg, Hitze färbt rot.
     *
     * @param m Transformationsmatrix (Ursprung in der Kernmitte)
     * @param vc Puffer für additives Leuchten
     * @param focus Abstand Kern -> Emitter/Kristall in Blöcken
     * @param reach Radius der Kammer in Blöcken (wie weit sich das Feld wölbt)
     * @param t Animationszeit in Ticks
     * @param energy Helligkeit 0..1 (0 = nichts zeichnen)
     * @param heat Instabilität 0..1 (ab 0.4 färbt sich das Feld rot)
     * @param shield Stabilisator-Vorrat 0..1
     * @param unstable {@code true}, wenn der Kern kurz vor dem Kollaps steht (Entladungen)
     * @param overdrive Overdrive-Stufe 0..4 (Farbe der Pulse)
     */
    public static void render(Matrix4f m, VertexConsumer vc, float focus, float reach, float t, float energy, float heat,
                              float shield, boolean unstable, int overdrive) {
        BLUE = ColorOverride.get(CoreColors.FIELD, BLUE_DEF);
        VIOLET = ColorOverride.dark(CoreColors.FIELD, VIOLET_DEF, 0.3F);
        if (energy <= 0.01F) return;
        final int LINES = RenderQuality.scale(FULL_LINES, 6), STEPS = RenderQuality.scale(FULL_STEPS, 14);
        float strength = 0.3F + 0.7F * shield;
        float danger = Mth.clamp((heat - 0.4F) / 0.6F, 0.0F, 1.0F);
        float[] col = lerp(BLUE, VIOLET, 0.5F + 0.5F * Mth.sin(t * 0.03F));
        if (overdrive > 0) col = lerp(col, OVERDRIVE[Math.min(3, overdrive) - 1], 0.7F);
        col = lerp(col, CRITICAL, danger);

        float yBottom = -focus + 0.35F, yTop = focus - 0.35F;
        float bulge = Math.max(0.7F, reach * 0.78F);
        float rot = t * 0.004F;
        int window = (int) (t * 0.2F);

        // Feldlinien berechnen
        float[][][] pts = new float[LINES][STEPS + 1][];
        boolean[] alive = new boolean[LINES];
        for (int k = 0; k < LINES; k++) {
            alive[k] = hash(k * 131 + window) <= strength * 1.6F - 0.3F;
            float lon = rot + k * Mth.TWO_PI / LINES;
            for (int i = 0; i <= STEPS; i++) {
                float s = i / (float) STEPS;
                float wobble = danger > 0.0F ? danger * 0.12F * Mth.sin(t * 0.5F + k * 1.7F + s * 9.0F) : 0.0F;
                float r = bulge * (float) Math.pow(Mth.sin(s * Mth.PI), 0.8) * (1.0F + wobble);
                float y = Mth.lerp(s, yBottom, yTop);
                pts[k][i] = new float[]{Mth.cos(lon) * r, y, Mth.sin(lon) * r};
            }
        }

        for (int k = 0; k < LINES; k++) {
            int n = (k + 1) % LINES;
            float flick = unstable ? 0.6F + 0.4F * (0.5F + 0.5F * hash(k * 17 + (int) (t * 0.7F))) : 1.0F;
            for (int i = 0; i < STEPS; i++) {
                float s = i / (float) STEPS;
                float[] a = pts[k][i], b = pts[k][i + 1];
                // Lichtpuls läuft vom Emitter nach oben
                float pulse = 0.5F + 0.5F * Mth.sin((s - t * 0.025F) * Mth.TWO_PI * 2.0F + k);
                float edge = Mth.sin(s * Mth.PI); // an den Polen gebündelt, in der Mitte offen
                float la = energy * strength * flick * (0.2F + 0.55F * pulse * pulse) * (0.5F + 0.5F * edge);
                if (alive[k]) {
                    segment(vc, m, a[0], a[1], a[2], b[0], b[1], b[2], 0.006F, col[0], col[1], col[2], la * 0.8F, la * 0.8F);
                }
                // Membran zur Nachbarlinie
                if (alive[k] && alive[n]) {
                    float[] c = pts[n][i + 1], d = pts[n][i];
                    float ma = energy * strength * 0.035F * edge;
                    quadFade(vc, m, a[0], a[1], a[2], d[0], d[1], d[2], c[0], c[1], c[2], b[0], b[1], b[2],
                            col[0], col[1], col[2], ma, ma);
                }
            }
        }

        // Projektionskegel aus dem Emitter in das Feld
        float coneTop = yBottom + (yTop - yBottom) * 0.12F;
        float coneR = bulge * (float) Math.pow(Mth.sin(0.12F * Mth.PI), 0.8);
        for (int k = 0; k < LINES; k++) {
            float lon = rot + k * Mth.TWO_PI / LINES;
            segment(vc, m, 0, yBottom - 0.2F, 0, Mth.cos(lon) * coneR, coneTop, Mth.sin(lon) * coneR, 0.01F,
                    1.0F, 1.0F, 1.0F, energy * strength * 0.7F, energy * strength * 0.3F);
        }
        cube(vc, m, 0, yBottom - 0.2F, 0, 0.04F, 1.0F, 1.0F, 1.0F, energy);

        // aufsteigende Feldringe
        for (int j = 0; j < 3; j++) {
            float s = Mth.frac(t * 0.008F + j / 3.0F);
            float r = bulge * (float) Math.pow(Mth.sin(s * Mth.PI), 0.8);
            float y = Mth.lerp(s, yBottom, yTop);
            flatCircle(vc, new Matrix4f(m).translate(0, y, 0), r, 0.01F, 64, col, energy * strength * Mth.sin(s * Mth.PI) * 0.5F);
        }

        // Entladungen zwischen den Linien bei Gefahr
        if (danger > 0.3F) {
            int seed = (int) (t * 0.6F);
            int arcs = 1 + Math.round(danger * 5);
            for (int q = 0; q < arcs; q++) {
                int k = Math.floorMod((int) (hash(seed * 13 + q) * 100), LINES);
                int i = 4 + Math.floorMod((int) (hash(seed * 7 + q) * 100), STEPS - 8);
                float[] a = pts[k][i], b = pts[(k + 1) % LINES][i + 1];
                float mx = (a[0] + b[0]) / 2 + hash(seed + q) * 0.15F, my = (a[1] + b[1]) / 2, mz = (a[2] + b[2]) / 2 + hash(seed * 3 + q) * 0.15F;
                segment(vc, m, a[0], a[1], a[2], mx, my, mz, 0.015F, 1.0F, 0.85F, 0.85F, energy, energy);
                segment(vc, m, mx, my, mz, b[0], b[1], b[2], 0.015F, 1.0F, 0.85F, 0.85F, energy, energy);
            }
        }
    }
}
