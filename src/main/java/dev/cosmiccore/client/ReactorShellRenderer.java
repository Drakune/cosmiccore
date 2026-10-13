package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.cube;
import static dev.cosmiccore.client.RenderUtil.quad2;
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
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * Die einheitliche kosmische Huelle: Ist die Struktur gebildet, sind die Modelle
 * von Gehaeuse, Ports, Rahmen und Glas ausgeblendet und werden hier als zusammenhaengende Panzerung gezeichnet:
 * <ul>
 *   <li>Sternenfeld-Bloecke (End-Portal-Shader) mit leuchtenden Panelrahmen; innere Flaechen entfallen,
 *       damit die Struktur wie aus einem Guss wirkt.</li>
 *   <li>Energiewellen laufen durch die Panele nach oben - schneller und heller im Betrieb, rot bei Instabilitaet.</li>
 *   <li>Rahmen-Saeulen tragen senkrechte Energieschienen, Ports leuchtende Symbole, Fokusglas wird zum Sichtfenster.</li>
 *   <li>Beim Bilden faehrt eine Scan-Ebene von unten nach oben und verwandelt Block fuer Block.</li>
 * </ul>
 * Ursprung ist die Kernmitte. Der Controller bleibt als normaler Block sichtbar.
 */
public final class ReactorShellRenderer {
    private ReactorShellRenderer() {}

    private static final float[] PANEL = {0.55F, 0.3F, 1.0F};
    private static final float[] PANEL_HOT = {1.0F, 0.75F, 0.45F};
    private static final float[] CRITICAL = {1.0F, 0.15F, 0.15F};
    private static final float[] ENERGY_GLYPH = {0.35F, 0.95F, 1.0F};
    private static final float[] ITEM_GLYPH = {1.0F, 0.6F, 0.2F};

    /** Vorberechnete Zelle der Huelle: Versatz zum Kern, Typ und Bitmaske der sichtbaren Seiten. */
    private record ShellCell(int dx, int dy, int dz, CorePattern.Cell cell, int faces) {}

    private static final Map<Integer, List<ShellCell>> CACHE = new HashMap<>();

    private static boolean isShell(CorePattern.Cell c) {
        return c == CorePattern.Cell.HULL || c == CorePattern.Cell.SLOT || c == CorePattern.Cell.FRAME
                || c == CorePattern.Cell.GLASS;
    }

    /** Diese Zellen verdecken ihre Nachbarn (Glas nicht - es ist ein Fenster). */
    private static boolean isOpaqueShell(CorePattern.Cell c) {
        return c == CorePattern.Cell.HULL || c == CorePattern.Cell.SLOT || c == CorePattern.Cell.FRAME;
    }

    private static List<ShellCell> cells(int size) {
        return CACHE.computeIfAbsent(size, s -> {
            ReactorSize rs = ReactorSize.bySize(s);
            List<ShellCell> out = new ArrayList<>();
            if (rs == null) return out;
            CorePattern p = rs.pattern();
            int h = p.half();
            Map<Long, CorePattern.Cell> grid = new HashMap<>();
            p.forEach(BlockPos.ZERO, (pos, cell) -> grid.put(pos.asLong(), cell));
            p.forEach(BlockPos.ZERO, (pos, cell) -> {
                if (!isShell(cell)) return;
                int mask = 0;
                for (Direction d : Direction.values()) {
                    CorePattern.Cell n = grid.get(pos.relative(d).asLong());
                    boolean hidden = n != null && isOpaqueShell(n) && cell != CorePattern.Cell.GLASS;
                    if (!hidden) mask |= 1 << d.ordinal();
                }
                out.add(new ShellCell(pos.getX(), pos.getY(), pos.getZ(), cell, mask));
            });
            return out;
        });
    }

    public record Params(Level level, BlockPos corePos, int size, float t, float form, float energy, float heat,
                         boolean active, boolean unstable) {
        float sweepY() {
            // Hoehe der Scan-Ebene relativ zur Kernmitte
            int h = size / 2;
            return -h - 0.6F + form * (size + 1.2F);
        }

        boolean transformed(int dy) {
            return form >= 1.0F || dy + 0.5F < sweepY();
        }
    }

    private enum Kind { NORMAL, CONTROLLER, ENERGY_PORT, ITEM_PORT }

