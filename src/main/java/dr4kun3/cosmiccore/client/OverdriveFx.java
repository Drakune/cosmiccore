package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.annulusXY;
import static dr4kun3.cosmiccore.client.RenderUtil.hash;
import static dr4kun3.cosmiccore.client.RenderUtil.quadFade;
import static dr4kun3.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Overdrive als eine durchgehende Steigerung desselben Bildes (keine neuen Fremdkoerper pro Stufe):
 * Mit jeder Stufe wird die Korona um den Horizont dichter, ein sanfter Strahlenkranz waechst heraus und ab
 * Stufe 3 atmet eine Aura im Takt. Farbe und Scheibe (zweite Scheibe ab Stufe 1, Ringstruktur ab Stufe 2)
 * steuert der Kern-Renderer ueber die Palette.
 */
public final class OverdriveFx {
    private OverdriveFx() {}

    public static void render(PoseStack pose, VertexConsumer vc, int od, float rh, float t, float energy,
                              float camYaw, float camPitch, float[][] pal) {
        if (od <= 0 || energy <= 0.01F) return;
        float[] hot = pal[0], warm = pal[1], cool = pal[2];
        float breath = 0.5F + 0.5F * Mth.sin(t * 0.06F);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(camYaw));
        pose.mulPose(Axis.XP.rotation(-camPitch));
        Matrix4f m = pose.last().pose();

        // Korona: feine Entladungen am Horizont, dichter mit jeder Stufe
        int seed = (int) (t * 0.35F);
        int arcs = 2 + od * 2;
        for (int k = 0; k < arcs; k++) {
            float a0 = hash(seed * 13 + k) * Mth.PI;
            float r0 = rh * 1.12F;
            float px = Mth.cos(a0) * r0, py = Mth.sin(a0) * r0;
            for (int i = 1; i <= 4; i++) {
                float a = a0 + i * 0.12F;
                float r = r0 + rh * (0.15F + 0.25F * Math.abs(hash(seed * 7 + k * 5 + i)));
                float nx = Mth.cos(a) * r, ny = Mth.sin(a) * r;
                segment(vc, m, px, py, 0, nx, ny, 0, 0.008F, hot[0], hot[1], hot[2], 0.55F * energy, 0.25F * energy);
                px = nx;
                py = ny;
            }
        }

        // sanfter Strahlenkranz (ab Stufe 2), langsam drehend
        if (od >= 2) {
            int spikes = 8;
            for (int k = 0; k < spikes; k++) {
                float ang = k * Mth.TWO_PI / spikes + t * 0.002F;
                float len = rh * (2.4F + 0.5F * od + 0.4F * Mth.sin(t * 0.04F + k));
                float w = rh * 0.06F;
                float ca = Mth.cos(ang), sa = Mth.sin(ang);
                quadFade(vc, m, ca * rh - sa * w, sa * rh + ca * w, 0, ca * rh + sa * w, sa * rh - ca * w, 0,
                        ca * len, sa * len, 0, ca * len, sa * len, 0, warm[0], warm[1], warm[2], 0.35F * energy, 0.0F);
            }
        }

        // atmende Aura (Stufe 3)
        if (od >= 3) {
            for (int s = 0; s < 64; s++) {
                float a0 = Mth.TWO_PI * s / 64, a1 = Mth.TWO_PI * (s + 1) / 64;
                annulusXY(vc, m, rh * 1.15F, rh * (1.8F + 0.4F * breath), a0, a1, cool, 0.35F * energy, 0.0F);
            }
        }
        pose.popPose();
    }
}
