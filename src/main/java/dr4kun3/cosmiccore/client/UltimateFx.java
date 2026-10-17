package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.cube;
import static dr4kun3.cosmiccore.client.RenderUtil.hash;
import static dr4kun3.cosmiccore.client.RenderUtil.lerp;
import static dr4kun3.cosmiccore.client.RenderUtil.ring;
import static dr4kun3.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Besondere Effekte des ultimativen Kerns (11x11x11) - zusaetzlich zum normalen Bild:
 * <ul>
 *   <li><b>Spiralgalaxie:</b> drei Arme aus Sternen winden sich in der Scheibenebene um das Loch.</li>
 *   <li><b>Sonnensystem:</b> drei Sterne umkreisen den Kern auf geneigten Bahnen und ziehen Lichtschweife.</li>
 *   <li><b>Krone:</b> zwei goldene Lichtringe stehen senkrecht um den Horizont und drehen sich langsam.</li>
 * </ul>
 */
public final class UltimateFx {
    /** Nur statische Zeichenroutinen - keine Instanzen. */
    private UltimateFx() {}

    private static final float[] GOLD_DEF = {1.0F, 0.82F, 0.4F};
    private static float[] GOLD = GOLD_DEF;
    private static final float[] ROSE_DEF = {1.0F, 0.45F, 0.8F};
    private static float[] ROSE = ROSE_DEF;
    private static final float[] CYAN_DEF = {0.4F, 0.9F, 1.0F};
    private static float[] CYAN = CYAN_DEF;
    private static final float[] VIOLET = {0.6F, 0.35F, 1.0F};

    /**
     * Zeichnet die Zusatzeffekte des ultimativen Kerns: Spiralgalaxie in der Scheibenebene, drei kreisende Sterne mit
     * Lichtschweifen und eine goldene Krone um den Horizont.
     *
     * @param pose Pose-Stapel mit Ursprung in der Kernmitte
     * @param vc Puffer fuer additives Leuchten
     * @param rh Radius des Ereignishorizonts
     * @param inner Innenradius der Akkretionsscheibe
     * @param outer Aussenradius der Akkretionsscheibe
     * @param reach Radius der Kammer (aeusserste Bahn)
     * @param t Animationszeit in Ticks
     * @param spin Drehgeschwindigkeit (1 = normal, hoeher im Overdrive)
     * @param energy Helligkeit 0..1 (0 = nichts zeichnen)
     * @param tilt Neigung der Scheibe in Grad
     */
    public static void render(PoseStack pose, VertexConsumer vc, float rh, float inner, float outer, float reach, float t,
                              float spin, float energy, float tilt) {
        int ch = dr4kun3.cosmiccore.color.CoreColors.PARTICLES;
        GOLD = ColorOverride.get(ch, GOLD_DEF);
        CYAN = ColorOverride.light(ch, CYAN_DEF, 0.45F);
        ROSE = ColorOverride.dark(ch, ROSE_DEF, 0.3F);
        if (energy <= 0.01F) return;
        Matrix4f m = pose.last().pose();

        // Spiralgalaxie in der (gekippten) Scheibenebene
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(tilt));
        Matrix4f dm = pose.last().pose();
        int arms = 3, stars = RenderQuality.scale(46, 16);
        float rot = t * 0.01F * spin;
        for (int a = 0; a < arms; a++) {
            float[] prev = null;
            for (int i = 0; i < stars; i++) {
                float f = i / (float) stars;
                // ausserhalb der Akkretionsscheibe, bis kurz vor die Kammerwand - eigene Zone, keine Ueberlagerung
                float r = Mth.lerp(f, Math.max(outer * 1.02F, inner * 1.6F), Math.max(outer * 1.05F, reach * 0.96F));
                float ang = rot + a * Mth.TWO_PI / arms + (float) Math.log(1.0F + 5.0F * f) * 2.2F;
                float jitter = 0.08F * hash(a * 101 + i * 7) * r;
                float x = Mth.cos(ang) * (r + jitter), z = Mth.sin(ang) * (r + jitter);
                float y = 0.03F * hash(a * 31 + i * 13);
                float mag = 0.5F + 0.5F * hash(a * 17 + i * 3);
                float tw = 0.6F + 0.4F * Mth.sin(t * 0.2F + i);
                float[] c = lerp(lerp(GOLD, CYAN, f), ROSE, Math.max(0.0F, mag - 0.6F));
                cube(vc, dm, x, y, z, 0.012F + 0.02F * mag, c[0], c[1], c[2], energy * tw * (1.0F - 0.5F * f));
                float[] q = {x, y, z};
                if (prev != null && i % 2 == 0) {
                    segment(vc, dm, prev[0], prev[1], prev[2], x, y, z, 0.03F, c[0], c[1], c[2], 0.12F * energy, 0.12F * energy);
                }
                prev = q;
            }
        }
        pose.popPose();

        // Sonnensystem: drei Sterne auf geneigten Bahnen
        for (int k = 0; k < 3; k++) {
            // deutlich verschiedene Bahnen (Radius, Neigung, Richtung) und zeitlich versetztes Aufleuchten
            float orbit = reach * (0.58F + 0.17F * k);
            float speed = (k == 1 ? -0.011F : 0.013F) / (1.0F + 0.6F * k);
            float incl = 20.0F + 35.0F * k, node = k * 125.0F;
            float ang = t * speed * spin + k * 2.1F;
            float vis = 0.35F + 0.65F * (0.5F + 0.5F * Mth.sin(t * 0.017F + k * 2.1F));
            float[] p = RenderUtil.orbitPoint(orbit, ang, incl, node);
            float[] c = k == 0 ? GOLD : k == 1 ? CYAN : ROSE;
            RenderUtil.glowSphere(vc, new Matrix4f(m).translate(p[0], p[1], p[2]), 0.1F, 8, 12, c, 0.45F * energy * vis);
            cube(vc, m, p[0], p[1], p[2], 0.045F, 1.0F, 1.0F, 1.0F, energy * vis);
            float[] prev = p;
            for (int s = 1; s <= 9; s++) {
                float[] q = RenderUtil.orbitPoint(orbit, ang - Math.signum(speed) * s * 0.05F, incl, node);
                float a = energy * vis * (1.0F - s / 10.0F) * 0.5F;
                segment(vc, m, prev[0], prev[1], prev[2], q[0], q[1], q[2], 0.025F * (1.0F - s / 16.0F), c[0], c[1], c[2], a, a);
                prev = q;
            }
        }

        // Krone: zwei senkrechte Lichtringe um den Horizont
        // Krone: ein einzelner senkrechter Ring dicht am Horizont (innerhalb der Scheibe, ueber den anderen Effekten)
        ring(pose, vc, rh * 1.3F, 0.01F, t * 0.6F * spin, 90.0F, 0.0F, t, GOLD, 0.6F * energy);
    }
}
