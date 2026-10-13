package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.cube;
import static dev.cosmiccore.client.RenderUtil.flatCircle;
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
 * Die einheitliche kosmische Huelle: Ist die Struktur gebildet, sind die Modelle von Gehaeuse, Ports, Rahmen und
 * Glas ausgeblendet. Statt eckiger Bloecke umschliesst eine runde Kammer aus Sternenfeld das schwarze Loch:
 * <ul>
 *   <li>Schale unten und Kuppel oben (mit Oeffnung fuer die Jets), dazwischen ein offener Guertel mit Blick auf den Kern.</li>
 *   <li>Gebogene Rippen verbinden Schale und Kuppel (an Stelle der Runenrahmen).</li>
 *   <li>Breiten- und Laengengitter mit Energiewellen, die nach oben laufen - rot bei Instabilitaet.</li>
 *   <li>Ports zeigen ihr Symbol auf der Kugeloberflaeche, der Controller bleibt als Block sichtbar.</li>
 *   <li>Beim Bilden faehrt eine Scan-Scheibe (Kugelquerschnitt) nach oben und verwandelt die Struktur.</li>
 * </ul>
 * Ursprung ist die Kernmitte.
 */
public final class ReactorShellRenderer {
    private ReactorShellRenderer() {}

    private static final float LAT_OPEN = 32.0F * Mth.DEG_TO_RAD;  // offener Guertel +- 32 Grad
    private static final float LAT_POLE = 76.0F * Mth.DEG_TO_RAD;  // Polöffnungen fuer Kristalle/Jets
    private static final int LON_SEG = 48;
    private static final int LAT_STEPS = 8;

    private static final float[] GRID = {0.55F, 0.3F, 1.0F};
    private static final float[] GRID_HOT = {1.0F, 0.75F, 0.45F};
    private static final float[] CRITICAL = {1.0F, 0.15F, 0.15F};
    private static final float[] ENERGY_GLYPH = {0.35F, 0.95F, 1.0F};
    private static final float[] ITEM_GLYPH = {1.0F, 0.6F, 0.2F};

