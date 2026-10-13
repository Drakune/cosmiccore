package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.cube;
import static dev.cosmiccore.client.RenderUtil.flatCircle;
import static dev.cosmiccore.client.RenderUtil.hash;
import static dev.cosmiccore.client.RenderUtil.lerp;
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
import dev.cosmiccore.block.entity.SingularityCoreBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
 * Die kosmische Kammer: Ist die Struktur gebildet, sind die Modelle von Gehaeuse, Rahmen und Glas ausgeblendet.
 * Statt einer festen Huelle haelt ein Netz aus leuchtenden Partikeln das Feld um das schwarze Loch zusammen:
 * <ul>
 *   <li>Die Partikel liegen gleichmaessig auf einer Kugel und sind ueber leuchtende Fesseln mit ihren Nachbarn
 *       verbunden - das Netz dreht sich langsam, Kompressionswellen laufen von Pol zu Pol.</li>
 *   <li>Immer wieder draengt ein Partikel nach aussen und wird von einer Fessel zurueckgezogen; feine Zugspuren
 *       zeigen, wie das Feld nach innen zieht.</li>
 *   <li>Fehlt Stabilisator oder steigt die Instabilitaet, reissen Fesseln, Partikel brechen weiter aus, das Netz glueht rot.</li>
 *   <li>Beim Bilden dehnt sich das Netz aus dem Kern aus (nachdem die Bloecke eingesaugt wurden).</li>
 * </ul>
 * Ursprung ist die Kernmitte. Ports bleiben als echte Bloecke stehen, der Controller wird zur Steuerkonsole.
 */
public final class ReactorShellRenderer {
    private ReactorShellRenderer() {}

    private static final float[] SEAM = {0.62F, 0.4F, 1.0F};
    private static final float[] SEAM_HOT = {1.0F, 0.72F, 0.4F};
    private static final float[] CRITICAL = {1.0F, 0.12F, 0.12F};
    private static final float[][] OVERDRIVE_SEAM = {{0.45F, 0.85F, 1.0F}, {1.0F, 0.78F, 0.25F}, {1.0F, 0.9F, 1.0F}};
    private static final float[] ENERGY_GLYPH = {0.35F, 0.95F, 1.0F};
    private static final float[] ITEM_GLYPH = {1.0F, 0.6F, 0.2F};

    private record ShellCell(int dx, int dy, int dz, CorePattern.Cell cell) {}

    /** Haltenetz: Partikel (Einheitsvektoren auf der Kugel) und ihre Fesseln (Paare naechster Nachbarn). */
    private record Lattice(Vector3f[] points, int[][] links) {}

    private static final Map<Integer, Lattice> LATTICE = new HashMap<>();

    private static Lattice lattice(int size) {
        return LATTICE.computeIfAbsent(size, s -> {
            int n = 40 + s * 15; // 5x5x5: 115, 7x7x7: 145, 9x9x9: 175
            Vector3f[] pts = new Vector3f[n];
            float golden = Mth.PI * (3.0F - Mth.sqrt(5.0F));
            for (int i = 0; i < n; i++) {
                float y = 1.0F - 2.0F * (i + 0.5F) / n;
                float r = Mth.sqrt(1.0F - y * y);
                pts[i] = new Vector3f(Mth.cos(golden * i) * r, y, Mth.sin(golden * i) * r);
            }
            List<int[]> links = new ArrayList<>();
            java.util.Set<Long> seen = new java.util.HashSet<>();
            for (int i = 0; i < n; i++) {
                // drei naechste Nachbarn
                int[] best = {-1, -1, -1};
                float[] bd = {9, 9, 9};
                for (int j = 0; j < n; j++) {
                    if (j == i) continue;
                    float d = pts[i].distanceSquared(pts[j]);
                    for (int k = 0; k < 3; k++) {
                        if (d < bd[k]) {
                            for (int q = 2; q > k; q--) { bd[q] = bd[q - 1]; best[q] = best[q - 1]; }
                            bd[k] = d;
                            best[k] = j;
                            break;
                        }
                    }
                }
                for (int j : best) {
                    long key = Math.min(i, j) * 100000L + Math.max(i, j);
                    if (j >= 0 && seen.add(key)) links.add(new int[]{i, j});
                }
            }
            return new Lattice(pts, links.toArray(new int[0][]));
        });
    }

