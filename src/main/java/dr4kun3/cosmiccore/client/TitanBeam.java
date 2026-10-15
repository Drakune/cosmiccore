package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Der Strahl des Void-Titanen: Seit er im Ultimate-Modus erwacht ist, faellt aus einem gleissenden Lichtpunkt hoch
 * am Himmel ein Strahl senkrecht in den Sternentrichter - solange der Ultimate-Modus aktiv ist.
 * Alle Koordinaten relativ zur Kernmitte. Ferne Punkte werden maszstabsgetreu naeher an die Kamera gezeichnet
 * (wie eine Himmelskulisse), damit sie nicht hinter der Sichtweite abgeschnitten werden.
 */
public final class TitanBeam {
    private TitanBeam() {}

    /** Austrittspunkt des Strahls hoch ueber dem Reaktor (das Tor in das andere Universum). */
    public static final Vector3f SKY_TOP = new Vector3f(0.0F, 420.0F, 0.0F);
    /** Groesste Zeichendistanz fuer ferne Objekte. */
    public static final float SKY_DIST = 280.0F;

    private static final float[] VOID = {0.55F, 0.25F, 1.0F};
    private static final float[] CYAN = {0.45F, 0.95F, 1.0F};
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};

    /** Faktor, um den ein Punkt zur Kamera hin gezogen wird (1 = in Sichtweite). */
    public static float skyScale(Vector3f point, Vector3f cam) {
        float d = new Vector3f(point).sub(cam).length();
        return d > SKY_DIST ? SKY_DIST / d : 1.0F;
    }

    /** Matrix, die alles um die Kamera skaliert - Winkelgroessen bleiben erhalten. */
    public static Matrix4f skyFrame(Matrix4f m, Vector3f cam, float scale) {
        return new Matrix4f(m).translate(cam.x, cam.y, cam.z).scale(scale).translate(-cam.x, -cam.y, -cam.z);
    }

    /** Punkt, wie er in der Himmelskulisse erscheint. */
    public static Vector3f skyPoint(Vector3f point, Vector3f cam, float scale) {
        return new Vector3f(point).sub(cam).mul(scale).add(cam);
    }

    /**
     * Strahl von from nach to. reach 0..1 = wie weit er schon ausgefahren ist; intensity skaliert die Helligkeit
     * (die Cutscene laesst ihn beim Schuss aufgleissen).
     */
    public static void beam(Matrix4f m, VertexConsumer vc, Vector3f from, Vector3f to, float t, float reach, float intensity) {
        if (reach <= 0.0F || intensity <= 0.0F) return;
        Vector3f end = new Vector3f(from).lerp(to, Mth.clamp(reach, 0.0F, 1.0F));
        float flick = 0.9F + 0.1F * Mth.sin(t * 1.7F) * Mth.sin(t * 0.43F);
        float a = intensity * flick;
        segment(vc, m, from.x, from.y, from.z, end.x, end.y, end.z, 0.06F, WHITE[0], WHITE[1], WHITE[2], 0.9F * a, a);
        segment(vc, m, from.x, from.y, from.z, end.x, end.y, end.z, 0.22F, CYAN[0], CYAN[1], CYAN[2], 0.3F * a, 0.5F * a);
        segment(vc, m, from.x, from.y, from.z, end.x, end.y, end.z, 0.6F, VOID[0], VOID[1], VOID[2], 0.1F * a, 0.18F * a);
        // Energiewellen laufen den Strahl hinab
        float len = new Vector3f(end).sub(from).length();
        int pulses = 8;
        for (int k = 0; k < pulses; k++) {
            float s = Mth.frac(t * 0.01F + k / (float) pulses);
            if (s > reach) continue;
            Vector3f p = new Vector3f(from).lerp(to, s);
            Vector3f q = new Vector3f(from).lerp(to, Math.max(0.0F, s - 6.0F / Math.max(1.0F, len)));
            segment(vc, m, q.x, q.y, q.z, p.x, p.y, p.z, 0.4F, CYAN[0], CYAN[1], CYAN[2], 0.0F, 0.5F * a);
        }
    }

    /** Wo der Strahl in den Trichter muendet: leuchtender Wirbel. */
    public static void inlet(Matrix4f m, VertexConsumer vc, float mouthY, float t, float intensity) {
        Matrix4f im = new Matrix4f(m).translate(0, mouthY, 0);
        RenderUtil.glowSphere(vc, im, 0.35F + 0.05F * Mth.sin(t * 0.3F), 8, 12, WHITE, 0.6F * intensity);
        for (int i = 0; i < 3; i++) {
            float ph = Mth.frac(t * 0.03F + i / 3.0F);
            RenderUtil.flatCircle(vc, new Matrix4f(im).translate(0, 2.0F * (1.0F - ph), 0), 0.3F + 1.2F * (1.0F - ph), 0.03F, 32,
                    i == 1 ? VOID : CYAN, intensity * ph);
        }
    }

    /** Das Tor am Himmel: ein gleissender Stern mit kreuzfoermigem Lichtschein (m = Himmelskulissen-Rahmen). */
    public static void skyGate(Matrix4f m, VertexConsumer vc, float t, float alpha, Vector3f camRel) {
        Matrix4f gm = new Matrix4f(m).translate(SKY_TOP.x, SKY_TOP.y, SKY_TOP.z);
        float pulse = 0.85F + 0.15F * Mth.sin(t * 0.08F);
        RenderUtil.glowSphere(vc, gm, 4.0F * pulse, 10, 14, WHITE, 0.7F * alpha);
        RenderUtil.glowSphere(vc, gm, 10.0F, 10, 14, CYAN, 0.2F * alpha);
        RenderUtil.glowSphere(vc, gm, 22.0F, 10, 14, VOID, 0.08F * alpha);
        // Lichtkreuz zur Kamera ausgerichtet
        float dx = camRel.x - SKY_TOP.x, dy = camRel.y - SKY_TOP.y, dz = camRel.z - SKY_TOP.z;
        Matrix4f bm = new Matrix4f(gm).rotateY((float) Math.atan2(dx, dz)).rotateX((float) -Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        for (int k = 0; k < 4; k++) {
            float a = k * Mth.HALF_PI + t * 0.002F;
            float len = (k % 2 == 0 ? 40.0F : 26.0F) * pulse;
            segment(vc, bm, 0, 0, 0, Mth.cos(a) * len, Mth.sin(a) * len, 0, 0.8F, CYAN[0], CYAN[1], CYAN[2], 0.6F * alpha, 0.0F);
        }
    }
}
