package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.cube;
import static dev.cosmiccore.client.RenderUtil.flatCircle;
import static dev.cosmiccore.client.RenderUtil.hash;
import static dev.cosmiccore.client.RenderUtil.lerp;
import static dev.cosmiccore.client.RenderUtil.quadFade;
import static dev.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.cosmiccore.block.ModBlocks;
import dev.cosmiccore.multiblock.CorePattern;
import dev.cosmiccore.multiblock.ReactorSize;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Die kosmische Kammer: Ist die Struktur gebildet, sind die Modelle von Gehaeuse, Ports, Rahmen und Glas
 * ausgeblendet. An ihre Stelle tritt eine kinetische Kugelpanzerung um das schwarze Loch:
 * <ul>
 *   <li>Sechs Ringe aus gewoelbten Sternenfeld-Platten (drei unten, drei oben), abwechselnd gegenlaeufig rotierend.</li>
 *   <li>Lichtfugen zwischen den Platten - das Licht des Kerns dringt nach aussen, die Innenseiten gluehen.</li>
 *   <li>Eine Iris-Blende im oberen Pol, die sich im Betrieb oeffnet; Rippen tragen Lichtpulse durch den offenen Guertel.</li>
 *   <li>Beim Bilden fliegen die Plattenringe von aussen heran und rasten nacheinander ein.</li>
 *   <li>Instabilitaet: Platten zittern nach aussen, rotes Licht schiesst durch die Fugen, Blitze springen ueber die Huelle.</li>
 * </ul>
 * Ursprung ist die Kernmitte. Der Controller bleibt als normaler Block sichtbar.
 */
public final class ReactorShellRenderer {
    private ReactorShellRenderer() {}

    /** Plattenringe: {Breite unten, Breite oben, Plattenanzahl, Drehsinn}. Grad. */
    private static final float[][] BANDS = {
            {-80, -62, 7, 1}, {-60, -44, 11, -1}, {-42, -28, 14, 1},
            {28, 42, 14, -1}, {44, 60, 11, 1}, {62, 76, 7, -1}};
    private static final float PLATE_FILL = 0.88F;   // Rest = Fuge
    private static final float THICK = 0.14F;
    private static final float IRIS_LAT = 78.0F * Mth.DEG_TO_RAD;

    private static final float[] SEAM = {0.62F, 0.4F, 1.0F};
    private static final float[] SEAM_HOT = {1.0F, 0.72F, 0.4F};
    private static final float[] CRITICAL = {1.0F, 0.12F, 0.12F};
    private static final float[] ENERGY_GLYPH = {0.35F, 0.95F, 1.0F};
    private static final float[] ITEM_GLYPH = {1.0F, 0.6F, 0.2F};

    private record ShellCell(int dx, int dy, int dz, CorePattern.Cell cell) {}

    private static final Map<Integer, List<ShellCell>> CACHE = new HashMap<>();

    private static List<ShellCell> cells(int size) {
        return CACHE.computeIfAbsent(size, s -> {
            List<ShellCell> out = new ArrayList<>();
            ReactorSize rs = ReactorSize.bySize(s);
            if (rs == null) return out;
            rs.pattern().forEach(BlockPos.ZERO, (pos, cell) -> {
                if (cell == CorePattern.Cell.HULL || cell == CorePattern.Cell.SLOT || cell == CorePattern.Cell.FRAME
                        || cell == CorePattern.Cell.GLASS) {
                    out.add(new ShellCell(pos.getX(), pos.getY(), pos.getZ(), cell));
                }
            });
            return out;
        });
    }

    public record Params(Level level, BlockPos corePos, int size, float t, float form, float energy, float heat,
                         boolean active, boolean unstable, float growth) {
        float radius() {
            return size / 2 + 0.5F;
        }

        float sweepY() {
            float r = radius();
            return -r - 1.5F + form * (2 * r + 3.0F);
        }

        boolean done() {
            return form >= 1.0F;
        }

        float spin() {
            return (unstable ? 2.2F : active ? 1.0F : 0.25F) * (1.0F + 0.6F * growth);
        }
    }

