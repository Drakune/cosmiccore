package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.annulusXY;
import static dev.cosmiccore.client.RenderUtil.cube;
import static dev.cosmiccore.client.RenderUtil.hash;
import static dev.cosmiccore.client.RenderUtil.lerp;
import static dev.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Das Eindaemmungsschild: Die Resonanzschild-Emitter halten ein geodaetisches Kraftfeld um das kosmische Loch.
 * <ul>
 *   <li>Deutliche Kontur (Fresnel-Saum zum Betrachter), schimmernde Waben, die in Wellen aufleuchten.</li>
 *   <li>Emitter-Strahlen mit Einschlagsblitz und Wellenringen auf der Oberflaeche.</li>
 *   <li>Die Staerke zeigt den Stabilisator-Vorrat: geht er aus, reisst das Gitter auf und flackert.</li>
 *   <li>Gefahr: ab mittlerer Instabilitaet gluehen rote Risse, kurz vor dem Kollaps zucken Blitze zwischen Schild und Kern.</li>
 * </ul>
 */
public final class ShieldFx {
    private ShieldFx() {}

    private static final float[] BLUE = {0.35F, 0.75F, 1.0F};
    private static final float[] VIOLET = {0.6F, 0.4F, 1.0F};
    private static final float[] CRITICAL = {1.0F, 0.12F, 0.12F};
    private static final float[][] OVERDRIVE = {{0.4F, 0.85F, 1.0F}, {1.0F, 0.75F, 0.2F}, {0.95F, 0.5F, 1.0F}};

    private static List<Vector3f[]> edges;
    private static List<Vector3f[]> faces;

    /** Ein Mal unterteiltes Ikosaeder: Kanten und Dreiecke auf der Einheitskugel. */
    private static void build() {
        if (edges != null) return;
        float g = (1.0F + Mth.sqrt(5.0F)) / 2.0F;
        float[][] v = {{-1, g, 0}, {1, g, 0}, {-1, -g, 0}, {1, -g, 0}, {0, -1, g}, {0, 1, g}, {0, -1, -g}, {0, 1, -g},
                {g, 0, -1}, {g, 0, 1}, {-g, 0, -1}, {-g, 0, 1}};
        int[][] f = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6},
                {7, 1, 8}, {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
        Map<String, Vector3f[]> out = new HashMap<>();
        List<Vector3f[]> tris = new ArrayList<>();
        for (int[] tri : f) {
            Vector3f a = vec(v[tri[0]]), b = vec(v[tri[1]]), c = vec(v[tri[2]]);
            Vector3f ab = new Vector3f(a).add(b).normalize(), bc = new Vector3f(b).add(c).normalize(), ca = new Vector3f(c).add(a).normalize();
            tris.add(new Vector3f[]{a, ab, ca});
            tris.add(new Vector3f[]{ab, b, bc});
            tris.add(new Vector3f[]{ca, bc, c});
            tris.add(new Vector3f[]{ab, bc, ca});
            Vector3f[][] sub = {{a, ab}, {ab, b}, {b, bc}, {bc, c}, {c, ca}, {ca, a}, {ab, bc}, {bc, ca}, {ca, ab}};
            for (Vector3f[] e : sub) out.putIfAbsent(key(e[0], e[1]), e);
        }
        edges = new ArrayList<>(out.values());
        faces = tris;
    }

    private static Vector3f vec(float[] p) {
        return new Vector3f(p[0], p[1], p[2]).normalize();
    }

    private static String key(Vector3f a, Vector3f b) {
        String ka = String.format("%.3f,%.3f,%.3f", a.x, a.y, a.z), kb = String.format("%.3f,%.3f,%.3f", b.x, b.y, b.z);
        return ka.compareTo(kb) < 0 ? ka + "|" + kb : kb + "|" + ka;
    }

