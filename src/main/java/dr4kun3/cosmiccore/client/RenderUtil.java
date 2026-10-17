package dr4kun3.cosmiccore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Prozedurale Geometrie fuer die leuchtenden Effekte (gedacht fuer CosmicRenderTypes.glow()). */
public final class RenderUtil {
    /** Nur statische Zeichenroutinen - keine Instanzen. */
    private RenderUtil() {}

    /**
     * Leuchtender Ring mit laufenden Lichtpunkten.
     *
     * @param pose Pose-Stapel (Ursprung = Ringmitte)
     * @param vc Puffer fuer additives Licht
     * @param radius Radius
     * @param halfWidth halbe Bandbreite
     * @param spin Drehung in Grad
     * @param tiltX Neigung um x in Grad
     * @param tiltZ Neigung um z in Grad
     * @param t Animationszeit (laufende Lichter)
     * @param col Farbe RGB 0..1
     * @param intensity Helligkeit 0..1
     */
    public static void ring(PoseStack pose, VertexConsumer vc, float radius, float halfWidth, float spin,
                            float tiltX, float tiltZ, float t, float[] col, float intensity) {
        pose.pushPose();
        pose.rotate(Axis.ZP.rotationDegrees(tiltZ));
        pose.rotate(Axis.XP.rotationDegrees(tiltX));
        pose.rotate(Axis.YP.rotationDegrees(spin));
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

    /**
     * Punkt auf einer geneigten Kreisbahn (wie eine Planetenbahn).
     *
     * @param radius Bahnradius
     * @param angle Winkel auf der Bahn (Bogenmass)
     * @param inclDeg Bahnneigung in Grad
     * @param nodeDeg Drehung der Bahnebene um die Hochachse in Grad
     * @return {x, y, z}
     */
    public static float[] orbitPoint(float radius, float angle, float inclDeg, float nodeDeg) {
        float x = radius * Mth.cos(angle);
        float z = radius * Mth.sin(angle);
        float inc = inclDeg * Mth.DEG_TO_RAD;
        float y1 = z * Mth.sin(inc);
        float z1 = z * Mth.cos(inc);
        float node = nodeDeg * Mth.DEG_TO_RAD;
        return new float[]{x * Mth.cos(node) - z1 * Mth.sin(node), y1, x * Mth.sin(node) + z1 * Mth.cos(node)};
    }

    /**
     * Kugel nur mit Positionen (fuer das End-Sternenfeld).
     *
     * @param vc Puffer (End-Gateway)
     * @param m Matrix
     * @param r Radius
     * @param lat Anzahl Breitenringe
     * @param lon Anzahl Laengensegmente
     */
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

    /**
     * Undurchsichtige einfarbige Kugel (z. B. der schwarze Ereignishorizont).
     *
     * @param vc Puffer (deckend)
     * @param m Matrix
     * @param r Radius
     * @param lat Anzahl Breitenringe
     * @param lon Anzahl Laengensegmente
     * @param red Rot 0..1
     * @param green Gruen 0..1
     * @param blue Blau 0..1
     */
    public static void solidSphere(VertexConsumer vc, Matrix4f m, float r, int lat, int lon, float red, float green, float blue) {
        for (int i = 0; i < lat; i++) {
            float p0 = (float) (Math.PI * i / lat - Math.PI / 2);
            float p1 = (float) (Math.PI * (i + 1) / lat - Math.PI / 2);
            for (int j = 0; j < lon; j++) {
                float l0 = (float) (Math.PI * 2 * j / lon);
                float l1 = (float) (Math.PI * 2 * (j + 1) / lon);
                float[] a = sph(r, p0, l0), b = sph(r, p1, l0), c = sph(r, p1, l1), d = sph(r, p0, l1);
                quad2(vc, m, a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], red, green, blue, 1.0F);
            }
        }
    }

    /**
     * Ringsegment in der XY-Ebene; die Deckkraft verlaeuft von innen nach aussen.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param r0 Innenradius
     * @param r1 Aussenradius
     * @param ang0 Startwinkel (Bogenmass)
     * @param ang1 Endwinkel (Bogenmass)
     * @param col Farbe RGB 0..1
     * @param aIn Deckkraft innen
     * @param aOut Deckkraft aussen
     */
    public static void annulusXY(VertexConsumer vc, Matrix4f m, float r0, float r1, float ang0, float ang1,
                                 float[] col, float aIn, float aOut) {
        float c0 = Mth.cos(ang0), s0 = Mth.sin(ang0), c1 = Mth.cos(ang1), s1 = Mth.sin(ang1);
        quadFade(vc, m, r0 * c0, r0 * s0, 0, r0 * c1, r0 * s1, 0, r1 * c1, r1 * s1, 0, r1 * c0, r1 * s0, 0,
                col[0], col[1], col[2], aIn, aOut);
    }

