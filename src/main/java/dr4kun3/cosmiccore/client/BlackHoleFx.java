package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.cube;
import static dr4kun3.cosmiccore.client.RenderUtil.hash;
import static dr4kun3.cosmiccore.client.RenderUtil.lerp;
import static dr4kun3.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Ultimativer Kern - Schwarzes Loch wie in echt: Materie faellt auf Kepler-Spiralen in den Horizont (wird dabei
 * schneller, heisser und zu Schlieren gestreckt, kurz vor dem Horizont verblasst sie), Staub umkreist das Loch auf
 * unterschiedlich geneigten Bahnen, und gelegentlich wird ein Klumpen zu einem langen Gezeitenstrom zerrissen.
 * Alle Teilchen haben eigene Radien, Geschwindigkeiten und Phasen, damit nichts deckungsgleich uebereinanderliegt.
 */
public final class BlackHoleFx {
    /** Nur statische Zeichenroutinen - keine Instanzen. */
    private BlackHoleFx() {}

    private static final float[] COOL_DEF = {0.55F, 0.35F, 1.0F};
    private static float[] COOL = COOL_DEF;
    private static final float[] WARM_DEF = {1.0F, 0.6F, 0.2F};
    private static float[] WARM = WARM_DEF;
    private static final float[] HOT_DEF = {1.0F, 0.95F, 0.85F};
    private static float[] HOT = HOT_DEF;
    private static final float[] DUST_DEF = {0.75F, 0.8F, 1.0F};
    private static float[] DUST = DUST_DEF;

    /** Uebernimmt die Kanalfarbe "Partikel" des Farb-Moduls (sonst bleibt die eigene Farbwelt). */
    private static void colors() {
        int ch = dr4kun3.cosmiccore.color.CoreColors.PARTICLES;
        COOL = ColorOverride.dark(ch, COOL_DEF, 0.35F);
        WARM = ColorOverride.get(ch, WARM_DEF);
        HOT = ColorOverride.light(ch, HOT_DEF, 0.7F);
        DUST = ColorOverride.light(ch, DUST_DEF, 0.4F);
    }

    /**
     * Einfallende Materie in der Scheibenebene: Teilchen spiralen von aussen beschleunigend bis in den Horizont und
     * ziehen einen Lichtschweif.
     *
     * @param m Matrix der gekippten Scheibenebene (y = Scheibennormale)
     * @param vc Puffer fuer additives Leuchten
     * @param rh Radius des Ereignishorizonts
     * @param inner Innenradius der Scheibe
     * @param outer Aussenradius der Scheibe
     * @param t Animationszeit in Ticks
     * @param energy Helligkeit 0..1
     */
    public static void infall(Matrix4f m, VertexConsumer vc, float rh, float inner, float outer, float t, float energy) {
        colors();
        int n = RenderQuality.scale(42, 14);
        for (int k = 0; k < n; k++) {
            float life = 160.0F + 120.0F * (hash(k * 7) * 0.5F + 0.5F);  // Ticks bis in den Horizont
            float s = Mth.frac(t / life + hash(k * 13) * 0.5F + 0.5F);    // 0 = Start aussen, 1 = Horizont
            float[] p = infallPos(k, s, rh, inner, outer);
            float[] q = infallPos(k, Math.max(0.0F, s - 0.035F - 0.05F * s), rh, inner, outer);
            float heat = s * s;
            float[] col = s < 0.5F ? lerp(COOL, WARM, s * 2.0F) : lerp(WARM, HOT, (s - 0.5F) * 2.0F);
            // einblenden aussen, kurz vor dem Horizont rotverschoben dunkler
            float a = energy * Mth.clamp(s * 6.0F, 0.0F, 1.0F) * (1.0F - (float) Math.pow(s, 6)) * (0.45F + 0.55F * heat);
            segment(vc, m, q[0], q[1], q[2], p[0], p[1], p[2], 0.006F + 0.012F * heat, col[0], col[1], col[2], 0.0F, a);
            cube(vc, m, p[0], p[1], p[2], 0.01F + 0.012F * heat, col[0], col[1], col[2], a);
        }
    }

    /**
     * Position eines einfallenden Teilchens: Radius schrumpft zum Horizont, der Winkel waechst nach Kepler (innen
     * rasend schnell).
     *
     * @param k Nummer des Teilchens (fuer feste Zufallswerte)
     * @param s Fortschritt 0 (aussen) bis 1 (am Horizont)
     * @param rh Radius des Ereignishorizonts
     * @param inner Innenradius der Scheibe
     * @param outer Aussenradius der Scheibe
     * @return {x, y, z} in der Scheibenebene
     */
    private static float[] infallPos(int k, float s, float rh, float inner, float outer) {
        float r0 = outer * (0.85F + 0.3F * (hash(k * 3) * 0.5F + 0.5F));
        float r = Mth.lerp((float) Math.pow(s, 1.6), r0, rh * 1.02F);
        // Keplersch: Winkel waechst mit 1/r^1.5 - innen rasend schnell
        float ang = hash(k * 5) * Mth.PI + 0.9F * ((float) Math.pow(r0 / r, 1.5) - 1.0F);
        float y = hash(k * 11) * 0.12F * r0 * (1.0F - s);
        return new float[]{Mth.cos(ang) * r, y, Mth.sin(ang) * r};
    }

