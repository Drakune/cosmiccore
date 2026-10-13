package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.cube;
import static dev.cosmiccore.client.RenderUtil.flatCircle;
import static dev.cosmiccore.client.RenderUtil.lerp;
import static dev.cosmiccore.client.RenderUtil.ring;
import static dev.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Das grosse Kosmos-Laser-Geschuetz: Die vier Laser-Bloecke um die Deckenmitte verschmelzen zu einer drehenden
 * Plattform, vier Streben tragen einen Kugelkopf mit Lauf. Ohne Ziel sucht der Lauf den Himmel ab, mit Ziel
 * schwenkt er auf den Asteroiden und feuert einen gleissenden Strahl. Ursprung ist die Kernmitte.
 */
public final class BigLaserFx {
    private BigLaserFx() {}

    private static final float[] BEAM = {0.45F, 0.85F, 1.0F};
    private static final float[] CHARGE = {0.7F, 0.45F, 1.0F};
    private static final float[] IMPACT = {1.0F, 0.65F, 0.3F};
    private static final float BARREL = 2.3F;

    /** Hoehe des Kugelkopfs relativ zur Kernmitte. */
    public static float headY(int half) {
        return half + 2.6F;
    }

    /** Zielrichtung des Laufs (Einheitsvektor). Ohne Ziel: langsames Absuchen des Himmels. */
    public static Vector3f aim(int half, float t, Vec3 target) {
        if (target != null) {
            Vector3f d = new Vector3f((float) target.x, (float) (target.y - headY(half)), (float) target.z);
            if (d.lengthSquared() > 1.0E-4F) return d.normalize();
        }
        float yaw = t * 0.012F;
        float pitch = 50.0F * Mth.DEG_TO_RAD + 0.2F * Mth.sin(t * 0.02F);
        return new Vector3f(Mth.cos(pitch) * Mth.sin(yaw), Mth.sin(pitch), Mth.cos(pitch) * Mth.cos(yaw));
    }

    public static void solid(PoseStack pose, MultiBufferSource buffers, int half, float t, Vector3f aim) {
        float base = half + 0.5F, head = headY(half);
        VertexConsumer portal = buffers.getBuffer(RenderType.endPortal());
        Matrix4f m = pose.last().pose();
        // Plattform (Scheibe ueber den vier Laser-Bloecken)
        pose.pushPose();
        pose.translate(0.0, base + 0.12, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(t * 0.3F));
        RenderUtil.starCylinder(portal, pose.last().pose(), 1.55F, 0.1F, 32);
        pose.popPose();
        // vier Streben zum Kopf
        for (int k = 0; k < 4; k++) {
            float ang = k * Mth.HALF_PI;
            float x = Mth.cos(ang) * 1.0F, z = Mth.sin(ang) * 1.0F;
            RenderUtil.starStrut(portal, m, x, base + 0.2F, z, x * 0.25F, head - 0.3F, z * 0.25F, 0.09F);
        }
        // Lauf
        float bx = aim.x * BARREL, by = aim.y * BARREL, bz = aim.z * BARREL;
        RenderUtil.starStrut(portal, m, 0, head, 0, bx, head + by, bz, 0.13F);
        // Kugelkopf (Sternenhimmel wie der Kern)
        VertexConsumer gateway = buffers.getBuffer(RenderType.endGateway());
        pose.pushPose();
        pose.translate(0.0, head, 0.0);
        RenderUtil.sphere(gateway, pose.last().pose(), 0.45F, 10, 16);
        pose.popPose();
    }

    public static void glow(PoseStack pose, VertexConsumer vc, int half, float t, Vector3f aim, Vec3 target, float energy) {
        float base = half + 0.5F, head = headY(half);
        boolean firing = target != null;
        float pulse = 0.6F + 0.4F * Mth.sin(t * (firing ? 0.6F : 0.12F));
        Matrix4f m = pose.last().pose();

        // Plattformrand und Knoten an den vier Laser-Bloecken
        Matrix4f pm = new Matrix4f(m).translate(0, base + 0.23F, 0);
        flatCircle(vc, pm, 1.55F, 0.035F, 64, CHARGE, energy * pulse);
        flatCircle(vc, pm, 1.1F, 0.015F, 48, BEAM, energy * 0.6F);
        for (int k = 0; k < 4; k++) {
            float ang = k * Mth.HALF_PI;
            float x = Mth.cos(ang), z = Mth.sin(ang);
            cube(vc, m, x, base + 0.3F, z, 0.08F, 1.0F, 1.0F, 1.0F, energy * pulse);
            // Energie steigt die Streben hinauf
            float s = Mth.frac(t * (firing ? 0.06F : 0.02F) + k * 0.25F);
            float px = Mth.lerp(s, x, x * 0.25F), py = Mth.lerp(s, base + 0.2F, head - 0.3F), pz = Mth.lerp(s, z, z * 0.25F);
            cube(vc, m, px, py, pz, 0.06F, BEAM[0], BEAM[1], BEAM[2], energy);
        }

        // Kardanringe um den Kopf
        pose.pushPose();
        pose.translate(0.0, head, 0.0);
        ring(pose, vc, 0.62F, 0.02F, t * 2.0F, 70.0F, 0.0F, t, CHARGE, energy * 0.8F);
        ring(pose, vc, 0.72F, 0.015F, -t * 1.4F, -30.0F, 60.0F, t + 30.0F, BEAM, energy * 0.6F);
        pose.popPose();

        // Laderinge entlang des Laufs, die zur Muendung wandern
        pose.pushPose();
        pose.translate(0.0, head, 0.0);
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(aim.x, aim.z)));
        pose.mulPose(Axis.XP.rotation(-(float) Math.asin(Mth.clamp(aim.y, -1.0F, 1.0F))));
        for (int k = 0; k < 3; k++) {
            float s = Mth.frac(t * (firing ? 0.05F : 0.015F) + k / 3.0F);
            pose.pushPose();
            pose.translate(0.0, 0.0, 0.5F + s * (BARREL - 0.5F));
            ring(pose, vc, 0.2F, 0.02F, t * 4.0F, 90.0F, 0.0F, t, CHARGE, energy * Mth.sin(s * Mth.PI));
            pose.popPose();
        }
        pose.popPose();

        float mx = aim.x * BARREL, my = head + aim.y * BARREL, mz = aim.z * BARREL;
        cube(vc, m, mx, my, mz, 0.1F + 0.05F * pulse, 1.0F, 1.0F, 1.0F, energy * (firing ? 1.0F : 0.5F));

        // Strahl
        if (firing) {
            float tx = (float) target.x, ty = (float) target.y, tz = (float) target.z;
            float flick = 0.8F + 0.2F * Mth.sin(t * 1.9F);
            segment(vc, m, mx, my, mz, tx, ty, tz, 0.05F, 1.0F, 1.0F, 1.0F, flick, flick);
            segment(vc, m, mx, my, mz, tx, ty, tz, 0.16F, BEAM[0], BEAM[1], BEAM[2], 0.5F * flick, 0.5F * flick);
            segment(vc, m, mx, my, mz, tx, ty, tz, 0.35F, CHARGE[0], CHARGE[1], CHARGE[2], 0.15F, 0.15F);
            float[] ic = lerp(IMPACT, new float[]{1, 1, 1}, 0.3F);
            cube(vc, m, tx, ty, tz, 0.5F + 0.2F * Mth.sin(t * 0.9F), ic[0], ic[1], ic[2], 0.6F);
            cube(vc, m, mx, my, mz, 0.25F, BEAM[0], BEAM[1], BEAM[2], 0.8F);
        }
    }
}
