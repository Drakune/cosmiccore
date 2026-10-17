package dr4kun3.cosmiccore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dr4kun3.cosmiccore.CosmicCore;
import dr4kun3.cosmiccore.ModItems;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Das Kosmische Handbuch als 3D-Buch: violetter Ledereinband mit Goldrahmen und Stern-Emblem, zwei Seitenbloecke,
 * Ruecken mit Goldbuenden. In der Hand klappt es auf, eine Seite blaettert sich regelmaessig um, ueber der
 * Doppelseite schwebt ein leuchtendes Sternsiegel. Im Inventar, am Boden und im Rahmen liegt es geschlossen,
 * das Emblem pulsiert. Textur: textures/item/cosmic_book.png (Atlas 128x64, siehe tools/generate_resources.py).
 */
@EventBusSubscriber(modid = CosmicCore.MODID, value = Dist.CLIENT)
public final class GuideBookRenderer {
    /** Nur statische Zeichenroutinen - keine Instanzen. */
    private GuideBookRenderer() {}

    private static final Identifier TEX = CosmicCore.id("textures/item/cosmic_book.png");
    private static final float TW = 128.0F, TH = 64.0F;
    // Atlas-Bereiche (x, y, Breite, Hoehe) - wie BOOK_ATLAS im Generator
    private static final int[] COVER = {0, 0, 30, 40}, PAGE = {30, 0, 30, 40}, FORE = {60, 0, 6, 40}, SPINE = {66, 0, 10, 40},
            INSIDE = {76, 0, 30, 40}, EDGE = {0, 40, 30, 6}, VIOLET = {30, 40, 4, 4}, GOLD_UV = {34, 40, 4, 4};
    private static final float[] GOLD = {1.0F, 0.78F, 0.35F}, VIOLET_GLOW = {0.7F, 0.45F, 1.0F}, CYAN = {0.45F, 0.9F, 1.0F};

    /** Masse in Bloecken: Breite einer Haelfte, Hoehe, Deckel- und Seitenblockdicke. */
    private static final float W = 0.56F, H = 0.76F, C = 0.035F, P = 0.07F;
    private static final long OPEN_MS = 650;

    /** Seit wann haelt der Spieler das Buch (je Hand); -1 = nicht in der Hand. */
    private static final long[] heldSince = {-1, -1};