    private static final Map<Integer, List<ShellCell>> CACHE = new HashMap<>();

    private static List<ShellCell> cells(int size) {
        return CACHE.computeIfAbsent(size, s -> {
            List<ShellCell> out = new ArrayList<>();
            ReactorSize rs = ReactorSize.bySize(s);
            if (rs == null) return out;
            rs.pattern().forEach(BlockPos.ZERO, (pos, cell) -> {
                if (cell == CorePattern.Cell.HULL || cell == CorePattern.Cell.SLOT || cell == CorePattern.Cell.FRAME
                        || cell == CorePattern.Cell.GLASS || cell == CorePattern.Cell.COIL || cell == CorePattern.Cell.CRYSTAL) {
                    out.add(new ShellCell(pos.getX(), pos.getY(), pos.getZ(), cell));
                }
            });
            return out;
        });
    }

    public record Params(Level level, BlockPos corePos, int size, float t, float form, float energy, float heat,
                         boolean active, boolean unstable, float growth, int overdrive, SingularityCoreBlockEntity core) {
        /** muss zu CorePattern.shellRadius() passen (Phantom-Bloecke). */
        float radius() {
            return size / 2 + 0.2F;
        }

        /** Entstehen der Kammer aus dem Kern (0..1), beginnt nach dem Einsaugen. */
        float emerge() {
            return Mth.clamp((form - 0.5F) / 0.5F, 0.0F, 1.0F);
        }

        boolean done() {
            return form >= 1.0F;
        }

        float spin() {
            return (unstable ? 2.2F : active ? 1.0F : 0.25F) * (1.0F + 0.6F * growth);
        }
    }

    /** Lokales Koordinatensystem der Konsole: Ursprung im Controller, +Z zeigt vom Kern weg nach aussen. */
    private static Matrix4f consoleFrame(Matrix4f m, ShellCell c) {
        float yaw = (float) Math.atan2(c.dx(), c.dz());
        return new Matrix4f(m).translate(c.dx(), c.dy(), c.dz()).rotateY(yaw);
    }

    /** Punkt auf der (nach hinten geneigten) Bildschirmflaeche: u in [-1, 1] quer, v in [0, 1] hoch. */
    private static float[] screen(float u, float v) {
        return new float[]{u * 0.4F, -0.04F + v * 0.42F, 0.075F - v * 0.18F};
    }

