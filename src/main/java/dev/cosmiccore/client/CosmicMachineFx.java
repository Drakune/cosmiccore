package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.cube;
import static dev.cosmiccore.client.RenderUtil.flatCircle;
import static dev.cosmiccore.client.RenderUtil.flatLine;
import static dev.cosmiccore.client.RenderUtil.hash;
import static dev.cosmiccore.client.RenderUtil.orbitPoint;
import static dev.cosmiccore.client.RenderUtil.ring;
import static dev.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Verwandelt die ganze Struktur in ein kosmisches Geraet. Gezeichnet vom Kern-Renderer, Ursprung = Kernmitte.
 * <ul>
 *   <li>Runenrahmen werden zu Sternenfeld-Saeulen mit aufsteigenden Leuchtrunen und Boegen zur Decke.</li>
 *   <li>Eine riesige Armillarsphaere (drei Gyroskop-Ringe mit Markierungen und wandernden "Planeten")
 *       dreht sich um den ganzen Reaktor.</li>
 *   <li>Am Boden rotiert ein Runenkreis (Heptagramm), unter der Decke ein Sternbild.</li>
 *   <li>Asteroiden aus Sternenfeld kreisen um die Maschine.</li>
 * </ul>
 * Aufteilung in {@link #starfield} und {@link #glow}, weil ein Wechsel des RenderTypes den vorherigen Puffer schliesst.
 */
public final class CosmicMachineFx {
    private CosmicMachineFx() {}

    public record Params(int half, float t, float speed, float intensity, float flicker, float growth,
                         boolean active, float[] c1, float[] c2, float[] c3) {}

    // ------------------------------------------------------------------ Sternenfeld-Teile (eigene RenderTypes)

    public static void starfield(PoseStack pose, MultiBufferSource buffers, Params p) {
        int h = p.half();
        // Saeulen an den vier Ecken (ersetzen die ausgeblendeten Runenrahmen)
        VertexConsumer portal = buffers.getBuffer(RenderType.endPortal());
        Matrix4f m = pose.last().pose();
        float w = 0.3F;
        float y0 = -h + 0.5F, y1 = h - 0.5F;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float cx = sx * h, cz = sz * h;
                RenderUtil.starBox(portal, m, cx - w, y0, cz - w, cx + w, y1, cz + w);
            }
        }

        // Asteroiden aus Sternenfeld
        VertexConsumer gateway = buffers.getBuffer(RenderType.endGateway());
        int rocks = 5 + h;
        float orbit = h * 1.5F + 1.6F;
        for (int i = 0; i < rocks; i++) {
            float[] q = orbitPoint(orbit + 0.6F * hash(i * 7), p.t() * (0.004F + 0.002F * (i % 3)) * p.speed() + i * 2.1F,
                    (i * 41 % 50) - 25.0F, i * 63.0F);
            float s = 0.12F + 0.08F * (0.5F + 0.5F * hash(i * 13));
            pose.pushPose();
            pose.translate(q[0], q[1], q[2]);
            pose.mulPose(Axis.YP.rotationDegrees(p.t() * (1.0F + i % 3)));
            pose.mulPose(Axis.XP.rotationDegrees(p.t() * 0.7F + i * 40));
            RenderUtil.starBox(gateway, pose.last().pose(), -s, -s, -s, s, s, s);
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------ Leuchtende Teile (RenderType.lightning)

    public static void glow(PoseStack pose, VertexConsumer glow, Params p) {
        pillars(pose, glow, p);
        armillary(pose, glow, p);
        floorCircle(pose, glow, p);
        constellation(pose, glow, p);
        asteroidGlow(pose, glow, p);
    }

    /** Leuchtkanten der Saeulen, aufsteigende Runen und Boegen von den Saeulenkoepfen zur Deckenmitte. */
    private static void pillars(PoseStack pose, VertexConsumer glow, Params p) {
        int h = p.half();
        Matrix4f m = pose.last().pose();
        float y0 = -h + 0.5F, y1 = h - 0.5F, height = y1 - y0;
        float w = 0.31F;
        float a = p.intensity() * p.flicker();
        int corner = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float cx = sx * h, cz = sz * h;
                // vertikale Kanten
                for (int ex = -1; ex <= 1; ex += 2) {
                    for (int ez = -1; ez <= 1; ez += 2) {
                        segment(glow, m, cx + ex * w, y0, cz + ez * w, cx + ex * w, y1, cz + ez * w, 0.012F,
                                p.c2()[0], p.c2()[1], p.c2()[2], a * 0.8F, a * 0.8F);
                    }
                }
                // aufsteigende Runen: Ringe, die an der Saeule hochwandern
                int runes = p.active() ? 4 : 2;
                for (int k = 0; k < runes; k++) {
                    float s = Mth.frac(p.t() * 0.01F * p.speed() + k / (float) runes + corner * 0.13F);
                    float y = y0 + s * height;
                    float fade = Mth.sin(s * Mth.PI);
                    pose.pushPose();
                    pose.translate(cx, y, cz);
                    pose.mulPose(Axis.YP.rotationDegrees(p.t() * 3.0F + k * 45.0F));
                    Matrix4f rm = pose.last().pose();
                    float r = 0.42F;
                    // quadratische Rune mit Kreuz
                    flatLine(glow, rm, -r, -r, r, -r, 0.02F, p.c1(), a * fade);
                    flatLine(glow, rm, r, -r, r, r, 0.02F, p.c1(), a * fade);
                    flatLine(glow, rm, r, r, -r, r, 0.02F, p.c1(), a * fade);
                    flatLine(glow, rm, -r, r, -r, -r, 0.02F, p.c1(), a * fade);
                    flatLine(glow, rm, -r * 0.6F, 0, r * 0.6F, 0, 0.015F, p.c3(), a * fade);
                    flatLine(glow, rm, 0, -r * 0.6F, 0, r * 0.6F, 0.015F, p.c3(), a * fade);
                    pose.popPose();
                }
                // Bogen vom Saeulenkopf zur Deckenmitte (nur im Betrieb)
                if (p.active()) {
                    int seed = (int) (p.t() * 0.4F) + corner * 101;
                    float px = cx, py = y1, pz = cz;
                    int steps = 7;
                    for (int i = 1; i <= steps; i++) {
                        float f = i / (float) steps;
                        float nx = cx * (1 - f), nz = cz * (1 - f);
                        float ny = y1 + Mth.sin(f * Mth.PI) * 0.6F - f * 0.3F;
                        if (i < steps) {
                            nx += hash(seed * 17 + i) * 0.15F;
                            ny += hash(seed * 17 + i + 5) * 0.15F;
                            nz += hash(seed * 17 + i + 9) * 0.15F;
                        }
                        segment(glow, m, px, py, pz, nx, ny, nz, 0.018F, p.c1()[0], p.c1()[1], p.c1()[2], a * 0.8F, a * 0.8F);
                        px = nx;
                        py = ny;
                        pz = nz;
                    }
                }
                corner++;
            }
        }
    }

    /** Drei riesige Gyroskop-Ringe um die gesamte Struktur. */
    private static void armillary(PoseStack pose, VertexConsumer glow, Params p) {
        int h = p.half();
        float r = h * 1.5F + 0.8F;
        float t = p.t(), sp = p.speed() * 0.35F;
        float a = p.intensity() * p.flicker();
        float[][] cols = {p.c1(), p.c2(), p.c3()};
        float[] tiltX = {90.0F, 60.0F, 20.0F};
        float[] tiltZ = {0.0F, 70.0F, -50.0F};
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            // jeder Ring praezediert langsam um die Hochachse
            pose.mulPose(Axis.YP.rotationDegrees(t * (0.2F + 0.15F * i) * sp * (i % 2 == 0 ? 1 : -1)));
            pose.mulPose(Axis.ZP.rotationDegrees(tiltZ[i]));
            pose.mulPose(Axis.XP.rotationDegrees(tiltX[i]));
            float spin = t * (0.6F + 0.3F * i) * sp * (i == 1 ? -1 : 1);
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            Matrix4f m = pose.last().pose();
            // Doppelring
            flatCircle(glow, m, r, 0.035F, 96, cols[i], a * 0.7F);
            flatCircle(glow, m, r - 0.18F, 0.015F, 96, cols[i], a * 0.45F);
            // Skalenstriche
            int ticks = 36;
            for (int k = 0; k < ticks; k++) {
                float ang = k * Mth.TWO_PI / ticks;
                float len = k % 3 == 0 ? 0.28F : 0.14F;
                flatLine(glow, m, Mth.cos(ang) * (r - 0.18F), Mth.sin(ang) * (r - 0.18F),
                        Mth.cos(ang) * (r - 0.18F + len), Mth.sin(ang) * (r - 0.18F + len), 0.012F, cols[i], a * 0.6F);
            }
            // wandernde "Planeten" mit Schweif
            for (int k = 0; k < 2; k++) {
                float ang = t * 0.03F * (1 + i) * p.speed() + k * Mth.PI + i;
                float x = Mth.cos(ang) * r, z = Mth.sin(ang) * r;
                cube(glow, m, x, 0, z, 0.12F + 0.04F * Mth.sin(t * 0.2F + k), 1.0F, 1.0F, 1.0F, a);
                for (int s = 1; s <= 6; s++) {
                    float ang2 = ang - s * 0.04F;
                    cube(glow, m, Mth.cos(ang2) * r, 0, Mth.sin(ang2) * r, 0.08F * (1 - s / 7.0F),
                            cols[i][0], cols[i][1], cols[i][2], a * (1 - s / 7.0F));
                }
            }
            pose.popPose();
        }
    }

    /** Rotierender Runenkreis mit Heptagramm auf dem Boden der Kammer. */
    private static void floorCircle(PoseStack pose, VertexConsumer glow, Params p) {
        int h = p.half();
        float a = p.intensity() * p.flicker();
        float r = h - 0.6F;
        pose.pushPose();
        pose.translate(0.0, -h + 0.52, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(p.t() * 0.4F * p.speed()));
        Matrix4f m = pose.last().pose();
        flatCircle(glow, m, r, 0.03F, 64, p.c2(), a * 0.8F);
        flatCircle(glow, m, r * 0.82F, 0.015F, 64, p.c1(), a * 0.6F);
        flatCircle(glow, m, r * 0.3F, 0.02F, 40, p.c3(), a * 0.7F);
        // Heptagramm {7/3}
        int n = 7;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = ((i + 3) % n) * Mth.TWO_PI / n;
            flatLine(glow, m, Mth.cos(a0) * r * 0.82F, Mth.sin(a0) * r * 0.82F,
                    Mth.cos(a1) * r * 0.82F, Mth.sin(a1) * r * 0.82F, 0.018F, p.c3(), a * 0.65F);
        }
        // Runenzeichen zwischen den Kreisen, gegenlaeufig
        pose.mulPose(Axis.YP.rotationDegrees(-p.t() * 1.0F * p.speed()));
        Matrix4f rm = pose.last().pose();
        int glyphs = 12;
        for (int i = 0; i < glyphs; i++) {
            float ang = i * Mth.TWO_PI / glyphs;
            float rr = r * 0.91F;
            float gx = Mth.cos(ang) * rr, gz = Mth.sin(ang) * rr;
            float tx = -Mth.sin(ang) * 0.12F, tz = Mth.cos(ang) * 0.12F;
            float pulse = 0.5F + 0.5F * Mth.sin(p.t() * 0.15F + i);
            flatLine(glow, rm, gx - tx, gz - tz, gx + tx, gz + tz, 0.012F, p.c1(), a * pulse);
            flatLine(glow, rm, gx, gz, gx + Mth.cos(ang) * 0.1F, gz + Mth.sin(ang) * 0.1F, 0.012F, p.c1(), a * pulse);
        }
        pose.popPose();
    }

    /** Sternbild unter der Decke: Sterne, die sich mit Linien verbinden, wenn der Reaktor laeuft. */
    private static void constellation(PoseStack pose, VertexConsumer glow, Params p) {
        int h = p.half();
        float a = p.intensity() * p.flicker();
        pose.pushPose();
        pose.translate(0.0, h - 0.53, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(-p.t() * 0.15F * p.speed()));
        Matrix4f m = pose.last().pose();
        int stars = 9;
        float[][] pts = new float[stars][];
        float spread = h - 0.8F;
        for (int i = 0; i < stars; i++) {
            pts[i] = new float[]{hash(i * 31 + 3) * spread, hash(i * 17 + 11) * spread};
            float tw = 0.5F + 0.5F * Mth.sin(p.t() * 0.2F + i * 1.7F);
            cube(glow, m, pts[i][0], 0, pts[i][1], 0.04F + 0.03F * tw, 1.0F, 1.0F, 1.0F, a * (0.5F + 0.5F * tw));
        }
        // Verbindungslinien bauen sich mit der Leistung nach und nach auf
        float connect = p.active() ? 0.4F + 0.6F * p.growth() : 0.2F;
        int lines = Math.round((stars - 1) * connect);
        for (int i = 0; i < lines; i++) {
            flatLine(glow, m, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], 0.01F, p.c2(), a * 0.6F);
        }
        pose.popPose();
    }

    /** Leuchtende Schweife hinter den Asteroiden. */
    private static void asteroidGlow(PoseStack pose, VertexConsumer glow, Params p) {
        int h = p.half();
        Matrix4f m = pose.last().pose();
        int rocks = 5 + h;
        float orbit = h * 1.5F + 1.6F;
        float a = p.intensity() * p.flicker();
        for (int i = 0; i < rocks; i++) {
            float r = orbit + 0.6F * hash(i * 7);
            float w0 = (0.004F + 0.002F * (i % 3)) * p.speed();
            float ang = p.t() * w0 + i * 2.1F;
            float inc = (i * 41 % 50) - 25.0F, node = i * 63.0F;
            float[] q0 = orbitPoint(r, ang, inc, node);
            float[] q1 = orbitPoint(r, ang - 0.12F, inc, node);
            segment(glow, m, q0[0], q0[1], q0[2], q1[0], q1[1], q1[2], 0.05F, p.c2()[0], p.c2()[1], p.c2()[2], a * 0.5F, 0.0F);
        }
    }
}
