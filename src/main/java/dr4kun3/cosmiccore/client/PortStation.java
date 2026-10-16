package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.cube;
import static dr4kun3.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dr4kun3.cosmiccore.CosmicCore;
import dr4kun3.cosmiccore.block.CorePhase;
import dr4kun3.cosmiccore.block.ModBlocks;
import dr4kun3.cosmiccore.block.ModProperties;
import dr4kun3.cosmiccore.block.entity.ControllerBlockEntity;
import dr4kun3.cosmiccore.block.entity.PortBlockEntity;
import dr4kun3.cosmiccore.block.entity.SingularityCoreBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Port einer gebildeten Struktur als kleine Station: Sockel, geneigter Holo-Bildschirm mit Live-Daten und
 * Anschlussknoten - aber nur dort, wo wirklich etwas angedockt ist (Rohr, Kabel, Truhe ... aus beliebigen Mods).
 * Einzelne Ports zeichnet der PortRenderer, die Fluegel der Steuerkonsole der ReactorShellRenderer.
 * Koordinaten: Ursprung in der Blockmitte, +Z zeigt nach vorn (zum Betrachter), +X nach rechts.
 */
public final class PortStation {
    private PortStation() {}

    public static final float[] ENERGY = {0.35F, 0.95F, 1.0F};
    public static final float[] ITEM = {1.0F, 0.6F, 0.2F};
    private static final float[] PLATE = {0.05F, 0.03F, 0.09F};
    private static final float[] PANEL = {0.02F, 0.015F, 0.05F};
    private static final float TEXT_SCALE = 0.0052F;
    private static final float PX = 0.68F / TEXT_SCALE; // Bildschirmbreite in Schriftpixeln
    private static final int ROW_TITLE = 3, ROW_A = 18, ROW_B = 30, ROW_C = 42, BAR = 54;

    /** Daten fuer die Anzeige (Controller und Kern koennen auf dem Client kurz fehlen). */
    public record Data(@Nullable ControllerBlockEntity ctrl, @Nullable SingularityCoreBlockEntity core, float t, float e, int used) {
        /** Daten fuer einen bestimmten Port (belegte Lagerplaetze beim Item-Port). */
        public Data withPort(@Nullable PortBlockEntity port) {
            return new Data(ctrl, core, t, e, port instanceof dr4kun3.cosmiccore.block.entity.ItemPortBlockEntity ip ? ip.getClientUsed() : 0);
        }
    }

    // ------------------------------------------------------------------ Anschluesse

    /** Bitmaske der Weltseiten (Direction.get3DDataValue), an denen etwas angedockt ist - zwischengespeichert. */
    public static int connections(Level level, PortBlockEntity be, boolean energy) {
        long now = level.getGameTime();
        if (now - be.clientConnTick >= 10 || be.clientConnTick == Long.MIN_VALUE) {
            int mask = 0;
            for (Direction d : Direction.values()) {
                if (connected(level, be.getBlockPos(), d, energy)) mask |= 1 << d.get3DDataValue();
            }
            be.clientConnMask = mask;
            be.clientConnTick = now;
        }
        return be.clientConnMask;
    }

    /** Angedockt: ein Block mit passender Schnittstelle (Energie bzw. Items) oder ein Block einer anderen Mod. */
    private static boolean connected(Level level, BlockPos port, Direction d, boolean energy) {
        BlockPos n = port.relative(d);
        BlockState s = level.getBlockState(n);
        if (s.isAir()) return false;
        String ns = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace();
        if (ns.equals(CosmicCore.MODID)) return false; // eigene Strukturbloecke
        boolean cap = energy ? level.getCapability(Capabilities.Energy.BLOCK, n, d.getOpposite()) != null
                : level.getCapability(Capabilities.Item.BLOCK, n, d.getOpposite()) != null;
        return cap || !ns.equals("minecraft");
    }

    // ------------------------------------------------------------------ Pass 1: Koerper, Bildschirm, Text

