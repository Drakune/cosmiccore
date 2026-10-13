package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.cube;
import static dr4kun3.cosmiccore.client.RenderUtil.flatCircle;
import static dr4kun3.cosmiccore.client.RenderUtil.hash;
import static dr4kun3.cosmiccore.client.RenderUtil.lerp;
import static dr4kun3.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Kosmos-Laser als Energiepartikel: Die vier Laser-Bloecke werden zu vier leuchtenden Partikeln, die um die
 * Position des Sternentrichters kreisen.
 * <ol>
 *   <li><b>Ruhe:</b> die Partikel ziehen mit Lichtschweifen auf einer geneigten Bahn ihre Kreise.</li>
 *   <li><b>Aufladen:</b> ein Asteroid wurde erfasst - die Partikel spiralen beschleunigend zu einem Brennpunkt,
 *       Blitze schlagen hinein, der Brennpunkt gluehte immer heller.</li>
 *   <li><b>Feuern:</b> ein gleissender Strahl mit Doppelhelix trifft den Asteroiden, am Einschlag blitzt es und
 *       Schockwellen laufen; die freigesetzte Energie stroemt als Lichtpunkte den Strahl entlang zurueck und
 *       von dort hinab in den Reaktor.</li>
 * </ol>
 * Ursprung ist die Kernmitte.
 */
public final class EnergyLaserFx {
    private EnergyLaserFx() {}