    /** Huellen-Zellen (Gehaeuse, Ports, Rahmen, Glas) relativ zum Kern - fuer die Verwandlung und die Port-Symbole. */
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
                         boolean active, boolean unstable) {
        float radius() {
            return size / 2 + 0.5F;
        }

        /** Hoehe der Scan-Scheibe relativ zur Kernmitte. */
        float sweepY() {
            float r = radius();
            return -r - 0.1F + form * (2 * r + 0.2F);
        }

        boolean done() {
            return form >= 1.0F;
        }
    }

    private static float[] sph(float r, float lat, float lon) {
        float c = Mth.cos(lat);
        return new float[]{r * c * Mth.cos(lon), r * Mth.sin(lat), r * c * Mth.sin(lon)};
    }

    private static int ribCount(int size) {
        return size >= 9 ? 8 : 4;
    }

    private static float ribLon(int k, int ribs) {
        return Mth.PI / 4 + k * Mth.TWO_PI / ribs;
    }

    // ------------------------------------------------------------------ Pass 1: Sternenfeld + noch nicht verwandelte Bloecke

    public static void solid(PoseStack pose, MultiBufferSource buffers, Params p) {
        float r = p.radius();
        float sweep = p.sweepY();
        VertexConsumer portal = buffers.getBuffer(RenderType.endPortal());
        Matrix4f m = pose.last().pose();

        // Schale und Kuppel
        band(portal, m, r, -LAT_POLE, -LAT_OPEN, sweep, p.done());
        band(portal, m, r, LAT_OPEN, LAT_POLE, sweep, p.done());
        // Rippen durch den offenen Guertel
        int ribs = ribCount(p.size());
        float w = 0.11F;
        for (int k = 0; k < ribs; k++) {
            float lon = ribLon(k, ribs);
            for (int i = 0; i < LAT_STEPS; i++) {
                float l0 = Mth.lerp(i / (float) LAT_STEPS, -LAT_OPEN, LAT_OPEN);
                float l1 = Mth.lerp((i + 1) / (float) LAT_STEPS, -LAT_OPEN, LAT_OPEN);
                if (!p.done() && r * Mth.sin(l1) > sweep) continue;
                for (float rr : new float[]{r, r - 0.18F}) {
                    starQuad(portal, m, sph(rr, l0, lon - w), sph(rr, l0, lon + w), sph(rr, l1, lon + w), sph(rr, l1, lon - w));
                }
            }
        }

        // waehrend der Verwandlung: Bloecke oberhalb der Scan-Scheibe zeigen noch ihr altes Aussehen
        if (!p.done()) {
            var dispatcher = Minecraft.getInstance().getBlockRenderer();
            for (ShellCell c : cells(p.size())) {
                if (c.dy() + 0.5F < sweep) continue;
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

    /** Kugelzone zwischen zwei Breiten mit Sternenfeld-Shader, nur unterhalb der Scan-Scheibe. */
    private static void band(VertexConsumer vc, Matrix4f m, float r, float latA, float latB, float sweep, boolean done) {
        for (int i = 0; i < LAT_STEPS; i++) {
            float l0 = Mth.lerp(i / (float) LAT_STEPS, latA, latB);
            float l1 = Mth.lerp((i + 1) / (float) LAT_STEPS, latA, latB);
            if (!done && r * Mth.sin(Math.max(l0, l1)) > sweep) continue;
            for (int j = 0; j < LON_SEG; j++) {
                float o0 = Mth.TWO_PI * j / LON_SEG, o1 = Mth.TWO_PI * (j + 1) / LON_SEG;
                starQuad(vc, m, sph(r, l0, o0), sph(r, l0, o1), sph(r, l1, o1), sph(r, l1, o0));
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

    // ------------------------------------------------------------------ Pass 2: Leuchtendes Gitter, Symbole, Scan-Scheibe

    public static void glow(Matrix4f m, VertexConsumer vc, Params p) {
        float r = p.radius() + 0.01F;
        float sweep = p.done() ? Float.MAX_VALUE : p.sweepY();
        float speed = p.unstable() ? 2.2F : p.active() ? 1.0F : 0.35F;
        float[] col = RenderUtil.lerp(GRID, GRID_HOT, p.active() ? 0.3F : 0.0F);
        col = RenderUtil.lerp(col, CRITICAL, p.heat() * p.heat());
        float base = p.energy();

        // Breitenkreise auf Schale und Kuppel, Energiewelle laeuft nach oben
        for (float lat : latLines()) {
            float y = r * Mth.sin(lat);
            if (y > sweep) continue;
            float wave = 0.5F + 0.5F * Mth.sin(lat * 6.0F - p.t() * 0.08F * speed);
            float a = base * (0.2F + 0.8F * wave * wave) * flicker(p, (int) (lat * 100));
            float rr = r * Mth.cos(lat);
            for (int j = 0; j < LON_SEG; j++) {
                float o0 = Mth.TWO_PI * j / LON_SEG, o1 = Mth.TWO_PI * (j + 1) / LON_SEG;
                segment(vc, m, rr * Mth.cos(o0), y, rr * Mth.sin(o0), rr * Mth.cos(o1), y, rr * Mth.sin(o1),
                        Math.abs(lat) == LAT_OPEN ? 0.03F : 0.012F, col[0], col[1], col[2], a, a);
            }
        }
        // Meridiane
        int meridians = 16;
        for (int k = 0; k < meridians; k++) {
            float lon = Mth.TWO_PI * k / meridians;
            for (float[] range : new float[][]{{-LAT_POLE, -LAT_OPEN}, {LAT_OPEN, LAT_POLE}}) {
                for (int i = 0; i < LAT_STEPS; i++) {
                    float l0 = Mth.lerp(i / (float) LAT_STEPS, range[0], range[1]);
                    float l1 = Mth.lerp((i + 1) / (float) LAT_STEPS, range[0], range[1]);
                    float[] a0 = sph(r, l0, lon), a1 = sph(r, l1, lon);
                    if (Math.max(a0[1], a1[1]) > sweep) continue;
                    float wave = 0.5F + 0.5F * Mth.sin(l0 * 6.0F - p.t() * 0.08F * speed);
                    float a = base * 0.5F * (0.2F + 0.8F * wave) * flicker(p, k * 13 + i);
                    segment(vc, m, a0[0], a0[1], a0[2], a1[0], a1[1], a1[2], 0.01F, col[0], col[1], col[2], a, a);
                }
            }
        }
        // Rippen: Kanten und Lichtpulse, die zur Kuppel laufen
        int ribs = ribCount(p.size());
        for (int k = 0; k < ribs; k++) {
            float lon = ribLon(k, ribs);
            for (float side : new float[]{-0.11F, 0.11F}) {
                float[] a0 = sph(r, -LAT_OPEN, lon + side), a1;
                for (int i = 1; i <= LAT_STEPS; i++) {
                    a1 = sph(r, Mth.lerp(i / (float) LAT_STEPS, -LAT_OPEN, LAT_OPEN), lon + side);
                    if (Math.max(a0[1], a1[1]) <= sweep) {
                        segment(vc, m, a0[0], a0[1], a0[2], a1[0], a1[1], a1[2], 0.014F, col[0], col[1], col[2], base * 0.8F, base * 0.8F);
                    }
                    a0 = a1;
                }
            }
            if (p.done()) {
                float s = Mth.frac(p.t() * 0.015F * speed + k * 0.37F);
                float[] q = sph(r + 0.02F, Mth.lerp(s, -LAT_OPEN, LAT_OPEN), lon);
                cube(vc, m, q[0], q[1], q[2], 0.05F, 1.0F, 1.0F, 1.0F, base * Mth.sin(s * Mth.PI));
            }
        }
        // Polringe (Oeffnungen fuer Kristalle und Jets)
        for (float lat : new float[]{-LAT_POLE, LAT_POLE}) {
            float y = r * Mth.sin(lat);
            if (y > sweep) continue;
            float rr = r * Mth.cos(lat);
            for (int j = 0; j < LON_SEG; j++) {
                float o0 = Mth.TWO_PI * j / LON_SEG, o1 = Mth.TWO_PI * (j + 1) / LON_SEG;
                segment(vc, m, rr * Mth.cos(o0), y, rr * Mth.sin(o0), rr * Mth.cos(o1), y, rr * Mth.sin(o1), 0.03F,
                        col[0], col[1], col[2], base, base);
            }
        }

        if (p.done()) glyphs(m, vc, p, r);
        else scanDisc(m, vc, p);
    }

    private static float[] latLines() {
        return new float[]{-LAT_POLE, -60 * Mth.DEG_TO_RAD, -46 * Mth.DEG_TO_RAD, -LAT_OPEN,
                LAT_OPEN, 46 * Mth.DEG_TO_RAD, 60 * Mth.DEG_TO_RAD};
    }

    private static float flicker(Params p, int seed) {
        if (!p.unstable()) return 1.0F;
        return 0.55F + 0.45F * (0.5F + 0.5F * RenderUtil.hash((int) (p.t() * 0.5F) + seed * 31));
    }

    /** Symbole der Ports und ein Ring um den Controller - projiziert auf die Kugeloberflaeche. */
    private static void glyphs(Matrix4f m, VertexConsumer vc, Params p, float r) {
        for (ShellCell c : cells(p.size())) {
            if (c.cell() != CorePattern.Cell.HULL && c.cell() != CorePattern.Cell.SLOT) continue;
            BlockState s = p.level().getBlockState(p.corePos().offset(c.dx(), c.dy(), c.dz()));
            boolean energy = s.is(ModBlocks.ENERGY_PORT.get()), item = s.is(ModBlocks.ITEM_PORT.get());
            boolean controller = s.is(ModBlocks.CONTROLLER.get());
            if (!energy && !item && !controller) continue;
            Vector3f n = new Vector3f(c.dx(), c.dy(), c.dz()).normalize();
            // Port-Symbole liegen auf der Schale; in den Polloechern und im Guertel auf die naechste Schalenkante schieben
            float lat = (float) Math.asin(n.y);
            lat = Mth.clamp(lat, -LAT_POLE + 0.12F, -LAT_OPEN - 0.12F);
            float lon = (float) Math.atan2(n.z, n.x);
            float[] cpos = sph(r + 0.02F, lat, lon);
            Vector3f normal = new Vector3f(cpos[0], cpos[1], cpos[2]).normalize();
            Vector3f east = new Vector3f(-Mth.sin(lon), 0, Mth.cos(lon));
            Vector3f north = new Vector3f(normal).cross(east).normalize().negate();
            float pulse = 0.6F + 0.4F * Mth.sin(p.t() * 0.12F + c.dx() + c.dz());
            if (controller) {
                circle(m, vc, cpos, east, north, 0.32F, new float[]{0.8F, 0.6F, 1.0F}, p.energy() * pulse);
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
        float y = p.sweepY();
        float cr = Mth.sqrt(Math.max(0.0F, r * r - y * y));
        float fade = Mth.sin(p.form() * Mth.PI);
        float[] white = {0.9F, 0.95F, 1.0F};
        float[] violet = {0.6F, 0.4F, 1.0F};
        org.joml.Matrix4f mm = new org.joml.Matrix4f(m).translate(0, y, 0);
        flatCircle(vc, mm, Math.max(0.05F, cr), 0.05F, 64, white, fade);
        for (int i = 1; i <= 4; i++) {
            float rr = cr * (1.0F - i * 0.22F);
            if (rr > 0.05F) flatCircle(vc, mm, rr, 0.015F, 48, violet, fade * 0.6F);
        }
        // Speichen
        for (int k = 0; k < 12; k++) {
            float ang = Mth.TWO_PI * k / 12 + p.t() * 0.05F;
            RenderUtil.flatLine(vc, mm, 0, 0, Mth.cos(ang) * cr, Mth.sin(ang) * cr, 0.01F, violet, fade * 0.5F);
        }
    }
}
