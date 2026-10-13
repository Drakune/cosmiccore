package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.hash;
import static dr4kun3.cosmiccore.client.RenderUtil.lerp;
import static dr4kun3.cosmiccore.client.RenderUtil.quad2;
import static dr4kun3.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dr4kun3.cosmiccore.entity.CosmicAsteroid;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Asteroid: ein schattierter Gesteinsbrocken mit Kratern, aus dem kosmische Kristalle wachsen, dazu ein feiner
 * Kometenschweif. Unter Laserbeschuss gluehen die dem Strahl zugewandte Seite und die Risse auf, der Brocken zittert
 * und Splitter platzen ab.
 */
public class CosmicAsteroidRenderer extends EntityRenderer<CosmicAsteroid> {
    private static final float[][] ROCK = {{0.46F, 0.41F, 0.37F}, {0.36F, 0.35F, 0.41F}, {0.30F, 0.26F, 0.36F}};
    private static final float[][] CRYSTAL = {{0.45F, 0.85F, 1.0F}, {0.75F, 0.5F, 1.0F}, {1.0F, 0.8F, 0.4F}};
    private static final int[] CLUSTERS = {2, 4, 7};
    private static final float[] TRAIL = {0.6F, 0.75F, 1.0F};
    private static final float[] HOT = {1.0F, 0.45F, 0.12F};
    private static final float[] EMBER = {1.0F, 0.7F, 0.3F};
    private static final float[] WHITE_HOT = {1.0F, 0.93F, 0.78F};
    private static final Vector3f MOON = new Vector3f(0.35F, 0.85F, 0.4F).normalize();
    private static final int LAT = 10, LON = 16;

    public CosmicAsteroidRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(CosmicAsteroid entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(CosmicAsteroid a, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = a.tickCount + partialTick;
        float overload = a.getOverload();
        // zum Schluss blaeht sich der ueberladene Asteroid auf
        float swell = overload > 0.75F ? (overload - 0.75F) / 0.25F : 0.0F;
        float r = a.getSizeBlocks() * 0.5F * a.getPullScale() * (1.0F + 0.15F * swell * swell);
        float harvest = Math.max(a.getPull() * 0.6F, overload);
        int variant = a.getVariant();
        int seed = a.getId() * 7919;

        pose.pushPose();
        pose.translate(0.0, a.getBbHeight() / 2.0, 0.0);
        if (harvest > 0) {
            float amp = (0.03F * harvest + 0.09F * overload * overload) * r;
            float q = t * (3.0F + 4.0F * overload);
            pose.translate(hash((int) q) * amp, hash((int) q + 5) * amp, hash((int) q + 9) * amp);
        }
        Matrix4f wm = new Matrix4f(pose.last().pose());

        // ---------------------------------------------------------------- Gestein (deckend, schattiert)
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * (0.8F + (seed & 3) * 0.4F)));
        pose.mulPose(Axis.XP.rotationDegrees(t * 0.5F + (seed & 63)));
        Matrix4f m = new Matrix4f(pose.last().pose());
        Matrix3f nm = new Matrix3f(pose.last().normal());
        float[][][] pts = surface(r, seed);
        VertexConsumer solid = buffers.getBuffer(RenderType.debugQuads());
        rock(solid, m, nm, pts, ROCK[variant], harvest, overload, seed);
        // abplatzende Splitter (in Weltrichtung nach unten, dem Strahl entgegen)
        int chips = harvest > 0.2F ? 6 : 0;
        for (int k = 0; k < chips; k++) {
            float ph = Mth.frac(t * 0.035F + k / 6.0F + hash(seed + k) * 0.1F);
            Vector3f p = chipPos(k, seed, r, ph);
            float s = r * 0.07F * (1.0F - ph) + 0.01F;
            float[] c = lerp(ROCK[variant], HOT, 0.5F * (1.0F - ph));
            chip(solid, wm, p, s, t * 0.3F + k, c);
        }