    /**
     * Merkt sich je Hand, seit wann das Buch gehalten wird (fuer das Aufklappen beim In-die-Hand-Nehmen).
     *
     * @param event Ende eines Client-Ticks
     */
    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        var player = Minecraft.getInstance().player;
        for (InteractionHand hand : InteractionHand.values()) {
            boolean held = player != null && player.getItemInHand(hand).is(ModItems.GUIDE_BOOK.get());
            int i = hand.ordinal();
            if (held && heldSince[i] < 0) heldSince[i] = Util.getMillis();
            else if (!held) heldSince[i] = -1;
        }
    }

    /**
     * Wie weit das Buch in dieser Ansicht aufgeklappt ist: in der Hand oeffnet es sich, im Inventar bleibt es zu.
     *
     * @param ctx Anzeigeart (Hand, Inventar, Boden ...)
     * @return 0 (zu) bis 1 (offen)
     */
    private static float openness(ItemDisplayContext ctx) {
        if (!ctx.firstPerson() && ctx != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && ctx != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            return 0.0F;
        }
        var player = Minecraft.getInstance().player;
        boolean left = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        int hand = 0;
        if (player != null) {
            boolean mainIsRight = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
            hand = (left == mainIsRight) ? InteractionHand.OFF_HAND.ordinal() : InteractionHand.MAIN_HAND.ordinal();
        }
        long since = heldSince[hand];
        if (since < 0) return ctx.firstPerson() ? 0.0F : 1.0F; // andere Spieler: offen
        float f = Mth.clamp((Util.getMillis() - since) / (float) OPEN_MS, 0.0F, 1.0F);
        return f * f * (3.0F - 2.0F * f);
    }

    /**
     * Zeichnet das 3D-Buch: zwei Haelften mit Deckel und Seitenblock, Buchruecken, ein regelmaessig umblaetterndes
     * Blatt
     * und ueber der aufgeschlagenen Doppelseite ein Sternsiegel mit Funken.
     *
     * @param ctx Anzeigeart (bestimmt, wie weit das Buch offen ist)
     * @param pose Pose-Stapel in der Item-Mitte
     * @param buffers Puffer
     * @param light Lichtwert
     * @param overlay Overlay-Wert
     * @param t Animationszeit in Ticks
     */
    public static void render(ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay, float t) {
        float open = openness(ctx);
        float ang = open * 78.0F; // Oeffnungswinkel je Haelfte
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TEX));
        pose.pushPose();
        // Buch steht auf dem Ruecken-Gelenk (lokales Y = Hoehe), die Seiten zeigen zum Betrachter
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        pose.translate(-0.12F * open, 0.0F, 0.0F);
        // Ruecken
        box(vc, pose, -C, -H / 2, -(P + C), 0.0F, H / 2, P + C, light, overlay,
                SPINE, VIOLET, VIOLET, VIOLET, GOLD_UV, GOLD_UV);
        // vordere Haelfte (+Z) und hintere Haelfte (-Z), um das Gelenk geklappt
        half(vc, pose, -ang, 1, light, overlay);
        half(vc, pose, ang, -1, light, overlay);
        // Blatt, das sich umblaettert (nur offen in der Hand)
        if (open > 0.95F) {
            float cycle = (t % 70.0F) / 14.0F; // 14 Ticks Blaettern, dann Pause
            if (cycle < 1.0F) {
                float e = cycle * cycle * (3.0F - 2.0F * cycle);
                float a = Mth.lerp(e, ang, -ang);
                float lift = Mth.sin(e * Mth.PI) * 0.06F;
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(a));
                pose.translate(0, 0, lift);
                // Blatt leicht gewoelbt: zwei Segmente
                sheet(vc, pose, light, overlay, e);
                pose.popPose();
            }
        }
        pose.popPose();

        // Leuchten: Emblem (geschlossen) bzw. Siegel und Funken (offen)
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        pose.translate(-0.12F * open, 0.0F, 0.0F);
        float pulse = 0.6F + 0.4F * Mth.sin(t * 0.12F);
        if (open < 0.5F) {
            // Emblem auf dem Vorderdeckel: Raute und Kernpunkt glimmen
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-ang));
            Matrix4f m = new Matrix4f(pose.last().pose()).translate(W / 2, H / 2 - 15.5F / 40.0F * H, P + C + 0.004F);
            float r = 7.0F / 30.0F * W, a = (1.0F - open * 2.0F) * pulse;
            float[][] d = {{0, r}, {r, 0}, {0, -r}, {-r, 0}};
            for (int k = 0; k < 4; k++) {
                float[] p0 = d[k], p1 = d[(k + 1) % 4];
                RenderUtil.segment(glow, m, p0[0], p0[1], 0, p1[0], p1[1], 0, 0.008F, GOLD[0], GOLD[1], GOLD[2], a * 0.8F, a * 0.8F);
            }
            RenderUtil.glowSphere(glow, m, 0.03F + 0.01F * pulse, 6, 8, new float[]{1.0F, 0.95F, 0.8F}, a * 0.9F);
            pose.popPose();
        } else {
            // Sternsiegel ueber der Doppelseite und aufsteigende Funken
            float s = (open - 0.5F) * 2.0F;
            Matrix4f m = new Matrix4f(pose.last().pose()).translate(0.18F, 0.0F, 0.0F);
            Matrix4f ring = new Matrix4f(m).rotateZ(Mth.HALF_PI).rotateY(t * 0.03F);
            RenderUtil.flatCircle(glow, ring, 0.16F * s, 0.008F, 48, VIOLET_GLOW, 0.7F * s * pulse);
            RenderUtil.flatCircle(glow, new Matrix4f(ring).rotateY(-t * 0.07F), 0.1F * s, 0.006F, 6, GOLD, 0.8F * s);
            RenderUtil.glowSphere(glow, m, 0.025F + 0.01F * pulse, 6, 8, new float[]{1.0F, 0.95F, 0.85F}, 0.9F * s);
            for (int k = 0; k < 10; k++) {
                float f = Mth.frac(t * 0.012F + k * 0.1F);
                float az = k * 2.4F + t * 0.02F;
                float rr = 0.06F + 0.18F * RenderUtil.hash(k * 13) * 0.5F + 0.09F;
                Vector3f q = new Vector3f(0.08F + 0.2F * f, -0.25F + 0.6F * f, Mth.sin(az) * rr);
                float[] col = k % 3 == 0 ? CYAN : k % 3 == 1 ? GOLD : VIOLET_GLOW;
                RenderUtil.cube(glow, m, q.x - 0.18F, q.y, q.z, 0.008F + 0.006F * (1 - f),
                        col[0], col[1], col[2], s * Mth.sin(f * Mth.PI));
            }
        }
        pose.popPose();
    }

    /**
     * Eine Buchhaelfte: Deckel aussen, Seitenblock innen, um das Gelenk gedreht.
     *
     * @param vc Puffer
     * @param pose Pose-Stapel am Gelenk
     * @param angle Oeffnungswinkel in Grad
     * @param side +1 vordere, -1 hintere Haelfte
     * @param light Lichtwert
     * @param overlay Overlay-Wert
     */
    private static void half(VertexConsumer vc, PoseStack pose, float angle, int side, int light, int overlay) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        float zIn = 0.0F, zPage = side * P, zOut = side * (P + C);
        float z0 = Math.min(zIn, zPage), z1 = Math.max(zIn, zPage);
        // Seitenblock (etwas kleiner als der Deckel)
        float m = 0.02F;
        int[] inner = PAGE;
        box(vc, pose, 0.0F, -H / 2 + m, z0, W - m, H / 2 - m, z1, light, overlay,
                VIOLET, FORE, side > 0 ? inner : EDGE, side > 0 ? EDGE : inner, EDGE, EDGE);
        // Deckel
        float c0 = Math.min(zPage, zOut), c1 = Math.max(zPage, zOut);
        box(vc, pose, 0.0F, -H / 2, c0, W, H / 2, c1, light, overlay,
                VIOLET, VIOLET, side > 0 ? INSIDE : COVER, side > 0 ? COVER : INSIDE, VIOLET, VIOLET);
        pose.popPose();
    }

    /**
     * Das umblaetternde Blatt, beidseitig mit Seitentextur und mittig etwas gewoelbt.
     *
     * @param vc Puffer
     * @param pose Pose-Stapel am Gelenk (bereits gedreht)
     * @param light Lichtwert
     * @param overlay Overlay-Wert
     * @param e Fortschritt des Umblaetterns 0..1 (staerkste Woelbung in der Mitte)
     */
    private static void sheet(VertexConsumer vc, PoseStack pose, int light, int overlay, float e) {
        Matrix4f m = pose.last().pose();
        float bend = Mth.sin(e * Mth.PI) * 0.05F;
        float xm = W * 0.5F, x1 = W - 0.03F, y0 = -H / 2 + 0.03F, y1 = H / 2 - 0.03F;
        float u0 = PAGE[0] / TW, u1 = (PAGE[0] + PAGE[2]) / TW, uM = (u0 + u1) / 2, v0 = PAGE[1] / TH, v1 = (PAGE[1] + PAGE[3]) / TH;
        // zwei Segmente: Gelenk -> Mitte (gewoelbt), Mitte -> Kante
        quad(vc, m, 0, y0, 0, xm, y0, bend, xm, y1, bend, 0, y1, 0, u0, v1, uM, v0, 0, 0, 1, light, overlay);
        quad(vc, m, xm, y0, bend, x1, y0, 0, x1, y1, 0, xm, y1, bend, uM, v1, u1, v0, 0, 0, 1, light, overlay);
    }

    /**
     * Quader mit eigener Textur je Seite.
     *
     * @param vc Puffer
     * @param pose Pose-Stapel
     * @param x0 kleinste x
     * @param y0 kleinste y
     * @param z0 kleinste z
     * @param x1 groesste x
     * @param y1 groesste y
     * @param z1 groesste z
     * @param light Lichtwert
     * @param overlay Overlay-Wert
     * @param west Atlas-Bereich der Seite -X (Gelenk)
     * @param east Atlas-Bereich der Seite +X (Kante)
     * @param north Atlas-Bereich der Seite -Z
     * @param south Atlas-Bereich der Seite +Z
     * @param up Atlas-Bereich oben
     * @param down Atlas-Bereich unten
     */
    private static void box(VertexConsumer vc, PoseStack pose, float x0, float y0, float z0, float x1, float y1, float z1,
                            int light, int overlay, int[] west, int[] east, int[] north, int[] south, int[] up, int[] down) {
        Matrix4f m = pose.last().pose();
        quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, uv(west), -1, 0, 0, light, overlay);
        quad(vc, m, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, uv(east), 1, 0, 0, light, overlay);
        quad(vc, m, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, uv(north), 0, 0, -1, light, overlay);
        quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, uv(south), 0, 0, 1, light, overlay);
        quad(vc, m, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, uv(up), 0, 1, 0, light, overlay);
        quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, uv(down), 0, -1, 0, light, overlay);
    }

    /**
     * Rechnet einen Atlas-Bereich in Texturkoordinaten um.
     *
     * @param r Bereich {x, y, Breite, Hoehe} in Pixeln
     * @return {u0, v0, u1, v1}
     */
    private static float[] uv(int[] r) {
        return new float[]{r[0] / TW, (r[1] + r[3]) / TH, (r[0] + r[2]) / TW, r[1] / TH};
    }

    /**
     * Viereck a-b-c-d mit Texturkoordinaten aus einem Feld.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param ax Ecke a x
     * @param ay Ecke a y
     * @param az Ecke a z
     * @param bx Ecke b x
     * @param by Ecke b y
     * @param bz Ecke b z
     * @param cx Ecke c x
     * @param cy Ecke c y
     * @param cz Ecke c z
     * @param dx Ecke d x
     * @param dy Ecke d y
     * @param dz Ecke d z
     * @param uv Texturkoordinaten {u0, v0, u1, v1}
     * @param nx Normale x
     * @param ny Normale y
     * @param nz Normale z
     * @param light Lichtwert
     * @param overlay Overlay-Wert
     */
    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float[] uv, float nx, float ny, float nz,
                             int light, int overlay) {
        quad(vc, m, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, uv[0], uv[1], uv[2], uv[3], nx, ny, nz, light, overlay);
    }

    /**
     * Viereck a-b-c-d; Texturkoordinaten: a = (u0, v0), b = (u1, v0), c = (u1, v1), d = (u0, v1).
     *
     * @param vc Puffer
     * @param m Matrix
     * @param ax Ecke a x
     * @param ay Ecke a y
     * @param az Ecke a z
     * @param bx Ecke b x
     * @param by Ecke b y
     * @param bz Ecke b z
     * @param cx Ecke c x
     * @param cy Ecke c y
     * @param cz Ecke c z
     * @param dx Ecke d x
     * @param dy Ecke d y
     * @param dz Ecke d z
     * @param u0 linke Texturkante
     * @param v0 obere Texturkante
     * @param u1 rechte Texturkante
     * @param v1 untere Texturkante
     * @param nx Normale x
     * @param ny Normale y
     * @param nz Normale z
     * @param light Lichtwert
     * @param overlay Overlay-Wert
     */
    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float u0, float v0, float u1, float v1,
                             float nx, float ny, float nz, int light, int overlay) {
        vertex(vc, m, ax, ay, az, u0, v0, nx, ny, nz, light, overlay);
        vertex(vc, m, bx, by, bz, u1, v0, nx, ny, nz, light, overlay);
        vertex(vc, m, cx, cy, cz, u1, v1, nx, ny, nz, light, overlay);
        vertex(vc, m, dx, dy, dz, u0, v1, nx, ny, nz, light, overlay);
    }

    /**
     * Ein Eckpunkt mit allen Attributen des Entity-Formats.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param x Position x
     * @param y Position y
     * @param z Position z
     * @param u Texturkoordinate u
     * @param v Texturkoordinate v
     * @param nx Normale x
     * @param ny Normale y
     * @param nz Normale z
     * @param light Lichtwert
     * @param overlay Overlay-Wert
     */
    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, float nx, float ny, float nz,
                               int light, int overlay) {
        vc.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
    }
}
