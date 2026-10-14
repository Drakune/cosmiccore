package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Der ferne Planet des Void-Titanen und sein Strahl: Hoch ueber dem Reaktor kreist der Planet; seit der Titan im
 * Ultimate-Modus erwacht ist, faellt aus ihm dauerhaft ein Strahl senkrecht in den Sternentrichter.
 * Alle Koordinaten relativ zur Kernmitte. Ferne Objekte werden maszstabsgetreu naeher an die Kamera gezeichnet
 * (wie eine Himmelskulisse), damit sie nicht hinter der Sichtweite abgeschnitten werden.
 */
public final class TitanBeam {
    private TitanBeam() {}

    /** Planet hoch ueber dem Reaktor. */
    public static final Vector3f PLANET = new Vector3f(0.0F, 700.0F, 0.0F);
    public static final float PLANET_R = 70.0F;
    /** Fuesse des Titanen an der Unterseite des Planeten (er haengt kopfueber, Kopf Richtung Erde). */
    public static final Vector3f TITAN_FEET = new Vector3f(0.0F, 700.0F - PLANET_R, 0.0F);
    /** Massstab des Titanen (Modell ~6 Bloecke -> ~57 Bloecke gross). */
    public static final float TITAN_SCALE = 10.0F;
    /** Energiekugel ueber seinem Kopf (Knochen "orb", 92 px) - hier beginnt der Strahl. */
    public static final Vector3f ORB = new Vector3f(TITAN_FEET).sub(0.0F, 92.0F / 16.0F * TITAN_SCALE, 0.0F);
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

    /**
     * Der Planet: Oberflaeche aus Sternenfeld, violette Atmosphaere, ein geneigtes Ringsystem. crack 0..1 = aufbrechende
     * Kruste an der Unterseite (dort steigt der Titan empor). m muss bereits der Himmelskulissen-Rahmen sein.
     */
    public static void planet(MultiBufferSource buffers, Matrix4f m, float t, float crack, float alpha) {
        Matrix4f pm = new Matrix4f(m).translate(PLANET.x, PLANET.y, PLANET.z).rotateY(t * 0.0008F);
        RenderUtil.sphere(buffers.getBuffer(RenderType.endGateway()), pm, PLANET_R, 20, 32);
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        RenderUtil.glowSphere(glow, pm, PLANET_R * 1.03F, 16, 24, VOID, 0.22F * alpha);
        RenderUtil.glowSphere(glow, pm, PLANET_R * 1.1F, 16, 24, CYAN, 0.07F * alpha);
        // Ringsystem
        Matrix4f rm = new Matrix4f(pm).rotateX(0.35F).rotateZ(0.2F);
        for (int i = 0; i < 6; i++) {
            float rr = PLANET_R * (1.45F + i * 0.13F);
            RenderUtil.flatCircle(glow, rm, rr, 0.6F + 0.4F * (i % 2), 96, i % 2 == 0 ? VOID : CYAN, (0.35F - i * 0.04F) * alpha);
        }
        // aufbrechende Kruste: gleissender Spalt an der Unterseite
        if (crack > 0.0F) {
            Matrix4f cm = new Matrix4f(m).translate(PLANET.x, PLANET.y - PLANET_R, PLANET.z);
            RenderUtil.glowSphere(glow, cm, 6.0F + 14.0F * crack, 12, 16, VOID, 0.5F * crack);
            RenderUtil.glowSphere(glow, cm, 3.0F + 6.0F * crack, 10, 14, WHITE, 0.6F * crack);
            for (int k = 0; k < 10; k++) {
                float a = k * Mth.TWO_PI / 10 + RenderUtil.hash(k) * 0.3F;
                float len = (14.0F + 10.0F * RenderUtil.hash(k * 3)) * crack;
                segment(glow, cm, 0, 0.5F, 0, Mth.cos(a) * len, 0.5F, Mth.sin(a) * len, 0.5F, CYAN[0], CYAN[1], CYAN[2], 0.8F * crack, 0.0F);
            }
        }
    }
}
