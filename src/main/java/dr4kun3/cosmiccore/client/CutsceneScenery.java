package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.hash;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Kulissen der Titan-Cutscene, alle kamerazentriert (unendlich fern): Farbverlaufs-Himmel, Sternenfelder, weiche
 * Nebel, eine Spiralgalaxie, Sonnen mit Lichtreflexen, die Erde (Kontinente, Eis, Wolken, Stadtlichter, Atmosphaere),
 * ein Gasriese mit Baendern, Sturm und Ring, ein Mond und der Lichtgeschwindigkeits-Tunnel.
 * Die Matrix m ist jeweils auf die Kamera zentriert; right/up sind die Bildschirmachsen der Kamera.
 */
final class CutsceneScenery {
    /** Nur statische Zeichenroutinen - keine Instanzen. */
    private CutsceneScenery() {}

    interface ColorFn {
        /**
         * Farbe fuer einen Punkt der Einheitskugel (Planeten-Koordinaten).
         *
         * @param x Richtung x
         * @param y Richtung y
         * @param z Richtung z
         * @return Farbe als 0xAARRGGBB
         */
        int argb(float x, float y, float z);
    }

    // ------------------------------------------------------------------ Grundformen

    /**
     * Ein farbiger Eckpunkt.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param x Position x
     * @param y Position y
     * @param z Position z
     * @param c Farbe 0xAARRGGBB
     */
    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, int c) {
        vc.addVertex(m, x, y, z).setColor((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255);
    }

    /**
     * Kugel mit Farbe je Eckpunkt.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param c Mittelpunkt
     * @param r Radius
     * @param lat Anzahl Breitenringe
     * @param lon Anzahl Laengensegmente
     * @param rot Drehung der Oberflaeche um die eigene Achse (Bogenmass)
     * @param fn liefert die Farbe je Richtung
     */
    static void colorSphere(VertexConsumer vc, Matrix4f m, Vector3f c, float r, int lat, int lon, float rot, ColorFn fn) {
        int[][] col = new int[lat + 1][lon + 1];
        float[][][] pos = new float[lat + 1][lon + 1][];
        for (int i = 0; i <= lat; i++) {
            float phi = (float) (Math.PI * i / lat - Math.PI / 2);
            for (int j = 0; j <= lon; j++) {
                float lam = Mth.TWO_PI * j / lon;
                float x = Mth.cos(phi) * Mth.cos(lam), y = Mth.sin(phi), z = Mth.cos(phi) * Mth.sin(lam);
                pos[i][j] = new float[]{c.x + x * r, c.y + y * r, c.z + z * r};
                float lr = lam + rot;
                col[i][j] = fn.argb(Mth.cos(phi) * Mth.cos(lr), y, Mth.cos(phi) * Mth.sin(lr));
            }
        }
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                float[] a = pos[i][j], b = pos[i + 1][j], d = pos[i + 1][j + 1], e = pos[i][j + 1];
                vertex(vc, m, a[0], a[1], a[2], col[i][j]);
                vertex(vc, m, b[0], b[1], b[2], col[i + 1][j]);
                vertex(vc, m, d[0], d[1], d[2], col[i + 1][j + 1]);
                vertex(vc, m, e[0], e[1], e[2], col[i][j + 1]);
            }
        }
    }

    /**
     * Weiche Scheibe zur Kamera - Grundbaustein fuer Nebel, Halos und Lichter.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param c Mittelpunkt
     * @param right Kamera-Rechts-Vektor
     * @param up Kamera-Oben-Vektor
     * @param r0 Innenradius
     * @param r1 Aussenradius
     * @param col Farbe RGB 0..1
     * @param aIn Deckkraft innen
     * @param aOut Deckkraft aussen
     */
    static void disc(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f right, Vector3f up, float r0, float r1,
                     float[] col, float aIn, float aOut) {
        int seg = 24;
        for (int s = 0; s < seg; s++) {
            float a0 = Mth.TWO_PI * s / seg, a1 = Mth.TWO_PI * (s + 1) / seg;
            Vector3f i0 = at(c, right, up, Mth.cos(a0) * r0, Mth.sin(a0) * r0), i1 = at(c, right, up, Mth.cos(a1) * r0, Mth.sin(a1) * r0);
            Vector3f o1 = at(c, right, up, Mth.cos(a1) * r1, Mth.sin(a1) * r1), o0 = at(c, right, up, Mth.cos(a0) * r1, Mth.sin(a0) * r1);
            RenderUtil.quadFade(vc, m, i0.x, i0.y, i0.z, i1.x, i1.y, i1.z, o1.x, o1.y, o1.z, o0.x, o0.y, o0.z, col[0], col[1], col[2], aIn, aOut);
        }
    }

    /**
     * Punkt in der Kameraebene um einen Mittelpunkt.
     *
     * @param c Mittelpunkt
     * @param right Kamera-Rechts-Vektor
     * @param up Kamera-Oben-Vektor
     * @param x Versatz nach rechts
     * @param y Versatz nach oben
     * @return der Punkt
     */
    private static Vector3f at(Vector3f c, Vector3f right, Vector3f up, float x, float y) {
        return new Vector3f(c).add(new Vector3f(right).mul(x)).add(new Vector3f(up).mul(y));
    }

    /**
     * Gestreckter Lichtstreifen (anamorph) durch einen Punkt.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param c Mittelpunkt
     * @param dir Richtung des Streifens
     * @param side Querrichtung
     * @param len halbe Laenge
     * @param w halbe Breite
     * @param col Farbe RGB 0..1
     * @param a Deckkraft in der Mitte
     */
    static void streak(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f dir, Vector3f side, float len, float w, float[] col, float a) {
        Vector3f d = new Vector3f(dir).mul(len), s = new Vector3f(side).mul(w);
        Vector3f p0 = new Vector3f(c).sub(d), p1 = new Vector3f(c).add(d);
        RenderUtil.quadFade(vc, m, c.x - s.x, c.y - s.y, c.z - s.z, c.x + s.x, c.y + s.y, c.z + s.z,
                p1.x, p1.y, p1.z, p1.x, p1.y, p1.z, col[0], col[1], col[2], a, 0.0F);
        RenderUtil.quadFade(vc, m, c.x - s.x, c.y - s.y, c.z - s.z, c.x + s.x, c.y + s.y, c.z + s.z,
                p0.x, p0.y, p0.z, p0.x, p0.y, p0.z, col[0], col[1], col[2], a, 0.0F);
    }

    // ------------------------------------------------------------------ Himmel

    /**
     * Deckender Himmel mit Farbverlauf von oben nach unten (verdeckt Welt und Vanilla-Himmel).
     *
     * @param solid deckender Puffer
     * @param m Matrix (kamerazentriert)
     * @param r Radius
     * @param top Farbe oben 0xAARRGGBB
     * @param horizon Farbe am Horizont
     * @param bottom Farbe unten
     */
    static void gradientDome(VertexConsumer solid, Matrix4f m, float r, int top, int horizon, int bottom) {
        colorSphere(solid, m, new Vector3f(), r, 12, 20, 0.0F, (x, y, z) -> {
            int a = y > 0 ? horizon : horizon, b = y > 0 ? top : bottom;
            return lerpColor(a, b, (float) Math.pow(Math.abs(y), 0.7));
        });
    }

    /**
     * Sternenfeld: viele kleine, verschieden helle und farbige Sterne, die helleren mit Kreuzglanz und Funkeln.
     *
     * @param vc Puffer
     * @param m Matrix (kamerazentriert)
     * @param right Kamera-Rechts-Vektor
     * @param up Kamera-Oben-Vektor
     * @param seed Zufallswert (gleiche Sterne bei gleichem Wert)
     * @param count Anzahl Sterne
     * @param radius Entfernung der Sternkugel
     * @param t Animationszeit (Funkeln)
     * @param alpha Deckkraft 0..1
     */
    static void stars(VertexConsumer vc, Matrix4f m, Vector3f right, Vector3f up, int seed, int count, float radius, float t, float alpha) {
        float[][] tints = {{0.75F, 0.85F, 1.0F}, {1.0F, 1.0F, 1.0F}, {1.0F, 0.9F, 0.7F}, {1.0F, 0.7F, 0.6F}, {0.8F, 0.7F, 1.0F}};
        for (int i = 0; i < count; i++) {
            float z = hash(seed + i * 3), ph = hash(seed + i * 5) * Mth.PI;
            float rr = Mth.sqrt(Math.max(0.0F, 1.0F - z * z));
            Vector3f p = new Vector3f(rr * Mth.cos(ph), z, rr * Mth.sin(ph)).mul(radius);
            float mag = (float) Math.pow(hash(seed + i * 7) * 0.5F + 0.5F, 6.0); // wenige helle, viele schwache
            float tw = 0.75F + 0.25F * Mth.sin(t * (0.05F + 0.1F * (hash(seed + i * 11) * 0.5F + 0.5F)) + i);
            float s = 0.05F + 0.28F * mag;
            float[] c = tints[Math.floorMod(seed + i, tints.length)];
            float a = alpha * (0.35F + 0.65F * mag) * tw;
            Vector3f r0 = new Vector3f(right).mul(s), u0 = new Vector3f(up).mul(s);
            RenderUtil.quad2(vc, m, p.x - r0.x - u0.x, p.y - r0.y - u0.y, p.z - r0.z - u0.z, p.x + r0.x - u0.x, p.y + r0.y - u0.y,
                    p.z + r0.z - u0.z, p.x + r0.x + u0.x, p.y + r0.y + u0.y, p.z + r0.z + u0.z, p.x - r0.x + u0.x,
                    p.y - r0.y + u0.y, p.z - r0.z + u0.z, c[0], c[1], c[2], a);
            if (mag > 0.35F) { // Kreuzglanz der hellen Sterne
                streak(vc, m, p, right, up, s * 7.0F, s * 0.25F, c, a * 0.6F);
                streak(vc, m, p, up, right, s * 5.0F, s * 0.25F, c, a * 0.45F);
            }
        }
    }

    /**
     * Weicher Nebel aus vielen ueberlagerten, transparenten Wolken um eine Richtung.
     *
     * @param vc Puffer
     * @param m Matrix (kamerazentriert)
     * @param right Kamera-Rechts-Vektor
     * @param up Kamera-Oben-Vektor
     * @param dir Richtung des Nebels
     * @param dist Entfernung
     * @param spread Ausdehnung
     * @param a erste Farbe RGB 0..1
     * @param b zweite Farbe RGB 0..1
     * @param seed Zufallswert
     * @param blobs Anzahl Wolken
     * @param alpha Deckkraft 0..1
     */
    static void nebula(VertexConsumer vc, Matrix4f m, Vector3f right, Vector3f up, Vector3f dir, float dist, float spread,
                       float[] a, float[] b, int seed, int blobs, float alpha) {
        Vector3f d = new Vector3f(dir).normalize();
        Vector3f u = new Vector3f(d).cross(Math.abs(d.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        Vector3f v = new Vector3f(d).cross(u).normalize();
        for (int i = 0; i < blobs; i++) {
            float ox = hash(seed + i * 13) * spread, oy = hash(seed + i * 17) * spread * 0.6F;
            float rad = spread * (0.25F + 0.35F * (hash(seed + i * 19) * 0.5F + 0.5F));
            Vector3f p = new Vector3f(d).mul(dist).add(new Vector3f(u).mul(ox)).add(new Vector3f(v).mul(oy));
            float[] c = RenderUtil.lerp(a, b, hash(seed + i * 23) * 0.5F + 0.5F);
            disc(vc, m, p, right, up, 0.0F, rad, c, alpha * (0.5F + 0.5F * (hash(seed + i * 29) * 0.5F + 0.5F)), 0.0F);
        }
    }

    /**
     * Spiralgalaxie: heller Kern, zwei Arme aus Sternhaufen, Nebelflecken in den Armen.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param right Kamera-Rechts-Vektor
     * @param up Kamera-Oben-Vektor
     * @param c Mittelpunkt
     * @param radius Radius
     * @param tiltX Neigung um x (Bogenmass)
     * @param tiltZ Neigung um z (Bogenmass)
     * @param spin Drehung (Bogenmass)
     */
    static void galaxy(VertexConsumer vc, Matrix4f m, Vector3f right, Vector3f up, Vector3f c, float radius, float tiltX,
                       float tiltZ, float spin) {
        Matrix4f gm = new Matrix4f(m).translate(c.x, c.y, c.z).rotateX(tiltX).rotateZ(tiltZ).rotateY(spin);
        float[] core = {1.0F, 0.92F, 0.75F}, arm = {0.6F, 0.75F, 1.0F}, pink = {1.0F, 0.45F, 0.8F};
        for (int armI = 0; armI < 2; armI++) {
            for (int i = 0; i < 220; i++) {
                float f = (float) Math.pow((i + 1) / 220.0F, 0.8);
                float a = armI * Mth.PI + f * 7.5F + hash(armI * 997 + i) * 0.35F;
                float rr = radius * f * (1.0F + 0.12F * hash(armI * 31 + i * 3));
                float x = Mth.cos(a) * rr, z = Mth.sin(a) * rr, y = hash(armI * 53 + i * 7) * radius * 0.03F;
                float[] col = RenderUtil.lerp(core, i % 9 == 0 ? pink : arm, f);
                float s = radius * (0.006F + 0.01F * (1.0F - f));
                RenderUtil.cube(vc, gm, x, y, z, s, col[0], col[1], col[2], 0.8F * (1.0F - 0.6F * f));
            }
        }
        Vector3f cw = new Vector3f(c);
        disc(vc, m, cw, right, up, 0.0F, radius * 0.25F, core, 0.6F, 0.0F);
        disc(vc, m, cw, right, up, 0.0F, radius * 0.7F, arm, 0.12F, 0.0F);
    }

    /**
     * Sonne mit Kern, Halo, Kreuzglanz und waagrechtem (anamorphem) Lichtstreifen.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param right Kamera-Rechts-Vektor
     * @param up Kamera-Oben-Vektor
     * @param p Position
     * @param size Groesse des Kerns
     * @param col Farbe des Lichts RGB 0..1
     * @param alpha Deckkraft 0..1
     */
    static void sun(VertexConsumer vc, Matrix4f m, Vector3f right, Vector3f up, Vector3f p, float size, float[] col, float alpha) {
        float[] white = {1, 1, 1};
        disc(vc, m, p, right, up, 0.0F, size, white, alpha, alpha * 0.8F);
        disc(vc, m, p, right, up, size, size * 3.0F, col, alpha * 0.7F, 0.0F);
        disc(vc, m, p, right, up, size * 2.5F, size * 9.0F, col, alpha * 0.18F, 0.0F);
        streak(vc, m, p, right, up, size * 24.0F, size * 0.18F, RenderUtil.lerp(col, new float[]{0.6F, 0.8F, 1.0F}, 0.5F), alpha * 0.55F);
        streak(vc, m, p, up, right, size * 7.0F, size * 0.1F, col, alpha * 0.35F);
    }

    // ------------------------------------------------------------------ Planeten

    /**
     * Die Erde: Ozeane, Kontinente, Wuesten, Polkappen, Tag-Nacht-Grenze mit Stadtlichtern.
     *
     * @param solid deckender Puffer
     * @param m Matrix
     * @param c Mittelpunkt
     * @param r Radius
     * @param rot Drehung um die eigene Achse
     * @param sun Richtung zur Sonne
     */
    static void earth(VertexConsumer solid, Matrix4f m, Vector3f c, float r, float rot, Vector3f sun) {
        Vector3f s = new Vector3f(sun).normalize();
        colorSphere(solid, m, c, r, 24, 40, rot, (x, y, z) -> {
            float n = land(x, y, z);
            int col;
            if (Math.abs(z) > 0.93F + 0.04F * n) col = 0xFFE8F0FF;                      // Eis (Pole liegen seitlich im Bild)
            else if (n > 0.18F) {
                float dry = Mth.sin(x * 6.1F + z * 4.3F) * 0.5F + 0.5F;
                col = Math.abs(z) < 0.38F && dry > 0.6F ? 0xFFB49A64 : lerpColor(0xFF2E7A3A, 0xFF1C5A2A, Mth.clamp(n - 0.18F, 0, 1)); // Wueste/Wald
            } else col = lerpColor(0xFF0A2A6E, 0xFF2A70B4, Mth.clamp((n + 0.1F) / 0.28F, 0, 1)); // Tiefsee -> Kuestenwasser
            // Licht (nur die Beleuchtung dreht nicht mit der Oberflaeche - naeherungsweise genuegt die Oberflaechennormale)
            float light = Mth.clamp(x * s.x + y * s.y + z * s.z, 0.0F, 1.0F);
            int lit = scale(col, 0.06F + 0.94F * light);
            if (light < 0.08F && n > 0.25F && hash((int) (x * 97) * 31 + (int) (y * 89) * 17 + (int) (z * 83)) > 0.55F) {
                lit = lerpColor(lit, 0xFFFFC060, 0.7F);                                    // Stadtlichter
            }
            return lit;
        });
    }

    /**
     * Wolkendecke der Erde (additiv) und blau leuchtender Atmosphaerensaum.
     *
     * @param glow Puffer fuer additives Licht
     * @param m Matrix
     * @param right Kamera-Rechts-Vektor
     * @param up Kamera-Oben-Vektor
     * @param c Mittelpunkt der Erde
     * @param r Radius der Erde
     * @param rot Drehung der Wolken
     * @param sun Richtung zur Sonne
     */
    static void earthAtmosphere(VertexConsumer glow, Matrix4f m, Vector3f right, Vector3f up, Vector3f c, float r, float rot, Vector3f sun) {
        Vector3f s = new Vector3f(sun).normalize();
        colorSphere(glow, m, c, r * 1.012F, 18, 30, rot * 1.3F, (x, y, z) -> {
            float cl = Mth.sin(x * 7.3F + y * 2.1F) * Mth.sin(z * 6.7F - y * 3.3F) + 0.5F * Mth.sin(x * 13.0F + z * 11.0F);
            float light = Mth.clamp(x * s.x + y * s.y + z * s.z + 0.1F, 0.0F, 1.0F);
            int a = (int) (255 * Mth.clamp((cl - 0.55F) * 1.2F, 0.0F, 0.45F) * light);
            return (a << 24) | 0xFFFFFF;
        });
        float[] blue = {0.35F, 0.65F, 1.0F};
        disc(glow, m, c, right, up, r * 0.96F, r * 1.13F, blue, 0.55F, 0.0F);
        disc(glow, m, c, right, up, r * 1.0F, r * 1.35F, blue, 0.12F, 0.0F);
    }

    /**
     * Landmaske: Ueberlagerte Wellen ergeben Kontinente (groesser als Schwelle = Land).
     *
     * @param x Richtung x
     * @param y Richtung y
     * @param z Richtung z
     * @return Hoehenwert (etwa -1..1)
     */
    private static float land(float x, float y, float z) {
        return Mth.sin(2.7F * x + 1.3F) * Mth.cos(2.1F * y + 0.7F) * Mth.sin(3.3F * z + 2.2F)
                + 0.55F * Mth.sin(5.9F * x + 3.1F * y + 2.0F) * Mth.sin(4.7F * z + 0.3F)
                + 0.3F * Mth.sin(11.3F * x - 7.1F * z + 1.1F) * Mth.cos(9.7F * y);
    }

    /**
     * Der Mond: grau mit Kratern und Maria, beleuchtet.
     *
     * @param solid deckender Puffer
     * @param m Matrix
     * @param c Mittelpunkt
     * @param r Radius
     * @param sun Richtung zur Sonne
     */
    static void moon(VertexConsumer solid, Matrix4f m, Vector3f c, float r, Vector3f sun) {
        Vector3f s = new Vector3f(sun).normalize();
        colorSphere(solid, m, c, r, 14, 22, 0.0F, (x, y, z) -> {
            float mare = Mth.sin(x * 3.1F + 1.0F) * Mth.sin(y * 2.7F + z * 1.9F);
            float crater = Mth.sin(x * 17.0F) * Mth.sin(y * 19.0F) * Mth.sin(z * 15.0F);
            int col = lerpColor(0xFFB8B8B4, 0xFF6A6A70, Mth.clamp(mare * 0.8F + 0.3F + crater * 0.2F, 0, 1));
            return scale(col, 0.05F + 0.95F * Mth.clamp(x * s.x + y * s.y + z * s.z, 0, 1));
        });
    }

    /**
     * Gasriese: Wolkenbaender, Turbulenzen und ein grosser Sturm; Schattengrenze zur Sonne.
     *
     * @param solid deckender Puffer
     * @param m Matrix
     * @param c Mittelpunkt
     * @param r Radius
     * @param rot Drehung um die eigene Achse
     * @param sun Richtung zur Sonne
     */
    static void gasGiant(VertexConsumer solid, Matrix4f m, Vector3f c, float r, float rot, Vector3f sun) {
        Vector3f s = new Vector3f(sun).normalize();
        int[] pal = {0xFFE6CCA8, 0xFFB46E4A, 0xFF7A4A8C, 0xFFF0DCC0, 0xFF9A5A3C, 0xFF5A3A7A};
        colorSphere(solid, m, c, r, 28, 44, rot, (x, y, z) -> {
            float lon = (float) Math.atan2(z, x);
            float v = y * 9.0F + 0.55F * Mth.sin(lon * 3.0F + y * 14.0F) + 0.25F * Mth.sin(y * 31.0F + lon);
            float band = (v % 6.0F + 6.0F) % 6.0F;
            int i0 = (int) band, i1 = (i0 + 1) % pal.length;
            int col = lerpColor(pal[i0], pal[i1], band - i0);
            float dx = lon - 1.0F, dy = y - 0.28F;
            float storm = dx * dx * 6.0F + dy * dy * 40.0F;
            if (storm < 1.0F) col = lerpColor(col, 0xFFC0402A, (1.0F - storm) * 0.8F);
            float light = Mth.clamp(x * s.x + y * s.y + z * s.z, 0.0F, 1.0F);
            return scale(col, 0.04F + 0.96F * (float) Math.pow(light, 0.8));
        });
    }

    /**
     * Planetenring (halbtransparent) in der Aequatorebene - getrennt in vordere und hintere Haelfte, damit der Planet
     * dazwischen liegen kann.
     *
     * @param tint halbtransparenter Puffer
     * @param m Matrix
     * @param c Mittelpunkt des Planeten
     * @param r0 Innenradius
     * @param r1 Aussenradius
     * @param tiltX Neigung um x
     * @param tiltZ Neigung um z
     * @param front {@code true}: nur die vordere Haelfte, sonst die hintere
     * @param cam Kameraposition
     */
    static void ring(VertexConsumer tint, Matrix4f m, Vector3f c, float r0, float r1, float tiltX, float tiltZ, boolean front, Vector3f cam) {
        Matrix4f rm = new Matrix4f(m).translate(c.x, c.y, c.z).rotateX(tiltX).rotateZ(tiltZ);
        // Kamerarichtung im Ringraum: welche Haelfte liegt vorn?
        Vector3f toCam = new Vector3f(cam).sub(c);
        Matrix4f inv = new Matrix4f().rotateX(tiltX).rotateZ(tiltZ).invert();
        inv.transformDirection(toCam);
        float camAng = (float) Math.atan2(toCam.z, toCam.x);
        int seg = 96, bands = 7;
        for (int b = 0; b < bands; b++) {
            float ra = Mth.lerp(b / (float) bands, r0, r1), rb = Mth.lerp((b + 1) / (float) bands, r0, r1);
            float dens = 0.25F + 0.5F * (Mth.sin(b * 2.3F) * 0.5F + 0.5F);
            if (b == 3) dens = 0.05F; // Cassini-Teilung
            int col = lerpColor(0xFFD8C2A0, 0xFF8A6A9A, b / (float) bands);
            for (int i = 0; i < seg; i++) {
                float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
                boolean isFront = Mth.cos((a0 + a1) * 0.5F - camAng) > 0;
                if (isFront != front) continue;
                int c2 = ((int) (dens * 255) << 24) | (col & 0xFFFFFF);
                vertex(tint, rm, Mth.cos(a0) * ra, 0, Mth.sin(a0) * ra, c2);
                vertex(tint, rm, Mth.cos(a0) * rb, 0, Mth.sin(a0) * rb, c2);
                vertex(tint, rm, Mth.cos(a1) * rb, 0, Mth.sin(a1) * rb, c2);
                vertex(tint, rm, Mth.cos(a1) * ra, 0, Mth.sin(a1) * ra, c2);
            }
        }
    }

    // ------------------------------------------------------------------ Lichtgeschwindigkeit

    /**
     * Tunnel aus Sternenstreifen: Streifen entstehen im Fluchtpunkt und werden beim Naeherkommen laenger und heller;
     * dazu ein Leuchten am Ende und spiralig mitlaufende Lichtfaeden.
     *
     * @param glow Puffer fuer additives Licht
     * @param m Matrix (kamerazentriert)
     * @param fwd Flugrichtung
     * @param local Zeit seit Beginn des Flugs
     * @param speed Geschwindigkeit 0..1
     * @param alpha Deckkraft 0..1
     */
    static void warp(VertexConsumer glow, Matrix4f m, Vector3f fwd, float local, float speed, float alpha) {
        Vector3f u = new Vector3f(fwd).cross(Math.abs(fwd.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        Vector3f v = new Vector3f(fwd).cross(u).normalize();
        float[] white = {1, 1, 1}, blue = {0.45F, 0.7F, 1.0F}, violet = {0.65F, 0.4F, 1.0F};
        int n = RenderQuality.scale(520, 160);
        for (int i = 0; i < n; i++) {
            float ang = hash(i * 7) * Mth.PI + local * 0.004F;
            float rr = 1.0F + 26.0F * (float) Math.pow(hash(i * 13) * 0.5F + 0.5F, 1.4);
            float d = 80.0F * (1.0F - Mth.frac(hash(i * 17) + local * 0.03F * (0.4F + 1.6F * speed)));
            float len = (1.0F + 30.0F * speed * speed) * (0.4F + rr / 26.0F);
            Vector3f side = new Vector3f(u).mul(Mth.cos(ang) * rr).add(new Vector3f(v).mul(Mth.sin(ang) * rr));
            Vector3f p0 = new Vector3f(fwd).mul(d).add(side), p1 = new Vector3f(fwd).mul(d + len).add(side);
            float[] c = RenderUtil.lerp(white, i % 4 == 0 ? violet : blue, rr / 26.0F);
            float a = alpha * Mth.clamp(1.0F - d / 80.0F, 0.0F, 1.0F) * (0.3F + 0.7F * speed);
            RenderUtil.segment(glow, m, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, 0.03F + 0.05F * (rr / 26.0F), c[0], c[1], c[2], a, 0.0F);
        }
        // Licht am Ende des Tunnels und mitlaufende Spiralfaeden
        Vector3f end = new Vector3f(fwd).mul(85.0F);
        Vector3f right = u, up = v;
        disc(glow, m, end, right, up, 0.0F, 4.0F + 10.0F * speed, white, alpha * (0.3F + 0.6F * speed), 0.0F);
        disc(glow, m, end, right, up, 0.0F, 18.0F + 30.0F * speed, blue, alpha * 0.18F * speed, 0.0F);
        for (int arm = 0; arm < 3; arm++) {
            Vector3f prev = null;
            for (int i = 0; i <= 30; i++) {
                float f = i / 30.0F;
                float d = 80.0F * (1.0F - f);
                float ang = arm * Mth.TWO_PI / 3 + f * 6.0F + local * 0.08F;
                float rr = 6.0F + 14.0F * f;
                Vector3f p = new Vector3f(fwd).mul(d).add(new Vector3f(u).mul(Mth.cos(ang) * rr)).add(new Vector3f(v).mul(Mth.sin(ang) * rr));
                if (prev != null) {
                    RenderUtil.segment(glow, m, prev.x, prev.y, prev.z, p.x, p.y, p.z, 0.12F, violet[0], violet[1], violet[2],
                            alpha * 0.12F * speed * f, alpha * 0.12F * speed * f);
                }
                prev = p;
            }
        }
    }

    // ------------------------------------------------------------------ Farbe

    /**
     * Mischt zwei Farben mit Alpha.
     *
     * @param a erste Farbe 0xAARRGGBB
     * @param b zweite Farbe
     * @param t Anteil der zweiten 0..1
     * @return Mischfarbe
     */
    static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int aa = (a >>> 24), ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24), br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    /**
     * Dunkelt eine Farbe ab (Alpha bleibt).
     *
     * @param c Farbe 0xAARRGGBB
     * @param f Faktor 0..1
     * @return die Farbe
     */
    static int scale(int c, float f) {
        return (c & 0xFF000000) | ((int) (((c >> 16) & 255) * f) << 16) | ((int) (((c >> 8) & 255) * f) << 8) | (int) ((c & 255) * f);
    }
}