    private static Kind kind(Params p, ShellCell c) {
        if (c.cell() != CorePattern.Cell.HULL && c.cell() != CorePattern.Cell.SLOT) return Kind.NORMAL;
        BlockState s = p.level().getBlockState(p.corePos().offset(c.dx(), c.dy(), c.dz()));
        if (s.is(ModBlocks.CONTROLLER.get())) return Kind.CONTROLLER;
        if (s.is(ModBlocks.ENERGY_PORT.get())) return Kind.ENERGY_PORT;
        if (s.is(ModBlocks.ITEM_PORT.get())) return Kind.ITEM_PORT;
        return Kind.NORMAL;
    }

    // ------------------------------------------------------------------ Pass 1: Sternenfeld + noch nicht verwandelte Bloecke

    public static void solid(PoseStack pose, MultiBufferSource buffers, Params p) {
        List<ShellCell> list = cells(p.size());
        VertexConsumer portal = buffers.getBuffer(RenderType.endPortal());
        Matrix4f m = pose.last().pose();
        List<ShellCell> pending = new ArrayList<>();
        for (ShellCell c : list) {
            if (c.cell() == CorePattern.Cell.GLASS) continue;
            if (kind(p, c) == Kind.CONTROLLER) continue;
            if (!p.transformed(c.dy())) {
                pending.add(c);
                continue;
            }
            starCubeFaces(portal, m, c.dx() - 0.5F, c.dy() - 0.5F, c.dz() - 0.5F, c.faces());
        }
        // waehrend der Verwandlung: Bloecke oberhalb der Scan-Ebene zeigen noch ihr altes Aussehen
        if (p.form() < 1.0F) {
            var dispatcher = Minecraft.getInstance().getBlockRenderer();
            for (ShellCell c : list) {
                if (p.transformed(c.dy())) continue;
                BlockPos wp = p.corePos().offset(c.dx(), c.dy(), c.dz());
                BlockState s = p.level().getBlockState(wp);
                if (s.is(ModBlocks.CONTROLLER.get())) continue;
                BlockState look = s.getBlock().defaultBlockState(); // Ruhe-Modell (Phase DORMANT)
                pose.pushPose();
                pose.translate(c.dx() - 0.5F, c.dy() - 0.5F, c.dz() - 0.5F);
                dispatcher.renderSingleBlock(look, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        ModelData.EMPTY, null);
                pose.popPose();
            }
        }
    }

    /** Wuerfel mit Sternenfeld-Shader, nur die sichtbaren Seiten. (x,y,z) = untere Ecke. */
    private static void starCubeFaces(VertexConsumer vc, Matrix4f m, float x, float y, float z, int mask) {
        float x1 = x + 1, y1 = y + 1, z1 = z + 1;
        if ((mask & (1 << Direction.DOWN.ordinal())) != 0) starFace(vc, m, x, y, z, x1, y, z, x1, y, z1, x, y, z1);
        if ((mask & (1 << Direction.UP.ordinal())) != 0) starFace(vc, m, x, y1, z, x, y1, z1, x1, y1, z1, x1, y1, z);
        if ((mask & (1 << Direction.NORTH.ordinal())) != 0) starFace(vc, m, x, y, z, x, y1, z, x1, y1, z, x1, y, z);
        if ((mask & (1 << Direction.SOUTH.ordinal())) != 0) starFace(vc, m, x, y, z1, x1, y, z1, x1, y1, z1, x, y1, z1);
        if ((mask & (1 << Direction.WEST.ordinal())) != 0) starFace(vc, m, x, y, z, x, y, z1, x, y1, z1, x, y1, z);
        if ((mask & (1 << Direction.EAST.ordinal())) != 0) starFace(vc, m, x1, y, z, x1, y1, z, x1, y1, z1, x1, y, z1);
    }

    private static void starFace(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                                 float cx, float cy, float cz, float dx, float dy, float dz) {
        vc.addVertex(m, ax, ay, az);
        vc.addVertex(m, bx, by, bz);
        vc.addVertex(m, cx, cy, cz);
        vc.addVertex(m, dx, dy, dz);
        vc.addVertex(m, dx, dy, dz);
        vc.addVertex(m, cx, cy, cz);
        vc.addVertex(m, bx, by, bz);
        vc.addVertex(m, ax, ay, az);
    }

    // ------------------------------------------------------------------ Pass 2: Leuchtende Panele, Symbole, Scan-Ebene

