package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.cube;
import static dev.cosmiccore.client.RenderUtil.flatCircle;
import static dev.cosmiccore.client.RenderUtil.lerp;
import static dev.cosmiccore.client.RenderUtil.quadFade;
import static dev.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Traktorstrahl-Anlage: Die vier Kosmos-Laser verschmelzen zu vier Pylonen, die einen Haltering und darin einen
 * kippbaren Emitterring hoch ueber dem Sternentrichter tragen. Ohne Ziel tastet ein schwacher Kegel den Himmel ab;
 * mit Ziel neigt sich der Ring zum Asteroiden und ein Lichtkegel zieht ihn heran - Ringe wandern dabei vom
 * Asteroiden zum Emitter. Ursprung ist die Kernmitte.
 */
public final class TractorFx {
    private TractorFx() {}

    /** Hoehe des Rings ueber der Kernmitte (relativ zur Baugroesse) - muss zur Server-Logik passen. */
    public static final float RING_HEIGHT = 3.5F;
    private static final float RING_R = 1.3F;
    private static final float[] TRACTOR = {0.35F, 1.0F, 0.85F};
    private static final float[] VIOLET = {0.65F, 0.45F, 1.0F};
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};

    private static float ringY(int half) {
        return half + RING_HEIGHT;
    }

    /** Normale des Emitterrings: zum Ziel geneigt (max. ca. 60 Grad), sonst sanft taumelnd nach oben. */
    public static Vector3f normal(int half, float t, Vec3 target) {
        Vector3f up = new Vector3f(0, 1, 0);
        if (target != null) {
            Vector3f d = new Vector3f((float) target.x, (float) (target.y - ringY(half)), (float) target.z);
            if (d.lengthSquared() > 1.0E-4F) return up.lerp(d.normalize(), 0.65F).normalize();
        }
        return new Vector3f(0.18F * Mth.sin(t * 0.02F), 1.0F, 0.18F * Mth.cos(t * 0.02F)).normalize();
    }

    public static void solid(PoseStack pose, MultiBufferSource buffers, int half, float t, Vector3f n) {
        float y = ringY(half);
        VertexConsumer portal = buffers.getBuffer(RenderType.endPortal());
        Matrix4f m = pose.last().pose();
        for (int k = 0; k < 4; k++) {
            float ang = k * Mth.HALF_PI;
            float x = Mth.cos(ang), z = Mth.sin(ang);
            // Sockel auf dem Laser-Block, Pylon schraeg nach aussen zum Haltering
            pose.pushPose();
            pose.translate(x, half + 0.6F, z);
            RenderUtil.starCylinder(portal, pose.last().pose(), 0.38F, 0.1F, 16);
            pose.popPose();
            RenderUtil.starStrut(portal, m, x, half + 0.65F, z, x * (RING_R + 0.3F), y, z * (RING_R + 0.3F), 0.08F);
        }
        // Haltering (waagerecht)
        pose.pushPose();
        pose.translate(0.0, y, 0.0);
        pose.mulPose(new Quaternionf().rotationX(Mth.HALF_PI));
        RenderUtil.starTorus(portal, pose.last().pose(), RING_R + 0.3F, 0.07F, 40, 8);
        pose.popPose();
        // Emitterring (kippt zum Ziel)
        pose.pushPose();
        pose.translate(0.0, y, 0.0);
        pose.mulPose(new Quaternionf().rotationTo(0, 0, 1, n.x, n.y, n.z));
        pose.mulPose(new Quaternionf().rotationZ(t * 0.01F));
        RenderUtil.starTorus(portal, pose.last().pose(), RING_R, 0.12F, 40, 10);
        pose.popPose();
    }

    public static void glow(PoseStack pose, VertexConsumer vc, int half, float t, Vector3f n, Vec3 target, float targetSize,
                            float energy) {
        float y = ringY(half);
        boolean pulling = target != null;
        Matrix4f m = pose.last().pose();
        float pulse = 0.6F + 0.4F * Mth.sin(t * (pulling ? 0.4F : 0.1F));

        // Haltering-Rand und Energie, die die Pylonen hochlaeuft
        flatCircle(vc, new Matrix4f(m).translate(0, y, 0), RING_R + 0.3F, 0.02F, 64, VIOLET, energy * 0.6F);
        for (int k = 0; k < 4; k++) {
            float ang = k * Mth.HALF_PI;
            float x = Mth.cos(ang), z = Mth.sin(ang);
            float s = Mth.frac(t * (pulling ? 0.05F : 0.015F) + k * 0.25F);
            float px = Mth.lerp(s, x, x * (RING_R + 0.3F)), py = Mth.lerp(s, half + 0.65F, y), pz = Mth.lerp(s, z, z * (RING_R + 0.3F));
            cube(vc, m, px, py, pz, 0.06F, TRACTOR[0], TRACTOR[1], TRACTOR[2], energy * Mth.sin(s * Mth.PI));
            flatCircle(vc, new Matrix4f(m).translate(x, half + 0.72F, z), 0.38F, 0.015F, 24, TRACTOR, energy * 0.5F);
        }

        // Basis des Emitterrings
        Vector3f u = new Vector3f(n).cross(Math.abs(n.y) > 0.95F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        Vector3f v = new Vector3f(n).cross(u).normalize();
        Vector3f c = new Vector3f(0, y, 0);
        // leuchtender Innenrand
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            Vector3f p0 = ringPoint(c, u, v, RING_R - 0.13F, a0), p1 = ringPoint(c, u, v, RING_R - 0.13F, a1);
            segment(vc, m, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, 0.03F, TRACTOR[0], TRACTOR[1], TRACTOR[2], energy * pulse, energy * pulse);
        }

        if (pulling) {
            // Traktorkegel vom Emitterring zum Asteroiden
            Vector3f tp = new Vector3f((float) target.x, (float) target.y, (float) target.z);
            float tr = Math.max(0.4F, targetSize * 0.55F);
            int lines = 20;
            for (int i = 0; i < lines; i++) {
                float a0 = Mth.TWO_PI * i / lines + t * 0.01F, a1 = Mth.TWO_PI * (i + 1) / lines + t * 0.01F;
                Vector3f r0 = ringPoint(c, u, v, RING_R * 0.9F, a0), r1 = ringPoint(c, u, v, RING_R * 0.9F, a1);
                Vector3f q0 = ringPoint(tp, u, v, tr, a0), q1 = ringPoint(tp, u, v, tr, a1);
                // Kegelflaeche (zart) und Kanten
                quadFade(vc, m, r0.x, r0.y, r0.z, r1.x, r1.y, r1.z, q1.x, q1.y, q1.z, q0.x, q0.y, q0.z,
                        TRACTOR[0], TRACTOR[1], TRACTOR[2], 0.12F * energy, 0.05F * energy);
                if (i % 2 == 0) {
                    segment(vc, m, r0.x, r0.y, r0.z, q0.x, q0.y, q0.z, 0.012F, TRACTOR[0], TRACTOR[1], TRACTOR[2], 0.5F * energy, 0.25F * energy);
                }
            }
            // Ringe wandern vom Asteroiden zum Emitter (der Zug wird sichtbar)
            for (int k = 0; k < 5; k++) {
                float s = Mth.frac(t * 0.03F + k / 5.0F); // 0 = am Asteroiden, 1 = am Ring
                Vector3f rc = new Vector3f(tp).lerp(c, s);
                float rr = Mth.lerp(s, tr, RING_R * 0.9F);
                float a = energy * Mth.sin(s * Mth.PI) * 0.8F;
                float[] col = lerp(TRACTOR, VIOLET, s);
                for (int i = 0; i < 32; i++) {
                    float a0 = Mth.TWO_PI * i / 32, a1 = Mth.TWO_PI * (i + 1) / 32;
                    Vector3f p0 = ringPoint(rc, u, v, rr, a0), p1 = ringPoint(rc, u, v, rr, a1);
                    segment(vc, m, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, 0.02F, col[0], col[1], col[2], a, a);
                }
            }
            // heller Kern des Strahls und Halo um den Asteroiden
            segment(vc, m, c.x, c.y, c.z, tp.x, tp.y, tp.z, 0.04F, WHITE[0], WHITE[1], WHITE[2], 0.6F * energy, 0.6F * energy);
            RenderUtil.glowSphere(vc, new Matrix4f(m).translate(tp.x, tp.y, tp.z), tr * 1.25F, 8, 12, TRACTOR, 0.15F * energy * pulse);
        } else {
            // Abtasten: kurzer, schwacher Kegel nach oben
            Vector3f tip = new Vector3f(n).mul(4.0F).add(c);
            for (int i = 0; i < 12; i++) {
                float a0 = Mth.TWO_PI * i / 12 + t * 0.02F;
                Vector3f r0 = ringPoint(c, u, v, RING_R * 0.9F, a0);
                segment(vc, m, r0.x, r0.y, r0.z, tip.x, tip.y, tip.z, 0.01F, TRACTOR[0], TRACTOR[1], TRACTOR[2], 0.3F * energy, 0.0F);
            }
        }
    }

    private static Vector3f ringPoint(Vector3f c, Vector3f u, Vector3f v, float r, float a) {
        return new Vector3f(u).mul(Mth.cos(a) * r).add(new Vector3f(v).mul(Mth.sin(a) * r)).add(c);
    }
}