        // ---------------------------------------------------------------- Leuchtendes (additiv)
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        // Kristall-Cluster
        float pulse = 0.75F + 0.25F * Mth.sin(t * (0.12F + 0.6F * overload) + seed);
        for (int cl = 0; cl < CLUSTERS[variant]; cl++) {
            Vector3f d = new Vector3f(hash(seed + cl * 41), hash(seed + cl * 43) * 0.8F, hash(seed + cl * 47)).normalize();
            float[] col = CRYSTAL[(variant == 2 && cl % 3 == 2) ? 2 : (cl % 2)];
            float base = radiusAt(d, r, seed) * 0.92F;
            for (int s = 0; s < 3; s++) {
                Vector3f dir = new Vector3f(d).add(hash(seed + cl * 7 + s * 3) * 0.4F, hash(seed + cl * 11 + s * 5) * 0.4F,
                        hash(seed + cl * 13 + s * 9) * 0.4F).normalize();
                float len = r * (0.22F + 0.18F * (hash(seed + cl * 17 + s) * 0.5F + 0.5F)) * (s == 0 ? 1.3F : 1.0F);
                crystal(glow, m, new Vector3f(d.x * base, d.y * base * 0.85F, d.z * base), dir, len, len * 0.2F,
                        lerp(col, WHITE_HOT, overload), (0.4F + 0.35F * harvest + 0.5F * overload) * pulse);
            }
        }
        // Risse: werden unter Beschuss heiss
        float[] crackCol = lerp(lerp(CRYSTAL[1], HOT, harvest), WHITE_HOT, overload);
        float crackA = 0.25F + 0.75F * harvest;
        float crackW = 1.0F + 2.5F * overload;
        for (int k = 0; k < 4 + variant * 2; k++) {
            float lat = hash(seed + k * 11) * 1.2F, lon = hash(seed + k * 23) * Mth.PI;
            Vector3f prev = null;
            for (int i = 0; i <= 5; i++) {
                float la = lat + hash(seed + k * 31 + i) * 0.3F, lo = lon + i * 0.22F;
                Vector3f d = new Vector3f(Mth.cos(la) * Mth.cos(lo), Mth.sin(la), Mth.cos(la) * Mth.sin(lo));
                Vector3f p = d.mul(radiusAt(d, r, seed) * 1.015F);
                p.y *= 0.85F;
                if (prev != null) {
                    segment(glow, m, prev.x, prev.y, prev.z, p.x, p.y, p.z, (0.012F * r + 0.006F) * crackW,
                            crackCol[0], crackCol[1], crackCol[2], crackA, crackA);
                }
                prev = p;
            }
        }
        // Ueberladung: Entladungsblitze zucken ueber die Oberflaeche
        if (overload > 0.1F) {
            int arcs = 2 + (int) (overload * 6);
            for (int k = 0; k < arcs; k++) {
                int as = seed + k * 101 + (int) (t * (0.5F + overload));
                if (hash(as) < 0.2F - overload) continue;
                Vector3f d = new Vector3f(hash(as * 3), hash(as * 5), hash(as * 7)).normalize();
                Vector3f prev = new Vector3f(d).mul(radiusAt(d, r, seed));
                for (int i = 1; i <= 4; i++) {
                    Vector3f n = new Vector3f(d).add(hash(as * 11 + i) * 0.25F * i, hash(as * 13 + i) * 0.25F * i, hash(as * 17 + i) * 0.25F * i)
                            .normalize().mul(r * (1.02F + 0.12F * i * overload));
                    segment(glow, m, prev.x, prev.y, prev.z, n.x, n.y, n.z, 0.015F * r + 0.01F, 1.0F, 0.95F, 0.85F,
                            overload, overload * 0.6F);
                    prev = n;
                }
            }
        }
        pose.popPose();

