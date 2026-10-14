package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Der Strahl des Void-Titanen: nach dem Erwachen des ultimativen Kerns faellt er dauerhaft aus einem Riss am Himmel in
 * den Kern. Auch die Cutscene nutzt ihn (Position des Risses, Aussehen des Strahls).
 */
public final class TitanBeam {
    private TitanBeam() {}

    /** Riss am Himmel relativ zur Kernmitte (dort erscheint der Titan). */
    public static final Vector3f RIFT = new Vector3f(64.0F, 96.0F, -48.0F);

    private static final float[] VOID = {0.55F, 0.25F, 1.0F};
    private static final float[] CYAN = {0.45F, 0.95F, 1.0F};
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};

    /**
     * Strahl von from nach to (Koordinaten relativ zur Kernmitte). reach 0..1 = wie weit er schon ausgefahren ist,
     * intensity skaliert die Helligkeit (die Cutscene laesst ihn beim Schuss kurz aufgleissen).
     */
    public static void beam(Matrix4f m, VertexConsumer vc, Vector3f from, Vector3f to, float t, float reach, float intensity) {
        if (reach <= 0.0F || intensity <= 0.0F) return;
        Vector3f end = new Vector3f(from).lerp(to, Mth.clamp(reach, 0.0F, 1.0F));
        float flick = 0.9F + 0.1F * Mth.sin(t * 1.7F) * Mth.sin(t * 0.43F);
        float a = intensity * flick;
        segment(vc, m, from.x, from.y, from.z, end.x, end.y, end.z, 0.05F, WHITE[0], WHITE[1], WHITE[2], 0.9F * a, a);
        segment(vc, m, from.x, from.y, from.z, end.x, end.y, end.z, 0.2F, CYAN[0], CYAN[1], CYAN[2], 0.35F * a, 0.45F * a);
        segment(vc, m, from.x, from.y, from.z, end.x, end.y, end.z, 0.55F, VOID[0], VOID[1], VOID[2], 0.12F * a, 0.16F * a);
        // Energiewellen, die den Strahl hinab in den Kern laufen
        float len = new Vector3f(end).sub(from).length();
        int pulses = 6;
        for (int k = 0; k < pulses; k++) {
            float s = Mth.frac(t * 0.012F + k / (float) pulses);
            if (s > reach) continue;
            Vector3f p = new Vector3f(from).lerp(to, s);
            Vector3f q = new Vector3f(from).lerp(to, Math.max(0.0F, s - 4.0F / Math.max(1.0F, len)));
            segment(vc, m, q.x, q.y, q.z, p.x, p.y, p.z, 0.35F, CYAN[0], CYAN[1], CYAN[2], 0.0F, 0.5F * a);
        }
    }

    /** Der Riss am Himmel: ein schmaler, leuchtender Spalt mit wirbelndem Rand (open 0..1). */
    public static void rift(Matrix4f m, VertexConsumer vc, Vector3f c, float t, float open, Vector3f camRel) {
        if (open <= 0.0F) return;
        // zur Kamera ausrichten (von der Position des Risses aus gesehen)
        float dx = camRel.x - c.x, dy = camRel.y - c.y, dz = camRel.z - c.z;
        float yaw = (float) Math.atan2(dx, dz), pitch = (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
        Matrix4f rm = new Matrix4f(m).translate(c.x, c.y, c.z).rotateY(yaw).rotateX(-pitch);
        float w = 6.0F * open, h = 18.0F * open;
        int seg = 48;
        for (int layer = 0; layer < 3; layer++) {
            float sc = 1.0F + layer * 0.35F;
            float[] col = layer == 0 ? WHITE : layer == 1 ? CYAN : VOID;
            float alpha = (layer == 0 ? 0.6F : layer == 1 ? 0.3F : 0.15F) * open;
            for (int i = 0; i < seg; i++) {
                float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
                float wob0 = 1.0F + 0.08F * Mth.sin(a0 * 5.0F + t * 0.1F + layer);
                float wob1 = 1.0F + 0.08F * Mth.sin(a1 * 5.0F + t * 0.1F + layer);
                segment(vc, rm, Mth.cos(a0) * w * sc * wob0, Mth.sin(a0) * h * sc * wob0, 0,
                        Mth.cos(a1) * w * sc * wob1, Mth.sin(a1) * h * sc * wob1, 0, 0.25F + 0.25F * layer,
                        col[0], col[1], col[2], alpha, alpha);
            }
        }
        // dunkles Inneres mit Sternenstaub, der herausweht
        for (int k = 0; k < 16; k++) {
            float s = Mth.frac(t * 0.01F + k / 16.0F);
            float ang = k * 2.39996F + t * 0.01F;
            float r = s * 1.6F;
            float x = Mth.cos(ang) * w * r, y = Mth.sin(ang) * h * r;
            RenderUtil.cube(vc, rm, x, y, 0, 0.12F * (1.0F - s) + 0.04F, 0.9F, 0.85F, 1.0F, open * (1.0F - s));
        }
    }
}