    /**
     * Staub auf geneigten Kepler-Bahnen um das Loch - jede Bahn mit eigenem Radius, innen schneller.
     *
     * @param m Matrix im Weltrahmen (Ursprung in der Kernmitte)
     * @param vc Puffer fuer additives Leuchten
     * @param rh Radius des Ereignishorizonts
     * @param reach Radius der Kammer (aeusserste Bahn)
     * @param t Animationszeit in Ticks
     * @param energy Helligkeit 0..1
     */
    public static void orbiters(Matrix4f m, VertexConsumer vc, float rh, float reach, float t, float energy) {
        colors();
        int n = RenderQuality.scale(28, 10);
        for (int k = 0; k < n; k++) {
            float f = (k + 0.5F) / n; // Radien gleichmaessig verteilt - keine zwei Teilchen auf derselben Bahn
            float r = Mth.lerp(f, rh * 2.2F, reach * 0.9F);
            float omega = 0.05F * (float) Math.pow(rh * 2.2F / r, 1.5) * (k % 3 == 0 ? -1.0F : 1.0F);
            float incl = hash(k * 17) * 70.0F, node = (hash(k * 19) * 0.5F + 0.5F) * 360.0F;
            float ang = t * omega + k * 2.39996F;
            float tw = 0.55F + 0.45F * Mth.sin(t * (0.05F + 0.03F * f) + k * 1.3F);
            float[] p = RenderUtil.orbitPoint(r, ang, incl, node);
            float[] q = RenderUtil.orbitPoint(r, ang - Math.signum(omega) * (0.12F + 0.1F * (1.0F - f)), incl, node);
            float[] col = lerp(HOT, DUST, f);
            float a = energy * tw * (0.8F - 0.4F * f);
            segment(vc, m, q[0], q[1], q[2], p[0], p[1], p[2], 0.006F, col[0], col[1], col[2], 0.0F, a * 0.7F);
            cube(vc, m, p[0], p[1], p[2], 0.008F + 0.008F * (1.0F - f), col[0], col[1], col[2], a);
        }
    }

    /**
     * Gezeitenstrom: etwa alle 10 Sekunden wird ein Klumpen zu einem langen Faden gestreckt und spiralt hinein.
     *
     * @param m Matrix der Scheibenebene
     * @param vc Puffer fuer additives Leuchten
     * @param rh Radius des Ereignishorizonts
     * @param inner Innenradius der Scheibe
     * @param outer Aussenradius der Scheibe
     * @param t Animationszeit in Ticks
     * @param energy Helligkeit 0..1
     */
    public static void tidalStream(Matrix4f m, VertexConsumer vc, float rh, float inner, float outer, float t, float energy) {
        colors();
        final float period = 200.0F;
        int cycle = (int) Math.floor(t / period);
        float s = (t - cycle * period) / period;
        if (s > 0.75F) return; // Pause bis zum naechsten Ereignis
        float e = s / 0.75F;
        int seed = cycle * 977;
        float r0 = outer * 1.1F, startAng = hash(seed) * Mth.PI;
        float y0 = hash(seed + 3) * 0.2F * outer;
        int pieces = 26;
        float[] prev = null;
        for (int i = 0; i < pieces; i++) {
            // Kopf ist weiter gefallen als der Schwanz -> der Klumpen zieht sich zum Faden auseinander
            float lag = i / (float) pieces * 0.45F * e;
            float f = Mth.clamp(e - lag, 0.0F, 1.0F);
            float r = Mth.lerp((float) Math.pow(f, 1.4), r0, rh * 1.05F);
            float ang = startAng + 1.1F * ((float) Math.pow(r0 / r, 1.5) - 1.0F);
            float[] p = {Mth.cos(ang) * r, y0 * (1.0F - f), Mth.sin(ang) * r};
            float[] col = lerp(WARM, HOT, f);
            float a = energy * (1.0F - i / (float) pieces) * Mth.clamp(e * 5.0F, 0.0F, 1.0F) * (1.0F - (float) Math.pow(f, 8));
            if (prev != null) segment(vc, m, prev[0], prev[1], prev[2], p[0], p[1], p[2], 0.02F * (1.0F - i / (float) pieces) + 0.005F,
                    col[0], col[1], col[2], a, a);
            prev = p;
        }
    }
}