    /** Live-Daten als Text auf dem Bildschirm (links), beidseitig lesbar. */
    private static void consoleText(Matrix4f cm, MultiBufferSource buffers, Params p) {
        Font font = Minecraft.getInstance().font;
        SingularityCoreBlockEntity core = p.core();
        ReactorSize size = ReactorSize.bySize(p.size());
        int maxOd = size == null ? 0 : size.maxOverdrive();
        StringBuilder od = new StringBuilder();
        for (int i = 0; i < maxOd; i++) od.append(i < core.getOverdrive() ? '■' : '□');
        int inst = Math.round(core.getInstability());
        int instColor = inst < 40 ? 0x80FF90 : inst < 70 ? 0xFFE060 : 0xFF5060;
        String[] lines = {
                core.getEnergyPct() >= 100 ? "PUFFER VOLL" : p.active() ? (p.unstable() ? "! INSTABIL !" : "KERN AKTIV") : "",
                "OUT " + compact(core.getOutput()) + " RF/t",
                "PUF " + core.getEnergyPct() + "%",
                "INST " + inst + "%",
                "STUFE " + core.getScale() + "/" + core.getScaleMax(),
                maxOd > 0 ? "OD " + od : ""};
        int[] colors = {core.getEnergyPct() >= 100 ? 0xFFB040 : p.unstable() ? 0xFF5060 : 0xB0E8FF, 0x7AE8FF, 0xC8B8F0, instColor, 0xE0A8FF, 0xFFB040};
        float tilt = (float) Math.atan2(0.18, 0.42);
        float s = 0.0062F;
        for (int side = 0; side < 2; side++) {
            Matrix4f tm = new Matrix4f(cm);
            if (side == 1) tm.rotateY(Mth.PI); // Rueckseite (gespiegelt lesbar von innen)
            tm.translate(-0.37F, 0.36F, -0.098F + (side == 0 ? 0.006F : -0.006F)).rotateX(-tilt).scale(s, -s, s);
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].isEmpty()) continue;
                font.drawInBatch(lines[i], 0, i * 10.5F, 0xFF000000 | colors[i], false, tm, buffers,
                        Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
            }
        }
    }

    private static String compact(long n) {
        if (n >= 1_000_000L) return String.format("%.1fM", n / 1e6);
        if (n >= 10_000L) return String.format("%.1fk", n / 1e3);
        return Long.toString(n);
    }

    /** Holografische Anzeige der Konsole: Rahmen, Pufferbalken, Zeigerinstrument und Scanlinie. */
    private static void console(Matrix4f m, VertexConsumer vc, ShellCell c, Params p, float[] col) {
        Matrix4f cm = consoleFrame(m, c);
        float e = p.energy();
        float[] cyan = {0.35F, 0.9F, 1.0F}, violet = {0.75F, 0.5F, 1.0F};
        float[] warn = lerp(new float[]{0.4F, 1.0F, 0.5F}, CRITICAL, p.heat());
        // Rahmen
        float[][] fr = {screen(-1, 0), screen(1, 0), screen(1, 1), screen(-1, 1)};
        for (int i = 0; i < 4; i++) {
            float[] a = fr[i], b = fr[(i + 1) % 4];
            segment(vc, cm, a[0], a[1], a[2], b[0], b[1], b[2], 0.012F, col[0], col[1], col[2], e, e);
        }
        // Kopfzeile
        float[] h0 = screen(-0.9F, 0.88F), h1 = screen(0.9F, 0.88F);
        segment(vc, cm, h0[0], h0[1], h0[2], h1[0], h1[1], h1[2], 0.008F, col[0], col[1], col[2], e * 0.7F, e * 0.7F);
        // Pufferbalken am unteren Rand (echter Fuellstand)
        float fill = p.core().getEnergyPct() / 100.0F;
        float[] e0 = screen(-0.92F, 0.06F), e1 = screen(-0.92F + 1.84F * fill, 0.06F);
        if (fill > 0.0F) segment(vc, cm, e0[0], e0[1], e0[2], e1[0], e1[1], e1[2], 0.02F, cyan[0], cyan[1], cyan[2], e, e);
        // Zeigerinstrument (Instabilitaet)
        float cu = 0.62F, cv = 0.45F, rad = 0.26F;
        float[] prev = null;
        for (int i = 0; i <= 16; i++) {
            float ang = Mth.PI * (1.0F - i / 16.0F);
            float[] q = screen(cu + Mth.cos(ang) * rad, cv + Mth.sin(ang) * rad * 1.1F);
            if (prev != null) segment(vc, cm, prev[0], prev[1], prev[2], q[0], q[1], q[2], 0.008F, col[0], col[1], col[2], e, e);
            prev = q;
        }
        float needle = Mth.PI * (1.0F - p.heat()) + (p.unstable() ? 0.08F * Mth.sin(p.t() * 2.0F) : 0.0F);
        float[] n0 = screen(cu, cv), n1 = screen(cu + Mth.cos(needle) * rad * 0.9F, cv + Mth.sin(needle) * rad);
        segment(vc, cm, n0[0], n0[1], n0[2], n1[0], n1[1], n1[2], 0.012F, warn[0], warn[1], warn[2], e, e);
        // Scanlinie
        float sv = Mth.frac(p.t() * 0.02F);
        float[] s0 = screen(-1, sv), s1 = screen(1, sv);
        segment(vc, cm, s0[0], s0[1], s0[2], s1[0], s1[1], s1[2], 0.006F, 1.0F, 1.0F, 1.0F, e * 0.5F, e * 0.5F);
        // Projektorstrahlen vom Sockel zu den Bildschirmecken
        for (float[] corner : fr) {
            segment(vc, cm, 0, -0.1F, 0.0F, corner[0], corner[1], corner[2], 0.006F, col[0], col[1], col[2], e * 0.25F, e * 0.05F);
        }
    }

    private static float[] sph(float r, float lat, float lon) {
        float c = Mth.cos(lat);
        return new float[]{r * c * Mth.cos(lon), r * Mth.sin(lat), r * c * Mth.sin(lon)};
    }

    // ------------------------------------------------------------------ Einsaugen der Bloecke (nur Animation)

    /** Einsaug-Fortschritt eines Blocks (0 = an seinem Platz, 1 = im Kern verschwunden). Aussen zuerst. */
    private static float suck(Params p, ShellCell c) {
        float dist = Mth.sqrt(c.dx() * c.dx() + c.dy() * c.dy() + c.dz() * c.dz()) / (p.radius() * 1.8F);
        float delay = (1.0F - Mth.clamp(dist, 0.0F, 1.0F)) * 0.25F + 0.1F * (0.5F + 0.5F * hash(c.dx() * 73 + c.dy() * 19 + c.dz() * 7));
        return Mth.clamp((p.form() / 0.55F - delay) / 0.6F, 0.0F, 1.0F);
    }

    /** Position auf der Spiralbahn in den Kern. */
    private static float[] suckPos(ShellCell c, float k) {
        float e = k * k;
        float shrink = (float) Math.pow(1.0F - e, 1.2);
        float ang = e * 2.6F;
        float x = c.dx() * shrink, z = c.dz() * shrink;
        return new float[]{x * Mth.cos(ang) - z * Mth.sin(ang), c.dy() * shrink, x * Mth.sin(ang) + z * Mth.cos(ang)};
    }

    private static boolean flies(Params p, ShellCell c) {
        // Ports bleiben als echte Anschlussbloecke stehen
        BlockState s = p.level().getBlockState(p.corePos().offset(c.dx(), c.dy(), c.dz()));
        return !s.is(ModBlocks.ENERGY_PORT.get()) && !s.is(ModBlocks.ITEM_PORT.get());
    }

    // ------------------------------------------------------------------ Pass 1: Konsole und eingesaugte Bloecke

    public static void solid(PoseStack pose, MultiBufferSource buffers, Params p) {
        Matrix4f m = pose.last().pose();

        // Controller wird zur holografischen Steuerkonsole: Sockel und Bildschirmplatte aus Sternenfeld
        if (p.done()) {
            for (ShellCell c : cells(p.size())) {
                if (c.cell() != CorePattern.Cell.SLOT) continue;
                if (!p.level().getBlockState(p.corePos().offset(c.dx(), c.dy(), c.dz())).is(ModBlocks.CONTROLLER.get())) continue;
                Matrix4f cm = consoleFrame(m, c);
                VertexConsumer gw = buffers.getBuffer(RenderType.endPortal());
                RenderUtil.starStrut(gw, cm, 0, -0.5F, 0, 0, -0.08F, -0.05F, 0.1F);
                RenderUtil.starStrut(gw, cm, 0, -0.5F, 0, 0, -0.45F, 0, 0.22F);
                starQuad(gw, cm, new float[]{-0.44F, -0.08F, 0.06F}, new float[]{0.44F, -0.08F, 0.06F},
                        new float[]{0.44F, 0.42F, -0.12F}, new float[]{-0.44F, 0.42F, -0.12F});
                consoleText(cm, buffers, p);
            }
        }

        // Verwandlung: Kopien aller Strukturbloecke werden spiralfoermig in den Kern gesaugt (die echten Bloecke bleiben)
        if (!p.done()) {
            var dispatcher = Minecraft.getInstance().getBlockRenderer();
            for (ShellCell c : cells(p.size())) {
                float k = suck(p, c);
                if (k >= 1.0F || !flies(p, c)) continue;
                BlockState s = p.level().getBlockState(p.corePos().offset(c.dx(), c.dy(), c.dz()));
                if (s.isAir()) continue;
                float[] q = suckPos(c, k);
                float scale = (float) Math.pow(1.0F - k, 0.7);
                pose.pushPose();
                pose.translate(q[0], q[1], q[2]);
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(k * k * 540.0F));
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(k * 200.0F * (c.dx() % 2 == 0 ? 1 : -1)));
                pose.scale(scale, scale, scale);
                pose.translate(-0.5F, -0.5F, -0.5F);
                dispatcher.renderSingleBlock(s.getBlock().defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                pose.popPose();
            }
        }
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
        float[] seam = lerp(SEAM, SEAM_HOT, p.active() ? 0.45F : 0.0F);
        if (p.overdrive() > 0) seam = lerp(seam, OVERDRIVE_SEAM[Math.min(3, p.overdrive()) - 1], 0.8F);
        seam = lerp(seam, CRITICAL, p.heat() * p.heat());
        float base = p.energy();
        float pulse = 0.65F + 0.35F * Mth.sin(p.t() * 0.11F * p.spin());

        holdingNet(m, vc, p, seam, base, pulse);
        if (p.done()) {
            glyphs(m, vc, p, r);
        } else {
            formationGlow(m, vc, p, seam);
        }
    }

    /** Das Partikel-Haltenetz, das das Feld um das Loch zusammenhaelt. */
    private static void holdingNet(Matrix4f m, VertexConsumer vc, Params p, float[] seam, float base, float pulse) {
        float grow = p.done() ? 1.0F : Mth.clamp((p.emerge() - 0.05F) / 0.7F, 0.0F, 1.0F);
        if (grow <= 0.0F) return;
        grow = 1.0F - (1.0F - grow) * (1.0F - grow) * (1.0F - grow);
        Lattice net = lattice(p.size());
        float R = p.radius() * grow * (1.0F + 0.012F * Mth.sin(p.t() * 0.09F));
        float strength = 0.3F + 0.7F * p.core().getShield();
        float danger = Mth.clamp((p.heat() - 0.4F) / 0.6F, 0.0F, 1.0F);
        float t = p.t();
        int window = (int) (t / 30.0F);
        float waveY = Mth.sin(t * 0.03F * p.spin()); // Kompressionswelle von Pol zu Pol

        // Drehung des ganzen Netzes (starr, damit die Fesseln ihre Laenge halten)
        Matrix4f rot = new Matrix4f().rotateY(t * 0.003F * p.spin()).rotateX(0.25F * Mth.sin(t * 0.002F));
        int n = net.points().length;
        Vector3f[] home = new Vector3f[n];
        Vector3f[] pos = new Vector3f[n];
        float[] push = new float[n];
        for (int i = 0; i < n; i++) {
            Vector3f v = rot.transformDirection(new Vector3f(net.points()[i]));
            home[i] = v;
            float ph = 0.5F + 0.5F * hash(i * 97);
            // Draengen nach aussen (selten, bei Gefahr haeufiger und weiter)
            float bulge = (float) Math.pow(Math.max(0.0F, Mth.sin(t * 0.05F + ph * Mth.TWO_PI)), 8.0) * (0.05F + 0.25F * danger);
            float escape = 0.0F;
            if (danger > 0.25F && hash(window * 131 + i * 7) > 1.0F - danger * 0.3F) {
                escape = Mth.sin(Mth.frac(t / 30.0F) * Mth.PI) * 0.35F * danger; // bricht aus und wird zurueckgezogen
            }
            push[i] = (bulge + escape) * R;
            pos[i] = new Vector3f(v).mul(R + push[i]);
        }

        // Fesseln zwischen Nachbarn
        for (int[] l : net.links()) {
            int a = l[0], b = l[1];
            boolean broken = (danger > 0.2F || strength < 0.6F)
                    && hash(a * 31 + b * 17 + window) > 1.0F - Math.max(danger * 0.45F, (0.6F - strength) * 0.8F);
            if (broken) continue;
            float wave = (float) Math.exp(-Math.pow(home[a].y - waveY, 2) * 18.0);
            float al = base * strength * (0.12F + 0.4F * wave) * pulse;
            Vector3f pa = pos[a], pb = pos[b];
            segment(vc, m, pa.x, pa.y, pa.z, pb.x, pb.y, pb.z, 0.005F, seam[0], seam[1], seam[2], al, al);
        }

        // Partikel, ihre Fesseln nach innen und feine Zugspuren zum Kern
        for (int i = 0; i < n; i++) {
            Vector3f q = pos[i];
            float wave = (float) Math.exp(-Math.pow(home[i].y - waveY, 2) * 18.0);
            float pushing = push[i] / Math.max(0.01F, R);
            float flick = p.unstable() ? 0.6F + 0.4F * (0.5F + 0.5F * hash(i * 13 + (int) (t * 0.8F))) : 1.0F;
            float a = base * flick * (0.55F + 0.45F * wave);
            cube(vc, m, q.x, q.y, q.z, 0.022F + 0.03F * pushing, 1.0F, 1.0F, 1.0F, a);
            cube(vc, m, q.x, q.y, q.z, 0.05F + 0.05F * pushing, seam[0], seam[1], seam[2], a * 0.3F);
            if (pushing > 0.015F) {
                // die Fessel spannt sich und zieht das Partikel zurueck
                Vector3f anchor = new Vector3f(home[i]).mul(R);
                float ta = Math.min(1.0F, base * (0.4F + 3.0F * pushing));
                segment(vc, m, anchor.x, anchor.y, anchor.z, q.x, q.y, q.z, 0.01F, 1.0F, 0.95F, 0.9F, ta * 0.4F, ta);
            }
            if (hash(i * 53 + (int) (t * 0.1F)) > 0.75F) {
                // Zug nach innen: kurze Spur Richtung Kern
                float s = Mth.frac(t * 0.04F + i * 0.13F);
                Vector3f from = new Vector3f(q).mul(1.0F - 0.05F * s), to = new Vector3f(q).mul(1.0F - 0.05F * s - 0.12F);
                segment(vc, m, from.x, from.y, from.z, to.x, to.y, to.z, 0.006F, seam[0], seam[1], seam[2], a * 0.5F * (1.0F - s), 0.0F);
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
                console(m, vc, c, p, bridgeCol);
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

    /** Lichtspuren der eingesaugten Bloecke und der Blitz, aus dem die Kammer entsteht. */
    private static void formationGlow(Matrix4f m, VertexConsumer vc, Params p, float[] seam) {
        for (ShellCell c : cells(p.size())) {
            float k = suck(p, c);
            if (k <= 0.0F || k >= 1.0F || !flies(p, c)) continue;
            float[] q = suckPos(c, k), q2 = suckPos(c, Math.max(0.0F, k - 0.12F));
            float a = Mth.sin(k * Mth.PI);
            segment(vc, m, q2[0], q2[1], q2[2], q[0], q[1], q[2], 0.06F * (1.0F - k * 0.5F), seam[0], seam[1], seam[2], 0.0F, a);
        }
        // Blitz: kurz nach dem Einsaugen bricht die Kammer aus dem Kern hervor
        float flash = Mth.clamp(1.0F - Math.abs(p.form() - 0.52F) / 0.14F, 0.0F, 1.0F);
        if (flash > 0.0F) {
            float r = p.radius() * (0.2F + 1.1F * Mth.clamp((p.form() - 0.42F) / 0.3F, 0.0F, 1.0F));
            RenderUtil.glowSphere(vc, m, r, 12, 18, new float[]{1.0F, 0.95F, 1.0F}, 0.55F * flash);
            RenderUtil.glowSphere(vc, m, r * 0.6F, 10, 14, seam, 0.8F * flash);
        }
    }
}