        // Ueberladung: pulsierender Hitzeschein und zum Schluss Lichtstrahlen
        if (overload > 0.0F) {
            float beat = 0.5F + 0.5F * Mth.sin(t * (0.3F + 1.4F * overload));
            float[] hc = lerp(HOT, WHITE_HOT, overload);
            RenderUtil.glowSphere(glow, wm, r * (1.15F + 0.35F * overload * beat), 10, 14, hc, (0.12F + 0.35F * overload) * (0.6F + 0.4F * beat));
            RenderUtil.glowSphere(glow, wm, r * (1.6F + 0.8F * overload), 8, 12, HOT, 0.08F * overload);
            if (overload > 0.5F) {
                float rays = (overload - 0.5F) / 0.5F;
                for (int k = 0; k < 10; k++) {
                    Vector3f d = new Vector3f(hash(seed + k * 61), hash(seed + k * 67), hash(seed + k * 71)).normalize();
                    float l = r * (1.2F + 2.5F * rays) * (0.6F + 0.4F * Mth.sin(t * 0.7F + k));
                    segment(glow, wm, 0, 0, 0, d.x * l, d.y * l, d.z * l, r * 0.06F, WHITE_HOT[0], WHITE_HOT[1], WHITE_HOT[2],
                            0.7F * rays, 0.0F);
                }
            }
        }

        // Glut der Splitter
        for (int k = 0; k < chips; k++) {
            float ph = Mth.frac(t * 0.035F + k / 6.0F + hash(seed + k) * 0.1F);
            Vector3f p = chipPos(k, seed, r, ph);
            Vector3f q = chipPos(k, seed, r, Math.max(0.0F, ph - 0.08F));
            segment(glow, wm, q.x, q.y, q.z, p.x, p.y, p.z, r * 0.03F, EMBER[0], EMBER[1], EMBER[2], 0.0F, 0.7F * (1.0F - ph));
        }