    public static void glow(Matrix4f m, VertexConsumer vc, Params p) {
        List<ShellCell> list = cells(p.size());
        float speed = p.unstable() ? 2.2F : p.active() ? 1.0F : 0.35F;
        float[] base = RenderUtil.lerp(PANEL, PANEL_HOT, p.active() ? 0.25F : 0.0F);
        base = RenderUtil.lerp(base, CRITICAL, p.heat() * p.heat());
        for (ShellCell c : list) {
            if (!p.transformed(c.dy())) continue;
            Kind kind = kind(p, c);
            if (kind == Kind.CONTROLLER) {
                controllerFrame(m, vc, c, p, base);
                continue;
            }
            // Energiewelle laeuft nach oben durch die Huelle
            float wave = 0.5F + 0.5F * Mth.sin(c.dy() * 0.9F - p.t() * 0.09F * speed + (c.dx() + c.dz()) * 0.25F);
            float flick = p.unstable() ? 0.6F + 0.4F * (0.5F + 0.5F * RenderUtil.hash((int) (p.t() * 0.5F) + c.dx() * 31 + c.dz() * 17 + c.dy() * 7)) : 1.0F;
            float a = p.energy() * (0.25F + 0.75F * wave * wave) * flick;
            for (Direction d : Direction.values()) {
                if ((c.faces() & (1 << d.ordinal())) == 0) continue;
                switch (c.cell()) {
                    case GLASS -> window(m, vc, c, d, base, a);
                    case FRAME -> rails(m, vc, c, d, base, a, p.t(), speed);
                    default -> {
                        panel(m, vc, c, d, base, a);
                        if (kind == Kind.ENERGY_PORT) glyphBolt(m, vc, c, d, ENERGY_GLYPH, p.energy());
                        if (kind == Kind.ITEM_PORT) glyphChevron(m, vc, c, d, ITEM_GLYPH, p.energy());
                    }
                }
            }
        }
        if (p.form() < 1.0F) scanPlane(m, vc, p);
    }

    /** Punkt auf einer Seite: (u, v) in [-0.5, 0.5], leicht nach aussen versetzt gegen Flackern. */
    private static float[] onFace(ShellCell c, Direction d, float u, float v) {
        float nx = d.getStepX(), ny = d.getStepY(), nz = d.getStepZ();
        float ux, uy, uz, vx, vy, vz;
        if (d.getAxis() == Direction.Axis.Y) { ux = 1; uy = 0; uz = 0; vx = 0; vy = 0; vz = 1; }
        else if (d.getAxis() == Direction.Axis.Z) { ux = 1; uy = 0; uz = 0; vx = 0; vy = 1; vz = 0; }
        else { ux = 0; uy = 0; uz = 1; vx = 0; vy = 1; vz = 0; }
        float o = 0.503F;
        return new float[]{c.dx() + nx * o + ux * u + vx * v, c.dy() + ny * o + uy * u + vy * v, c.dz() + nz * o + uz * u + vz * v};
    }