    /**
     * pose: Ursprung Blockmitte, bereits zur Station gedreht (yaw = Drehung lokal -> Welt um Y).
     * screenYaw dreht nur den Bildschirm (Fluegel schauen zur Konsolenmitte).
     */
    public static void solid(PoseStack pose, MultiBufferSource buffers, Level level, BlockState port, boolean energy, int connMask,
                             float yaw, float screenYaw, float grow, Data data, boolean ownBase, boolean screen) {
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        // Sockel
        pose.pushPose();
        pose.translate(-0.14F, -0.5F, -0.14F);
        pose.scale(0.28F, 0.4F * grow, 0.28F);
        dispatcher.renderSingleBlock(ModBlocks.VOID_CASING.get().defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY);
        pose.popPose();
        // Anschlussknoten an jeder belegten Seite
        CorePhase phase = port.hasProperty(ModProperties.PHASE) ? port.getValue(ModProperties.PHASE) : CorePhase.FORMED;
        if (phase == CorePhase.DORMANT) phase = CorePhase.FORMED;
        var model = Minecraft.getInstance().getModelManager().getStandaloneModel(PortRenderer.plugModel(energy, phase));
        for (Direction d : Direction.values()) {
            if ((connMask & (1 << d.get3DDataValue())) == 0) continue;
            Vector3f c = connector(d, yaw);
            pose.pushPose();
            pose.translate(c.x, c.y, c.z);
            pose.scale(0.36F * grow, 0.36F * grow, 0.36F * grow);
            pose.translate(-0.5F, -0.5F, -0.5F);
            net.minecraft.client.renderer.block.ModelBlockRenderer.renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), model,
                    1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        Matrix4f base = pose.last().pose();
        // Bodenfeld wie ein Portal (bei den Konsolenfluegeln zeichnet die Konsole ein gemeinsames Feld)
        if (ownBase) portalCapsule(buffers, new Matrix4f(base).translate(0, -0.495F, 0), 0.0F, 0.34F * Math.max(0.05F, grow));
        VertexConsumer dq = buffers.getBuffer(RenderType.debugQuads());
        if (!screen) return; // Port direkt am Controller: seine Anzeige liegt auf dem breiten Konsolenbildschirm
        Matrix4f sm = new Matrix4f(base).rotateY(screenYaw).translate(0, (1.0F - grow) * -0.4F, 0);
        float[] b0 = screen(-1.04F, -0.04F), b1 = screen(1.04F, -0.04F), b2 = screen(1.04F, 1.04F), b3 = screen(-1.04F, 1.04F);
        RenderUtil.quad2(dq, sm, b0[0], b0[1], b0[2], b1[0], b1[1], b1[2], b2[0], b2[1], b2[2], b3[0], b3[1], b3[2],
                PANEL[0], PANEL[1], PANEL[2], 1.0F);
        if (grow > 0.7F) text(sm, buffers, level, energy, data);
    }

    /** Lage des Anschlussknotens fuer eine Weltseite im lokalen Rahmen. */
    private static Vector3f connector(Direction d, float yaw) {
        if (d == Direction.UP) return new Vector3f(0.0F, 0.32F, -0.28F);
        if (d == Direction.DOWN) return new Vector3f(0.0F, -0.34F, 0.0F);
        Vector3f v = new Vector3f(d.getStepX(), 0, d.getStepZ()).rotateY(-yaw);
        return new Vector3f(v.x * 0.32F, -0.27F, v.z * 0.32F);
    }

    private static float[] screen(float u, float v) {
        return new float[]{u * 0.34F, -0.04F + v * 0.42F, 0.08F - v * 0.16F};
    }

    private static Matrix4f textFrame(Matrix4f sm, float lift) {
        float tilt = (float) Math.atan2(0.16, 0.42);
        float[] tl = screen(-1, 1);
        return new Matrix4f(sm).translate(tl[0], tl[1], tl[2]).rotateX(-tilt).translate(0, 0, lift).scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
    }