        // Kometenschweif entgegen der Flugrichtung
        Vec3 v = a.getDeltaMovement();
        if (v.lengthSqr() > 1.0E-8) {
            Vector3f back = new Vector3f((float) -v.x, (float) -v.y, (float) -v.z).normalize();
            float len = r * 6.0F;
            segment(glow, wm, 0, 0, 0, back.x * len, back.y * len, back.z * len, r * 0.45F, TRAIL[0], TRAIL[1], TRAIL[2], 0.14F, 0.0F);
            for (int i = 0; i < 5; i++) {
                Vector3f o = new Vector3f(hash(seed + i * 3), hash(seed + i * 5), hash(seed + i * 7)).mul(r * 0.45F);
                float l = len * (0.5F + 0.5F * (hash(seed + i * 9) * 0.5F + 0.5F));
                segment(glow, wm, o.x, o.y, o.z, o.x + back.x * l, o.y + back.y * l, o.z + back.z * l, r * 0.04F,
                        TRAIL[0], TRAIL[1], TRAIL[2], 0.35F, 0.0F);
            }
            for (int k = 0; k < 10; k++) {
                float s = Mth.frac(t * 0.04F + k / 10.0F);
                Vector3f o = new Vector3f(hash(seed + k * 13), hash(seed + k * 17), hash(seed + k * 19)).mul(r * (0.3F + 0.5F * s));
                Vector3f p = new Vector3f(back).mul(len * 0.9F * s).add(o);
                RenderUtil.cube(glow, wm, p.x, p.y, p.z, r * 0.035F * (1.0F - s) + 0.01F, 1.0F, 1.0F, 1.0F, 0.6F * (1.0F - s));
            }
        }
        // zarte Koma, unter Beschuss als Hitzeschein
        RenderUtil.glowSphere(glow, wm, r * 1.2F, 8, 12, TRAIL, 0.05F);
        if (harvest > 0) RenderUtil.glowSphere(glow, wm, r * (1.05F + 0.15F * harvest), 8, 12, HOT, 0.16F * harvest);
        pose.popPose();
        super.render(a, yaw, partialTick, pose, buffers, light);
    }

    // ------------------------------------------------------------------ Form

    /** Radius in Richtung d (Einheitsvektor): Kartoffelform mit Beulen und Kratern. */
    private static float radiusAt(Vector3f d, float r, int seed) {
        float s = 1.0F;
        for (int b = 0; b < 5; b++) {
            Vector3f bd = new Vector3f(hash(seed + b * 3 + 1), hash(seed + b * 5 + 2), hash(seed + b * 7 + 3)).normalize();
            float dot = Math.max(0.0F, d.dot(bd));
            s += 0.16F * hash(seed + b * 11) * dot * dot * dot;
        }
        for (int c = 0; c < 4; c++) {
            Vector3f cd = new Vector3f(hash(seed + c * 19 + 7), hash(seed + c * 23 + 8), hash(seed + c * 29 + 9)).normalize();
            float size = 0.25F + 0.15F * (hash(seed + c * 31) * 0.5F + 0.5F);
            float cosr = Mth.cos(size);
            float dot = d.dot(cd);
            if (dot > cosr) {
                float u = (dot - cosr) / (1.0F - cosr); // 0 am Rand, 1 in der Mitte
                s += 0.05F * Mth.sin(u * Mth.PI) * (1.0F - u) * 2.0F - 0.12F * u * u;
            }
        }
        return r * s;
    }

    private static float[][][] surface(float r, int seed) {
        float[][][] pts = new float[LAT + 1][LON][];
        for (int i = 0; i <= LAT; i++) {
            float phi = (float) (Math.PI * i / LAT - Math.PI / 2);
            for (int j = 0; j < LON; j++) {
                float lam = Mth.TWO_PI * j / LON;
                Vector3f d = new Vector3f(Mth.cos(phi) * Mth.cos(lam), Mth.sin(phi), Mth.cos(phi) * Mth.sin(lam));
                boolean pole = i == 0 || i == LAT;
                float rr = radiusAt(d, r, seed) * (1.0F + 0.035F * hash(seed + (pole ? i * 1000 : i * 97 + j * 13)));
                pts[i][j] = new float[]{d.x * rr, d.y * rr * 0.85F, d.z * rr};
            }
        }
        return pts;
    }

    /** Flach schattierte Gesteinsflaechen: Mondlicht von oben, violettes Reaktorlicht und Laserglut von unten. */
    private static void rock(VertexConsumer vc, Matrix4f m, Matrix3f nm, float[][][] pts, float[] base, float harvest,
                             float overload, int seed) {
        for (int i = 0; i < LAT; i++) {
            for (int j = 0; j < LON; j++) {
                float[] a = pts[i][j], b = pts[i + 1][j], c = pts[i + 1][(j + 1) % LON], d = pts[i][(j + 1) % LON];
                Vector3f e1 = new Vector3f(c[0] - a[0], c[1] - a[1], c[2] - a[2]);
                Vector3f e2 = new Vector3f(d[0] - b[0], d[1] - b[1], d[2] - b[2]);
                Vector3f n = e1.cross(e2);
                if (n.lengthSquared() < 1.0E-10F) n.set(a[0] + c[0], a[1] + c[1], a[2] + c[2]);
                n.normalize();
                Vector3f center = new Vector3f(a[0] + c[0], a[1] + c[1], a[2] + c[2]);
                if (n.dot(center) < 0) n.negate();
                Vector3f wn = nm.transform(new Vector3f(n));
                float diffuse = Math.max(0.0F, wn.dot(MOON));
                float under = Math.max(0.0F, -wn.y);
                float shade = 0.22F + 0.7F * diffuse;
                float tint = 0.9F + 0.1F * hash(seed + i * 31 + j * 7);
                // Glut: erst die dem Strahl zugewandte Unterseite, bei Ueberladung der ganze Brocken bis zur Weissglut
                float heat = Math.max(harvest * under * under, overload * (0.55F + 0.45F * under));
                float[] hc = lerp(HOT, WHITE_HOT, overload * overload);
                float rr = base[0] * shade * tint * (1.0F - heat) + 0.12F * under + hc[0] * heat;
                float gg = base[1] * shade * tint * (1.0F - heat) + 0.05F * under + hc[1] * heat;
                float bb = base[2] * shade * tint * (1.0F - heat) + 0.18F * under + hc[2] * heat;
                quad2(vc, m, a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2],
                        Math.min(1.0F, rr), Math.min(1.0F, gg), Math.min(1.0F, bb), 1.0F);
            }
        }
    }

    /** Kristall: gestreckte Doppelpyramide aus leuchtenden Flaechen mit hellen Kanten. */
    private static void crystal(VertexConsumer vc, Matrix4f m, Vector3f base, Vector3f dir, float len, float w, float[] col, float a) {
        Vector3f u = new Vector3f(dir).cross(Math.abs(dir.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        Vector3f v = new Vector3f(dir).cross(u).normalize();
        Vector3f mid = new Vector3f(dir).mul(len * 0.35F).add(base);
        Vector3f tip = new Vector3f(dir).mul(len).add(base);
        Vector3f[] ring = new Vector3f[4];
        for (int k = 0; k < 4; k++) {
            float ang = k * Mth.HALF_PI;
            ring[k] = new Vector3f(u).mul(Mth.cos(ang) * w).add(new Vector3f(v).mul(Mth.sin(ang) * w)).add(mid);
        }
        for (int k = 0; k < 4; k++) {
            Vector3f p = ring[k], q = ring[(k + 1) % 4];
            float shade = 0.7F + 0.3F * (k % 2);
            quad2(vc, m, p.x, p.y, p.z, q.x, q.y, q.z, tip.x, tip.y, tip.z, tip.x, tip.y, tip.z,
                    col[0] * shade, col[1] * shade, col[2] * shade, a * 0.55F);
            quad2(vc, m, p.x, p.y, p.z, q.x, q.y, q.z, base.x, base.y, base.z, base.x, base.y, base.z,
                    col[0] * shade, col[1] * shade, col[2] * shade, a * 0.35F);
            segment(vc, m, p.x, p.y, p.z, tip.x, tip.y, tip.z, w * 0.08F, 1.0F, 1.0F, 1.0F, a * 0.6F, a);
        }
    }

    private static Vector3f chipPos(int k, int seed, float r, float ph) {
        Vector3f d = new Vector3f(hash(seed + k * 37) * 0.8F, -1.0F, hash(seed + k * 41) * 0.8F).normalize();
        Vector3f p = d.mul(r * (0.85F + 1.8F * ph));
        p.y -= ph * ph * r * 0.6F;
        return p;
    }

    private static void chip(VertexConsumer vc, Matrix4f m, Vector3f p, float s, float spin, float[] c) {
        Matrix4f cm = new Matrix4f(m).translate(p.x, p.y, p.z).rotateXYZ(spin, spin * 1.3F, spin * 0.7F);
        float x0 = -s, x1 = s, y0 = -s * 0.7F, y1 = s * 0.7F, z0 = -s, z1 = s;
        float[] sh = {1.0F, 0.6F, 0.85F, 0.75F, 0.9F, 0.65F};
        quad2(vc, cm, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, c[0] * sh[0], c[1] * sh[0], c[2] * sh[0], 1.0F);
        quad2(vc, cm, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, c[0] * sh[1], c[1] * sh[1], c[2] * sh[1], 1.0F);
        quad2(vc, cm, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, c[0] * sh[2], c[1] * sh[2], c[2] * sh[2], 1.0F);
        quad2(vc, cm, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, c[0] * sh[3], c[1] * sh[3], c[2] * sh[3], 1.0F);
        quad2(vc, cm, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, c[0] * sh[4], c[1] * sh[4], c[2] * sh[4], 1.0F);
        quad2(vc, cm, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, c[0] * sh[5], c[1] * sh[5], c[2] * sh[5], 1.0F);
    }
}
