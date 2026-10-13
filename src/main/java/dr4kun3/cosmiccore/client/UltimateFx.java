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
    private UltimateFx() {}

    private static final float[] GOLD = {1.0F, 0.82F, 0.4F};
    private static final float[] ROSE = {1.0F, 0.45F, 0.8F};
    private static final float[] CYAN = {0.4F, 0.9F, 1.0F};
    private static final float[] VIOLET = {0.6F, 0.35F, 1.0F};

    public static void render(PoseStack pose, VertexConsumer vc, float rh, float inner, float outer, float reach, float t,
                              float spin, float energy, float tilt) {
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
                float r = Mth.lerp(f, inner * 1.05F, outer * 1.15F);
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
            float orbit = reach * (0.62F + 0.13F * k);
            float speed = 0.012F / (1.0F + 0.5F * k);
            float incl = 25.0F + 30.0F * k, node = k * 110.0F;
            float ang = t * speed * spin + k * 2.0F;
            float[] p = RenderUtil.orbitPoint(orbit, ang, incl, node);
            float[] c = k == 0 ? GOLD : k == 1 ? CYAN : ROSE;
            RenderUtil.glowSphere(vc, new Matrix4f(m).translate(p[0], p[1], p[2]), 0.12F, 8, 12, c, 0.5F * energy);
            cube(vc, m, p[0], p[1], p[2], 0.05F, 1.0F, 1.0F, 1.0F, energy);
            float[] prev = p;
            for (int s = 1; s <= 14; s++) {
                float[] q = RenderUtil.orbitPoint(orbit, ang - s * 0.05F, incl, node);
                float a = energy * (1.0F - s / 15.0F) * 0.6F;
                segment(vc, m, prev[0], prev[1], prev[2], q[0], q[1], q[2], 0.025F * (1.0F - s / 16.0F), c[0], c[1], c[2], a, a);
                prev = q;
            }
        }

        // Krone: zwei senkrechte Lichtringe um den Horizont
        ring(pose, vc, rh * 2.2F, 0.012F, t * 0.6F * spin, 90.0F, 0.0F, t, GOLD, 0.7F * energy);
        ring(pose, vc, rh * 2.35F, 0.01F, -t * 0.45F * spin, 90.0F, 90.0F, t + 40.0F, GOLD, 0.55F * energy);
    }
}