    private static void text(Matrix4f sm, MultiBufferSource buffers, Level level, boolean energy, Data d) {
        Font font = Minecraft.getInstance().font;
        Matrix4f tm = textFrame(sm, 0.004F);
        center(font, tm, buffers, "◆ " + tr(energy ? "console.cosmiccore.wing_energy" : "console.cosmiccore.wing_items") + " ◆",
                ROW_TITLE, energy ? 0x7AE8FF : 0xFFC070);
        if (energy) {
            if (d.core() == null) return;
            long cap = ReactorShellRenderer.capacity(d.core().getStructureSize());
            row(font, tm, buffers, tr("console.cosmiccore.output"), compact(d.core().getOutput()) + " RF/t", ROW_A, 0x7AE8FF);
            row(font, tm, buffers, tr("console.cosmiccore.buffer"), d.core().getEnergyPct() + " %", ROW_B, 0xC8B8F0);
            row(font, tm, buffers, tr("console.cosmiccore.capacity"), compact(cap) + " RF", ROW_C, 0xA898C8);
            return;
        }
        // Item-Port: einzige Angabe sind die belegten Plaetze seines Lagers
        int slots = dr4kun3.cosmiccore.block.entity.ItemPortBlockEntity.STORAGE_SLOTS;
        row(font, tm, buffers, tr("console.cosmiccore.slots"), d.used() + " / " + slots, ROW_B - 4, d.used() >= slots ? 0xFF6060 : 0xFFC070);
    }

