package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.cube;
import static dev.cosmiccore.client.RenderUtil.hash;
import static dev.cosmiccore.client.RenderUtil.lerp;
import static dev.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Das Eindaemmungsschild: Die Resonanzschild-Emitter projizieren Strahlen auf eine geodaetische Energiekugel um
 * das schwarze Loch. Ihre Staerke zeigt den Stabilisator-Vorrat - geht er aus, flackert das Schild und reisst auf.
 * Einschlaege lassen Wellenringe ueber die Oberflaeche laufen, bei Instabilitaet faerbt es sich rot.
 */
public final class ShieldFx {
    private ShieldFx() {}

    private static final float[] BLUE = {0.35F, 0.7F, 1.0F};
    private static final float[] VIOLET = {0.6F, 0.4F, 1.0F};
    private static final float[] CRITICAL = {1.0F, 0.15F, 0.15F};

    private static List<Vector3f[]> edges;

    /** Kanten eines einmal unterteilten Ikosaeders (Einheitskugel). */
    private static List<Vector3f[]> geodesic() {
        if (edges != null) return edges;
        float g = (1.0F + Mth.sqrt(5.0F)) / 2.0F;
        float[][] v = {{-1, g, 0}, {1, g, 0}, {-1, -g, 0}, {1, -g, 0}, {0, -1, g}, {0, 1, g}, {0, -1, -g}, {0, 1, -g},
                {g, 0, -1}, {g, 0, 1}, {-g, 0, -1}, {-g, 0, 1}};
        int[][] f = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6},
                {7, 1, 8}, {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
        Map<String, Vector3f[]> out = new HashMap<>();
        for (int[] tri : f) {
            Vector3f a = new Vector3f(v[tri[0]][0], v[tri[0]][1], v[tri[0]][2]).normalize();
            Vector3f b = new Vector3f(v[tri[1]][0], v[tri[1]][1], v[tri[1]][2]).normalize();
            Vector3f c = new Vector3f(v[tri[2]][0], v[tri[2]][1], v[tri[2]][2]).normalize();
            Vector3f ab = new Vector3f(a).add(b).normalize(), bc = new Vector3f(b).add(c).normalize(), ca = new Vector3f(c).add(a).normalize();
            Vector3f[][] sub = {{a, ab}, {ab, b}, {b, bc}, {bc, c}, {c, ca}, {ca, a}, {ab, bc}, {bc, ca}, {ca, ab}};
            for (Vector3f[] e : sub) out.putIfAbsent(key(e[0], e[1]), e);
        }
        edges = new ArrayList<>(out.values());
        return edges;
    }

    private static String key(Vector3f a, Vector3f b) {
        String ka = String.format("%.3f,%.3f,%.3f", a.x, a.y, a.z), kb = String.format("%.3f,%.3f,%.3f", b.x, b.y, b.z);
        return ka.compareTo(kb) < 0 ? ka + "|" + kb : kb + "|" + ka;
    }