    /**
     * Leuchtende Kugelhuelle.
     *
     * @param vc Puffer fuer additives Licht
     * @param m Matrix
     * @param r Radius
     * @param lat Anzahl Breitenringe
     * @param lon Anzahl Laengensegmente
     * @param col Farbe RGB 0..1
     * @param alpha Helligkeit 0..1
     */
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

    /**
     * Dreieck nur mit Positionen, beidseitig sichtbar (als entartetes Viereck) - fuer das Sternenfeld.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param a Ecke 1 {x, y, z}
     * @param b Ecke 2
     * @param c Ecke 3
     */
    private static void starTri(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float[] c) {
        vc.addVertex(m, a[0], a[1], a[2]);
        vc.addVertex(m, b[0], b[1], b[2]);
        vc.addVertex(m, c[0], c[1], c[2]);
        vc.addVertex(m, c[0], c[1], c[2]);
        vc.addVertex(m, c[0], c[1], c[2]);
        vc.addVertex(m, b[0], b[1], b[2]);
        vc.addVertex(m, a[0], a[1], a[2]);
        vc.addVertex(m, a[0], a[1], a[2]);
    }

    /**
     * Massiver Oktaeder mit Sternenfeld.
     *
     * @param vc Puffer (End-Gateway)
     * @param m Matrix
     * @param rx Radius in der Waagrechten
     * @param ry halbe Hoehe
     */
    public static void starOctahedron(VertexConsumer vc, Matrix4f m, float rx, float ry) {
        float[][] eq = {{rx, 0, 0}, {0, 0, rx}, {-rx, 0, 0}, {0, 0, -rx}};
        float[] top = {0, ry, 0}, bottom = {0, -ry, 0};
        for (int i = 0; i < 4; i++) {
            starTri(vc, m, eq[i], eq[(i + 1) % 4], top);
            starTri(vc, m, eq[i], eq[(i + 1) % 4], bottom);
        }
    }

