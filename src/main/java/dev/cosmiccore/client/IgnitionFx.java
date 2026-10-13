package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.cube;
import static dev.cosmiccore.client.RenderUtil.flatCircle;
import static dev.cosmiccore.client.RenderUtil.hash;
import static dev.cosmiccore.client.RenderUtil.lerp;
import static dev.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Zuenden eines Kerns: Sterne stuerzen aus dem Himmel in einem leichten Wirbel in die Mitte des Reaktors und
 * gehen dort in einem Lichtblitz auf. Ursprung ist die Kernmitte, Fortschritt ph 0..1.
 */
public final class IgnitionFx {
    private IgnitionFx() {}

    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};
    private static final float[] CYAN = {0.45F, 0.9F, 1.0F};
    private static final float[] GOLD = {1.0F, 0.82F, 0.4F};
    private static final float[] VIOLET = {0.7F, 0.45F, 1.0F};

    public static void render(Matrix4f m, VertexConsumer vc, int size, float ph, float energy) {
        if (ph < 0.0F) return;
        int stars = 30 + size * 3;
        float spread = 18.0F + size * 3.0F;
        float env = Mth.clamp((1.0F - ph) / 0.2F, 0.0F, 1.0F);
        for (int k = 0; k < stars; k++) {
            float delay = (0.5F + 0.5F * hash(k * 37)) * 0.45F;
            float s = Mth.clamp((ph - delay) / 0.5F, 0.0F, 1.0F);
            if (s <= 0.0F || s >= 1.0F) continue;
            // Startpunkt auf der oberen Halbkugel um den Reaktor
            float lon = hash(k * 13) * Mth.PI, lat = (0.15F + 0.75F * (0.5F + 0.5F * hash(k * 29))) * Mth.HALF_PI;
            float r0 = spread * (0.7F + 0.3F * (0.5F + 0.5F * hash(k * 7)));
            Vector3f p = starPos(lon, lat, r0, s);
            Vector3f q = starPos(lon, lat, r0, Math.max(0.0F, s - 0.08F));
            float[] c = lerp(k % 2 == 0 ? CYAN : VIOLET, GOLD, s * s);
            float a = energy * Mth.sin(s * Mth.PI * 0.5F + Mth.HALF_PI * 0.2F);
            segment(vc, m, q.x, q.y, q.z, p.x, p.y, p.z, 0.05F + 0.04F * (1.0F - s), c[0], c[1], c[2], 0.0F, a);
            cube(vc, m, p.x, p.y, p.z, 0.07F + 0.08F * (1.0F - s), WHITE[0], WHITE[1], WHITE[2], a);
        }
        // Lichtblitz und Ringwelle, wenn die Sterne im Kern ankommen
        float hit = Mth.clamp((ph - 0.55F) / 0.45F, 0.0F, 1.0F);
        if (hit > 0.0F) {
            float pulse = Mth.sin(hit * Mth.PI);
            RenderUtil.glowSphere(vc, m, 0.4F + 0.9F * pulse, 10, 14, WHITE, 0.35F * energy * pulse);
            flatCircle(vc, m, 0.5F + size * 0.5F * hit, 0.04F, 64, GOLD, energy * (1.0F - hit) * env);
        }
    }

    /** Flugbahn: vom Himmel in einem Wirbel (Drehung um die Hochachse) beschleunigend in die Mitte. */
    private static Vector3f starPos(float lon, float lat, float r0, float s) {
        float e = s * s;
        float r = r0 * (1.0F - e);
        float ang = lon + e * 2.2F;
        return new Vector3f(Mth.cos(lat) * Mth.cos(ang) * r, Mth.sin(lat) * r, Mth.cos(lat) * Mth.sin(ang) * r);
    }
}