    private static float[] sph(float r, float lat, float lon) {
        float c = Mth.cos(lat);
        return new float[]{r * c * Mth.cos(lon), r * Mth.sin(lat), r * c * Mth.sin(lon)};
    }

    /** Einrast-Fortschritt eines Plattenrings waehrend der Verwandlung (0 = weit draussen, 1 = sitzt). */
    private static float assemble(Params p, float bandY) {
        if (p.done()) return 1.0F;
        float k = Mth.clamp((p.sweepY() - bandY) / 1.6F, 0.0F, 1.0F);
        return 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k); // ease-out
    }

    /** Radius und Verdrehung eines Plattenrings inkl. Einflug-Animation und Instabilitaets-Zittern. */
    private static float bandRotation(Params p, int b) {
        float[] band = BANDS[b];
        return p.t() * 0.004F * p.spin() * band[3] * (1.0F + 0.25F * (b % 3));
    }

    // ------------------------------------------------------------------ Pass 1: Sternenfeld-Platten

    public static void solid(PoseStack pose, MultiBufferSource buffers, Params p) {
        float r = p.radius();
        VertexConsumer portal = buffers.getBuffer(RenderType.endPortal());
        Matrix4f m = pose.last().pose();

        for (int b = 0; b < BANDS.length; b++) {
            float[] band = BANDS[b];
            float lat0 = band[0] * Mth.DEG_TO_RAD, lat1 = band[1] * Mth.DEG_TO_RAD;
            float bandY = r * Mth.sin((lat0 + lat1) * 0.5F);
            float in = assemble(p, bandY);
            if (in <= 0.001F) continue;
            float rot = bandRotation(p, b) + (1.0F - in) * 2.5F * band[3];
            int plates = (int) band[2];
            float step = Mth.TWO_PI / plates;
            for (int k = 0; k < plates; k++) {
                float jitter = p.heat() > 0.6F ? (p.heat() - 0.6F) * 0.35F * Math.max(0, hash((int) (p.t() * 0.7F) + k * 31 + b * 7)) : 0.0F;
                float rr = r + (1.0F - in) * 3.0F + jitter;
                float o0 = rot + k * step, o1 = o0 + step * PLATE_FILL;
                plate(portal, m, rr, lat0, lat1, o0, o1);
            }
        }

        // Iris-Blende im oberen Pol: Lamellen schieben sich zusammen (geschlossen) oder ziehen sich zurueck (offen)
        float irisIn = assemble(p, r * Mth.sin(IRIS_LAT));
        if (irisIn > 0.001F) {
            float open = p.active() ? 0.75F + 0.1F * Mth.sin(p.t() * 0.05F) : 0.15F;
            float rimR = r * Mth.cos(IRIS_LAT), y = r * Mth.sin(IRIS_LAT) + (1.0F - irisIn) * 3.0F;
            int blades = 8;
            float rot = p.t() * 0.01F * p.spin();
            for (int k = 0; k < blades; k++) {
                float a0 = rot + k * Mth.TWO_PI / blades, a1 = a0 + Mth.TWO_PI / blades * 1.35F;
                float inner = rimR * open;
                float[] q0 = {Mth.cos(a0) * rimR, y, Mth.sin(a0) * rimR};
                float[] q1 = {Mth.cos(a1) * rimR, y, Mth.sin(a1) * rimR};
                float tip = a0 + 0.9F;
                float[] q2 = {Mth.cos(tip) * inner, y + 0.02F * k, Mth.sin(tip) * inner};
                starQuad(portal, m, q0, q1, q2, q2);
            }
        }

        // Controller wird zum schwebenden Interface-Knoten
        if (p.done()) {
            for (ShellCell c : cells(p.size())) {
                if (c.cell() != CorePattern.Cell.SLOT) continue;
                if (!p.level().getBlockState(p.corePos().offset(c.dx(), c.dy(), c.dz())).is(ModBlocks.CONTROLLER.get())) continue;
                pose.pushPose();
                pose.translate(c.dx(), c.dy() + 0.05F * Mth.sin(p.t() * 0.08F), c.dz());
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(p.t() * 1.2F));
                RenderUtil.starOctahedron(buffers.getBuffer(RenderType.endGateway()), pose.last().pose(), 0.26F, 0.36F);
                pose.popPose();
            }
        }

        // waehrend der Verwandlung: Bloecke oberhalb der Scan-Scheibe zeigen noch ihr altes Aussehen
        if (!p.done()) {
            var dispatcher = Minecraft.getInstance().getBlockRenderer();
            for (ShellCell c : cells(p.size())) {
                if (c.dy() + 0.5F < p.sweepY()) continue;
                BlockState s = p.level().getBlockState(p.corePos().offset(c.dx(), c.dy(), c.dz()));
                if (s.is(ModBlocks.CONTROLLER.get())) continue;
                pose.pushPose();
                pose.translate(c.dx() - 0.5F, c.dy() - 0.5F, c.dz() - 0.5F);
                dispatcher.renderSingleBlock(s.getBlock().defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                pose.popPose();
            }
        }
    }

    /** Eine gewoelbte Platte mit Dicke (Aussen-, Innenflaeche und Kanten). */
    private static void plate(VertexConsumer vc, Matrix4f m, float r, float lat0, float lat1, float o0, float o1) {
        float ri = r - THICK;
        int sub = 3;
        for (int i = 0; i < sub; i++) {
            float la = Mth.lerp(i / (float) sub, lat0, lat1), lb = Mth.lerp((i + 1) / (float) sub, lat0, lat1);
            for (int j = 0; j < sub; j++) {
                float oa = Mth.lerp(j / (float) sub, o0, o1), ob = Mth.lerp((j + 1) / (float) sub, o0, o1);
                starQuad(vc, m, sph(r, la, oa), sph(r, la, ob), sph(r, lb, ob), sph(r, lb, oa));
            }
        }
        // Kanten (Dicke)
        starQuad(vc, m, sph(r, lat0, o0), sph(ri, lat0, o0), sph(ri, lat1, o0), sph(r, lat1, o0));
        starQuad(vc, m, sph(r, lat0, o1), sph(ri, lat0, o1), sph(ri, lat1, o1), sph(r, lat1, o1));
        starQuad(vc, m, sph(r, lat0, o0), sph(ri, lat0, o0), sph(ri, lat0, o1), sph(r, lat0, o1));
        starQuad(vc, m, sph(r, lat1, o0), sph(ri, lat1, o0), sph(ri, lat1, o1), sph(r, lat1, o1));
    }

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

    // ------------------------------------------------------------------ Pass 2: Licht

    public static void glow(Matrix4f m, VertexConsumer vc, Params p) {
        float r = p.radius();
        float[] seam = lerp(lerp(SEAM, SEAM_HOT, p.active() ? 0.45F : 0.0F), CRITICAL, p.heat() * p.heat());
        float base = p.energy();
        float pulse = 0.65F + 0.35F * Mth.sin(p.t() * 0.11F * p.spin());

        for (int b = 0; b < BANDS.length; b++) {
            float[] band = BANDS[b];
            float lat0 = band[0] * Mth.DEG_TO_RAD, lat1 = band[1] * Mth.DEG_TO_RAD;
            float bandY = r * Mth.sin((lat0 + lat1) * 0.5F);
            float in = assemble(p, bandY);
            if (in <= 0.001F) continue;
            float rot = bandRotation(p, b) + (1.0F - in) * 2.5F * band[3];
            int plates = (int) band[2];
            float step = Mth.TWO_PI / plates;
            float rr = r + (1.0F - in) * 3.0F;
            float snap = in < 1.0F ? 1.0F + 2.0F * (1.0F - in) : 1.0F; // beim Einrasten gleissen die Fugen auf
            for (int k = 0; k < plates; k++) {
                float o0 = rot + k * step, o1 = o0 + step * PLATE_FILL, oGap = o0 + step;
                float flick = p.unstable() ? 0.5F + 0.5F * (0.5F + 0.5F * hash((int) (p.t() * 0.6F) + k * 13 + b * 101)) : 1.0F;
                float a = Math.min(1.0F, base * pulse * flick * snap);
                // Fuge: Licht dringt von innen durch (vom Innen- zum Aussenradius ausblendend)
                for (int i = 0; i < 3; i++) {
                    float la = Mth.lerp(i / 3.0F, lat0, lat1), lb = Mth.lerp((i + 1) / 3.0F, lat0, lat1);
                    float[] a0 = sph(rr - THICK, la, o1), a1 = sph(rr - THICK, la, oGap), b0 = sph(rr + 0.05F, lb, o1), b1 = sph(rr + 0.05F, lb, oGap);
                    quadFade(vc, m, a0[0], a0[1], a0[2], a1[0], a1[1], a1[2], b1[0], b1[1], b1[2], b0[0], b0[1], b0[2],
                            seam[0], seam[1], seam[2], a * 0.9F, a * 0.5F);
                }
                // feine Leuchtkanten oben/unten an jeder Platte
                float[] e0 = sph(rr + 0.01F, lat0, o0), e1 = sph(rr + 0.01F, lat0, o1);
                float[] f0 = sph(rr + 0.01F, lat1, o0), f1 = sph(rr + 0.01F, lat1, o1);
                segment(vc, m, e0[0], e0[1], e0[2], e1[0], e1[1], e1[2], 0.012F, seam[0], seam[1], seam[2], a * 0.6F, a * 0.6F);
                segment(vc, m, f0[0], f0[1], f0[2], f1[0], f1[1], f1[2], 0.012F, seam[0], seam[1], seam[2], a * 0.6F, a * 0.6F);
                // Innenseite wird vom Kern angestrahlt
                float[] i0 = sph(rr - THICK - 0.01F, lat0, o0), i1 = sph(rr - THICK - 0.01F, lat0, o1);
                float[] j0 = sph(rr - THICK - 0.01F, lat1, o0), j1 = sph(rr - THICK - 0.01F, lat1, o1);
                float lit = base * 0.22F * (lat0 < 0 ? 1.0F : 0.8F);
                quadFade(vc, m, i0[0], i0[1], i0[2], i1[0], i1[1], i1[2], j1[0], j1[1], j1[2], j0[0], j0[1], j0[2],
                        SEAM_HOT[0], SEAM_HOT[1], SEAM_HOT[2], lat0 < 0 ? lit * 0.4F : lit, lat0 < 0 ? lit : lit * 0.4F);
            }
        }

        // Lichtringe an den Kanten des offenen Guertels
        if (p.done()) {
            for (float lat : new float[]{-28 * Mth.DEG_TO_RAD, 28 * Mth.DEG_TO_RAD}) {
                float y = r * Mth.sin(lat) * 0.98F, rr = r * Mth.cos(lat) * 0.98F;
                Matrix4f mm = new Matrix4f(m).translate(0, y, 0);
                flatCircle(vc, mm, rr, 0.035F, 72, seam, base * pulse);
            }
            // Rippen durch den Guertel mit wandernden Lichtpulsen
            int ribs = p.size() >= 9 ? 8 : 4;
            for (int k = 0; k < ribs; k++) {
                float lon = Mth.PI / 4 + k * Mth.TWO_PI / ribs;
                float[] prev = null;
                for (int i = 0; i <= 10; i++) {
                    float[] q = sph(r * 0.98F, Mth.lerp(i / 10.0F, -28 * Mth.DEG_TO_RAD, 28 * Mth.DEG_TO_RAD), lon);
                    if (prev != null) segment(vc, m, prev[0], prev[1], prev[2], q[0], q[1], q[2], 0.03F, seam[0], seam[1], seam[2], base * 0.8F, base * 0.8F);
                    prev = q;
                }
                float s = Mth.frac(p.t() * 0.02F * p.spin() + k * 0.37F);
                float[] q = sph(r * 0.98F, Mth.lerp(s, -28 * Mth.DEG_TO_RAD, 28 * Mth.DEG_TO_RAD), lon);
                cube(vc, m, q[0], q[1], q[2], 0.07F, 1.0F, 1.0F, 1.0F, base * Mth.sin(s * Mth.PI));
            }
            // Iris-Rand
            float rimR = r * Mth.cos(IRIS_LAT);
            Matrix4f top = new Matrix4f(m).translate(0, r * Mth.sin(IRIS_LAT) + 0.03F, 0);
            flatCircle(vc, top, rimR, 0.04F, 48, seam, base);
            // Instabilitaet: Blitze springen ueber die Huelle
            if (p.heat() > 0.55F) arcs(m, vc, p, r);
            glyphs(m, vc, p, r);
        } else {
            scanDisc(m, vc, p);
        }
    }

    private static void arcs(Matrix4f m, VertexConsumer vc, Params p, float r) {
        int count = 1 + Math.round((p.heat() - 0.55F) * 10);
        int seed = (int) (p.t() * 0.5F);
        for (int k = 0; k < count; k++) {
            float lat = hash(seed * 13 + k) * 1.2F, lon = hash(seed * 29 + k) * Mth.PI;
            float[] prev = sph(r + 0.08F, lat, lon);
            for (int i = 1; i <= 6; i++) {
                float[] q = sph(r + 0.08F + 0.1F * Math.abs(hash(seed + k * 7 + i)), lat + hash(seed * 3 + k + i) * 0.25F, lon + i * 0.12F);
                segment(vc, m, prev[0], prev[1], prev[2], q[0], q[1], q[2], 0.02F, 1.0F, 0.9F, 0.9F, 0.9F, 0.9F);
                prev = q;
            }
        }
    }

    /** Symbole der Ports und ein Ring um den Controller - als Hologramm auf der Kugeloberflaeche. */
    private static void glyphs(Matrix4f m, VertexConsumer vc, Params p, float r) {
        for (ShellCell c : cells(p.size())) {
            if (c.cell() != CorePattern.Cell.HULL && c.cell() != CorePattern.Cell.SLOT) continue;
            BlockState s = p.level().getBlockState(p.corePos().offset(c.dx(), c.dy(), c.dz()));
            boolean energy = s.is(ModBlocks.ENERGY_PORT.get()), item = s.is(ModBlocks.ITEM_PORT.get());
            boolean controller = s.is(ModBlocks.CONTROLLER.get());
            if (!energy && !item && !controller) continue;
            Vector3f n = new Vector3f(c.dx(), c.dy(), c.dz()).normalize();
            float lat = Mth.clamp((float) Math.asin(n.y), -70 * Mth.DEG_TO_RAD, -35 * Mth.DEG_TO_RAD);
            float lon = (float) Math.atan2(n.z, n.x);
            float[] cpos = sph(r + 0.06F, lat, lon);
            Vector3f normal = new Vector3f(cpos[0], cpos[1], cpos[2]).normalize();
            Vector3f east = new Vector3f(-Mth.sin(lon), 0, Mth.cos(lon));
            Vector3f north = new Vector3f(normal).cross(east).normalize().negate();
            float pulse = 0.6F + 0.4F * Mth.sin(p.t() * 0.12F + c.dx() + c.dz());
            // Lichtbruecke vom Block (Knoten bzw. Port) zur Kugel - mit Pulsen, die zur Kammer laufen
            float[] bridgeCol = controller ? new float[]{0.8F, 0.6F, 1.0F} : energy ? ENERGY_GLYPH : ITEM_GLYPH;
            segment(vc, m, c.dx(), c.dy(), c.dz(), cpos[0], cpos[1], cpos[2], 0.05F, bridgeCol[0], bridgeCol[1], bridgeCol[2],
                    p.energy() * 0.8F, p.energy() * 0.4F);
            segment(vc, m, c.dx(), c.dy(), c.dz(), cpos[0], cpos[1], cpos[2], 0.015F, 1.0F, 1.0F, 1.0F, p.energy(), p.energy() * 0.6F);
            float bs = Mth.frac(p.t() * 0.04F + c.dx() * 0.3F);
            cube(vc, m, Mth.lerp(bs, c.dx(), cpos[0]), Mth.lerp(bs, c.dy(), cpos[1]), Mth.lerp(bs, c.dz(), cpos[2]),
                    0.05F, 1.0F, 1.0F, 1.0F, p.energy() * Mth.sin(bs * Mth.PI));
            if (controller) {
                circle(m, vc, cpos, east, north, 0.32F, new float[]{0.8F, 0.6F, 1.0F}, p.energy() * pulse);
                // Kranz um den schwebenden Knoten
                Matrix4f nm = new Matrix4f(m).translate(c.dx(), c.dy(), c.dz()).rotateY(p.t() * 0.03F);
                flatCircle(vc, nm, 0.42F, 0.02F, 32, bridgeCol, p.energy() * pulse);
                cube(vc, m, c.dx(), c.dy(), c.dz(), 0.08F + 0.03F * pulse, 1.0F, 1.0F, 1.0F, p.energy());
            } else if (energy) {
                float[] g = ENERGY_GLYPH;
                gl(m, vc, cpos, east, north, 0.08F, 0.3F, -0.1F, 0.0F, g, p.energy());
                gl(m, vc, cpos, east, north, -0.1F, 0.0F, 0.1F, 0.0F, g, p.energy());
                gl(m, vc, cpos, east, north, 0.1F, 0.0F, -0.08F, -0.3F, g, p.energy());
                circle(m, vc, cpos, east, north, 0.4F, g, p.energy() * pulse * 0.6F);
            } else {
                float[] g = ITEM_GLYPH;
                for (float y : new float[]{0.12F, -0.08F}) {
                    gl(m, vc, cpos, east, north, -0.2F, y + 0.1F, 0.0F, y - 0.1F, g, p.energy());
                    gl(m, vc, cpos, east, north, 0.0F, y - 0.1F, 0.2F, y + 0.1F, g, p.energy());
                }
                circle(m, vc, cpos, east, north, 0.4F, g, p.energy() * pulse * 0.6F);
            }
        }
    }

    private static void gl(Matrix4f m, VertexConsumer vc, float[] c, Vector3f u, Vector3f v, float u0, float v0, float u1, float v1,
                           float[] col, float a) {
        segment(vc, m, c[0] + u.x * u0 + v.x * v0, c[1] + u.y * u0 + v.y * v0, c[2] + u.z * u0 + v.z * v0,
                c[0] + u.x * u1 + v.x * v1, c[1] + u.y * u1 + v.y * v1, c[2] + u.z * u1 + v.z * v1, 0.025F, col[0], col[1], col[2], a, a);
    }

    private static void circle(Matrix4f m, VertexConsumer vc, float[] c, Vector3f u, Vector3f v, float rad, float[] col, float a) {
        int n = 20;
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            gl(m, vc, c, u, v, Mth.cos(a0) * rad, Mth.sin(a0) * rad, Mth.cos(a1) * rad, Mth.sin(a1) * rad, col, a);
        }
    }

    /** Die Scan-Scheibe der Verwandlung: Kugelquerschnitt mit konzentrischen Ringen. */
    private static void scanDisc(Matrix4f m, VertexConsumer vc, Params p) {
        float r = p.radius();
        float y = Mth.clamp(p.sweepY(), -r, r);
        float cr = Mth.sqrt(Math.max(0.0F, r * r - y * y));
        float fade = Mth.sin(p.form() * Mth.PI);
        float[] white = {0.9F, 0.95F, 1.0F};
        float[] violet = {0.6F, 0.4F, 1.0F};
        Matrix4f mm = new Matrix4f(m).translate(0, y, 0);
        flatCircle(vc, mm, Math.max(0.05F, cr), 0.06F, 64, white, fade);
        for (int i = 1; i <= 4; i++) {
            float rr = cr * (1.0F - i * 0.22F);
            if (rr > 0.05F) flatCircle(vc, mm, rr, 0.015F, 48, violet, fade * 0.6F);
        }
        for (int k = 0; k < 12; k++) {
            float ang = Mth.TWO_PI * k / 12 + p.t() * 0.05F;
            RenderUtil.flatLine(vc, mm, 0, 0, Mth.cos(ang) * cr, Mth.sin(ang) * cr, 0.01F, violet, fade * 0.5F);
        }
    }
}
