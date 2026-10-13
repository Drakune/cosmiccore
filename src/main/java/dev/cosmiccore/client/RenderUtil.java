package dev.cosmiccore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Prozedurale Geometrie fuer die leuchtenden Effekte (gedacht fuer RenderType.lightning()). */
public final class RenderUtil {
    private RenderUtil() {}

    /** Leuchtender Ring mit laufenden Lichtern. */
    public static void ring(PoseStack pose, VertexConsumer vc, float radius, float halfWidth, float spin,
                            float tiltX, float tiltZ, float t, float[] col, float intensity) {
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(tiltZ));
        pose.mulPose(Axis.XP.rotationDegrees(tiltX));
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        Matrix4f m = pose.last().pose();
        final int n = 56;
        for (int i = 0; i < n; i++) {
            float a0 = (float) (Math.PI * 2 * i / n);
            float a1 = (float) (Math.PI * 2 * (i + 1) / n);
            float pulse = 0.25F + 0.75F * (0.5F + 0.5F * Mth.sin(i * 0.55F - t * 0.25F));
            float a = Mth.clamp(intensity * pulse, 0.0F, 1.0F);
            float r0 = radius - halfWidth, r1 = radius + halfWidth;
            float h = halfWidth * 0.6F;
            quad2(vc, m, r0 * Mth.cos(a0), h, r0 * Mth.sin(a0), r1 * Mth.cos(a0), h, r1 * Mth.sin(a0),
                    r1 * Mth.cos(a1), h, r1 * Mth.sin(a1), r0 * Mth.cos(a1), h, r0 * Mth.sin(a1), col[0], col[1], col[2], a);
            quad2(vc, m, r0 * Mth.cos(a0), -h, r0 * Mth.sin(a0), r1 * Mth.cos(a0), -h, r1 * Mth.sin(a0),
                    r1 * Mth.cos(a1), -h, r1 * Mth.sin(a1), r0 * Mth.cos(a1), -h, r0 * Mth.sin(a1), col[0], col[1], col[2], a * 0.8F);
        }
        pose.popPose();
    }

    public static float[] orbitPoint(float radius, float angle, float inclDeg, float nodeDeg) {
        float x = radius * Mth.cos(angle);
        float z = radius * Mth.sin(angle);
        float inc = inclDeg * Mth.DEG_TO_RAD;
        float y1 = z * Mth.sin(inc);
        float z1 = z * Mth.cos(inc);
        float node = nodeDeg * Mth.DEG_TO_RAD;
        return new float[]{x * Mth.cos(node) - z1 * Mth.sin(node), y1, x * Mth.sin(node) + z1 * Mth.cos(node)};
    }

    /** Kugel (nur Position, fuer RenderType.endGateway()). */
    public static void sphere(VertexConsumer vc, Matrix4f m, float r, int lat, int lon) {
        for (int i = 0; i < lat; i++) {
            float p0 = (float) (Math.PI * i / lat - Math.PI / 2);
            float p1 = (float) (Math.PI * (i + 1) / lat - Math.PI / 2);
            for (int j = 0; j < lon; j++) {
                float l0 = (float) (Math.PI * 2 * j / lon);
                float l1 = (float) (Math.PI * 2 * (j + 1) / lon);
                float[] a = sph(r, p0, l0), b = sph(r, p1, l0), c = sph(r, p1, l1), d = sph(r, p0, l1);
                // beide Wicklungen, damit kein Culling-Zustand eine Seite verschluckt
                vc.addVertex(m, a[0], a[1], a[2]);
                vc.addVertex(m, b[0], b[1], b[2]);
                vc.addVertex(m, c[0], c[1], c[2]);
                vc.addVertex(m, d[0], d[1], d[2]);
                vc.addVertex(m, d[0], d[1], d[2]);
                vc.addVertex(m, c[0], c[1], c[2]);
                vc.addVertex(m, b[0], b[1], b[2]);
                vc.addVertex(m, a[0], a[1], a[2]);
            }
        }
    }

    /** Leuchtende Kugelhuelle (Farbe + Alpha, fuer RenderType.lightning()). */
    public static void glowSphere(VertexConsumer vc, Matrix4f m, float r, int lat, int lon, float[] col, float alpha) {
        for (int i = 0; i < lat; i++) {
            float p0 = (float) (Math.PI * i / lat - Math.PI / 2);
            float p1 = (float) (Math.PI * (i + 1) / lat - Math.PI / 2);
            for (int j = 0; j < lon; j++) {
                float l0 = (float) (Math.PI * 2 * j / lon);
                float l1 = (float) (Math.PI * 2 * (j + 1) / lon);
                float[] a = sph(r, p0, l0), b = sph(r, p1, l0), c = sph(r, p1, l1), d = sph(r, p0, l1);
                quad2(vc, m, a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], col[0], col[1], col[2], alpha);
            }
        }
    }

    private static float[] sph(float r, float phi, float lam) {
        float cp = Mth.cos(phi);
        return new float[]{r * cp * Mth.cos(lam), r * Mth.sin(phi), r * cp * Mth.sin(lam)};
    }

    /** Gezackter Energiebogen vom Ursprung zum Ziel. */
    public static void arc(VertexConsumer vc, Matrix4f m, float tx, float ty, float tz, int seed,
                           float[] col, float flicker, boolean wild) {
        final int steps = 6;
        float px = 0, py = 0, pz = 0;
        float jitter = wild ? 0.16F : 0.08F;
        for (int i = 1; i <= steps; i++) {
            float f = i / (float) steps;
            float nx = tx * f, ny = ty * f, nz = tz * f;
            if (i < steps) {
                nx += hash(seed * 31 + i * 3) * jitter;
                ny += hash(seed * 31 + i * 3 + 1) * jitter;
                nz += hash(seed * 31 + i * 3 + 2) * jitter;
            }
            float fade = 1.0F - 0.5F * f;
            segment(vc, m, px, py, pz, nx, ny, nz, 0.02F, col[0], col[1], col[2], 0.95F * flicker * fade, 0.8F * flicker * fade);
            px = nx;
            py = ny;
            pz = nz;
        }
    }

    /** Duenner Strahl aus zwei gekreuzten Quads. a0/a1 sind die Alphawerte am Anfang/Ende. */
    public static void segment(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                               float w, float r, float g, float b, float a0, float a1) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4F) return;
        dx /= len;
        dy /= len;
        dz /= len;
        float ux = 0, uy = 1, uz = 0;
        if (Math.abs(dy) > 0.95F) {
            ux = 1;
            uy = 0;
        }
        float n1x = dy * uz - dz * uy, n1y = dz * ux - dx * uz, n1z = dx * uy - dy * ux;
        float l1 = Mth.sqrt(n1x * n1x + n1y * n1y + n1z * n1z);
        n1x = n1x / l1 * w;
        n1y = n1y / l1 * w;
        n1z = n1z / l1 * w;
        float n2x = dy * n1z - dz * n1y, n2y = dz * n1x - dx * n1z, n2z = dx * n1y - dy * n1x;
        float l2 = Mth.sqrt(n2x * n2x + n2y * n2y + n2z * n2z);
        n2x = n2x / l2 * w;
        n2y = n2y / l2 * w;
        n2z = n2z / l2 * w;

        quadFade(vc, m, ax - n1x, ay - n1y, az - n1z, ax + n1x, ay + n1y, az + n1z,
                bx + n1x, by + n1y, bz + n1z, bx - n1x, by - n1y, bz - n1z, r, g, b, a0, a1);
        quadFade(vc, m, ax - n2x, ay - n2y, az - n2z, ax + n2x, ay + n2y, az + n2z,
                bx + n2x, by + n2y, bz + n2z, bx - n2x, by - n2y, bz - n2z, r, g, b, a0, a1);
    }

    public static void quadFade(VertexConsumer vc, Matrix4f m,
                                float x0, float y0, float z0, float x1, float y1, float z1,
                                float x2, float y2, float z2, float x3, float y3, float z3,
                                float r, float g, float b, float a0, float a1) {
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a0);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a0);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a1);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a1);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a1);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a1);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a0);
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a0);
    }

    public static void quad2(VertexConsumer vc, Matrix4f m,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        quadFade(vc, m, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, r, g, b, a, a);
    }

    public static void cube(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float s,
                            float r, float g, float b, float a) {
        float x0 = cx - s, x1 = cx + s, y0 = cy - s, y1 = cy + s, z0 = cz - s, z1 = cz + s;
        quad2(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
        quad2(vc, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, a);
        quad2(vc, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, a);
        quad2(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
        quad2(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
        quad2(vc, m, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
    }

    /** Oktaeder als leuchtende Kanten (Kristallform). */
    public static void octahedron(VertexConsumer vc, Matrix4f m, float rx, float ry, float w, float[] col, float a) {
        float[][] eq = {{rx, 0, 0}, {0, 0, rx}, {-rx, 0, 0}, {0, 0, -rx}};
        for (int i = 0; i < 4; i++) {
            float[] p = eq[i], q = eq[(i + 1) % 4];
            segment(vc, m, p[0], p[1], p[2], q[0], q[1], q[2], w, col[0], col[1], col[2], a, a);
            segment(vc, m, p[0], p[1], p[2], 0, ry, 0, w, col[0], col[1], col[2], a, a * 0.6F);
            segment(vc, m, p[0], p[1], p[2], 0, -ry, 0, w, col[0], col[1], col[2], a, a * 0.6F);
        }
    }

    public static float[] lerp(float[] a, float[] b, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** Billiger deterministischer Zufall in [-1, 1]. */
    public static float hash(int seed) {
        int h = seed * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return ((h & 0xFFFF) / 32767.5F) - 1.0F;
    }
}