    private static void line(Matrix4f m, VertexConsumer vc, ShellCell c, Direction d, float u0, float v0, float u1, float v1,
                             float w, float[] col, float a) {
        float[] p0 = onFace(c, d, u0, v0), p1 = onFace(c, d, u1, v1);
        segment(vc, m, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], w, col[0], col[1], col[2], a, a);
    }

    /** Panelrahmen mit Eckpunkten. */
    private static void panel(Matrix4f m, VertexConsumer vc, ShellCell c, Direction d, float[] col, float a) {
        float r = 0.42F, w = 0.012F;
        line(m, vc, c, d, -r, -r, r, -r, w, col, a);
        line(m, vc, c, d, r, -r, r, r, w, col, a);
        line(m, vc, c, d, r, r, -r, r, w, col, a);
        line(m, vc, c, d, -r, r, -r, -r, w, col, a);
        float k = 0.12F;
        line(m, vc, c, d, -k, 0, k, 0, w * 0.8F, col, a * 0.5F);
        line(m, vc, c, d, 0, -k, 0, k, w * 0.8F, col, a * 0.5F);
    }

    /** Rahmen-Saeulen: zwei senkrechte Energieschienen mit aufsteigendem Lichtpuls. */
    private static void rails(Matrix4f m, VertexConsumer vc, ShellCell c, Direction d, float[] col, float a, float t, float speed) {
        if (d.getAxis() == Direction.Axis.Y) return;
        line(m, vc, c, d, -0.25F, -0.5F, -0.25F, 0.5F, 0.016F, col, a);
        line(m, vc, c, d, 0.25F, -0.5F, 0.25F, 0.5F, 0.016F, col, a);
        float s = Mth.frac(t * 0.02F * speed - c.dy() * 0.15F);
        float[] p0 = onFace(c, d, 0, s - 0.5F);
        cube(vc, m, p0[0], p0[1], p0[2], 0.035F, 1.0F, 1.0F, 1.0F, a);
    }

    /** Fokusglas wird zum Sichtfenster: zarte Toenung und Rahmen. */
    private static void window(Matrix4f m, VertexConsumer vc, ShellCell c, Direction d, float[] col, float a) {
        float[] p0 = onFace(c, d, -0.5F, -0.5F), p1 = onFace(c, d, 0.5F, -0.5F), p2 = onFace(c, d, 0.5F, 0.5F), p3 = onFace(c, d, -0.5F, 0.5F);
        quad2(vc, m, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], p3[0], p3[1], p3[2], col[0], col[1], col[2], 0.08F * a);
        float r = 0.48F, w = 0.01F;
        line(m, vc, c, d, -r, -r, r, -r, w, col, a * 0.8F);
        line(m, vc, c, d, r, -r, r, r, w, col, a * 0.8F);
        line(m, vc, c, d, r, r, -r, r, w, col, a * 0.8F);
        line(m, vc, c, d, -r, r, -r, -r, w, col, a * 0.8F);
    }

    private static void glyphBolt(Matrix4f m, VertexConsumer vc, ShellCell c, Direction d, float[] col, float a) {
        float w = 0.025F;
        line(m, vc, c, d, 0.08F, 0.3F, -0.1F, 0.0F, w, col, a);
        line(m, vc, c, d, -0.1F, 0.0F, 0.1F, 0.0F, w, col, a);
        line(m, vc, c, d, 0.1F, 0.0F, -0.08F, -0.3F, w, col, a);
    }

    private static void glyphChevron(Matrix4f m, VertexConsumer vc, ShellCell c, Direction d, float[] col, float a) {
        float w = 0.025F;
        for (float y : new float[]{0.12F, -0.08F}) {
            line(m, vc, c, d, -0.2F, y + 0.1F, 0.0F, y - 0.1F, w, col, a);
            line(m, vc, c, d, 0.0F, y - 0.1F, 0.2F, y + 0.1F, w, col, a);
        }
    }

    /** Leuchtender Rahmen um den (sichtbar bleibenden) Controller. */
    private static void controllerFrame(Matrix4f m, VertexConsumer vc, ShellCell c, Params p, float[] col) {
        float pulse = 0.6F + 0.4F * Mth.sin(p.t() * 0.1F);
        float x0 = c.dx() - 0.51F, x1 = c.dx() + 0.51F, y0 = c.dy() - 0.51F, y1 = c.dy() + 0.51F, z0 = c.dz() - 0.51F, z1 = c.dz() + 0.51F;
        float a = p.energy() * pulse, w = 0.012F;
        float[][] corners = {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            float[] a0 = corners[e[0]], a1 = corners[e[1]];
            segment(vc, m, a0[0], a0[1], a0[2], a1[0], a1[1], a1[2], w, col[0], col[1], col[2], a, a);
        }
    }

    /** Die Scan-Ebene der Verwandlung: helles Gitter, das von unten nach oben durch die Struktur faehrt. */
    private static void scanPlane(Matrix4f m, VertexConsumer vc, Params p) {
        int h = p.size() / 2;
        float y = p.sweepY();
        float r = h + 0.6F;
        float fade = Mth.sin(p.form() * Mth.PI);
        float[] col = {0.8F, 0.9F, 1.0F};
        quad2(vc, m, -r, y, -r, r, y, -r, r, y, r, -r, y, r, 0.55F, 0.4F, 1.0F, 0.12F * fade);
        int lines = p.size() + 1;
        for (int i = 0; i <= lines; i++) {
            float f = -r + 2 * r * i / lines;
            segment(vc, m, f, y, -r, f, y, r, 0.012F, col[0], col[1], col[2], 0.6F * fade, 0.6F * fade);
            segment(vc, m, -r, y, f, r, y, f, 0.012F, col[0], col[1], col[2], 0.6F * fade, 0.6F * fade);
        }
        // heller Rand
        segment(vc, m, -r, y, -r, r, y, -r, 0.04F, 1, 1, 1, fade, fade);
        segment(vc, m, r, y, -r, r, y, r, 0.04F, 1, 1, 1, fade, fade);
        segment(vc, m, r, y, r, -r, y, r, 0.04F, 1, 1, 1, fade, fade);
        segment(vc, m, -r, y, r, -r, y, -r, 0.04F, 1, 1, 1, fade, fade);
    }
}