    /**
     * Massiver senkrechter Zylinder mit Deckeln und Sternenfeld.
     *
     * @param vc Puffer (End-Gateway)
     * @param m Matrix
     * @param r Radius
     * @param halfH halbe Hoehe
     * @param seg Anzahl Segmente
     */
    public static void starCylinder(VertexConsumer vc, Matrix4f m, float r, float halfH, int seg) {
        float[] ct = {0, halfH, 0}, cb = {0, -halfH, 0};
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float x0 = Mth.cos(a0) * r, z0 = Mth.sin(a0) * r, x1 = Mth.cos(a1) * r, z1 = Mth.sin(a1) * r;
            vc.addVertex(m, x0, -halfH, z0);
            vc.addVertex(m, x1, -halfH, z1);
            vc.addVertex(m, x1, halfH, z1);
            vc.addVertex(m, x0, halfH, z0);
            vc.addVertex(m, x0, halfH, z0);
            vc.addVertex(m, x1, halfH, z1);
            vc.addVertex(m, x1, -halfH, z1);
            vc.addVertex(m, x0, -halfH, z0);
            starTri(vc, m, new float[]{x0, halfH, z0}, new float[]{x1, halfH, z1}, ct);
            starTri(vc, m, new float[]{x0, -halfH, z0}, new float[]{x1, -halfH, z1}, cb);
        }
    }

    /**
     * Beidseitiges Viereck nur mit Positionen (Sternenfeld).
     *
     * @param vc Puffer
     * @param m Matrix
     * @param a Ecke 1 {x, y, z}
     * @param b Ecke 2
     * @param c Ecke 3
     * @param d Ecke 4
     */
    private static void starQuad(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float[] c, float[] d) {
        vc.addVertex(m, a[0], a[1], a[2]);
        vc.addVertex(m, b[0], b[1], b[2]);
        vc.addVertex(m, c[0], c[1], c[2]);
        vc.addVertex(m, d[0], d[1], d[2]);
        vc.addVertex(m, d[0], d[1], d[2]);
        vc.addVertex(m, c[0], c[1], c[2]);
        vc.addVertex(m, b[0], b[1], b[2]);
        vc.addVertex(m, a[0], a[1], a[2]);
    }

    /**
     * Torus in der XY-Ebene (Achse = Z) mit Sternenfeld.
     *
     * @param vc Puffer (End-Gateway)
     * @param m Matrix
     * @param big Radius des Rings
     * @param tube Radius der Roehre
     * @param segU Segmente entlang des Rings
     * @param segV Segmente um die Roehre
     */
    public static void starTorus(VertexConsumer vc, Matrix4f m, float big, float tube, int segU, int segV) {
        for (int i = 0; i < segU; i++) {
            float u0 = Mth.TWO_PI * i / segU, u1 = Mth.TWO_PI * (i + 1) / segU;
            for (int j = 0; j < segV; j++) {
                float v0 = Mth.TWO_PI * j / segV, v1 = Mth.TWO_PI * (j + 1) / segV;
                starQuad(vc, m, torus(big, tube, u0, v0), torus(big, tube, u1, v0), torus(big, tube, u1, v1), torus(big, tube, u0, v1));
            }
        }
    }

    /**
     * Punkt auf dem Torus.
     *
     * @param big Radius des Rings
     * @param tube Radius der Roehre
     * @param u Winkel entlang des Rings
     * @param v Winkel um die Roehre
     * @return {x, y, z}
     */
    private static float[] torus(float big, float tube, float u, float v) {
        float r = big + tube * Mth.cos(v);
        return new float[]{r * Mth.cos(u), r * Mth.sin(u), tube * Mth.sin(v)};
    }

    /**
     * Vierkant-Strebe mit Sternenfeld zwischen zwei beliebigen Punkten.
     *
     * @param vc Puffer (End-Gateway)
     * @param m Matrix
     * @param ax Start x
     * @param ay Start y
     * @param az Start z
     * @param bx Ende x
     * @param by Ende y
     * @param bz Ende z
     * @param w halbe Staerke
     */
    public static void starStrut(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float w) {
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
        float[][] o = {{n1x + n2x, n1y + n2y, n1z + n2z}, {n1x - n2x, n1y - n2y, n1z - n2z},
                {-n1x - n2x, -n1y - n2y, -n1z - n2z}, {-n1x + n2x, -n1y + n2y, -n1z + n2z}};
        for (int i = 0; i < 4; i++) {
            float[] p = o[i], q = o[(i + 1) % 4];
            starQuad(vc, m, new float[]{ax + p[0], ay + p[1], az + p[2]}, new float[]{ax + q[0], ay + q[1], az + q[2]},
                    new float[]{bx + q[0], by + q[1], bz + q[2]}, new float[]{bx + p[0], by + p[1], bz + p[2]});
        }
    }

    /**
     * Trompetenfoermige senkrechte Rotationsflaeche mit Sternenfeld.
     *
     * @param vc Puffer (End-Gateway)
     * @param m Matrix
     * @param y0 untere Hoehe
     * @param y1 obere Hoehe
     * @param r0 Radius unten
     * @param r1 Radius oben
     * @param exp Kruemmung (1 = Kegel, groesser = Trompete)
     * @param seg Segmente rundherum
     * @param steps Abschnitte in der Hoehe
     */
    public static void starTrumpet(VertexConsumer vc, Matrix4f m, float y0, float y1, float r0, float r1, float exp, int seg, int steps) {
        for (int i = 0; i < steps; i++) {
            float f0 = i / (float) steps, f1 = (i + 1) / (float) steps;
            float ya = Mth.lerp(f0, y0, y1), yb = Mth.lerp(f1, y0, y1);
            float ra = r0 + (r1 - r0) * (float) Math.pow(f0, exp), rb = r0 + (r1 - r0) * (float) Math.pow(f1, exp);
            for (int j = 0; j < seg; j++) {
                float a0 = Mth.TWO_PI * j / seg, a1 = Mth.TWO_PI * (j + 1) / seg;
                starQuad(vc, m, new float[]{Mth.cos(a0) * ra, ya, Mth.sin(a0) * ra}, new float[]{Mth.cos(a1) * ra, ya, Mth.sin(a1) * ra},
                        new float[]{Mth.cos(a1) * rb, yb, Mth.sin(a1) * rb}, new float[]{Mth.cos(a0) * rb, yb, Mth.sin(a0) * rb});
            }
        }
    }

    /**
     * Achsparalleler Quader mit Sternenfeld.
     *
     * @param vc Puffer (End-Gateway)
     * @param m Matrix
     * @param x0 kleinste x
     * @param y0 kleinste y
     * @param z0 kleinste z
     * @param x1 groesste x
     * @param y1 groesste y
     * @param z1 groesste z
     */
    public static void starBox(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1) {
        float[] a = {x0, y0, z0}, b = {x1, y0, z0}, c = {x1, y0, z1}, d = {x0, y0, z1};
        float[] e = {x0, y1, z0}, f = {x1, y1, z0}, g = {x1, y1, z1}, h = {x0, y1, z1};
        starQuad(vc, m, a, b, c, d);
        starQuad(vc, m, e, f, g, h);
        starQuad(vc, m, a, b, f, e);
        starQuad(vc, m, d, c, g, h);
        starQuad(vc, m, a, d, h, e);
        starQuad(vc, m, b, c, g, f);
    }

    /**
     * Senkrechte Lichtroehre (runder Strahl), von allen Seiten gleich dick.
     *
     * @param vc Puffer fuer additives Licht
     * @param m Matrix
     * @param x Achse x
     * @param y0 untere Hoehe
     * @param z Achse z
     * @param y1 obere Hoehe
     * @param r Radius
     * @param sides Anzahl Seiten
     * @param col Farbe RGB 0..1
     * @param a0 Helligkeit unten
     * @param a1 Helligkeit oben
     */
    public static void tube(VertexConsumer vc, Matrix4f m, float x, float y0, float z, float y1, float r, int sides,
                            float[] col, float a0, float a1) {
        for (int i = 0; i < sides; i++) {
            float t0 = Mth.TWO_PI * i / sides, t1 = Mth.TWO_PI * (i + 1) / sides;
            float x0 = x + Mth.cos(t0) * r, z0 = z + Mth.sin(t0) * r, x1 = x + Mth.cos(t1) * r, z1 = z + Mth.sin(t1) * r;
            quadFade(vc, m, x0, y0, z0, x1, y0, z1, x1, y1, z1, x0, y1, z0, col[0], col[1], col[2], a0, a1);
        }
    }

    /**
     * Flacher Leuchtkreis in der XZ-Ebene (y = 0).
     *
     * @param vc Puffer
     * @param m Matrix
     * @param radius Radius
     * @param w halbe Linienbreite
     * @param seg Anzahl Segmente
     * @param col Farbe RGB 0..1
     * @param a Helligkeit 0..1
     */
    public static void flatCircle(VertexConsumer vc, Matrix4f m, float radius, float w, int seg, float[] col, float a) {
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float r0 = radius - w, r1 = radius + w;
            quad2(vc, m, r0 * Mth.cos(a0), 0, r0 * Mth.sin(a0), r1 * Mth.cos(a0), 0, r1 * Mth.sin(a0),
                    r1 * Mth.cos(a1), 0, r1 * Mth.sin(a1), r0 * Mth.cos(a1), 0, r0 * Mth.sin(a1), col[0], col[1], col[2], a);
        }
    }

    /**
     * Flaches Leuchtband zwischen zwei Punkten in der XZ-Ebene.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param ax Start x
     * @param az Start z
     * @param bx Ende x
     * @param bz Ende z
     * @param w halbe Breite
     * @param col Farbe RGB 0..1
     * @param a Helligkeit 0..1
     */
    public static void flatLine(VertexConsumer vc, Matrix4f m, float ax, float az, float bx, float bz, float w, float[] col, float a) {
        float dx = bx - ax, dz = bz - az;
        float len = Mth.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4F) return;
        float nx = -dz / len * w, nz = dx / len * w;
        quad2(vc, m, ax - nx, 0, az - nz, ax + nx, 0, az + nz, bx + nx, 0, bz + nz, bx - nx, 0, bz - nz, col[0], col[1], col[2], a);
    }

    /**
     * Punkt auf einer Kugel.
     *
     * @param r Radius
     * @param phi Breite (Bogenmass)
     * @param lam Laenge (Bogenmass)
     * @return {x, y, z}
     */
    private static float[] sph(float r, float phi, float lam) {
        float cp = Mth.cos(phi);
        return new float[]{r * cp * Mth.cos(lam), r * Mth.sin(phi), r * cp * Mth.sin(lam)};
    }

    /**
     * Gezackter Energiebogen vom Ursprung zum Ziel.
     *
     * @param vc Puffer
     * @param m Matrix (Ursprung = Start)
     * @param tx Ziel x
     * @param ty Ziel y
     * @param tz Ziel z
     * @param seed Zufallswert fuer den Verlauf
     * @param col Farbe RGB 0..1
     * @param flicker Helligkeit 0..1
     * @param wild staerker gezackt (bei Instabilitaet)
     */
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

    /**
     * Duenner Strahl aus zwei gekreuzten Flaechen.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param ax Start x
     * @param ay Start y
     * @param az Start z
     * @param bx Ende x
     * @param by Ende y
     * @param bz Ende z
     * @param w halbe Breite
     * @param r Rot 0..1
     * @param g Gruen 0..1
     * @param b Blau 0..1
     * @param a0 Deckkraft am Anfang
     * @param a1 Deckkraft am Ende
     */
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

    /**
     * Beidseitiges Viereck; die Deckkraft verlaeuft von der ersten Kante (Ecken 1 und 2) zur zweiten (Ecken 3 und 4).
     *
     * @param vc Puffer
     * @param m Matrix
     * @param x0 Ecke 1 x
     * @param y0 Ecke 1 y
     * @param z0 Ecke 1 z
     * @param x1 Ecke 2 x
     * @param y1 Ecke 2 y
     * @param z1 Ecke 2 z
     * @param x2 Ecke 3 x
     * @param y2 Ecke 3 y
     * @param z2 Ecke 3 z
     * @param x3 Ecke 4 x
     * @param y3 Ecke 4 y
     * @param z3 Ecke 4 z
     * @param r Rot 0..1
     * @param g Gruen 0..1
     * @param b Blau 0..1
     * @param a0 Deckkraft an Ecken 1 und 2
     * @param a1 Deckkraft an Ecken 3 und 4
     */
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

    /**
     * Beidseitiges einfarbiges Viereck.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param x0 Ecke 1 x
     * @param y0 Ecke 1 y
     * @param z0 Ecke 1 z
     * @param x1 Ecke 2 x
     * @param y1 Ecke 2 y
     * @param z1 Ecke 2 z
     * @param x2 Ecke 3 x
     * @param y2 Ecke 3 y
     * @param z2 Ecke 3 z
     * @param x3 Ecke 4 x
     * @param y3 Ecke 4 y
     * @param z3 Ecke 4 z
     * @param r Rot 0..1
     * @param g Gruen 0..1
     * @param b Blau 0..1
     * @param a Deckkraft 0..1
     */
    public static void quad2(VertexConsumer vc, Matrix4f m,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        quadFade(vc, m, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, r, g, b, a, a);
    }

    /**
     * Einfarbiger Wuerfel um einen Mittelpunkt.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param cx Mitte x
     * @param cy Mitte y
     * @param cz Mitte z
     * @param s halbe Kantenlaenge
     * @param r Rot 0..1
     * @param g Gruen 0..1
     * @param b Blau 0..1
     * @param a Deckkraft 0..1
     */
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

    /**
     * Oktaeder als leuchtende Kanten (Kristallform).
     *
     * @param vc Puffer
     * @param m Matrix
     * @param rx Radius in der Waagrechten
     * @param ry halbe Hoehe
     * @param w Kantenbreite
     * @param col Farbe RGB 0..1
     * @param a Helligkeit 0..1
     */
    public static void octahedron(VertexConsumer vc, Matrix4f m, float rx, float ry, float w, float[] col, float a) {
        float[][] eq = {{rx, 0, 0}, {0, 0, rx}, {-rx, 0, 0}, {0, 0, -rx}};
        for (int i = 0; i < 4; i++) {
            float[] p = eq[i], q = eq[(i + 1) % 4];
            segment(vc, m, p[0], p[1], p[2], q[0], q[1], q[2], w, col[0], col[1], col[2], a, a);
            segment(vc, m, p[0], p[1], p[2], 0, ry, 0, w, col[0], col[1], col[2], a, a * 0.6F);
            segment(vc, m, p[0], p[1], p[2], 0, -ry, 0, w, col[0], col[1], col[2], a, a * 0.6F);
        }
    }

    /**
     * Mischt zwei Farben.
     *
     * @param a erste Farbe RGB 0..1
     * @param b zweite Farbe RGB 0..1
     * @param t Anteil der zweiten 0..1
     * @return Mischfarbe
     */
    public static float[] lerp(float[] a, float[] b, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /**
     * Billiger, fester Pseudozufall.
     *
     * @param seed Startwert
     * @return Wert in [-1, 1]
     */
    public static float hash(int seed) {
        int h = seed * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return ((h & 0xFFFF) / 32767.5F) - 1.0F;
    }
}