    public static void render(Matrix4f m, VertexConsumer vc, int size, float reach, float t, float energy, float heat,
                              float shield, boolean unstable) {
        if (energy <= 0.01F) return;
        float rs = reach - 0.05F;
        float strength = 0.25F + 0.75F * shield;
        float[] col = lerp(lerp(BLUE, VIOLET, 0.5F + 0.5F * Mth.sin(t * 0.03F)), CRITICAL, heat * heat);
        float rot = t * 0.006F;
        float cr = Mth.cos(rot), sr = Mth.sin(rot);
        int window = (int) (t * 0.25F);

        // geodaetisches Gitter
        int idx = 0;
        for (Vector3f[] e : geodesic()) {
            idx++;
            // ohne Stabilisatoren reisst das Schild auf (fehlende Kanten), bei Instabilitaet flackert es
            float gap = hash(idx * 131 + window);
            if (gap > strength * 1.6F - 0.2F) continue;
            float flick = unstable ? 0.5F + 0.5F * (0.5F + 0.5F * hash(idx * 17 + (int) (t * 0.7F))) : 1.0F;
            float a = energy * strength * 0.35F * flick;
            float ax = (e[0].x * cr - e[0].z * sr) * rs, az = (e[0].x * sr + e[0].z * cr) * rs, ay = e[0].y * rs;
            float bx = (e[1].x * cr - e[1].z * sr) * rs, bz = (e[1].x * sr + e[1].z * cr) * rs, by = e[1].y * rs;
            segment(vc, m, ax, ay, az, bx, by, bz, 0.008F, col[0], col[1], col[2], a, a);
        }
        // zarte Fuellung
        RenderUtil.glowSphere(vc, m, rs, 10, 16, col, 0.035F * energy * strength);

        // Emitter-Strahlen auf die Schildoberflaeche
        int half = size / 2;
        float lens = (size <= 5 ? half : half - 1) - 0.28F + 0.0F;
        int count = size >= 9 ? 8 : 4;
        for (int k = 0; k < count; k++) {
            boolean diag = k >= 4;
            float ang = (diag ? Mth.PI / 4 : 0.0F) + (k % 4) * Mth.HALF_PI;
            float from = diag ? (half - 1) * Mth.SQRT_OF_TWO - 0.28F : lens;
            float dx = Mth.cos(ang), dz = Mth.sin(ang);
            float pulse = 0.6F + 0.4F * Mth.sin(t * 0.3F + k * 1.3F);
            float a = energy * strength * pulse;
            segment(vc, m, dx * from, 0, dz * from, dx * rs, 0, dz * rs, 0.02F, 1.0F, 1.0F, 1.0F, a, a * 0.8F);
            segment(vc, m, dx * from, 0, dz * from, dx * rs, 0, dz * rs, 0.06F, col[0], col[1], col[2], a * 0.4F, a * 0.3F);
            cube(vc, m, dx * rs, 0, dz * rs, 0.05F + 0.03F * pulse, col[0], col[1], col[2], a);
            ripple(m, vc, new Vector3f(dx, 0, dz), rs, Mth.frac(t * 0.02F + k * 0.25F), col, a * 0.8F);
        }

        // zufaellige Einschlaege kosmischer Teilchen
        for (int i = 0; i < 3; i++) {
            int slot = (int) (t / 40.0F) * 3 + i;
            float phase = Mth.frac(t / 40.0F);
            Vector3f n = new Vector3f(hash(slot * 7), hash(slot * 13) * 0.8F, hash(slot * 19)).normalize();
            ripple(m, vc, n, rs, phase, col, energy * strength * 0.7F);
        }
    }

    /** Wellenring, der sich auf der Kugeloberflaeche um den Punkt n ausbreitet. */
    private static void ripple(Matrix4f m, VertexConsumer vc, Vector3f n, float rs, float phase, float[] col, float alpha) {
        float a = alpha * (1.0F - phase);
        if (a <= 0.01F) return;
        Vector3f up = Math.abs(n.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f u = new Vector3f(n).cross(up).normalize();
        Vector3f w = new Vector3f(n).cross(u).normalize();
        float spread = 0.15F + phase * 0.9F; // Winkelradius
        int seg = 18;
        float[] prev = null;
        for (int i = 0; i <= seg; i++) {
            float ang = Mth.TWO_PI * i / seg;
            Vector3f d = new Vector3f(n).mul(Mth.cos(spread))
                    .add(new Vector3f(u).mul(Mth.sin(spread) * Mth.cos(ang)))
                    .add(new Vector3f(w).mul(Mth.sin(spread) * Mth.sin(ang))).normalize().mul(rs * 1.01F);
            float[] p = {d.x, d.y, d.z};
            if (prev != null) segment(vc, m, prev[0], prev[1], prev[2], p[0], p[1], p[2], 0.012F, col[0], col[1], col[2], a, a);
            prev = p;
        }
    }
}
