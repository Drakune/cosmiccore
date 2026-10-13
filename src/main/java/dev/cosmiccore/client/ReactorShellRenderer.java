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
            {-78, -56, 10, 1}, {-52, -30, 16, -1},
            {30, 52, 16, -1}, {56, 74, 10, 1}};
    private static final float PLATE_FILL = 0.9F;    // Rest = Fuge
    private static final float THICK = 0.07F;
    private static final float IRIS_LAT = 78.0F * Mth.DEG_TO_RAD;

    private static final float[] SEAM = {0.62F, 0.4F, 1.0F};
    private static final float[] SEAM_HOT = {1.0F, 0.72F, 0.4F};
    private static final float[] CRITICAL = {1.0F, 0.12F, 0.12F};
    private static final float[][] OVERDRIVE_SEAM = {{0.45F, 0.85F, 1.0F}, {1.0F, 0.78F, 0.25F}, {1.0F, 0.9F, 1.0F}};
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

    /** Wachstum eines Plattenrings aus dem Kern heraus (0 = im Kern, 1 = sitzt). Aequatornahe Ringe zuerst. */
    private static float assemble(Params p, float bandY) {
        if (p.done()) return 1.0F;
        float delay = Math.abs(bandY) / p.radius() * 0.35F;
        float k = Mth.clamp((p.emerge() - delay) / 0.6F, 0.0F, 1.0F);
        return 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k); // ease-out
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
                float rr = r * in + jitter;
                if (rr < THICK + 0.05F) continue;
                float o0 = rot + k * step, o1 = o0 + step * PLATE_FILL;
                plate(portal, m, rr, lat0, lat1, o0, o1);
            }
        }

        // Iris-Blende im oberen Pol: Lamellen schieben sich zusammen (geschlossen) oder ziehen sich zurueck (offen)
        float irisIn = assemble(p, r * Mth.sin(IRIS_LAT));
        if (irisIn > 0.001F) {
            float open = p.active() ? 0.75F + 0.1F * Mth.sin(p.t() * 0.05F) : 0.15F;
            float rimR = r * irisIn * Mth.cos(IRIS_LAT), y = r * irisIn * Mth.sin(IRIS_LAT);
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
        float[] seam = lerp(SEAM, SEAM_HOT, p.active() ? 0.45F : 0.0F);
        if (p.overdrive() > 0) seam = lerp(seam, OVERDRIVE_SEAM[Math.min(3, p.overdrive()) - 1], 0.8F);
        seam = lerp(seam, CRITICAL, p.heat() * p.heat());
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
            float rr = r * in;
            if (rr < THICK + 0.05F) continue;
            float snap = in < 1.0F ? 1.0F + 2.0F * (1.0F - in) : 1.0F; // beim Entstehen gleissen die Fugen auf
            for (int k = 0; k < plates; k++) {
                float o0 = rot + k * step, o1 = o0 + step * PLATE_FILL, oGap = o0 + step;
                float flick = p.unstable() ? 0.5F + 0.5F * (0.5F + 0.5F * hash((int) (p.t() * 0.6F) + k * 13 + b * 101)) : 1.0F;
                float a = Math.min(1.0F, base * pulse * flick * snap);
                // Fuge: Licht dringt von innen durch (vom Innen- zum Aussenradius ausblendend)
                for (int i = 0; i < 3; i++) {
                    float la = Mth.lerp(i / 3.0F, lat0, lat1), lb = Mth.lerp((i + 1) / 3.0F, lat0, lat1);
                    float[] a0 = sph(rr - THICK, la, o1), a1 = sph(rr - THICK, la, oGap), b0 = sph(rr + 0.05F, lb, o1), b1 = sph(rr + 0.05F, lb, oGap);
                    quadFade(vc, m, a0[0], a0[1], a0[2], a1[0], a1[1], a1[2], b1[0], b1[1], b1[2], b0[0], b0[1], b0[2],
                            seam[0], seam[1], seam[2], a * 0.6F, a * 0.25F);
                }
                // feine Leuchtkanten oben/unten an jeder Platte
                float[] e0 = sph(rr + 0.01F, lat0, o0), e1 = sph(rr + 0.01F, lat0, o1);
                float[] f0 = sph(rr + 0.01F, lat1, o0), f1 = sph(rr + 0.01F, lat1, o1);
                segment(vc, m, e0[0], e0[1], e0[2], e1[0], e1[1], e1[2], 0.007F, seam[0], seam[1], seam[2], a * 0.5F, a * 0.5F);
                segment(vc, m, f0[0], f0[1], f0[2], f1[0], f1[1], f1[2], 0.007F, seam[0], seam[1], seam[2], a * 0.5F, a * 0.5F);
                // Innenseite wird vom Kern angestrahlt
                float[] i0 = sph(rr - THICK - 0.01F, lat0, o0), i1 = sph(rr - THICK - 0.01F, lat0, o1);
                float[] j0 = sph(rr - THICK - 0.01F, lat1, o0), j1 = sph(rr - THICK - 0.01F, lat1, o1);
                float lit = base * 0.14F * (lat0 < 0 ? 1.0F : 0.8F);
                quadFade(vc, m, i0[0], i0[1], i0[2], i1[0], i1[1], i1[2], j1[0], j1[1], j1[2], j0[0], j0[1], j0[2],
                        SEAM_HOT[0], SEAM_HOT[1], SEAM_HOT[2], lat0 < 0 ? lit * 0.4F : lit, lat0 < 0 ? lit : lit * 0.4F);
            }
        }

        // Lichtringe an den Kanten des offenen Guertels
        if (p.done()) {
            for (float lat : new float[]{-30 * Mth.DEG_TO_RAD, 30 * Mth.DEG_TO_RAD}) {
                float y = r * Mth.sin(lat) * 0.98F, rr = r * Mth.cos(lat) * 0.98F;
                Matrix4f mm = new Matrix4f(m).translate(0, y, 0);
                flatCircle(vc, mm, rr, 0.015F, 96, seam, base * pulse * 0.8F);
            }
            // Rippen durch den Guertel mit wandernden Lichtpulsen
            int ribs = p.size() >= 9 ? 8 : 4;
            for (int k = 0; k < ribs; k++) {
                float lon = Mth.PI / 4 + k * Mth.TWO_PI / ribs;
                float[] prev = null;
                for (int i = 0; i <= 10; i++) {
                    float[] q = sph(r * 0.98F, Mth.lerp(i / 10.0F, -30 * Mth.DEG_TO_RAD, 30 * Mth.DEG_TO_RAD), lon);
                    if (prev != null) segment(vc, m, prev[0], prev[1], prev[2], q[0], q[1], q[2], 0.012F, seam[0], seam[1], seam[2], base * 0.6F, base * 0.6F);
                    prev = q;
                }
                float s = Mth.frac(p.t() * 0.02F * p.spin() + k * 0.37F);
                float[] q = sph(r * 0.98F, Mth.lerp(s, -30 * Mth.DEG_TO_RAD, 30 * Mth.DEG_TO_RAD), lon);
                cube(vc, m, q[0], q[1], q[2], 0.04F, 1.0F, 1.0F, 1.0F, base * Mth.sin(s * Mth.PI));
            }
            // Iris-Rand
            float rimR = r * Mth.cos(IRIS_LAT);
            Matrix4f top = new Matrix4f(m).translate(0, r * Mth.sin(IRIS_LAT) + 0.03F, 0);
            flatCircle(vc, top, rimR, 0.018F, 48, seam, base * 0.8F);
            // Instabilitaet: Blitze springen ueber die Huelle
            if (p.heat() > 0.55F) arcs(m, vc, p, r);
            glyphs(m, vc, p, r);
        } else {
            formationGlow(m, vc, p, seam);
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