    public static void render(PoseStack pose, VertexConsumer vc, int size, float reach, float t, float energy, float heat,
                              float shield, boolean unstable, float camYaw, float camPitch, int overdrive) {
        if (energy <= 0.01F) return;
        build();
        float rs = reach - 0.05F;
        float strength = 0.25F + 0.75F * shield;
        float danger = Mth.clamp((heat - 0.4F) / 0.6F, 0.0F, 1.0F);
        float[] col = lerp(BLUE, VIOLET, 0.5F + 0.5F * Mth.sin(t * 0.03F));
        if (overdrive > 0) col = lerp(col, OVERDRIVE[Math.min(3, overdrive) - 1], 0.75F); // Overdrive faerbt das Schild
        col = lerp(col, CRITICAL, danger);
        strength = Math.min(1.0F, strength * (1.0F + 0.15F * overdrive));
        int window = (int) (t * 0.25F);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(t * 0.006F));
        Matrix4f m = pose.last().pose();

        // Waben: Dreiecke leuchten in Wellen auf, die ueber die Kugel laufen
        for (int i = 0; i < faces.size(); i++) {
            Vector3f[] tri = faces.get(i);
            float cy = (tri[0].y + tri[1].y + tri[2].y) / 3.0F;
            float wave = 0.5F + 0.5F * Mth.sin(cy * 4.0F - t * 0.08F + i * 0.05F);
            float a = energy * strength * (0.03F + 0.12F * wave * wave);
            if (hash(i * 97 + window) > strength * 1.5F - 0.1F) continue; // Luecken bei schwachem Schild
            float[] fc = danger > 0.3F && hash(i * 13 + (int) (t * 0.3F)) > 1.0F - danger * 0.5F ? CRITICAL : col;
            Vector3f p0 = new Vector3f(tri[0]).mul(rs), p1 = new Vector3f(tri[1]).mul(rs), p2 = new Vector3f(tri[2]).mul(rs);
            RenderUtil.quad2(vc, m, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, p2.x, p2.y, p2.z, fc[0], fc[1], fc[2], a);
        }
        // Gitterkanten
        int idx = 0;
        for (Vector3f[] e : edges) {
            idx++;
            if (hash(idx * 131 + window) > strength * 1.6F - 0.2F) continue;
            float flick = unstable ? 0.5F + 0.5F * (0.5F + 0.5F * hash(idx * 17 + (int) (t * 0.7F))) : 1.0F;
            float a = energy * strength * 0.55F * flick;
            boolean crack = danger > 0.0F && hash(idx * 53 + (int) (t * 0.15F)) > 1.0F - danger * 0.6F;
            float[] ec = crack ? new float[]{1.0F, 0.85F, 0.8F} : col;
            float w = crack ? 0.02F : 0.01F;
            segment(vc, m, e[0].x * rs, e[0].y * rs, e[0].z * rs, e[1].x * rs, e[1].y * rs, e[1].z * rs, w,
                    ec[0], ec[1], ec[2], crack ? Math.min(1.0F, a * 2) : a, crack ? Math.min(1.0F, a * 2) : a);
        }
        pose.popPose();

