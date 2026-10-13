package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.annulusXY;
import static dev.cosmiccore.client.RenderUtil.flatCircle;
import static dev.cosmiccore.client.RenderUtil.hash;
import static dev.cosmiccore.client.RenderUtil.quadFade;
import static dev.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Zusatzeffekte je Overdrive-Stufe (kumulativ):
 * <ol>
 *   <li><b>Ueberladung:</b> Blitze knistern rund um den Ereignishorizont.</li>
 *   <li><b>Supernova:</b> Sternstrahlen-Kranz um das Loch (zusaetzlich zerfaellt die Scheibe in Ringe).</li>
 *   <li><b>Quasar:</b> Spiralarme reichen weit aus der Kammer hinaus, Gravitationswellen breiten sich aus,
 *       der Horizont bekommt eine pulsierende Aura (zusaetzlich riesige Jets).</li>
 * </ol>
 */
public final class OverdriveFx {
    private OverdriveFx() {}

    public static void render(PoseStack pose, VertexConsumer vc, int od, float rh, float inner, float outer, float reach,
                              int half, float t, float energy, float heat, float camYaw, float camPitch, float[][] pal) {
        if (od <= 0 || energy <= 0.01F) return;
        float[] hot = pal[0], warm = pal[1], cool = pal[2];

        // Billboard-Ebene zum Betrachter
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(camYaw));
        pose.mulPose(Axis.XP.rotation(-camPitch));
        Matrix4f bm = pose.last().pose();

        // Stufe 1: Blitze um den Horizont
        int seed = (int) (t * 0.7F);
        int arcs = 4 + od * 3;
        for (int k = 0; k < arcs; k++) {
            float a0 = hash(seed * 13 + k) * Mth.PI;
            float r0 = rh * 1.15F;
            float px = Mth.cos(a0) * r0, py = Mth.sin(a0) * r0;
            for (int i = 1; i <= 5; i++) {
                float a = a0 + i * 0.18F;
                float r = r0 + rh * (0.25F + 0.5F * Math.abs(hash(seed * 7 + k * 5 + i)));
                float nx = Mth.cos(a) * r, ny = Mth.sin(a) * r;
                segment(vc, bm, px, py, 0, nx, ny, 0, 0.012F, 1.0F, 1.0F, 1.0F, energy, energy * 0.7F);
                px = nx;
                py = ny;
            }
        }

        // Stufe 2: Sternstrahlen-Kranz
        if (od >= 2) {
            int spikes = 12;
            for (int k = 0; k < spikes; k++) {
                float ang = k * Mth.TWO_PI / spikes + t * 0.003F;
                float len = rh * (4.5F + 2.0F * Mth.sin(t * 0.05F + k * 1.7F)) * (k % 2 == 0 ? 1.0F : 0.6F);
                float w = rh * 0.09F;
                float ca = Mth.cos(ang), sa = Mth.sin(ang);
                quadFade(vc, bm, ca * rh - sa * w, sa * rh + ca * w, 0, ca * rh + sa * w, sa * rh - ca * w, 0,
                        ca * len, sa * len, 0, ca * len, sa * len, 0, warm[0], warm[1], warm[2], 0.75F * energy, 0.0F);
            }
        }

        // Stufe 3: pulsierende Aura um den Horizont
        if (od >= 3) {
            float pulse = 0.5F + 0.5F * Mth.sin(t * 0.25F);
            for (int s = 0; s < 64; s++) {
                float a0 = Mth.TWO_PI * s / 64, a1 = Mth.TWO_PI * (s + 1) / 64;
                annulusXY(vc, bm, rh * 1.2F, rh * (2.2F + pulse), a0, a1, cool, 0.6F * energy, 0.0F);
            }
        }
        pose.popPose();

        if (od >= 3) {
            Matrix4f m = pose.last().pose();
            float shell = half + 0.5F;
            // Spiralarme reichen weit aus der Kammer hinaus
            int arms = 4;
            for (int k = 0; k < arms; k++) {
                float px = 0, pz = 0;
                boolean first = true;
                for (int i = 0; i <= 40; i++) {
                    float f = i / 40.0F;
                    float r = Mth.lerp(f, outer, shell * 2.2F);
                    float ang = k * Mth.TWO_PI / arms + f * 4.5F - t * 0.01F;
                    float x = Mth.cos(ang) * r, z = Mth.sin(ang) * r;
                    if (!first) {
                        float[] c = RenderUtil.lerp(hot, cool, f);
                        float a = energy * (1.0F - f) * 0.8F;
                        segment(vc, m, px, 0, pz, x, 0, z, 0.06F + 0.15F * f, c[0], c[1], c[2], a, a);
                    }
                    px = x;
                    pz = z;
                    first = false;
                }
            }
            // Gravitationswellen: Ringe, die sich weit ueber die Struktur hinaus ausbreiten
            for (int k = 0; k < 3; k++) {
                float ph = Mth.frac(t * 0.006F + k / 3.0F);
                float r = Mth.lerp(ph, outer, shell * 3.5F);
                flatCircle(vc, m, r, 0.05F + 0.1F * ph, 96, warm, energy * (1.0F - ph) * 0.7F);
            }
        }
    }
}