    /** Hoehe der Bahnmitte ueber der Kernmitte (zusaetzlich zur halben Baugroesse) - passt zur Server-Logik. */
    public static final float HEIGHT = 2.2F;
    private static final float ORBIT = 0.9F;
    private static final float[] BEAM = {0.4F, 0.85F, 1.0F};
    private static final float[] VIOLET = {0.7F, 0.45F, 1.0F};
    private static final float[] GOLD = {1.0F, 0.8F, 0.35F};
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};

    public static void render(Matrix4f m, VertexConsumer vc, int half, float t, Vec3 target, float targetSize, float charge,
                              float harvest, float energy, float deliver, Vec3 source) {
        float cy = half + HEIGHT;
        if (deliver >= 0.0F) delivery(m, vc, cy, t, deliver, energy, source);
        Vector3f center = new Vector3f(0, cy, 0);
        boolean locked = target != null;
        float fire = locked ? Mth.clamp((charge - 0.95F) / 0.05F, 0.0F, 1.0F) : 0.0F;
        Vector3f dir = locked ? new Vector3f((float) target.x, (float) target.y - cy, (float) target.z).normalize() : new Vector3f(0, 1, 0);
        Vector3f focus = new Vector3f(dir).mul(0.35F * charge).add(center);

        // Runen auf der Decke ueber den vier Laser-Bloecken
        for (int k = 0; k < 4; k++) {
            float ang = k * Mth.HALF_PI;
            Matrix4f rm = new Matrix4f(m).translate(Mth.cos(ang), half + 0.53F, Mth.sin(ang)).rotateY(t * 0.02F);
            flatCircle(vc, rm, 0.22F, 0.008F, 24, BEAM, energy * 0.5F);
            flatCircle(vc, rm, 0.12F, 0.006F, 16, VIOLET, energy * 0.4F);
        }

        // Partikel: kreisen, beim Aufladen spiralen sie zum Brennpunkt
        float spin = t * (0.05F + 0.35F * charge * charge);
        float radius = Mth.lerp(charge, ORBIT, 0.14F);
        for (int k = 0; k < 4; k++) {
            float[] p = particle(k, spin, radius, cy, t, focus, charge);
            float glowSize = 0.05F + 0.03F * charge;
            cube(vc, m, p[0], p[1], p[2], glowSize, WHITE[0], WHITE[1], WHITE[2], energy);
            RenderUtil.glowSphere(vc, new Matrix4f(m).translate(p[0], p[1], p[2]), 0.11F + 0.06F * charge, 6, 10,
                    k % 2 == 0 ? BEAM : VIOLET, 0.22F * energy);
            // Lichtschweif
            float[] prev = p;
            for (int i = 1; i <= 8; i++) {
                float[] q = particle(k, spin - i * (0.12F + 0.1F * charge), radius, cy, t - i, focus, charge);
                float a = energy * (1.0F - i / 9.0F) * 0.7F;
                float[] c = k % 2 == 0 ? BEAM : VIOLET;
                segment(vc, m, prev[0], prev[1], prev[2], q[0], q[1], q[2], 0.018F * (1.0F - i / 10.0F), c[0], c[1], c[2], a, a * 0.8F);
                prev = q;
            }
            // Aufladen: Blitze in den Brennpunkt
            if (locked && charge > 0.3F && hash((int) (t * 1.3F) + k * 7) > 0.2F) {
                lightning(m, vc, p, new float[]{focus.x, focus.y, focus.z}, (int) (t * 1.3F) + k * 11, energy * charge);
            }
        }

        if (!locked) {
            // Ruhe: zarter Bahnring
            flatCircle(vc, new Matrix4f(m).translate(0, cy, 0).rotateZ(0.25F * Mth.sin(t * 0.01F)), ORBIT, 0.006F, 64, BEAM, energy * 0.25F);
            return;
        }

        // Brennpunkt
        float pulse = 0.6F + 0.4F * Mth.sin(t * 0.8F);
        RenderUtil.glowSphere(vc, new Matrix4f(m).translate(focus.x, focus.y, focus.z), 0.1F + 0.2F * charge * pulse, 8, 12, WHITE, 0.4F * energy * charge);
        RenderUtil.glowSphere(vc, new Matrix4f(m).translate(focus.x, focus.y, focus.z), 0.25F + 0.25F * charge, 8, 12, BEAM, 0.18F * energy * charge);

        if (fire <= 0.0F) return;
        Vector3f tp = new Vector3f((float) target.x, (float) target.y, (float) target.z);
        float tr = Math.max(0.4F, targetSize * 0.5F);
        // Einschlagpunkt auf der Oberflaeche; der Strahl faehrt beim Zuenden zum Ziel aus
        Vector3f hit = new Vector3f(dir).mul(-tr * 0.8F).add(tp);
        float ext = fire * fire * (3.0F - 2.0F * fire);
        Vector3f end = new Vector3f(focus).lerp(hit, ext);
        float flick = 0.88F + 0.12F * Mth.sin(t * 2.3F) * Mth.sin(t * 0.7F);
        Vector3f u = new Vector3f(dir).cross(Math.abs(dir.y) > 0.95F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        Vector3f w = new Vector3f(dir).cross(u).normalize();
        float len = new Vector3f(end).sub(focus).length();

        // Strahl: gleissender Kern mit weich auslaufenden Huellen
        float[] widths = {0.025F, 0.06F, 0.13F, 0.26F};
        float[][] cols = {WHITE, lerp(WHITE, BEAM, 0.4F), BEAM, VIOLET};
        float[] alphas = {1.0F, 0.6F, 0.26F, 0.09F};
        for (int i = 0; i < 4; i++) {
            float a = alphas[i] * energy * flick;
            segment(vc, m, focus.x, focus.y, focus.z, end.x, end.y, end.z, widths[i], cols[i][0], cols[i][1], cols[i][2], a, a * 0.85F);
        }
        // Lichtimpulse, die zum Asteroiden jagen
        for (int k = 0; k < 5; k++) {
            float s = Mth.frac(t * 0.07F + k / 5.0F);
            if (s > ext) continue;
            Vector3f p = new Vector3f(focus).lerp(hit, s);
            float a = energy * Mth.sin(s * Mth.PI);
            RenderUtil.glowSphere(vc, new Matrix4f(m).translate(p.x, p.y, p.z), 0.07F, 6, 8, WHITE, 0.35F * a);
            segment(vc, m, p.x - dir.x * 0.4F, p.y - dir.y * 0.4F, p.z - dir.z * 0.4F, p.x, p.y, p.z, 0.05F,
                    BEAM[0], BEAM[1], BEAM[2], 0.0F, 0.6F * a);
        }
        // eine feine Spirale um den Strahl
        int steps = Math.max(12, (int) (len * 4));
        Vector3f prev = null;
        for (int i = 0; i <= steps; i++) {
            float s = i / (float) steps;
            float ang = s * len * 1.8F - t * 0.5F;
            float hr = 0.085F * (1.0F - 0.3F * s);
            Vector3f p = new Vector3f(focus).lerp(end, s).add(new Vector3f(u).mul(Mth.cos(ang) * hr)).add(new Vector3f(w).mul(Mth.sin(ang) * hr));
            if (prev != null) segment(vc, m, prev.x, prev.y, prev.z, p.x, p.y, p.z, 0.007F, VIOLET[0], VIOLET[1], VIOLET[2], 0.55F * energy, 0.55F * energy);
            prev = p;
        }
        // kurze Entladungen, die am Strahl entlangkriechen
        int arcSeed = (int) (t * 0.5F);
        if (hash(arcSeed * 3) > 0.1F) {
            float s = 0.15F + 0.7F * (hash(arcSeed * 7) * 0.5F + 0.5F);
            Vector3f p = new Vector3f(focus).lerp(end, s);
            Vector3f out = new Vector3f(u).mul(hash(arcSeed * 11) * 0.35F).add(new Vector3f(w).mul(hash(arcSeed * 13) * 0.35F)).add(p);
            lightning(m, vc, new float[]{p.x, p.y, p.z}, new float[]{out.x, out.y, out.z}, arcSeed, 0.7F * energy);
        }
        // Muendungsblitz am Brennpunkt
        Matrix4f fm = new Matrix4f(m).translate(focus.x, focus.y, focus.z);
        RenderUtil.glowSphere(vc, fm, 0.16F + 0.04F * flick, 8, 10, WHITE, 0.5F * energy);
        segment(vc, fm, 0, 0, 0, u.x * 0.45F, u.y * 0.45F, u.z * 0.45F, 0.012F, 1, 1, 1, 0.8F * energy, 0.0F);
        segment(vc, fm, 0, 0, 0, -u.x * 0.45F, -u.y * 0.45F, -u.z * 0.45F, 0.012F, 1, 1, 1, 0.8F * energy, 0.0F);
        segment(vc, fm, 0, 0, 0, w.x * 0.45F, w.y * 0.45F, w.z * 0.45F, 0.012F, 1, 1, 1, 0.8F * energy, 0.0F);
        segment(vc, fm, 0, 0, 0, -w.x * 0.45F, -w.y * 0.45F, -w.z * 0.45F, 0.012F, 1, 1, 1, 0.8F * energy, 0.0F);
        if (ext < 0.99F) return;

        // Einschlag: heisser Fleck, Funkenregen und eine dezente Schockwelle
        Matrix4f hm = new Matrix4f(m).translate(hit.x, hit.y, hit.z);
        RenderUtil.glowSphere(vc, hm, 0.12F + 0.05F * flick, 8, 10, WHITE, 0.7F * energy);
        RenderUtil.glowSphere(vc, hm, 0.3F + 0.25F * harvest, 8, 12, GOLD, (0.25F + 0.2F * harvest) * energy);
        for (int k = 0; k < 14; k++) {
            float ph = Mth.frac(t * 0.08F + hash(k * 17) * 0.5F + k / 14.0F);
            Vector3f sd = new Vector3f(dir).mul(-0.5F).add(new Vector3f(u).mul(hash(k * 5) * 0.9F))
                    .add(new Vector3f(w).mul(hash(k * 9) * 0.9F)).normalize();
            float dist = ph * (0.6F + 0.6F * (hash(k * 3) * 0.5F + 0.5F)) * (1.0F + harvest);
            Vector3f p = new Vector3f(sd).mul(dist);
            p.y -= ph * ph * 0.5F;
            Vector3f q = new Vector3f(sd).mul(Math.max(0.0F, dist - 0.18F));
            q.y -= Math.max(0.0F, ph - 0.1F) * Math.max(0.0F, ph - 0.1F) * 0.5F;
            float[] c = lerp(WHITE, GOLD, ph);
            segment(vc, hm, q.x, q.y, q.z, p.x, p.y, p.z, 0.012F, c[0], c[1], c[2], 0.0F, energy * (1.0F - ph));
        }
        float ph = Mth.frac(t * 0.06F);
        boolean steep = Math.abs(dir.y) > 0.95F;
        Matrix4f sm = new Matrix4f(hm).rotateTowards(-dir.x, -dir.y, -dir.z, steep ? 1 : 0, steep ? 0 : 1, 0).rotateX(Mth.HALF_PI);
        flatCircle(vc, sm, tr * (0.3F + 0.9F * ph), 0.02F, 32, GOLD, energy * (1.0F - ph) * 0.6F);

        // Energie stroemt aus dem Asteroiden den Strahl entlang zurueck ...
        int motes = 7;
        for (int k = 0; k < motes; k++) {
            float s = Mth.frac(t * 0.03F + k / (float) motes); // 0 = Asteroid, 1 = Brennpunkt
            Vector3f p = new Vector3f(hit).lerp(focus, s * s);
            float off = 0.14F * (1.0F - s);
            p.add(new Vector3f(u).mul(hash(k * 13) * off)).add(new Vector3f(w).mul(hash(k * 29) * off));
            float[] c = lerp(GOLD, BEAM, s);
            cube(vc, m, p.x, p.y, p.z, 0.03F * (1.0F - 0.4F * s), c[0], c[1], c[2], energy * Mth.sin(s * Mth.PI));
        }
        // ... und vom Brennpunkt hinab in den Reaktor
        for (int k = 0; k < 4; k++) {
            float s = Mth.frac(t * 0.06F + k / 4.0F);
            float y = Mth.lerp(s, focus.y, half + 0.5F);
            cube(vc, m, focus.x * (1.0F - s), y, focus.z * (1.0F - s), 0.035F, GOLD[0], GOLD[1], GOLD[2], energy * (1.0F - s));
        }
        segment(vc, m, focus.x, focus.y, focus.z, 0, half + 0.5F, 0, 0.015F, GOLD[0], GOLD[1], GOLD[2], 0.5F * energy, 0.2F * energy);
    }

    /**
     * Uebergabe nach einem Abschuss (Phasen von ph 0..1):
     * <ol>
     *   <li>0 - 0.5: Explosionsblitz am Asteroiden, seine Bruchstuecke fliegen zum Laser - jedes landet mit kleinem
     *       Lichtring; die Energiekugel waechst.</li>
     *   <li>0.5 - 0.62: die Kugel formt sich - Schockwellen und Strahlenkranz.</li>
     *   <li>0.62 - 1: sie sinkt mit Lichtschweif in den Kern und geht dort mit Blitz und Ringwellen auf.</li>
     * </ol>
     */
    private static void delivery(Matrix4f m, VertexConsumer vc, float cy, float t, float ph, float energy, Vec3 source) {
        Vector3f mid = new Vector3f(0, cy, 0);
        Vector3f src = source != null ? new Vector3f((float) source.x, (float) source.y, (float) source.z)
                : new Vector3f(0, cy + 6.0F, 0);
        if (ph < 0.5F) {
            float g = ph / 0.5F;
            // Explosionsblitz am Asteroiden
            if (g < 0.35F) {
                float e = g / 0.35F;
                Matrix4f sm = new Matrix4f(m).translate(src.x, src.y, src.z);
                RenderUtil.glowSphere(vc, sm, 0.5F + 3.0F * e, 10, 14, GOLD, 0.5F * energy * (1.0F - e));
                RenderUtil.glowSphere(vc, sm, 0.3F + 1.2F * e, 8, 12, WHITE, 0.8F * energy * (1.0F - e));
                flatCircle(vc, sm, 1.0F + 5.0F * e, 0.12F, 64, GOLD, energy * (1.0F - e));
            }
            // wachsende Energiekugel
            Matrix4f om = new Matrix4f(m).translate(mid.x, mid.y, mid.z);
            RenderUtil.glowSphere(vc, om, 0.06F + 0.3F * g, 10, 14, GOLD, 0.45F * energy);
            RenderUtil.glowSphere(vc, om, 0.04F + 0.15F * g, 8, 12, WHITE, 0.7F * energy);
            // Bruchstuecke
            int fragments = 24;
            for (int k = 0; k < fragments; k++) {
                float delay = k / (float) fragments * 0.35F;
                float raw = (g - delay) / 0.65F;
                if (raw <= 0.0F) continue;
                if (raw < 1.0F) {
                    Vector3f scatter = new Vector3f(hash(k * 7), hash(k * 13), hash(k * 19)).mul(2.0F);
                    Vector3f p = fragmentPos(src, mid, scatter, raw);
                    Vector3f q = fragmentPos(src, mid, scatter, Math.max(0.0F, raw - 0.06F));
                    float[] c = lerp(GOLD, BEAM, hash(k * 3) * 0.5F + 0.5F);
                    shard(vc, m, p, 0.05F + 0.05F * (1.0F - raw), t * 0.4F + k, c, energy);
                    segment(vc, m, q.x, q.y, q.z, p.x, p.y, p.z, 0.03F, c[0], c[1], c[2], 0.0F, 0.7F * energy);
                } else if (raw < 1.3F) {
                    // Ankunft: kleiner Lichtring um die Kugel
                    float a = (raw - 1.0F) / 0.3F;
                    Matrix4f rm = new Matrix4f(om).rotateX(hash(k * 5) * Mth.PI).rotateZ(hash(k * 11) * Mth.PI);
                    flatCircle(vc, rm, 0.2F + 0.5F * a, 0.015F, 32, WHITE, energy * (1.0F - a));
                }
            }
            return;
        }
        if (ph < 0.62F) {
            // Kugel formt sich: Schockwellen und Strahlenkranz
            float f = (ph - 0.5F) / 0.12F;
            Matrix4f om = new Matrix4f(m).translate(mid.x, mid.y, mid.z);
            RenderUtil.glowSphere(vc, om, 0.36F + 0.08F * Mth.sin(f * Mth.PI), 10, 14, GOLD, 0.5F * energy);
            RenderUtil.glowSphere(vc, om, 0.19F, 8, 12, WHITE, 0.85F * energy);
            flatCircle(vc, om, 0.4F + 2.2F * f, 0.05F, 64, GOLD, energy * (1.0F - f));
            flatCircle(vc, new Matrix4f(om).rotateX(Mth.HALF_PI), 0.4F + 1.6F * f, 0.04F, 64, WHITE, energy * (1.0F - f));
            for (int k = 0; k < 8; k++) {
                float ang = k * Mth.TWO_PI / 8;
                float len = 0.5F + 1.2F * Mth.sin(f * Mth.PI);
                segment(vc, om, 0, 0, 0, Mth.cos(ang) * len, Mth.sin(ang * 2) * 0.3F * len, Mth.sin(ang) * len, 0.02F,
                        WHITE[0], WHITE[1], WHITE[2], energy, 0.0F);
            }
            return;
        }
        float d = (ph - 0.62F) / 0.38F;
        float fall = d * d;
        float y = Mth.lerp(fall, cy, 0.0F);
        float r = 0.4F * (1.0F - 0.55F * d);
        Matrix4f om = new Matrix4f(m).translate(0, y, 0);
        RenderUtil.glowSphere(vc, om, r, 10, 14, GOLD, 0.45F * energy);
        RenderUtil.glowSphere(vc, om, r * 0.5F, 8, 12, WHITE, 0.8F * energy);
        // Lichtschweif nach oben
        float tail = Mth.lerp(Math.max(0.0F, d - 0.15F) * Math.max(0.0F, d - 0.15F), cy, 0.0F);
        segment(vc, m, 0, tail, 0, 0, y, 0, r * 0.5F, GOLD[0], GOLD[1], GOLD[2], 0.0F, 0.6F * energy);
        if (d > 0.85F) {
            // Aufnahme im Kern
            float f = (d - 0.85F) / 0.15F;
            RenderUtil.glowSphere(vc, m, 0.3F + 1.2F * f, 10, 14, WHITE, 0.6F * energy * (1.0F - f));
            flatCircle(vc, m, 0.5F + 2.2F * f, 0.04F, 64, GOLD, energy * (1.0F - f));
            flatCircle(vc, new Matrix4f(m).rotateX(Mth.HALF_PI), 0.4F + 1.8F * f, 0.03F, 64, WHITE, energy * (1.0F - f));
            flatCircle(vc, new Matrix4f(m).rotateZ(Mth.HALF_PI), 0.4F + 1.8F * f, 0.03F, 64, BEAM, energy * (1.0F - f));
        }
    }

    /** Rotierender Kristallsplitter: heller Kern mit leuchtender Doppelpyramide. */
    private static void shard(VertexConsumer vc, Matrix4f m, Vector3f p, float s, float spin, float[] c, float a) {
        Matrix4f sm = new Matrix4f(m).translate(p.x, p.y, p.z).rotateY(spin).rotateZ(spin * 0.6F);
        cube(vc, sm, 0, 0, 0, s * 0.35F, 1.0F, 1.0F, 1.0F, a);
        RenderUtil.octahedron(vc, sm, s * 0.7F, s * 1.6F, 0.008F, c, a);
        RenderUtil.glowSphere(vc, sm, s * 1.1F, 6, 8, c, 0.18F * a);
    }

    /** Flugbahn eines Bruchstuecks: vom (aufgesprengten) Asteroiden in einem Bogen zum Laser. */
    private static Vector3f fragmentPos(Vector3f src, Vector3f mid, Vector3f scatter, float f) {
        float e = f * f * (3.0F - 2.0F * f);
        Vector3f p = new Vector3f(src).add(new Vector3f(scatter).mul(Mth.sin(Math.min(1.0F, f * 2.0F) * Mth.HALF_PI)));
        p.lerp(mid, e);
        p.y += Mth.sin(f * Mth.PI) * 1.5F;
        return p;
    }

    /** Position eines Partikels: Kreisbahn um die Bahnmitte, beim Aufladen hin zum Brennpunkt gezogen. */
    private static float[] particle(int k, float spin, float radius, float cy, float t, Vector3f focus, float charge) {
        float ang = spin + k * Mth.HALF_PI;
        float tilt = 0.35F;
        float x = Mth.cos(ang) * radius;
        float z = Mth.sin(ang) * radius;
        float y = cy + Mth.sin(ang) * radius * tilt + 0.08F * Mth.sin(t * 0.05F + k);
        return new float[]{Mth.lerp(charge, x, focus.x + x * 0.6F), Mth.lerp(charge, y, focus.y + (y - cy) * 0.6F),
                Mth.lerp(charge, z, focus.z + z * 0.6F)};
    }

    private static void lightning(Matrix4f m, VertexConsumer vc, float[] a, float[] b, int seed, float alpha) {
        float px = a[0], py = a[1], pz = a[2];
        for (int i = 1; i <= 4; i++) {
            float f = i / 4.0F;
            float nx = Mth.lerp(f, a[0], b[0]) + (i < 4 ? hash(seed * 7 + i) * 0.07F : 0);
            float ny = Mth.lerp(f, a[1], b[1]) + (i < 4 ? hash(seed * 11 + i) * 0.07F : 0);
            float nz = Mth.lerp(f, a[2], b[2]) + (i < 4 ? hash(seed * 13 + i) * 0.07F : 0);
            segment(vc, m, px, py, pz, nx, ny, nz, 0.008F, 1.0F, 1.0F, 1.0F, alpha, alpha);
            px = nx;
            py = ny;
            pz = nz;
        }
    }
}