    /** Zeile "Bezeichnung  [Symbol] Anzahl · Restzeit" - auch fuer den Controller-Bildschirm. */
    static void itemRow(Font font, Matrix4f tm, MultiBufferSource buffers, Level level, String label, ItemStack stack, int seconds,
                        int y, int color, float width) {
        String value = (stack.isEmpty() ? "0" : Integer.toString(stack.getCount())) + " · " + duration(seconds);
        font.drawInBatch(label, 5, y, 0xFFA898C8, false, tm, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        float vx = width - 5 - font.width(value);
        font.drawInBatch(value, vx, y, 0xFF000000 | color, false, tm, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        if (!stack.isEmpty()) icon(tm, buffers, level, stack, vx - 11, y);
    }

    static void icon(Matrix4f tm, MultiBufferSource buffers, Level level, ItemStack stack, float x, float y) {
        PoseStack ps = new PoseStack();
        ps.last().pose().set(tm);
        // gleichmaessig skaliert (sonst kippen die Normalen und das Symbol wird schwarz), leicht vor der Glasflaeche
        ps.translate(x + 4, y + 3.5F, 1.0F);
        ps.scale(8.0F, -8.0F, 8.0F);
        if (!(buffers instanceof dr4kun3.cosmiccore.client.render.DeferredBuffers d)) return;
        var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, level, null, 0);
        state.submit(ps, d.collector(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
    }

    /** Restzeit kompakt: 45s, 12m30s, 3h05m - oder leer/pausiert/aus. */
    public static String duration(int seconds) {
        if (seconds == ControllerBlockEntity.TIME_PAUSED) return tr("console.cosmiccore.paused");
        if (seconds == ControllerBlockEntity.TIME_OFF) return tr("console.cosmiccore.off");
        if (seconds < 0) return "—";
        if (seconds == 0) return tr("console.cosmiccore.empty");
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m" + String.format("%02d", seconds % 60) + "s";
        if (seconds < 360000) return (seconds / 3600) + "h" + String.format("%02d", (seconds / 60) % 60) + "m";
        return "99h+";
    }

    private static void center(Font font, Matrix4f tm, MultiBufferSource buffers, String text, int y, int color) {
        font.drawInBatch(text, (PX - font.width(text)) / 2.0F, y, 0xFF000000 | color, false, tm, buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    private static void row(Font font, Matrix4f tm, MultiBufferSource buffers, String label, String value, int y, int color) {
        font.drawInBatch(label, 5, y, 0xFFA898C8, false, tm, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(value, PX - 5 - font.width(value), y, 0xFF000000 | color, false, tm, buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    // ------------------------------------------------------------------ Pass 2: Licht

    public static void glow(Matrix4f base, VertexConsumer vc, boolean energy, int connMask, float yaw, float screenYaw, float grow, Data d,
                            boolean ownBase, boolean screen) {
        float[] col = energy ? ENERGY : ITEM;
        float e = d.e() * grow;
        if (ownBase) capsuleRim(vc, new Matrix4f(base).translate(0, -0.49F, 0), 0.0F, 0.34F * grow, col, e * 0.7F);
        connectorGlow(base, vc, energy, connMask, yaw, e, d);
        if (!screen) return;
        Matrix4f sm = new Matrix4f(base).rotateY(screenYaw).translate(0, (1.0F - grow) * -0.4F, 0);
        float[][] fr = {screen(-1.03F, -0.03F), screen(1.03F, -0.03F), screen(1.03F, 1.03F), screen(-1.03F, 1.03F)};
        for (int i = 0; i < 4; i++) {
            float[] a = fr[i], b = fr[(i + 1) % 4];
            segment(vc, sm, a[0], a[1], a[2], b[0], b[1], b[2], 0.005F, col[0], col[1], col[2], e * 0.8F, e * 0.8F);
            cube(vc, sm, a[0], a[1], a[2], 0.015F, 1.0F, 1.0F, 1.0F, e);
        }
        Matrix4f tm = textFrame(sm, 0.002F);
        segment(vc, tm, 5, 12, 0, PX - 5, 12, 0, 0.5F, col[0], col[1], col[2], e * 0.6F, e * 0.6F);
        if (energy && d.core() != null) {
            float fill = Mth.clamp(d.core().getEnergyPct() / 100.0F, 0, 1);
            segment(vc, tm, 5, BAR, 0, PX - 5, BAR, 0, 1.4F, col[0], col[1], col[2], e * 0.15F, e * 0.15F);
            if (fill > 0) segment(vc, tm, 5, BAR, 0, 5 + (PX - 10) * fill, BAR, 0, 1.4F, col[0], col[1], col[2], e, e);
        } else if (!energy) {
            // Fuellstand des Lagers: 27 Kaestchen, belegte leuchten
            int slots = dr4kun3.cosmiccore.block.entity.ItemPortBlockEntity.STORAGE_SLOTS;
            float cw = (PX - 10) / (float) slots;
            for (int k = 0; k < slots; k++) {
                float x0 = 5 + k * cw + 0.6F, x1 = 5 + (k + 1) * cw - 0.6F;
                boolean full = k < d.used();
                float[] c = full && d.used() >= slots ? new float[]{1.0F, 0.35F, 0.35F} : col;
                segment(vc, tm, x0, BAR - 8, 0, x1, BAR - 8, 0, 3.0F, c[0], c[1], c[2], full ? e : e * 0.15F, full ? e : e * 0.15F);
            }
        }
        float sv = Mth.frac(d.t() * 0.015F + (energy ? 0.5F : 0.0F)) * 62.0F;
        segment(vc, tm, 2, sv, 0, PX - 2, sv, 0, 0.35F, 1.0F, 1.0F, 1.0F, e * 0.1F, e * 0.1F);
    }

    /** Leitungen von den Anschlussknoten zum Sockel, mit Fluss (Energie hinaus, Items hinein). */
    private static void connectorGlow(Matrix4f base, VertexConsumer vc, boolean energy, int connMask, float yaw, float e, Data d) {
        float[] col = energy ? ENERGY : ITEM;
        for (Direction dir : Direction.values()) {
            if ((connMask & (1 << dir.get3DDataValue())) == 0) continue;
            Vector3f c = connector(dir, yaw);
            Vector3f to = new Vector3f(0, Math.min(c.y, -0.15F), 0);
            segment(vc, base, c.x, c.y, c.z, to.x, to.y, to.z, 0.015F, col[0], col[1], col[2], e * 0.7F, e * 0.7F);
            float s = Mth.frac(d.t() * 0.05F + dir.ordinal() * 0.17F);
            float ps = energy ? 1.0F - s : s;
            cube(vc, base, Mth.lerp(ps, c.x, to.x), Mth.lerp(ps, c.y, to.y), Mth.lerp(ps, c.z, to.z), 0.02F, 1, 1, 1, e * Mth.sin(ps * Mth.PI));
        }
    }

    // ------------------------------------------------------------------ Portalfeld

    /**
     * Schwarzes Portalfeld (End-Portal-Sternenhimmel) in der XZ-Ebene: Kapsel von -halfLen bis +halfLen entlang X mit
     * Radius r (halfLen = 0: runde Scheibe). Verbindet Controller und Ports der Steuerkonsole zu einer Flaeche.
     */
    public static void portalCapsule(MultiBufferSource buffers, Matrix4f m, float halfLen, float r) {
        VertexConsumer vc = buffers.getBuffer(RenderType.endPortal());
        int seg = 16;
        // Mittelteil
        if (halfLen > 0) twoSided(vc, m, -halfLen, -r, halfLen, -r, halfLen, r, -halfLen, r);
        // halbrunde Enden
        for (int end = -1; end <= 1; end += 2) {
            float cx = end * halfLen;
            for (int i = 0; i < seg; i++) {
                float a0 = -Mth.HALF_PI + Mth.PI * i / seg, a1 = -Mth.HALF_PI + Mth.PI * (i + 1) / seg;
                float x0 = cx + end * Mth.cos(a0) * r, z0 = Mth.sin(a0) * r, x1 = cx + end * Mth.cos(a1) * r, z1 = Mth.sin(a1) * r;
                twoSided(vc, m, cx, 0, x0, z0, x1, z1, cx, 0);
            }
        }
    }

    /** Leuchtender Rand des Portalfelds. */
    public static void capsuleRim(VertexConsumer vc, Matrix4f m, float halfLen, float r, float[] col, float a) {
        if (r <= 0.001F) return;
        if (halfLen > 0) {
            segment(vc, m, -halfLen, 0, -r, halfLen, 0, -r, 0.012F, col[0], col[1], col[2], a, a);
            segment(vc, m, -halfLen, 0, r, halfLen, 0, r, 0.012F, col[0], col[1], col[2], a, a);
        }
        int seg = 20;
        for (int end = -1; end <= 1; end += 2) {
            float cx = end * halfLen;
            for (int i = 0; i < seg; i++) {
                float a0 = -Mth.HALF_PI + Mth.PI * i / seg, a1 = -Mth.HALF_PI + Mth.PI * (i + 1) / seg;
                segment(vc, m, cx + end * Mth.cos(a0) * r, 0, Mth.sin(a0) * r, cx + end * Mth.cos(a1) * r, 0, Mth.sin(a1) * r,
                        0.012F, col[0], col[1], col[2], a, a);
            }
        }
    }

    private static void twoSided(VertexConsumer vc, Matrix4f m, float x0, float z0, float x1, float z1, float x2, float z2, float x3, float z3) {
        vc.addVertex(m, x0, 0, z0);
        vc.addVertex(m, x1, 0, z1);
        vc.addVertex(m, x2, 0, z2);
        vc.addVertex(m, x3, 0, z3);
        vc.addVertex(m, x3, 0, z3);
        vc.addVertex(m, x2, 0, z2);
        vc.addVertex(m, x1, 0, z1);
        vc.addVertex(m, x0, 0, z0);
    }

    // ------------------------------------------------------------------ Hilfen

    private static void disc(VertexConsumer vc, Matrix4f m, float r) {
        int seg = 32;
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            RenderUtil.quad2(vc, m, 0, 0, 0, Mth.cos(a0) * r, 0, Mth.sin(a0) * r, Mth.cos(a1) * r, 0, Mth.sin(a1) * r, 0, 0, 0,
                    PLATE[0], PLATE[1], PLATE[2], 1.0F);
        }
    }

    static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    static String compact(long n) {
        if (n >= 1_000_000_000L) return String.format("%.2fG", n / 1e9);
        if (n >= 1_000_000L) return String.format("%.1fM", n / 1e6);
        if (n >= 10_000L) return String.format("%.1fk", n / 1e3);
        return Long.toString(n);
    }
}