        // deutliche Kontur: Saum, der immer zum Betrachter zeigt
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(camYaw));
        pose.mulPose(Axis.XP.rotation(-camPitch));
        Matrix4f bm = pose.last().pose();
        int seg = 72;
        for (int s = 0; s < seg; s++) {
            float a0 = Mth.TWO_PI * s / seg, a1 = Mth.TWO_PI * (s + 1) / seg;
            float flick = unstable ? 0.6F + 0.4F * (0.5F + 0.5F * hash(s * 7 + (int) (t * 0.8F))) : 1.0F;
            annulusXY(vc, bm, rs * 0.86F, rs * 1.0F, a0, a1, col, 0.0F, 0.55F * energy * strength * flick);
            annulusXY(vc, bm, rs * 1.0F, rs * 1.06F, a0, a1, col, 0.55F * energy * strength * flick, 0.0F);
        }
        pose.popPose();

        Matrix4f wm = pose.last().pose();
        // Emitter-Strahlen mit Einschlagsblitz
        int half = size / 2;
        float lens = (size <= 5 ? half : half - 1) - 0.1F; // Fokuslinse der (verkleinerten) Emitter
        int count = size >= 9 ? 8 : 4;
        for (int k = 0; k < count; k++) {
            boolean diag = k >= 4;
            float ang = (diag ? Mth.PI / 4 : 0.0F) + (k % 4) * Mth.HALF_PI;
            float from = diag ? (half - 1) * Mth.SQRT_OF_TWO - 0.1F : lens;
            float dx = Mth.cos(ang), dz = Mth.sin(ang);
            float pulse = 0.6F + 0.4F * Mth.sin(t * 0.3F + k * 1.3F);
            float a = energy * strength * pulse;
            segment(vc, wm, dx * from, 0, dz * from, dx * rs, 0, dz * rs, 0.03F, 1.0F, 1.0F, 1.0F, a, a);
            segment(vc, wm, dx * from, 0, dz * from, dx * rs, 0, dz * rs, 0.09F, col[0], col[1], col[2], a * 0.45F, a * 0.35F);
            cube(vc, wm, dx * rs, 0, dz * rs, 0.07F + 0.04F * pulse, 1.0F, 1.0F, 1.0F, a);
            ripple(wm, vc, new Vector3f(dx, 0, dz), rs, Mth.frac(t * 0.02F + k * 0.25F), col, a);
        }
        // Einschlaege kosmischer Teilchen
        for (int i = 0; i < 3 + Math.round(danger * 4); i++) {
            float ph = t / 40.0F + i * 0.31F;
            int slot = (int) ph * 7 + i;
            Vector3f n = new Vector3f(hash(slot * 7), hash(slot * 13) * 0.8F, hash(slot * 19)).normalize();
            ripple(wm, vc, n, rs, Mth.frac(ph), col, energy * strength);
        }
        // kurz vor dem Kollaps: Blitze zwischen Schild und Kern
        if (danger > 0.6F) {
            int seed = (int) (t * 0.6F);
            for (int k = 0; k < 2 + Math.round(danger * 3); k++) {
                Vector3f n = new Vector3f(hash(seed * 7 + k), hash(seed * 11 + k) * 0.6F, hash(seed * 23 + k)).normalize();
                float[] prev = {n.x * rs, n.y * rs, n.z * rs};
                for (int i = 1; i <= 6; i++) {
                    float s = 1.0F - i / 6.0F;
                    float j = 0.15F * s;
                    float[] q = {n.x * rs * s + hash(seed + i * 3 + k) * j, n.y * rs * s + hash(seed + i * 5 + k) * j,
                            n.z * rs * s + hash(seed + i * 7 + k) * j};
                    segment(vc, wm, prev[0], prev[1], prev[2], q[0], q[1], q[2], 0.018F, 1.0F, 0.85F, 0.85F, energy, energy);
                    prev = q;
                }
            }
        }
    }

    /** Wellenring, der sich auf der Kugeloberflaeche um den Punkt n ausbreitet. */
    private static void ripple(Matrix4f m, VertexConsumer vc, Vector3f n, float rs, float phase, float[] col, float alpha) {
        float a = alpha * (1.0F - phase);
        if (a <= 0.01F) return;
        Vector3f up = Math.abs(n.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f u = new Vector3f(n).cross(up).normalize();
        Vector3f w = new Vector3f(n).cross(u).normalize();
        float spread = 0.15F + phase * 0.9F;
        int seg = 20;
        float[] prev = null;
        for (int i = 0; i <= seg; i++) {
            float ang = Mth.TWO_PI * i / seg;
            Vector3f d = new Vector3f(n).mul(Mth.cos(spread))
                    .add(new Vector3f(u).mul(Mth.sin(spread) * Mth.cos(ang)))
                    .add(new Vector3f(w).mul(Mth.sin(spread) * Mth.sin(ang))).normalize().mul(rs * 1.01F);
            float[] p = {d.x, d.y, d.z};
            if (prev != null) segment(vc, m, prev[0], prev[1], prev[2], p[0], p[1], p[2], 0.016F, col[0], col[1], col[2], a, a);
            prev = p;
        }
    }
}
