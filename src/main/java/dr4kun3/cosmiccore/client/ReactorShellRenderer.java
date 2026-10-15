package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.cube;
import static dr4kun3.cosmiccore.client.RenderUtil.flatCircle;
import static dr4kun3.cosmiccore.client.RenderUtil.hash;
import static dr4kun3.cosmiccore.client.RenderUtil.lerp;
import static dr4kun3.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dr4kun3.cosmiccore.block.ModBlocks;
import dr4kun3.cosmiccore.multiblock.CorePattern;
import dr4kun3.cosmiccore.multiblock.ReactorSize;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dr4kun3.cosmiccore.block.entity.SingularityCoreBlockEntity;
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
 * Statt einer festen Huelle haelt ein Sternbild-Netz das Feld um das schwarze Loch zusammen:
 * <ul>
 *   <li>Funkelnde Sterne unterschiedlicher Helligkeit liegen auf einer Kugel, feine Sternbildlinien verbinden sie -
 *       das Netz dreht sich langsam, Kompressionswellen laufen von Pol zu Pol.</li>
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
    private static final float[][] OVERDRIVE_SEAM = {{0.45F, 0.85F, 1.0F}, {1.0F, 0.78F, 0.25F}, {1.0F, 0.9F, 1.0F},
            {1.0F, 0.88F, 0.45F}};
    private static final float[] ENERGY_GLYPH = {0.35F, 0.95F, 1.0F};
    private static final float[] ITEM_GLYPH = {1.0F, 0.6F, 0.2F};

    private record ShellCell(int dx, int dy, int dz, CorePattern.Cell cell) {}

    /** Haltenetz: Partikel (Einheitsvektoren auf der Kugel) und ihre Fesseln (Paare naechster Nachbarn). */
    private record Lattice(Vector3f[] points, int[][] links) {}

    private static final Map<Integer, Lattice> LATTICE = new HashMap<>();

    private static Lattice lattice(int size) {
        int n0 = RenderQuality.scale(40 + size * 15, 40); // HOCH: 5x5x5: 115, 7x7x7: 145, 9x9x9: 175
        return LATTICE.computeIfAbsent(size * 1000 + n0, cacheKey -> {
            int n = n0;
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
                        || cell == CorePattern.Cell.GLASS || cell == CorePattern.Cell.COIL || cell == CorePattern.Cell.CRYSTAL
                        || cell == CorePattern.Cell.SATELLITE) {
                    out.add(new ShellCell(pos.getX(), pos.getY(), pos.getZ(), cell));
                }
            });
            return out;
        });
    }

    public record Params(Level level, BlockPos corePos, int size, float t, float form, float energy, float heat,
                         boolean active, boolean unstable, float growth, int overdrive, SingularityCoreBlockEntity core,
                         float settle) {
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
        return new float[]{u * 0.46F, -0.02F + v * 0.56F, 0.07F - v * 0.22F};
    }

    private static final float TEXT_SCALE = 0.0052F;
    private static final float PANEL_PX = 0.92F / TEXT_SCALE; // Breite in Textpixeln

    /** Textraum des Bildschirms: Ursprung oben links, x nach rechts, y nach unten (in Schriftpixeln). */
    private static Matrix4f textFrame(Matrix4f cm, float lift) {
        float tilt = (float) Math.atan2(0.22, 0.56);
        float[] tl = screen(-1, 1);
        return new Matrix4f(cm).translate(tl[0], tl[1], tl[2]).rotateX(-tilt).translate(0, 0, lift).scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
    }

    private static final int ROW_TITLE = 3, ROW_STATUS = 14, ROW_OUT = 28, ROW_BUF = 39, BAR_BUF = 49, ROW_INST = 56,
            BAR_INST = 66, ROW_LEVEL = 73, ROW_OD = 84, ROW_FIELD = 95;

    /** Live-Daten als Tabelle: Bezeichnung links, Wert mit Einheit rechtsbuendig. */
    private static void consoleText(Matrix4f cm, MultiBufferSource buffers, Params p) {
        Font font = Minecraft.getInstance().font;
        SingularityCoreBlockEntity core = p.core();
        ReactorSize size = ReactorSize.bySize(p.size());
        int maxOd = size == null ? 0 : size.maxOverdrive();
        StringBuilder od = new StringBuilder();
        for (int i = 0; i < maxOd; i++) od.append(i < core.getOverdrive() ? "\u25A0 " : "\u25A1 ");
        if (core.getOverdrive() > maxOd) od.append("\u2605 "); // Ultimate-Modus
        int inst = Math.round(core.getInstability());
        int instColor = inst < 40 ? 0x80FF90 : inst < 70 ? 0xFFE060 : 0xFF5060;
        long cap = ReactorSizeCapacity.capacity(core.getStructureSize());
        long stored = Math.round(cap * core.getEnergyPct() / 100.0);

        String status;
        int statusColor;
        if (core.getMeltdown() > 0) {
            status = tr("console.cosmiccore.meltdown", core.getMeltdown());
            statusColor = (p.t() / 5) % 2 < 1 ? 0xFF3030 : 0xFFFFFF;
        } else if (core.getEnergyPct() >= 100) {
            status = tr("console.cosmiccore.full");
            statusColor = 0xFFB040;
        } else if (p.unstable()) {
            status = tr("console.cosmiccore.unstable");
            statusColor = 0xFF5060;
        } else if (p.active()) {
            status = tr("console.cosmiccore.active");
            statusColor = 0x80FF90;
        } else {
            status = "";
            statusColor = 0;
        }

        Matrix4f tm = textFrame(cm, 0.004F);
        center(font, tm, buffers, "\u25C6 " + tr("console.cosmiccore.title") + " \u25C6", ROW_TITLE, 0xD8B8FF);
        if (!status.isEmpty()) center(font, tm, buffers, status, ROW_STATUS, statusColor);
        var ctrl = controllerOf(p);
        if (ctrl != null && ctrl.hasEnergyPort()) {
            // Leistung und Puffer zeigt der Energie-Port - hier Vorrat und Restlaufzeit
            PortStation.itemRow(font, tm, buffers, p.level(), tr("console.cosmiccore.fuel"), ctrl.getClientFuel(), ctrl.getFuelSeconds(),
                    ROW_OUT, 0xFFC070, PANEL_PX - 1);
            PortStation.itemRow(font, tm, buffers, p.level(), tr("console.cosmiccore.stabilizer"), ctrl.getClientStab(), ctrl.getStabSeconds(),
                    ROW_BUF, 0x9AD8FF, PANEL_PX - 1);
        } else {
            row(font, tm, buffers, tr("console.cosmiccore.output"), compact(core.getOutput()) + " RF/t", ROW_OUT, 0x7AE8FF);
            row(font, tm, buffers, tr("console.cosmiccore.buffer"), compact(stored) + " / " + compact(cap) + " RF", ROW_BUF, 0xC8B8F0);
        }
        row(font, tm, buffers, tr("console.cosmiccore.instability"), inst + " %", ROW_INST, instColor);
        row(font, tm, buffers, tr("console.cosmiccore.level"), core.getScale() + " / " + core.getScaleMax(), ROW_LEVEL, 0xE0A8FF);
        if (maxOd > 0) row(font, tm, buffers, tr("console.cosmiccore.overdrive"), od.toString().trim(), ROW_OD, 0xFFB040);
        row(font, tm, buffers, tr("console.cosmiccore.field"), Math.round(core.getShield() * 100) + " %", ROW_FIELD, 0x9AD8FF);
    }

    private static String tr(String key, Object... args) {
        return net.minecraft.network.chat.Component.translatable(key, args).getString();
    }

    private static void center(Font font, Matrix4f tm, MultiBufferSource buffers, String text, int y, int color) {
        float x = (PANEL_PX - font.width(text)) / 2.0F;
        font.drawInBatch(text, x, y, 0xFF000000 | color, false, tm, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    private static void row(Font font, Matrix4f tm, MultiBufferSource buffers, String label, String value, int y, int valueColor) {
        font.drawInBatch(label, 6, y, 0xFFA898C8, false, tm, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(value, PANEL_PX - 6 - font.width(value), y, 0xFF000000 | valueColor, false, tm, buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    /** Puffer-Kapazitaet je Baustufe (wie im Controller: ReactorSize.buffer()). */
    /** Controller der Struktur (sitzt in einer SLOT-Zelle). */
    @org.jetbrains.annotations.Nullable
    private static dr4kun3.cosmiccore.block.entity.ControllerBlockEntity controllerOf(Params p) {
        for (ShellCell c : cells(p.size())) {
            if (c.cell() != CorePattern.Cell.SLOT) continue;
            if (p.level().getBlockEntity(p.corePos().offset(c.dx(), c.dy(), c.dz())) instanceof dr4kun3.cosmiccore.block.entity.ControllerBlockEntity ctrl) {
                return ctrl;
            }
        }
        return null;
    }

    /** Puffer-Kapazitaet je Baustufe (auch fuer die Port-Stationen). */
    static long capacity(int structureSize) {
        return ReactorSizeCapacity.capacity(structureSize);
    }

    private static final class ReactorSizeCapacity {
        static long capacity(int structureSize) {
            ReactorSize rs = ReactorSize.bySize(structureSize);
            return rs == null ? dr4kun3.cosmiccore.CoreConfig.BUFFER_PER_TIER.get() : rs.buffer();
        }
    }

    // ------------------------------------------------------------------ Seitenfluegel der Steuerkonsole

    private static boolean isTerminal(Params p, ShellCell c) {
        BlockState s = p.level().getBlockState(p.corePos().offset(c.dx(), c.dy(), c.dz()));
        return s.hasProperty(dr4kun3.cosmiccore.block.ModProperties.TERMINAL) && s.getValue(dr4kun3.cosmiccore.block.ModProperties.TERMINAL);
    }

    /** Ausfahren der Fluegel: erst waechst die Konsole, dann gleiten die Fluegel seitlich heraus (0..1). */
    private static float wingOut(Params p) {
        float k = Mth.clamp((p.settle() - 0.25F) / 0.75F, 0.0F, 1.0F);
        return 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k);
    }

    @org.jetbrains.annotations.Nullable
    private static dr4kun3.cosmiccore.block.entity.PortBlockEntity wingPort(Params p, ShellCell c, int side) {
        BlockPos cpos = p.corePos().offset(c.dx(), c.dy(), c.dz());
        BlockState cs = p.level().getBlockState(cpos);
        if (!cs.hasProperty(dr4kun3.cosmiccore.block.ControllerBlock.FACING)) return null;
        var facing = cs.getValue(dr4kun3.cosmiccore.block.ControllerBlock.FACING);
        BlockPos pos = cpos.relative(side > 0 ? dr4kun3.cosmiccore.block.Terminal.right(facing) : dr4kun3.cosmiccore.block.Terminal.left(facing));
        return p.level().getBlockEntity(pos) instanceof dr4kun3.cosmiccore.block.entity.PortBlockEntity pb ? pb : null;
    }

    private static PortStation.Data stationData(Params p, ShellCell c) {
        BlockPos cpos = p.corePos().offset(c.dx(), c.dy(), c.dz());
        return new PortStation.Data(p.level().getBlockEntity(cpos) instanceof dr4kun3.cosmiccore.block.entity.ControllerBlockEntity ctrl
                ? ctrl : null, p.core(), p.t(), Math.max(0.6F, p.energy()), 0);
    }

    /** Fluegel der Steuerkonsole: dieselben Stationen wie bei einzelnen Ports, seitlich ausgefahren und zur Mitte gedreht. */
    private static void wingsSolid(PoseStack pose, MultiBufferSource buffers, Params p, ShellCell c) {
        float out = wingOut(p);
        if (out <= 0.01F) return;
        float yaw = (float) Math.atan2(c.dx(), c.dz());
        PortStation.Data data = stationData(p, c);
        for (int side = -1; side <= 1; side += 2) {
            var port = wingPort(p, c, side);
            if (port == null) continue;
            boolean energy = side > 0;
            int conn = PortStation.connections(p.level(), port, energy);
            pose.pushPose();
            pose.translate(c.dx(), c.dy(), c.dz());
            pose.mulPose(com.mojang.math.Axis.YP.rotation(yaw));
            pose.translate(side * out, 0, 0);
            PortStation.solid(pose, buffers, p.level(), port.getBlockState(), energy, conn, yaw, -side * 0.28F * out, out, data.withPort(port));
            pose.popPose();
        }
    }

    private static void wingsGlow(Matrix4f km, VertexConsumer vc, ShellCell c, Params p) {
        float out = wingOut(p);
        if (out <= 0.01F) return;
        float yaw = (float) Math.atan2(c.dx(), c.dz());
        Matrix4f cm = consoleFrame(km, c);
        PortStation.Data data = stationData(p, c);
        float e = data.e();
        for (int side = -1; side <= 1; side += 2) {
            var port = wingPort(p, c, side);
            if (port == null) continue;
            boolean energy = side > 0;
            float[] col = energy ? PortStation.ENERGY : PortStation.ITEM;
            int conn = PortStation.connections(p.level(), port, energy);
            float x = side * out;
            PortStation.glow(new Matrix4f(cm).translate(x, 0, 0), vc, energy, conn, yaw, -side * 0.28F * out, out, data.withPort(port));
            // Verbindung zur Konsole: Lichtleitung vom Fluegelsockel zur Mitte
            segment(vc, cm, x, -0.42F, 0, 0, -0.42F, 0, 0.02F, col[0], col[1], col[2], e * 0.7F, e * 0.7F);
            // Lichtimpuls laeuft auf beiden Seiten von der Konsolenmitte nach aussen zum Fluegel
            float ps = Mth.frac(p.t() * 0.04F + (energy ? 0.0F : 0.5F));
            cube(vc, cm, ps * x, -0.42F, 0, 0.022F, 1.0F, 1.0F, 1.0F, e * Mth.sin(ps * Mth.PI));
        }
    }

    private static String compact(long n) {
        if (n >= 1_000_000_000L) return String.format("%.2fG", n / 1e9);
        if (n >= 1_000_000L) return String.format("%.1fM", n / 1e6);
        if (n >= 10_000L) return String.format("%.1fk", n / 1e3);
        return Long.toString(n);
    }

    /** Leuchtender Teil der Konsole: Rahmen mit Eckakzenten, Trennlinien, Balken fuer Puffer und Instabilitaet. */
    private static void console(Matrix4f m, VertexConsumer vc, ShellCell c, Params p, float[] col) {
        Matrix4f cm = consoleFrame(m, c);
        float e = Math.max(0.6F, p.energy());
        float[] cyan = {0.35F, 0.9F, 1.0F};
        float[] warn = lerp(new float[]{0.4F, 1.0F, 0.5F}, CRITICAL, p.heat());
        // Rahmen
        float[][] fr = {screen(-1.02F, -0.02F), screen(1.02F, -0.02F), screen(1.02F, 1.02F), screen(-1.02F, 1.02F)};
        for (int i = 0; i < 4; i++) {
            float[] a = fr[i], b = fr[(i + 1) % 4];
            segment(vc, cm, a[0], a[1], a[2], b[0], b[1], b[2], 0.006F, col[0], col[1], col[2], e * 0.8F, e * 0.8F);
            cube(vc, cm, a[0], a[1], a[2], 0.018F, 1.0F, 1.0F, 1.0F, e);
        }
        // Bodenring des Sockels
        RenderUtil.flatCircle(vc, new Matrix4f(cm).translate(0, -0.49F, 0), 0.44F, 0.012F, 48, col, e * 0.7F);

        Matrix4f tm = textFrame(cm, 0.002F);
        // Trennlinien unter Titel und Status
        segment(vc, tm, 6, 12, 0, PANEL_PX - 6, 12, 0, 0.5F, col[0], col[1], col[2], e * 0.6F, e * 0.6F);
        segment(vc, tm, 6, 25, 0, PANEL_PX - 6, 25, 0, 0.4F, col[0], col[1], col[2], e * 0.35F, e * 0.35F);
        // Balken: Puffer und Instabilitaet (Spur + Fuellung)
        var ctrl = controllerOf(p);
        if (ctrl == null || !ctrl.hasEnergyPort()) bar(vc, tm, BAR_BUF, p.core().getEnergyPct() / 100.0F, cyan, e);
        bar(vc, tm, BAR_INST, p.core().getInstability() / 100.0F, warn, e);
        // feine Scanlinie
        float sv = Mth.frac(p.t() * 0.012F) * 108.0F;
        segment(vc, tm, 2, sv, 0, PANEL_PX - 2, sv, 0, 0.35F, 1.0F, 1.0F, 1.0F, e * 0.12F, e * 0.12F);
    }

    private static void bar(VertexConsumer vc, Matrix4f tm, float y, float frac, float[] col, float e) {
        float x0 = 6, x1 = PANEL_PX - 6;
        segment(vc, tm, x0, y, 0, x1, y, 0, 1.4F, col[0], col[1], col[2], e * 0.15F, e * 0.15F);
        float fill = Mth.clamp(frac, 0.0F, 1.0F);
        if (fill > 0.0F) segment(vc, tm, x0, y, 0, x0 + (x1 - x0) * fill, y, 0, 1.4F, col[0], col[1], col[2], e, e);
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
        BlockState s = p.level().getBlockState(p.corePos().offset(c.dx(), c.dy(), c.dz()));
        // fremde Bloecke (z. B. Rohre an den Andockstellen der Konsole) gehoeren nicht zur Struktur
        if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals(dr4kun3.cosmiccore.CosmicCore.MODID)) {
            return false;
        }
        return true; // auch die Ports verwandeln sich und kehren als Stationen zurueck
    }

    // ------------------------------------------------------------------ Pass 1: Konsole und eingesaugte Bloecke

    public static void solid(PoseStack pose, MultiBufferSource buffers, Params p) {
        Matrix4f m = pose.last().pose();

        // Controller wird zur holografischen Steuerkonsole: Sockel und Bildschirmplatte aus Sternenfeld
        if (p.done()) {
            for (ShellCell c : cells(p.size())) {
                if (c.cell() != CorePattern.Cell.SLOT) continue;
                if (!p.level().getBlockState(p.corePos().offset(c.dx(), c.dy(), c.dz())).is(ModBlocks.CONTROLLER.get())) continue;
                // nach dem Formen waechst die Konsole federnd aus ihrem Sockel heraus
                if (p.settle() <= 0.0F) continue;
                float sc = SingularityCoreBlockEntity.easeOutBack(p.settle());
                pose.pushPose();
                pose.translate(c.dx(), c.dy() - 0.5F, c.dz());
                pose.scale(sc, sc, sc);
                pose.translate(-c.dx(), -(c.dy() - 0.5F), -c.dz());
                Matrix4f cm = consoleFrame(pose.last().pose(), c);
                // Sockel: echte Gehaeuse-Saeule auf einer dunklen Bodenplatte (verdeckt die ausgeblendeten Bloecke darunter)
                pose.pushPose();
                pose.translate(c.dx(), c.dy(), c.dz());
                pose.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.atan2(c.dx(), c.dz())));
                pose.translate(-0.17F, -0.5F, -0.17F);
                pose.scale(0.34F, 0.44F, 0.34F);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(ModBlocks.VOID_CASING.get().defaultBlockState(), pose,
                        buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                pose.popPose();
                VertexConsumer dq = buffers.getBuffer(RenderType.debugQuads());
                disc(dq, new Matrix4f(cm).translate(0, -0.495F, 0), 0.46F, PLATE);
                // undurchsichtiger Bildschirmhintergrund
                float[] b0 = screen(-1.03F, -0.03F), b1 = screen(1.03F, -0.03F), b2 = screen(1.03F, 1.03F), b3 = screen(-1.03F, 1.03F);
                RenderUtil.quad2(dq, cm, b0[0], b0[1], b0[2], b1[0], b1[1], b1[2], b2[0], b2[1], b2[2], b3[0], b3[1], b3[2],
                        PANEL[0], PANEL[1], PANEL[2], 1.0F);
                if (p.settle() > 0.6F) consoleText(cm, buffers, p);
                if (isTerminal(p, c)) wingsSolid(pose, buffers, p, c);
                pose.popPose();
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
                BlockState fly = s.getBlock().defaultBlockState();
                if (s.is(ModBlocks.CONTROLLER.get()) || s.hasProperty(dr4kun3.cosmiccore.block.ModProperties.CONSOLE)) {
                    fly = s.setValue(dr4kun3.cosmiccore.block.ModProperties.PHASE, dr4kun3.cosmiccore.block.CorePhase.DORMANT);
                }
                dispatcher.renderSingleBlock(fly, pose, buffers, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                pose.popPose();
            }
        }
    }

    private static final float[] PLATE = {0.05F, 0.03F, 0.09F};
    private static final float[] PANEL = {0.02F, 0.015F, 0.05F};

    /** Flache, undurchsichtige Scheibe (debugQuads) in der XZ-Ebene. */
    private static void disc(VertexConsumer vc, Matrix4f m, float r, float[] col) {
        int seg = 32;
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            RenderUtil.quad2(vc, m, 0, 0, 0, Mth.cos(a0) * r, 0, Mth.sin(a0) * r, Mth.cos(a1) * r, 0, Mth.sin(a1) * r, 0, 0, 0,
                    col[0], col[1], col[2], 1.0F);
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
        if (p.overdrive() > 0) seam = lerp(seam, OVERDRIVE_SEAM[Math.min(4, p.overdrive()) - 1], 0.8F);
        // Farb-Modul: Naehte und Haltenetz der Kammer in der Kanalfarbe
        seam = ColorOverride.get(dr4kun3.cosmiccore.color.CoreColors.SEAMS, seam);
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

        // Sternbildlinien: nur ausgewaehlte Verbindungen, fein und hell
        float[] lineCol = lerp(seam, new float[]{0.85F, 0.92F, 1.0F}, 0.55F);
        for (int[] l : net.links()) {
            int a = l[0], b = l[1];
            if (hash(a * 7 + b * 13) > 0.1F) continue; // ca. 55 % der Fesseln bilden die Sternbilder
            boolean broken = (danger > 0.2F || strength < 0.6F)
                    && hash(a * 31 + b * 17 + window) > 1.0F - Math.max(danger * 0.45F, (0.6F - strength) * 0.8F);
            if (broken) continue;
            float wave = (float) Math.exp(-Math.pow(home[a].y - waveY, 2) * 18.0);
            float al = base * strength * (0.18F + 0.4F * wave) * pulse;
            Vector3f pa = pos[a], pb = pos[b];
            segment(vc, m, pa.x, pa.y, pa.z, pb.x, pb.y, pb.z, 0.0035F, lineCol[0], lineCol[1], lineCol[2], al, al);
        }

        // Partikel, ihre Fesseln nach innen und feine Zugspuren zum Kern
        for (int i = 0; i < n; i++) {
            Vector3f q = pos[i];
            float wave = (float) Math.exp(-Math.pow(home[i].y - waveY, 2) * 18.0);
            float pushing = push[i] / Math.max(0.01F, R);
            float flick = p.unstable() ? 0.6F + 0.4F * (0.5F + 0.5F * hash(i * 13 + (int) (t * 0.8F))) : 1.0F;
            // Stern: Helligkeit (Groessenklasse) und Funkeln je Partikel
            float mag = 0.3F + 0.7F * (0.5F + 0.5F * hash(i * 211)) * (0.5F + 0.5F * hash(i * 389));
            float twinkle = 0.6F + 0.4F * Mth.sin(t * (0.1F + 0.1F * (0.5F + 0.5F * hash(i * 17))) + i);
            float a = Math.min(1.0F, base * flick * twinkle * (0.45F + 0.55F * wave) * (0.5F + 0.7F * mag));
            float core = 0.01F + 0.018F * mag + 0.03F * pushing;
            cube(vc, m, q.x, q.y, q.z, core, 1.0F, 1.0F, 1.0F, a);
            cube(vc, m, q.x, q.y, q.z, core * 2.4F, seam[0], seam[1], seam[2], a * 0.25F);
            if (mag > 0.55F) {
                // vierzackiger Glanz der hellen Sterne
                float sp = (0.05F + 0.07F * mag) * twinkle;
                segment(vc, m, q.x - sp, q.y, q.z, q.x + sp, q.y, q.z, 0.003F, 1.0F, 1.0F, 1.0F, a * 0.7F, a * 0.7F);
                segment(vc, m, q.x, q.y - sp, q.z, q.x, q.y + sp, q.z, 0.003F, 1.0F, 1.0F, 1.0F, a * 0.7F, a * 0.7F);
                segment(vc, m, q.x, q.y, q.z - sp, q.x, q.y, q.z + sp, 0.003F, 1.0F, 1.0F, 1.0F, a * 0.7F, a * 0.7F);
            }
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
            float st = p.settle();
            float[] bridgeCol = controller ? new float[]{0.8F, 0.6F, 1.0F} : energy ? ENERGY_GLYPH : ITEM_GLYPH;
            if (st < 1.0F) {
                // Materialisieren: Lichtblitz und aufsteigender Scanring am Block
                Matrix4f bm = new Matrix4f(m).translate(c.dx(), c.dy(), c.dz());
                RenderUtil.glowSphere(vc, bm, 0.25F + 0.55F * st, 8, 12, bridgeCol, 0.7F * (1.0F - st));
                RenderUtil.glowSphere(vc, bm, 0.12F + 0.2F * st, 6, 10, new float[]{1, 1, 1}, 0.9F * (1.0F - st));
                RenderUtil.flatCircle(vc, new Matrix4f(bm).translate(0, -0.5F + st, 0), 0.55F, 0.03F, 32, bridgeCol, 1.0F - st);
                if (st <= 0.0F) continue;
            }
            // Lichtbruecke vom Block (Knoten bzw. Port) zur Kugel - waechst beim Materialisieren heraus
            float[] end = {Mth.lerp(st, c.dx(), cpos[0]), Mth.lerp(st, c.dy(), cpos[1]), Mth.lerp(st, c.dz(), cpos[2])};
            segment(vc, m, c.dx(), c.dy(), c.dz(), end[0], end[1], end[2], 0.05F, bridgeCol[0], bridgeCol[1], bridgeCol[2],
                    p.energy() * 0.8F, p.energy() * 0.4F);
            segment(vc, m, c.dx(), c.dy(), c.dz(), end[0], end[1], end[2], 0.015F, 1.0F, 1.0F, 1.0F, p.energy(), p.energy() * 0.6F);
            if (st < 1.0F && !controller) continue; // Port-Symbole erst, wenn die Bruecke steht
            float bs = Mth.frac(p.t() * 0.04F + c.dx() * 0.3F);
            cube(vc, m, Mth.lerp(bs, c.dx(), cpos[0]), Mth.lerp(bs, c.dy(), cpos[1]), Mth.lerp(bs, c.dz(), cpos[2]),
                    0.05F, 1.0F, 1.0F, 1.0F, p.energy() * Mth.sin(bs * Mth.PI));
            if (controller) {
                if (st >= 1.0F) circle(m, vc, cpos, east, north, 0.32F, new float[]{0.8F, 0.6F, 1.0F}, p.energy() * pulse);
                // Konsolenrahmen waechst mit der Konsole (gleiche Skalierung wie im deckenden Pass)
                float sc = SingularityCoreBlockEntity.easeOutBack(st);
                Matrix4f km = new Matrix4f(m).translate(c.dx(), c.dy() - 0.5F, c.dz()).scale(sc)
                        .translate(-c.dx(), -(c.dy() - 0.5F), -c.dz());
                console(km, vc, c, p, bridgeCol);
                if (isTerminal(p, c)) wingsGlow(km, vc, c, p);
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
