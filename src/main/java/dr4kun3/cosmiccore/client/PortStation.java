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
import dr4kun3.cosmiccore.block.entity.EnergyPortBlockEntity;
import dr4kun3.cosmiccore.block.entity.ItemPortBlockEntity;
import dr4kun3.cosmiccore.block.entity.PortBlockEntity;
import dr4kun3.cosmiccore.block.entity.SingularityCoreBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import dr4kun3.cosmiccore.client.render.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
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
    /** Nur statische Zeichenroutinen - keine Instanzen. */
    private PortStation() {}

    public static final float[] ENERGY = {0.35F, 0.95F, 1.0F};
    public static final float[] ITEM = {1.0F, 0.6F, 0.2F};
    private static final float[] PLATE = {0.05F, 0.03F, 0.09F};
    private static final float[] PANEL = {0.02F, 0.015F, 0.05F};
    private static final float TEXT_SCALE = 0.0052F;
    private static final float PX = 0.68F / TEXT_SCALE; // Bildschirmbreite in Schriftpixeln
    private static final int ROW_TITLE = 3, ROW_A = 18, ROW_B = 30, ROW_C = 42, BAR = 54;

    /** Daten fuer die Anzeige (Controller und Kern koennen auf dem Client kurz fehlen). */
    public record Data(@Nullable ControllerBlockEntity ctrl, @Nullable SingularityCoreBlockEntity core, float t, float e,
                       @Nullable PortBlockEntity port) {
        /**
         * Daten fuer einen bestimmten Port (Lagerinhalt beim Item-Port, Durchfluss beim Energie-Port).
         *
         * @param port der Port oder {@code null}
         * @return neue Daten fuer diesen Port
         */
        public Data withPort(@Nullable PortBlockEntity port) {
            return new Data(ctrl, core, t, e, port);
        }

        /**
         * Belegte Lagerplaetze (nur beim Item-Port).
         *
         * @return Anzahl 0..27
         */
        public int used() {
            return port instanceof ItemPortBlockEntity ip ? ip.getClientUsed() : 0;
        }
    }

    /** Groesse eines Lagerplatzes im Raster in Schriftpixeln und Oberkante des Rasters. */
    static final float CELL = 13.0F, GRID_Y = 15.0F;

    /**
     * Werte des Energie-Ports: Durchfluss, gespeicherte Energie und Kapazitaet - vom Port gemessen, sonst aus dem Kern
     * geschaetzt.
     *
     * @param port der Port oder {@code null}
     * @param core der Kern oder {@code null}
     * @return {Durchfluss RF/t, gespeichert RF, Kapazitaet RF}
     */
    static long[] energyValues(@Nullable PortBlockEntity port, @Nullable SingularityCoreBlockEntity core) {
        if (port instanceof EnergyPortBlockEntity ep && ep.getClientCapacity() > 0) {
            return new long[]{ep.getClientFlow(), ep.getClientStored(), ep.getClientCapacity()};
        }
        if (core == null) return new long[]{0, 0, 0};
        long cap = ReactorShellRenderer.capacity(core.getStructureSize());
        return new long[]{0, Math.round(cap * core.getEnergyPct() / 100.0), cap};
    }

    /**
     * Lagerraster: 3 Reihen zu 9 Plaetzen mit den echten Items und ihrer Anzahl.
     *
     * @param font Schrift
     * @param tm Matrix fuer die Schrift
     * @param buffers Puffer
     * @param level Welt
     * @param port der Item-Port oder {@code null}
     * @param x0 linke Kante des Rasters in Schriftpixeln
     * @param y0 Oberkante des Rasters in Schriftpixeln
     * @param cell Kantenlaenge eines Platzes in Schriftpixeln
     */
    static void itemGrid(Font font, Matrix4f tm, MultiBufferSource buffers, Level level, @Nullable PortBlockEntity port, float x0, float y0,
                         float cell) {
        if (!(port instanceof ItemPortBlockEntity ip)) return;
        for (int k = 0; k < ItemPortBlockEntity.STORAGE_SLOTS; k++) {
            ItemStack stack = ip.getClientStack(k);
            if (stack.isEmpty()) continue;
            float cx = x0 + (k % 9) * cell, cy = y0 + (k / 9) * cell, size = cell * 0.62F;
            icon(tm, buffers, level, stack, cx + cell / 2, cy + cell / 2, size);
            if (stack.getCount() <= 1) continue;
            String n = stack.getCount() < 1000 ? Integer.toString(stack.getCount()) : stack.getCount() / 1000 + "k";
            float ns = cell / 26.0F;
            Matrix4f nm = new Matrix4f(tm).translate(cx + cell - 0.8F, cy + cell - 9.2F * ns, size * 0.75F).scale(ns, ns, ns);
            dr4kun3.cosmiccore.client.render.WorldText.draw(font, n, -font.width(n), 0, 0xFFFFFFFF, false, nm, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, net.minecraft.util.LightCoordsUtil.FULL_BRIGHT);
        }
    }

    /**
     * Leuchtende Rahmen des Lagerrasters: freie Plaetze schwach, belegte hell, ein volles Lager rot.
     *
     * @param vc Puffer
     * @param tm Matrix fuer die Schrift
     * @param port der Item-Port oder {@code null}
     * @param x0 linke Kante des Rasters in Schriftpixeln
     * @param y0 Oberkante des Rasters in Schriftpixeln
     * @param cell Kantenlaenge eines Platzes in Schriftpixeln
     * @param col Farbe RGB 0..1
     * @param a Deckkraft
     */
    static void itemGridGlow(VertexConsumer vc, Matrix4f tm, @Nullable PortBlockEntity port, float x0, float y0, float cell, float[] col,
                             float a) {
        ItemPortBlockEntity ip = port instanceof ItemPortBlockEntity i ? i : null;
        int slots = ItemPortBlockEntity.STORAGE_SLOTS;
        boolean full = ip != null && ip.getClientUsed() >= slots;
        float[] c = full ? new float[]{1.0F, 0.35F, 0.35F} : col;
        for (int k = 0; k < slots; k++) {
            boolean used = ip != null && !ip.getClientStack(k).isEmpty();
            float al = used ? a * 0.7F : a * 0.18F;
            float bx = x0 + (k % 9) * cell + 0.6F, by = y0 + (k / 9) * cell + 0.6F, bw = cell - 1.2F;
            segment(vc, tm, bx, by, 0, bx + bw, by, 0, 0.3F, c[0], c[1], c[2], al, al);
            segment(vc, tm, bx + bw, by, 0, bx + bw, by + bw, 0, 0.3F, c[0], c[1], c[2], al, al);
            segment(vc, tm, bx + bw, by + bw, 0, bx, by + bw, 0, 0.3F, c[0], c[1], c[2], al, al);
            segment(vc, tm, bx, by + bw, 0, bx, by, 0, 0.3F, c[0], c[1], c[2], al, al);
        }
    }

    // ------------------------------------------------------------------ Anschluesse

    /**
     * Seiten, an denen etwas angedockt ist - wird alle halbe Sekunde neu ermittelt.
     *
     * @param level Welt
     * @param be der Port
     * @param energy {@code true} fuer den Energie-Port
     * @return Bitmaske der Weltseiten (Bit = {@code Direction.get3DDataValue()})
     */
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

    /**
     * Angedockt: ein Block mit passender Schnittstelle (Energie bzw. Items) oder ein Block einer anderen Mod.
     *
     * @param level Welt
     * @param port Position des Ports
     * @param d zu pruefende Seite
     * @param energy {@code true} fuer den Energie-Port
     * @return {@code true}, wenn dort etwas angeschlossen ist
     */
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
     * Feste Teile einer Station: Sockel, Anschlussknoten, Bildschirmrahmen und Schrift.
     *
     * @param pose Pose-Stapel mit Ursprung in der Blockmitte, bereits zur Station gedreht
     * @param buffers Puffer
     * @param level Welt
     * @param port Blockzustand des Ports (Phase)
     * @param energy {@code true} fuer den Energie-Port
     * @param connMask Bitmaske der angedockten Seiten
     * @param yaw Drehung lokal -> Welt um die Hochachse (Bogenmass)
     * @param screenYaw zusaetzliche Drehung nur des Bildschirms (Fluegel schauen zur Konsolenmitte)
     * @param grow Einblenden 0..1 (waechst aus dem Sockel)
     * @param data angezeigte Werte
     * @param ownBase eigenen Sockel zeichnen (nicht bei Konsolen-Fluegeln)
     * @param screen Bildschirm zeichnen
     */
    public static void solid(PoseStack pose, MultiBufferSource buffers, Level level, BlockState port, boolean energy, int connMask,
                             float yaw, float screenYaw, float grow, Data data, boolean ownBase, boolean screen) {
        // Sockel
        pose.pushPose();
        pose.translate(-0.14F, -0.5F, -0.14F);
        pose.scale(0.28F, 0.4F * grow, 0.28F);
        dr4kun3.cosmiccore.client.render.BlockQuads.render(ModBlocks.VOID_CASING.get().defaultBlockState(), pose, buffers, net.minecraft.util.LightCoordsUtil.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY); // wie bis 1.21.11: feste Flaechen bleiben unsichtbar
        pose.popPose();
        // Anschluss an jeder belegten Seite: flacher Stecker genau in der Mitte der Blockseite, dazu ein Flachbandkabel,
        // das unter dem Bildschirm aus dem Sockel kommt und aussen um ihn herum zum Stecker biegt
        CorePhase phase = port.hasProperty(ModProperties.PHASE) ? port.getValue(ModProperties.PHASE) : CorePhase.FORMED;
        if (phase == CorePhase.DORMANT) phase = CorePhase.FORMED;
        var model = Minecraft.getInstance().getModelManager().getStandaloneModel(PortRenderer.plugModel(energy, phase));
        VertexConsumer ribbon = buffers.getBuffer(CosmicRenderTypes.solidColor());
        for (Direction d : Direction.values()) {
            if ((connMask & (1 << d.get3DDataValue())) == 0) continue;
            ribbonSolid(ribbon, pose.last().pose(), d, yaw, grow);
            Vector3f c = connector(d, yaw);
            pose.pushPose();
            pose.translate(c.x, c.y, c.z);
            // Modell zeigt nach Sueden (+Z): auf die Blockseite drehen, gleichmaessig skalieren (keine Verzerrung)
            if (d == Direction.UP) pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F));
            else if (d == Direction.DOWN) pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));
            else {
                Vector3f v = side(d, yaw);
                pose.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.atan2(v.x, v.z)));
            }
            pose.scale(0.34F * grow, 0.34F * grow, 0.34F * grow);
            pose.translate(-0.5F, -0.5F, -0.5F);
            dr4kun3.cosmiccore.client.render.BlockQuads.render(pose.last(), buffers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.cutoutMovingBlock()), model, net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        Matrix4f base = pose.last().pose();
        // Bodenfeld wie ein Portal (bei den Konsolenfluegeln zeichnet die Konsole ein gemeinsames Feld)
        if (ownBase) portalCapsule(buffers, new Matrix4f(base).translate(0, -0.495F, 0), 0.0F, 0.34F * Math.max(0.05F, grow));
        VertexConsumer dq = buffers.getBuffer(CosmicRenderTypes.solidColor());
        if (!screen) return; // Port direkt am Controller: seine Anzeige liegt auf dem breiten Konsolenbildschirm
        Matrix4f sm = new Matrix4f(base).rotateY(screenYaw).translate(0, (1.0F - grow) * -0.4F, 0);
        float[] b0 = screen(-1.04F, -0.04F), b1 = screen(1.04F, -0.04F), b2 = screen(1.04F, 1.04F), b3 = screen(-1.04F, 1.04F);
        RenderUtil.quad2(dq, sm, b0[0], b0[1], b0[2], b1[0], b1[1], b1[2], b2[0], b2[1], b2[2], b3[0], b3[1], b3[2],
                PANEL[0], PANEL[1], PANEL[2], 1.0F);
        if (grow > 0.7F) text(sm, buffers, level, energy, data);
    }

    /**
     * Lage des Anschlussknotens fuer eine Weltseite im lokalen Rahmen der Station.
     *
     * @param d Weltseite
     * @param yaw Drehung der Station (Bogenmass)
     * @return Position relativ zur Blockmitte
     */
    private static Vector3f connector(Direction d, float yaw) {
        if (d == Direction.UP) return new Vector3f(0.0F, PLUG_AT, 0.0F);
        if (d == Direction.DOWN) return new Vector3f(0.0F, -PLUG_AT, 0.0F);
        Vector3f v = side(d, yaw);
        return new Vector3f(v.x * PLUG_AT, 0.0F, v.z * PLUG_AT);
    }

    /**
     * Waagrechte Weltseite im lokalen Rahmen der Station.
     *
     * @param d Weltseite (nicht oben/unten)
     * @param yaw Drehung der Station (Bogenmass)
     * @return Einheitsvektor in der XZ-Ebene
     */
    private static Vector3f side(Direction d, float yaw) {
        return new Vector3f(d.getStepX(), 0, d.getStepZ()).rotateY(-yaw);
    }

    /**
     * Verlauf des Flachbandkabels vom Sockel zum Stecker (unten aus dem Sockel, unter dem Bildschirm hindurch, aussen
     * herum und mit weichem Bogen in den Stecker).
     *
     * @param d Weltseite
     * @param yaw Drehung der Station (Bogenmass)
     * @return Punkte des Verlaufs (leer fuer unten - dort sitzt der Stecker direkt unter dem Sockel)
     */
    private static Vector3f[] ribbonPath(Direction d, float yaw) {
        if (d == Direction.DOWN) return new Vector3f[0];
        if (d == Direction.UP) {
            // hinter dem Bildschirm hinauf und oben nach vorn zur Mitte der Oberseite
            return new Vector3f[]{new Vector3f(0, -0.26F, -0.14F), new Vector3f(0, -0.26F, -0.3F), new Vector3f(0, -0.2F, -0.36F),
                    new Vector3f(0, 0.36F, -0.36F), new Vector3f(0, 0.42F, -0.32F), new Vector3f(0, 0.45F, -0.22F),
                    new Vector3f(0, 0.45F, -0.14F)};
        }
        Vector3f v = side(d, yaw);
        float[][] p = {{0.14F, -0.26F}, {0.36F, -0.26F}, {0.42F, -0.245F}, {0.455F, -0.21F}, {0.465F, -0.17F}, {0.465F, -0.14F}};
        Vector3f[] out = new Vector3f[p.length];
        for (int i = 0; i < p.length; i++) out[i] = new Vector3f(v.x * p[i][0], p[i][1], v.z * p[i][0]);
        return out;
    }

    /**
     * Querachse des Flachbandes: waagrecht und quer zum Verlauf.
     *
     * @param d Weltseite
     * @param yaw Drehung der Station (Bogenmass)
     * @return Einheitsvektor
     */
    private static Vector3f ribbonAcross(Direction d, float yaw) {
        if (d.getAxis() == Direction.Axis.Y) return new Vector3f(1, 0, 0);
        Vector3f v = side(d, yaw);
        return new Vector3f(-v.z, 0, v.x);
    }

    /**
     * Flachbandkabel als dunkles Band (beidseitig sichtbar).
     *
     * @param vc Puffer (deckende Farbe)
     * @param m Matrix der Station
     * @param d Weltseite
     * @param yaw Drehung der Station (Bogenmass)
     * @param grow Einblenden 0..1
     */
    private static void ribbonSolid(VertexConsumer vc, Matrix4f m, Direction d, float yaw, float grow) {
        Vector3f[] path = ribbonPath(d, yaw);
        if (path.length < 2 || grow < 0.3F) return;
        Vector3f w = ribbonAcross(d, yaw).mul(RIBBON_HALF * grow);
        for (int i = 0; i + 1 < path.length; i++) {
            Vector3f a = path[i], b = path[i + 1];
            RenderUtil.quad2(vc, m, a.x - w.x, a.y - w.y, a.z - w.z, a.x + w.x, a.y + w.y, a.z + w.z,
                    b.x + w.x, b.y + w.y, b.z + w.z, b.x - w.x, b.y - w.y, b.z - w.z, RIBBON[0], RIBBON[1], RIBBON[2], 1.0F);
        }
    }

    /** Abstand der Steckermitte von der Blockmitte (der Stecker schliesst buendig mit der Blockseite ab). */
    private static final float PLUG_AT = 0.455F;
    /** Halbe Breite des Flachbandes und seine Farbe. */
    private static final float RIBBON_HALF = 0.07F;
    private static final float[] RIBBON = {0.09F, 0.08F, 0.13F};

    /**
     * Punkt auf der geneigten Bildschirmflaeche.
     *
     * @param u waagrecht -1..1
     * @param v senkrecht 0..1
     * @return {x, y, z}
     */
    private static float[] screen(float u, float v) {
        return new float[]{u * 0.34F, -0.04F + v * 0.42F, 0.08F - v * 0.16F};
    }

    /**
     * Rahmen fuer Schrift auf dem Bildschirm: links oben beginnend, passend geneigt und skaliert.
     *
     * @param sm Matrix der Station
     * @param lift Abstand vor der Glasflaeche
     * @return Matrix fuer die Schrift
     */
    private static Matrix4f textFrame(Matrix4f sm, float lift) {
        float tilt = (float) Math.atan2(0.16, 0.42);
        float[] tl = screen(-1, 1);
        return new Matrix4f(sm).translate(tl[0], tl[1], tl[2]).rotateX(-tilt).translate(0, 0, lift).scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
    }

    /**
     * Bildschirminhalt: Energie-Port zeigt Leistung, Puffer und Kapazitaet, Item-Port Brennstoff, Stabilisator und
     * Lagerbelegung.
     *
     * @param sm Matrix der Station
     * @param buffers Puffer
     * @param level Welt
     * @param energy {@code true} fuer den Energie-Port
     * @param d angezeigte Werte
     */
    private static void text(Matrix4f sm, MultiBufferSource buffers, Level level, boolean energy, Data d) {
        Font font = Minecraft.getInstance().font;
        Matrix4f tm = textFrame(sm, 0.004F);
        center(font, tm, buffers, "◆ " + tr(energy ? "console.cosmiccore.wing_energy" : "console.cosmiccore.wing_items") + " ◆",
                ROW_TITLE, energy ? 0x7AE8FF : 0xFFC070);
        if (energy) {
            if (d.core() == null) return;
            long[] ev = energyValues(d.port(), d.core());
            row(font, tm, buffers, tr("console.cosmiccore.transfer"), compact(ev[0]) + " RF/t", ROW_A, 0x7AE8FF);
            row(font, tm, buffers, tr("console.cosmiccore.output"), compact(d.core().getOutput()) + " RF/t", ROW_B, 0xA898C8);
            row(font, tm, buffers, tr("console.cosmiccore.buffer"), compact(ev[1]) + " / " + compact(ev[2]) + " RF", ROW_C, 0xC8B8F0);
            return;
        }
        // Item-Port: das Lager als Raster mit den echten Items
        itemGrid(font, tm, buffers, level, d.port(), (PX - 9 * CELL) / 2.0F, GRID_Y, CELL);
    }

    /**
     * Zeile "Bezeichnung  [Symbol] Anzahl · Restzeit" - auch fuer den Controller-Bildschirm.
     *
     * @param font Schrift
     * @param tm Matrix fuer die Schrift
     * @param buffers Puffer
     * @param level Welt
     * @param label Bezeichnung links
     * @param stack angezeigtes Item
     * @param seconds Restzeit in Sekunden (oder Sonderwert)
     * @param y Zeile in Schriftpixeln
     * @param color Farbe des Werts 0xRRGGBB
     * @param width Breite des Bildschirms in Schriftpixeln
     */
    static void itemRow(Font font, Matrix4f tm, MultiBufferSource buffers, Level level, String label, ItemStack stack, int seconds,
                        int y, int color, float width) {
        String value = (stack.isEmpty() ? "0" : Integer.toString(stack.getCount())) + " · " + duration(seconds);
        dr4kun3.cosmiccore.client.render.WorldText.draw(font, label, 5, y, 0xFFA898C8, false, tm, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, net.minecraft.util.LightCoordsUtil.FULL_BRIGHT);
        float vx = width - 5 - font.width(value);
        dr4kun3.cosmiccore.client.render.WorldText.draw(font, value, vx, y, 0xFF000000 | color, false, tm, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, net.minecraft.util.LightCoordsUtil.FULL_BRIGHT);
        if (!stack.isEmpty()) icon(tm, buffers, level, stack, vx - 11, y);
    }

    /**
     * Kleines Item-Symbol auf dem Bildschirm.
     *
     * @param tm Matrix fuer die Schrift
     * @param buffers Puffer
     * @param level Welt
     * @param stack das Item
     * @param x Position x in Schriftpixeln
     * @param y Position y in Schriftpixeln
     */
    static void icon(Matrix4f tm, MultiBufferSource buffers, Level level, ItemStack stack, float x, float y) {
        icon(tm, buffers, level, stack, x + 4, y + 3.5F, 8.0F);
    }

    /**
     * Item-Symbol beliebiger Groesse auf dem Bildschirm.
     *
     * @param tm Matrix fuer die Schrift
     * @param buffers Puffer
     * @param level Welt
     * @param stack das Item
     * @param cx Mitte x in Schriftpixeln
     * @param cy Mitte y in Schriftpixeln
     * @param size Kantenlaenge in Schriftpixeln
     */
    static void icon(Matrix4f tm, MultiBufferSource buffers, Level level, ItemStack stack, float cx, float cy, float size) {
        PoseStack ps = new PoseStack();
        ps.last().pose().set(tm);
        // gleichmaessig skaliert (sonst kippen die Normalen und das Symbol wird schwarz), leicht vor der Glasflaeche
        ps.translate(cx, cy, size / 8.0F);
        ps.scale(size, -size, size);
        if (!(buffers instanceof dr4kun3.cosmiccore.client.render.DeferredBuffers d)) return;
        var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, level, null, 0);
        state.submit(ps, d.collector(), net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
    }

    /**
     * Restzeit kompakt: 45s, 12m30s, 3h05m - oder leer, pausiert bzw. aus.
     *
     * @param seconds Sekunden oder einer der Sonderwerte aus ControllerBlockEntity
     * @return der Text
     */
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

    /**
     * Zentrierte Zeile auf dem Bildschirm.
     *
     * @param font Schrift
     * @param tm Matrix fuer die Schrift
     * @param buffers Puffer
     * @param text der Text
     * @param y Zeile
     * @param color Farbe 0xRRGGBB
     */
    private static void center(Font font, Matrix4f tm, MultiBufferSource buffers, String text, int y, int color) {
        dr4kun3.cosmiccore.client.render.WorldText.draw(font, text, (PX - font.width(text)) / 2.0F, y, 0xFF000000 | color, false, tm, buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, net.minecraft.util.LightCoordsUtil.FULL_BRIGHT);
    }

    /**
     * Zeile mit Bezeichnung links und Wert rechts.
     *
     * @param font Schrift
     * @param tm Matrix fuer die Schrift
     * @param buffers Puffer
     * @param label Bezeichnung
     * @param value Wert
     * @param y Zeile
     * @param color Farbe des Werts 0xRRGGBB
     */
    private static void row(Font font, Matrix4f tm, MultiBufferSource buffers, String label, String value, int y, int color) {
        dr4kun3.cosmiccore.client.render.WorldText.draw(font, label, 5, y, 0xFFA898C8, false, tm, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, net.minecraft.util.LightCoordsUtil.FULL_BRIGHT);
        dr4kun3.cosmiccore.client.render.WorldText.draw(font, value, PX - 5 - font.width(value), y, 0xFF000000 | color, false, tm, buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, net.minecraft.util.LightCoordsUtil.FULL_BRIGHT);
    }

    // ------------------------------------------------------------------ Pass 2: Licht

    /**
     * Leuchtende Teile einer Station: Rand des Portalfelds, Leitungen und Bildschirm-Glanz.
     *
     * @param base Matrix mit Ursprung in der Blockmitte
     * @param vc Puffer fuer additives Licht
     * @param energy {@code true} fuer den Energie-Port
     * @param connMask Bitmaske der angedockten Seiten
     * @param yaw Drehung der Station (Bogenmass)
     * @param screenYaw zusaetzliche Drehung des Bildschirms
     * @param grow Einblenden 0..1
     * @param d angezeigte Werte (Helligkeit)
     * @param ownBase eigenen Sockel mitzeichnen
     * @param screen Bildschirm mitzeichnen
     */
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
            long[] ev = energyValues(d.port(), d.core());
            float fill = ev[2] <= 0 ? 0.0F : Mth.clamp((float) ((double) ev[1] / ev[2]), 0, 1);
            segment(vc, tm, 5, BAR, 0, PX - 5, BAR, 0, 1.4F, col[0], col[1], col[2], e * 0.15F, e * 0.15F);
            if (fill > 0) segment(vc, tm, 5, BAR, 0, 5 + (PX - 10) * fill, BAR, 0, 1.4F, col[0], col[1], col[2], e, e);
        } else if (!energy) {
            itemGridGlow(vc, tm, d.port(), (PX - 9 * CELL) / 2.0F, GRID_Y, CELL, col, e);
        }
        float sv = Mth.frac(d.t() * 0.015F + (energy ? 0.5F : 0.0F)) * 62.0F;
        segment(vc, tm, 2, sv, 0, PX - 2, sv, 0, 0.35F, 1.0F, 1.0F, 1.0F, e * 0.1F, e * 0.1F);
    }

    /**
     * Leitungen von den Anschlussknoten zum Sockel, mit Fluss (Energie hinaus, Items hinein).
     *
     * @param base Matrix
     * @param vc Puffer
     * @param energy {@code true} fuer den Energie-Port
     * @param connMask Bitmaske der angedockten Seiten
     * @param yaw Drehung der Station
     * @param e Helligkeit 0..1
     * @param d angezeigte Werte (Zeit fuer den Fluss)
     */
    private static void connectorGlow(Matrix4f base, VertexConsumer vc, boolean energy, int connMask, float yaw, float e, Data d) {
        float[] col = energy ? ENERGY : ITEM;
        for (Direction dir : Direction.values()) {
            if ((connMask & (1 << dir.get3DDataValue())) == 0) continue;
            Vector3f[] path = ribbonPath(dir, yaw);
            if (path.length < 2) continue;
            // Adern des Flachbandes: drei feine Leuchtlinien knapp ueber dem Band
            Vector3f across = ribbonAcross(dir, yaw);
            for (int lane = -1; lane <= 1; lane++) {
                Vector3f off = new Vector3f(across).mul(lane * RIBBON_HALF * 0.55F);
                for (int i = 0; i + 1 < path.length; i++) {
                    Vector3f a = new Vector3f(path[i]).add(off), b = new Vector3f(path[i + 1]).add(off);
                    segment(vc, base, a.x, a.y, a.z, b.x, b.y, b.z, 0.006F, col[0], col[1], col[2], e * 0.55F, e * 0.55F);
                }
            }
            // Lichtpuls laeuft das Band entlang (Energie hinaus, Items hinein)
            float s = Mth.frac(d.t() * 0.03F + dir.ordinal() * 0.17F);
            Vector3f q = along(path, energy ? s : 1.0F - s);
            cube(vc, base, q.x, q.y, q.z, 0.022F, 1, 1, 1, e * Mth.sin(s * Mth.PI));
        }
    }

    /**
     * Punkt auf einem Linienzug nach Anteil der Gesamtlaenge.
     *
     * @param path die Punkte
     * @param f Anteil 0..1
     * @return der Punkt
     */
    private static Vector3f along(Vector3f[] path, float f) {
        float total = 0;
        for (int i = 0; i + 1 < path.length; i++) total += path[i].distance(path[i + 1]);
        float want = f * total;
        for (int i = 0; i + 1 < path.length; i++) {
            float len = path[i].distance(path[i + 1]);
            if (want <= len || i + 2 == path.length) return new Vector3f(path[i]).lerp(path[i + 1], len <= 0 ? 0 : Math.min(1, want / len));
            want -= len;
        }
        return new Vector3f(path[0]);
    }

    // ------------------------------------------------------------------ Portalfeld

    /**
     * Schwarzes Portalfeld (End-Sternenhimmel) in der XZ-Ebene: Kapsel entlang X - verbindet Controller und Ports der
     * Steuerkonsole zu einer Flaeche.
     *
     * @param buffers Puffer
     * @param m Matrix
     * @param halfLen halbe Laenge (0 = runde Scheibe)
     * @param r Radius
     */
    public static void portalCapsule(MultiBufferSource buffers, Matrix4f m, float halfLen, float r) {
        VertexConsumer vc = buffers.getBuffer(RenderTypes.endPortal());
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

    /**
     * Leuchtender Rand des Portalfelds.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param halfLen halbe Laenge
     * @param r Radius
     * @param col Farbe RGB 0..1
     * @param a Helligkeit 0..1
     */
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

    /**
     * Viereck in der XZ-Ebene, von oben und unten sichtbar.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param x0 Ecke 1 x
     * @param z0 Ecke 1 z
     * @param x1 Ecke 2 x
     * @param z1 Ecke 2 z
     * @param x2 Ecke 3 x
     * @param z2 Ecke 3 z
     * @param x3 Ecke 4 x
     * @param z3 Ecke 4 z
     */
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

    /**
     * Dunkle Bodenplatte als Kreisscheibe.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param r Radius
     */
    private static void disc(VertexConsumer vc, Matrix4f m, float r) {
        int seg = 32;
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            RenderUtil.quad2(vc, m, 0, 0, 0, Mth.cos(a0) * r, 0, Mth.sin(a0) * r, Mth.cos(a1) * r, 0, Mth.sin(a1) * r, 0, 0, 0,
                    PLATE[0], PLATE[1], PLATE[2], 1.0F);
        }
    }

    /**
     * Uebersetzter Text als String.
     *
     * @param key Uebersetzungsschluessel
     * @param args Platzhalterwerte
     * @return der Text
     */
    static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /**
     * Grosse Zahl kurz: 1.25G, 3.4M, 12.5k.
     *
     * @param n die Zahl
     * @return der Text
     */
    static String compact(long n) {
        if (n >= 1_000_000_000L) return String.format("%.2fG", n / 1e9);
        if (n >= 1_000_000L) return String.format("%.1fM", n / 1e6);
        if (n >= 10_000L) return String.format("%.1fk", n / 1e3);
        return Long.toString(n);
    }
}
