package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.cube;
import static dr4kun3.cosmiccore.client.RenderUtil.flatCircle;
import static dr4kun3.cosmiccore.client.RenderUtil.hash;
import static dr4kun3.cosmiccore.client.RenderUtil.lerp;
import static dr4kun3.cosmiccore.client.RenderUtil.segment;

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

    private static void particleColors() {
        int ch = dr4kun3.cosmiccore.color.CoreColors.PARTICLES;
        GOLD = ColorOverride.get(ch, GOLD_DEF);
        CYAN = ColorOverride.light(ch, CYAN_DEF, 0.45F);
        VIOLET = ColorOverride.dark(ch, VIOLET_DEF, 0.3F);
    }
    private static final float[] CYAN_DEF = {0.45F, 0.9F, 1.0F};
    private static float[] CYAN = CYAN_DEF;
    private static final float[] GOLD_DEF = {1.0F, 0.82F, 0.4F};
    private static float[] GOLD = GOLD_DEF;
    private static final float[] VIOLET_DEF = {0.7F, 0.45F, 1.0F};
    private static float[] VIOLET = VIOLET_DEF;

    public static void render(Matrix4f m, VertexConsumer vc, int size, float ph, float energy) {
        particleColors();
        if (ph < 0.0F) return;
        int stars = RenderQuality.scale(24 + size * 2, 10);
        float spread = 5.0F + size * 1.2F; // nah am Reaktor, damit man es gut erkennt
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
            segment(vc, m, q.x, q.y, q.z, p.x, p.y, p.z, 0.012F + 0.012F * (1.0F - s), c[0], c[1], c[2], 0.0F, a);
            cube(vc, m, p.x, p.y, p.z, 0.02F + 0.025F * (1.0F - s), WHITE[0], WHITE[1], WHITE[2], a);
        }
        // Lichtblitz und Ringwelle, wenn die Sterne im Kern ankommen
        float hit = Mth.clamp((ph - 0.55F) / 0.45F, 0.0F, 1.0F);
        if (hit > 0.0F) {
            float pulse = Mth.sin(hit * Mth.PI);
            RenderUtil.glowSphere(vc, m, 0.3F + 0.5F * pulse, 10, 14, WHITE, 0.3F * energy * pulse);
            flatCircle(vc, m, 0.4F + size * 0.25F * hit, 0.025F, 64, GOLD, energy * (1.0F - hit) * env);
        }
    }

    /**
     * Dauerhafter Zustrom, solange der Kern aktiv ist: feine Sterne fallen von oben aus dem All in den Trichter
     * (bzw. ohne Trichter auf die Deckenmitte) - leicht verdrillt, beschleunigend, mit Lichtschweif.
     *
     * @param mouthY Hoehe des Ziels relativ zur Kernmitte
     */
    public static void ambient(Matrix4f m, VertexConsumer vc, int size, float mouthY, float t, float energy) {
        particleColors();
        int stars = RenderQuality.scale(8 + size, 3);
        for (int k = 0; k < stars; k++) {
            float period = 60.0F + 25.0F * (0.5F + 0.5F * hash(k * 53));
            float cyc = (t + k * 31.0F) / period;
            int n = (int) cyc;
            float s = cyc - n;
            float lon = hash(k * 13 + n * 101) * Mth.PI;
            float startR = 2.0F + 6.0F * (0.5F + 0.5F * hash(k * 29 + n * 7));
            float startH = 14.0F + 18.0F * (0.5F + 0.5F * hash(k * 7 + n));
            Vector3f p = fallPos(lon, startR, startH, mouthY, s);
            Vector3f q = fallPos(lon, startR, startH, mouthY, Math.max(0.0F, s - 0.08F));
            float[] c = lerp(k % 2 == 0 ? CYAN : VIOLET, GOLD, s * s);
            float a = energy * 0.8F * Mth.sin(Math.min(1.0F, s * 1.2F) * Mth.PI * 0.5F) * (1.0F - s * s * s);
            segment(vc, m, q.x, q.y, q.z, p.x, p.y, p.z, 0.012F, c[0], c[1], c[2], 0.0F, a);
            cube(vc, m, p.x, p.y, p.z, 0.02F + 0.02F * (1.0F - s), WHITE[0], WHITE[1], WHITE[2], a);
        }
    }

    /** Fall von oben in den Trichter: Radius schrumpft auf die Oeffnung, leichte Drehung, beschleunigend. */
    private static Vector3f fallPos(float lon, float startR, float startH, float mouthY, float s) {
        float e = s * s;
        float r = Mth.lerp(e, startR, 0.15F);
        float ang = lon + e * 1.6F;
        return new Vector3f(Mth.cos(ang) * r, mouthY + startH * (1.0F - e), Mth.sin(ang) * r);
    }

    /** Flugbahn: vom Himmel in einem Wirbel (Drehung um die Hochachse) beschleunigend in die Mitte. */
    private static Vector3f starPos(float lon, float lat, float r0, float s) {
        float e = s * s;
        float r = r0 * (1.0F - e);
        float ang = lon + e * 2.2F;
        return new Vector3f(Mth.cos(lat) * Mth.cos(ang) * r, Mth.sin(lat) * r, Mth.cos(lat) * Mth.sin(ang) * r);
    }
}
