package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.cube;
import static dr4kun3.cosmiccore.client.RenderUtil.ring;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import dr4kun3.cosmiccore.ModItems;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Animierte 3D-Items:
 * <ul>
 *   <li>Sternenstaub: kleine schwebende Sternenfeld-Kristalle, die umeinander kreisen, mit Funkeln.</li>
 *   <li>Overdrive-Partikel: gleißender Kern aus Sternenfeld mit drei kreisenden Ringen in Gold und Violett.</li>
 *   <li>Quasar-Overdrive-Partikel: Sternkristall mit goldenen Kanten über einer Spiralgalaxie, Polarjets und eine
 *       goldene Druckwelle.</li>
 *   <li>Verdichteter Sternenstaub: dichter Sternenfeld-Kristall, um den sich Pressringe zusammenziehen, während
 *       Staubkörnchen spiralförmig hineingezogen werden.</li>
 *   <li>Neutronenstern-Fragment: winziger Pulsar - rasend schnell drehend, mit Leuchtturm-Strahlen und Magnetfeldbögen.</li>
 *   <li>Quasar-Essenz: Mini-Quasar - schwarzer Kern, glühende Akkretionsscheibe und pulsierende Jets.</li>
 *   <li>Module: schwebende Platine mit wandernden Lichtimpulsen und einem Hologramm-Symbol darüber.</li>
 * </ul>
 * Zeichenraum ist der Einheitswürfel (0..1), die Display-Transformationen stehen im Item-Modell.
 */
public class CosmicItemRenderer implements SpecialModelRenderer<ItemStack> {
    private static final float[] GOLD = {1.0F, 0.75F, 0.25F};
    private static final float[] VIOLET = {0.7F, 0.4F, 1.0F};
    private static final float[] CYAN = {0.45F, 0.9F, 1.0F};
    private static final float[] PINK = {1.0F, 0.35F, 0.55F};
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};
    private static final float[] ORANGE = {1.0F, 0.5F, 0.15F};

    /** Ab 1.21.4: Spezial-Renderer, den die Item-Definitionen (assets/cosmiccore/items) per Typ cosmiccore:cosmic_item anfordern. */
    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<?> bake(EntityModelSet models) {
            return new CosmicItemRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return CODEC;
        }
    }

    @Override
    public ItemStack extractArgument(ItemStack stack) {
        return stack;
    }

    @Override
    public void render(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay, boolean foil) {
        if (stack != null) renderByItem(stack, ctx, pose, buffers, light, overlay);
    }

    /**
     * Animationszeit: Spielzeit mit Teil-Tick, im Hauptmenü die Systemzeit.
     *
     * @return Zeit in Ticks
     */
    private static float time() {
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return mc.level == null ? (System.currentTimeMillis() % 100000L) / 50.0F : (mc.level.getGameTime() % 144000L) + pt;
    }

    /**
     * Wählt die passende Animation für das Item (Handbuch, Sternenstaub, Partikel, Neutronenstern, Quasar, Module).
     *
     * @param stack das gezeichnete Item
     * @param ctx Anzeigeart (Hand, Inventar, Boden ...)
     * @param pose Pose-Stapel
     * @param buffers Puffer
     * @param light Lichtwert
     * @param overlay Overlay-Wert
     */
    private void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float t = time();
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        if (stack.is(ModItems.GUIDE_BOOK.get())) {
            GuideBookRenderer.render(ctx, pose, buffers, light, overlay, t);
        } else if (stack.is(ModItems.OVERDRIVE_PARTICLE.get())) {
            renderParticle(pose, buffers, t);
        } else if (stack.is(ModItems.QUASAR_OVERDRIVE_PARTICLE.get())) {
            renderQuasarParticle(pose, buffers, t);
        } else if (stack.is(ModItems.STAR_DUST.get())) {
            renderStarDust(pose, buffers, t);
        } else if (stack.is(ModItems.COMPRESSED_STAR_DUST.get())) {
            renderCompressedDust(pose, buffers, t);
        } else if (stack.is(ModItems.NEUTRON_FRAGMENT.get())) {
            renderPulsar(pose, buffers, t);
        } else if (stack.is(ModItems.QUASAR_ESSENCE.get())) {
            renderQuasar(pose, buffers, t);
        } else if (stack.is(ModItems.EFFICIENCY_MODULE.get())) {
            renderModule(pose, buffers, t, GOLD, 0);
        } else if (stack.is(ModItems.STABILITY_MODULE.get())) {
            renderModule(pose, buffers, t, CYAN, 1);
        } else if (stack.is(ModItems.ANTI_ASTEROID_MODULE.get())) {
            renderModule(pose, buffers, t, PINK, 2);
        } else if (stack.is(ModItems.COLOR_MODULE.get())) {
            // Akzentfarbe wandert durch den Farbkreis
            float h = (t * 0.004F) % 1.0F;
            int rgb = Color.HSBtoRGB(h, 0.65F, 1.0F);
            renderModule(pose, buffers, t, new float[]{((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F}, 3);
        }
        pose.popPose();
    }

    /**
     * Overdrive-Partikel: gleißender Kern aus Sternenfeld mit drei kreisenden Ringen in Gold, Cyan und Violett.
     *
     * @param pose Pose-Stapel in der Item-Mitte
     * @param buffers Puffer
     * @param t Animationszeit
     */
    private static void renderParticle(PoseStack pose, MultiBufferSource buffers, float t) {
        float pulse = 0.5F + 0.5F * Mth.sin(t * 0.2F);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * 2.0F));
        RenderUtil.sphere(buffers.getBuffer(RenderType.endGateway()), pose.last().pose(), 0.17F + 0.015F * pulse, 10, 14);
        pose.popPose();
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        Matrix4f m = pose.last().pose();
        RenderUtil.glowSphere(glow, m, 0.22F + 0.02F * pulse, 10, 14, GOLD, 0.35F);
        RenderUtil.glowSphere(glow, m, 0.3F, 8, 12, VIOLET, 0.12F);
        ring(pose, glow, 0.34F, 0.02F, t * 4.0F, 70.0F, 0.0F, t, GOLD, 0.9F);
        ring(pose, glow, 0.38F, 0.016F, -t * 3.0F, -35.0F, 60.0F, t + 20.0F, VIOLET, 0.8F);
        ring(pose, glow, 0.42F, 0.012F, t * 2.2F, 15.0F, 120.0F, t + 40.0F, CYAN, 0.7F);
        for (int i = 0; i < 6; i++) {
            float[] p = RenderUtil.orbitPoint(0.4F, t * 0.08F + i * 1.05F, (i * 47 % 80) - 40.0F, i * 60.0F);
            cube(glow, m, p[0], p[1], p[2], 0.018F, 1.0F, 1.0F, 1.0F, 0.9F);
        }
    }

    /**
     * Sternenstaub: schwebende Sternenfeld-Splitter mit funkelnden Lichtpunkten.
     *
     * @param pose Pose-Stapel in der Item-Mitte
     * @param buffers Puffer
     * @param t Animationszeit
     */
    private static void renderStarDust(PoseStack pose, MultiBufferSource buffers, float t) {
        VertexConsumer gateway = buffers.getBuffer(RenderType.endGateway());
        float[][] shards = {{0.0F, -0.05F, 0.0F, 0.14F}, {0.18F, -0.12F, 0.08F, 0.09F}, {-0.16F, -0.14F, -0.06F, 0.1F},
                {0.06F, 0.12F, -0.14F, 0.07F}, {-0.08F, 0.08F, 0.16F, 0.06F}};
        for (int i = 0; i < shards.length; i++) {
            float[] s = shards[i];
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(t * 1.2F));
            pose.translate(s[0], s[1] + 0.03F * Mth.sin(t * 0.1F + i), s[2]);
            pose.mulPose(Axis.YP.rotationDegrees(t * (2.0F + i)));
            pose.mulPose(Axis.ZP.rotationDegrees(20.0F * i));
            RenderUtil.starOctahedron(gateway, pose.last().pose(), s[3], s[3] * 1.6F);
            pose.popPose();
        }
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        Matrix4f m = pose.last().pose();
        for (int i = 0; i < shards.length; i++) {
            float[] s = shards[i];
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(t * 1.2F));
            pose.translate(s[0], s[1] + 0.03F * Mth.sin(t * 0.1F + i), s[2]);
            pose.mulPose(Axis.YP.rotationDegrees(t * (2.0F + i)));
            pose.mulPose(Axis.ZP.rotationDegrees(20.0F * i));
            RenderUtil.octahedron(glow, pose.last().pose(), s[3] * 1.05F, s[3] * 1.7F, 0.006F, i % 2 == 0 ? VIOLET : CYAN, 0.8F);
            pose.popPose();
        }
        // Funkeln
        for (int i = 0; i < 8; i++) {
            float tw = 0.5F + 0.5F * Mth.sin(t * 0.3F + i * 2.1F);
            if (tw < 0.5F) continue;
            float a = i * 0.785F + t * 0.02F;
            cube(glow, m, Mth.cos(a) * 0.32F, -0.2F + (i % 4) * 0.12F, Mth.sin(a) * 0.32F, 0.012F + 0.02F * tw, 1.0F, 1.0F, 1.0F, tw);
        }
    }

    // ------------------------------------------------------------------ Verdichteter Sternenstaub

    /**
     * Verdichteter Sternenstaub: ein Kristall, um den sich Ringe im Takt zusammenziehen - beim Pressen leuchtet er auf.
     *
     * @param pose Pose-Stapel in der Item-Mitte
     * @param buffers Puffer
     * @param t Animationszeit
     */
    private static void renderCompressedDust(PoseStack pose, MultiBufferSource buffers, float t) {
        // Pressen im Takt: die Ringe ziehen sich zusammen, der Kristall leuchtet beim Zusammendrücken auf
        float press = 0.5F + 0.5F * Mth.sin(t * 0.12F);
        float squeeze = press * press;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * 1.6F));
        pose.mulPose(Axis.ZP.rotationDegrees(12.0F));
        float w = 0.17F - 0.015F * squeeze, h = 0.27F - 0.03F * squeeze;
        RenderUtil.starOctahedron(buffers.getBuffer(RenderType.endGateway()), pose.last().pose(), w, h);
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        Matrix4f m = pose.last().pose();
        RenderUtil.octahedron(glow, m, w * 1.04F, h * 1.04F, 0.008F, VIOLET, 0.9F);
        RenderUtil.octahedron(glow, m, w * 1.1F, h * 1.1F, 0.004F, WHITE, 0.4F + 0.5F * squeeze);
        // drei kleine Begleitkristalle, eng angepresst
        for (int i = 0; i < 3; i++) {
            float a = i * Mth.TWO_PI / 3;
            float r = 0.17F - 0.03F * squeeze;
            Matrix4f sm = new Matrix4f(m).translate(Mth.cos(a) * r, -0.08F + 0.05F * i, Mth.sin(a) * r).rotateY(a).rotateZ(0.5F);
            RenderUtil.octahedron(glow, sm, 0.045F, 0.08F, 0.005F, i % 2 == 0 ? CYAN : VIOLET, 0.8F);
        }
        pose.popPose();
        Matrix4f wm = pose.last().pose();
        RenderUtil.glowSphere(glow, wm, 0.24F, 8, 12, VIOLET, 0.1F + 0.15F * squeeze);
        RenderUtil.glowSphere(glow, wm, 0.15F, 8, 12, WHITE, 0.08F + 0.2F * squeeze);
        // Pressringe
        for (int i = 0; i < 3; i++) {
            float r = 0.42F - 0.14F * squeeze - i * 0.03F;
            ring(pose, glow, r, 0.012F, t * (3.0F - i), 60.0F * i - 20.0F, 35.0F * i, t + i * 15.0F, i == 1 ? CYAN : VIOLET, 0.55F + 0.35F * squeeze);
        }
        // Staubkörnchen werden spiralförmig hineingezogen
        for (int i = 0; i < 10; i++) {
            float s = Mth.frac(t * 0.02F + i * 0.1F); // 0 = außen, 1 = im Kristall
            float a = i * 2.39996F + s * 6.0F;
            float r = 0.46F * (1.0F - s);
            float y = (hashY(i) - 0.5F) * 0.5F * (1.0F - s);
            cube(glow, wm, Mth.cos(a) * r, y, Mth.sin(a) * r, 0.006F + 0.012F * (1.0F - s), 1.0F, 1.0F, 1.0F, Mth.sin(s * Mth.PI));
        }
    }

    /**
     * Fester Pseudozufall 0..1 je Nummer.
     *
     * @param i Nummer
     * @return Wert 0..1
     */
    private static float hashY(int i) {
        return Mth.frac(Mth.sin(i * 12.9898F) * 43758.547F);
    }

    // ------------------------------------------------------------------ Neutronenstern (Pulsar)

    /**
     * Neutronenstern: kleine, dichte Kugel mit zwei Lichtkegeln an den Polen, die sich ruhig drehen.
     *
     * @param pose Pose-Stapel in der Item-Mitte
     * @param buffers Puffer
     * @param t Animationszeit
     */
    private static void renderPulsar(PoseStack pose, MultiBufferSource buffers, float t) {
        float spin = t * 0.08F; // ruhige Drehung (kein Flackern)
        float flash = 0.5F + 0.5F * Mth.sin(t * 0.06F); // weicher, langsamer Puls statt Stroboskop
        flash *= 0.5F;
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(18.0F));
        RenderUtil.solidSphere(buffers.getBuffer(RenderType.debugQuads()), pose.last().pose(), 0.1F, 8, 12, 0.82F, 0.92F, 1.0F);
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        Matrix4f core = pose.last().pose();
        RenderUtil.glowSphere(glow, core, 0.13F, 8, 12, WHITE, 0.45F + 0.4F * flash);
        RenderUtil.glowSphere(glow, core, 0.2F, 8, 12, CYAN, 0.18F + 0.25F * flash);
        RenderUtil.glowSphere(glow, core, 0.3F, 8, 12, VIOLET, 0.05F + 0.1F * flash);
        // Magnetachse um 30 Grad gegen die Drehachse geneigt - die Strahlen streichen wie ein Leuchtturm herum
        pose.mulPose(Axis.YP.rotation(spin));
        pose.mulPose(Axis.XP.rotationDegrees(30.0F));
        Matrix4f m = pose.last().pose();
        for (int dir = -1; dir <= 1; dir += 2) {
            RenderUtil.segment(glow, m, 0, 0.09F * dir, 0, 0, 0.62F * dir, 0, 0.06F, CYAN[0], CYAN[1], CYAN[2], 0.7F, 0.0F);
            RenderUtil.segment(glow, m, 0, 0.09F * dir, 0, 0, 0.5F * dir, 0, 0.015F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F);
        }
        // Magnetfeldbögen (Dipol: r = L * sin^2(theta))
        for (int k = 0; k < 4; k++) {
            float phi = k * Mth.HALF_PI;
            float px = 0, py = 0, pz = 0;
            boolean first = true;
            for (int i = 0; i <= 16; i++) {
                float th = 0.35F + (Mth.PI - 0.7F) * i / 16.0F;
                float r = 0.3F * Mth.sin(th) * Mth.sin(th);
                float x = r * Mth.sin(th) * Mth.cos(phi), y = r * Mth.cos(th), z = r * Mth.sin(th) * Mth.sin(phi);
                if (!first) RenderUtil.segment(glow, m, px, py, pz, x, y, z, 0.006F, VIOLET[0], VIOLET[1], VIOLET[2], 0.45F, 0.45F);
                px = x;
                py = y;
                pz = z;
                first = false;
            }
        }
        pose.popPose();
        // Bruchstücke kreisen um den Stern
        Matrix4f wm = pose.last().pose();
        for (int i = 0; i < 4; i++) {
            float[] p = RenderUtil.orbitPoint(0.34F, t * 0.06F + i * 1.57F, 20.0F + i * 15.0F, i * 45.0F);
            cube(glow, wm, p[0], p[1], p[2], 0.016F, 0.8F, 0.9F, 1.0F, 0.9F);
        }
    }

    // ------------------------------------------------------------------ Quasar-Overdrive-Partikel

    private static final float[] DEEP_GOLD = {1.0F, 0.62F, 0.12F};

    /**
     * Quasar-Overdrive-Partikel: ein Sternkristall (zwei verdrehte Sternenfeld-Doppelpyramiden) mit goldenen Kanten
     * über einer kleinen Spiralgalaxie, schmale Polarjets mit wandernden Knoten und eine goldene Druckwelle.
     *
     * @param pose Pose-Stapel in der Item-Mitte
     * @param buffers Puffer
     * @param t Animationszeit
     */
    private static void renderQuasarParticle(PoseStack pose, MultiBufferSource buffers, float t) {
        float pulse = 0.5F + 0.5F * Mth.sin(t * 0.1F);
        // Sternkristall aus Sternenfeld
        VertexConsumer gateway = buffers.getBuffer(RenderType.endGateway());
        for (int k = 0; k < 2; k++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(t * (k == 0 ? 1.4F : -1.0F) + k * 45.0F));
            pose.mulPose(Axis.XP.rotationDegrees(k * 90.0F));
            RenderUtil.starOctahedron(gateway, pose.last().pose(), 0.11F, 0.2F);
            pose.popPose();
        }
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        for (int k = 0; k < 2; k++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(t * (k == 0 ? 1.4F : -1.0F) + k * 45.0F));
            pose.mulPose(Axis.XP.rotationDegrees(k * 90.0F));
            RenderUtil.octahedron(glow, pose.last().pose(), 0.115F, 0.21F, 0.006F, k == 0 ? GOLD : WHITE, 0.85F);
            pose.popPose();
        }
        Matrix4f m = pose.last().pose();
        RenderUtil.glowSphere(glow, m, 0.09F + 0.02F * pulse, 8, 12, WHITE, 0.35F + 0.2F * pulse);
        RenderUtil.glowSphere(glow, m, 0.2F, 8, 12, DEEP_GOLD, 0.12F);

        // Spiralgalaxie: zwei Arme aus Lichtpunkten, leicht gekippt, langsam drehend
        pose.pushPose();
        pose.translate(0.0F, -0.04F, 0.0F);
        pose.mulPose(Axis.XP.rotationDegrees(18.0F));
        pose.mulPose(Axis.YP.rotationDegrees(t * 0.9F));
        Matrix4f gm = pose.last().pose();
        for (int arm = 0; arm < 2; arm++) {
            for (int i = 0; i < 22; i++) {
                float f = i / 22.0F;
                float r = 0.13F + 0.3F * f;
                float a = arm * Mth.PI + f * 4.2F;
                float[] c = RenderUtil.lerp(GOLD, VIOLET, f);
                float tw = 0.55F + 0.45F * Mth.sin(t * 0.2F + i * 1.3F + arm);
                cube(glow, gm, Mth.cos(a) * r, 0.0F, Mth.sin(a) * r, 0.012F * (1.0F - 0.5F * f), c[0], c[1], c[2], tw * (1.0F - 0.5F * f));
            }
        }
        RenderUtil.flatCircle(glow, gm, 0.44F, 0.004F, 48, VIOLET, 0.35F);
        pose.popPose();

        // Polarjets mit Knoten
        for (int dir = -1; dir <= 1; dir += 2) {
            RenderUtil.segment(glow, m, 0, 0.18F * dir, 0, 0, 0.5F * dir, 0, 0.018F, CYAN[0], CYAN[1], CYAN[2], 0.6F, 0.0F);
            for (int k = 0; k < 2; k++) {
                float s = Mth.frac(t * 0.035F + k * 0.5F);
                cube(glow, m, 0, (0.2F + 0.3F * s) * dir, 0, 0.018F * (1.0F - s) + 0.006F, 1.0F, 0.9F, 0.6F, 1.0F - s);
            }
        }

        // ruhige goldene Druckwelle
        float wave = Mth.frac(t * 0.012F);
        RenderUtil.flatCircle(glow, new Matrix4f(m).rotateX(0.31F), 0.12F + 0.38F * wave, 0.01F, 48, DEEP_GOLD, 0.8F * (1.0F - wave));
    }

    // ------------------------------------------------------------------ Quasar

    /**
     * Quasar-Essenz: gleißender Kern mit Akkretionsscheibe und pulsierenden Jets.
     *
     * @param pose Pose-Stapel in der Item-Mitte
     * @param buffers Puffer
     * @param t Animationszeit
     */
    private static void renderQuasar(PoseStack pose, MultiBufferSource buffers, float t) {
        float beat = 0.5F + 0.5F * Mth.sin(t * 0.25F);
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(18.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(-10.0F));
        RenderUtil.solidSphere(buffers.getBuffer(RenderType.debugQuads()), pose.last().pose(), 0.075F, 8, 12, 0.0F, 0.0F, 0.0F);
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        Matrix4f m = pose.last().pose();
        // Photonenring und Akkretionsscheibe: innen weißglühend, außen violett
        RenderUtil.glowSphere(glow, m, 0.095F, 8, 12, WHITE, 0.25F);
        float[][] cols = {WHITE, {1.0F, 0.9F, 0.6F}, GOLD, ORANGE, {0.9F, 0.35F, 0.6F}, VIOLET};
        for (int i = 0; i < cols.length; i++) {
            float r = 0.12F + i * 0.045F;
            ring(pose, glow, r, 0.022F, t * (9.0F - i * 1.2F), 0.0F, 0.0F, t * 2.0F + i * 9.0F, cols[i], 0.95F - i * 0.1F);
        }
        // relativistische Jets mit Knoten, die nach außen jagen
        for (int dir = -1; dir <= 1; dir += 2) {
            RenderUtil.segment(glow, m, 0, 0.06F * dir, 0, 0, 0.62F * dir, 0, 0.045F + 0.015F * beat, CYAN[0], CYAN[1], CYAN[2], 0.75F, 0.0F);
            RenderUtil.segment(glow, m, 0, 0.06F * dir, 0, 0, 0.55F * dir, 0, 0.012F, 1.0F, 1.0F, 1.0F, 1.0F, 0.1F);
            for (int k = 0; k < 3; k++) {
                float s = Mth.frac(t * 0.05F + k / 3.0F);
                cube(glow, m, 0, (0.08F + 0.5F * s) * dir, 0, 0.025F * (1.0F - s) + 0.008F, 1.0F, 1.0F, 1.0F, 1.0F - s);
            }
        }
        RenderUtil.glowSphere(glow, m, 0.42F, 8, 12, VIOLET, 0.04F + 0.04F * beat);
        pose.popPose();
    }

    // ------------------------------------------------------------------ Module

    /**
     * Modul: schwebende Platine mit leuchtenden Leiterbahnen und einem Symbol darüber.
     *
     * @param pose Pose-Stapel in der Item-Mitte
     * @param buffers Puffer
     * @param t Animationszeit
     * @param accent Akzentfarbe RGB 0..1
     * @param symbol 0 = Effizienz (Zahnrad-Ring), 1 = Stabilität (Sechseck-Schild), 2 = Anti-Asteroid, 3 = Farbe
     */
    private static void renderModule(PoseStack pose, MultiBufferSource buffers, float t, float[] accent, int symbol) {
        float bob = 0.02F * Mth.sin(t * 0.08F);
        float pulse = 0.5F + 0.5F * Mth.sin(t * 0.18F);
        pose.pushPose();
        pose.translate(0.0F, -0.12F + bob, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(t * 1.5F));
        Matrix4f m = pose.last().pose();
        // Platine (deckend): dunkle Platte mit hellerem Rand und Goldkontakten
        VertexConsumer solid = buffers.getBuffer(RenderType.debugQuads());
        box(solid, m, -0.3F, -0.03F, -0.3F, 0.3F, 0.03F, 0.3F, 0.12F, 0.09F, 0.2F);
        box(solid, m, -0.3F, 0.03F, -0.3F, 0.3F, 0.036F, -0.27F, 0.3F, 0.24F, 0.45F);
        box(solid, m, -0.3F, 0.03F, 0.27F, 0.3F, 0.036F, 0.3F, 0.3F, 0.24F, 0.45F);
        for (int i = 0; i < 5; i++) {
            float x = -0.22F + i * 0.11F;
            box(solid, m, x - 0.025F, -0.02F, 0.3F, x + 0.025F, 0.02F, 0.36F, 0.86F, 0.7F, 0.3F);
            box(solid, m, x - 0.025F, -0.02F, -0.36F, x + 0.025F, 0.02F, -0.3F, 0.86F, 0.7F, 0.3F);
        }
        box(solid, m, -0.09F, 0.03F, -0.09F, 0.09F, 0.07F, 0.09F, 0.05F, 0.04F, 0.08F);
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        // Leiterbahnen mit wandernden Lichtimpulsen
        float y = 0.04F;
        float[][] traces = {{-0.24F, -0.2F, -0.09F, -0.2F}, {0.24F, 0.2F, 0.09F, 0.2F}, {-0.24F, 0.12F, -0.09F, 0.05F},
                {0.24F, -0.12F, 0.09F, -0.05F}, {0.0F, -0.24F, 0.0F, -0.09F}, {0.0F, 0.24F, 0.0F, 0.09F}};
        for (int i = 0; i < traces.length; i++) {
            float[] tr = traces[i];
            RenderUtil.segment(glow, m, tr[0], y, tr[1], tr[2], y, tr[3], 0.006F, accent[0], accent[1], accent[2], 0.35F, 0.35F);
            float s = Mth.frac(t * 0.04F + i * 0.37F);
            cube(glow, m, Mth.lerp(s, tr[0], tr[2]), y + 0.004F, Mth.lerp(s, tr[1], tr[3]), 0.014F, 1.0F, 1.0F, 1.0F, Mth.sin(s * Mth.PI));
        }
        // leuchtender Kern
        RenderUtil.glowSphere(glow, new Matrix4f(m).translate(0, 0.08F, 0), 0.07F + 0.02F * pulse, 8, 12, accent, 0.4F + 0.3F * pulse);
        pose.popPose();

        // Hologramm-Symbol schwebt über der Platine
        pose.pushPose();
        pose.translate(0.0F, 0.14F + bob * 1.5F, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(-t * 2.5F));
        Matrix4f hm = pose.last().pose();
        float a = 0.6F + 0.4F * pulse;
        RenderUtil.segment(glow, new Matrix4f(m), 0, 0.08F, 0, 0, 0.26F + bob, 0, 0.04F, accent[0], accent[1], accent[2], 0.25F, 0.0F);
        switch (symbol) {
            case 0 -> { // Zahnrad: Ring mit Zähnen
                RenderUtil.flatCircle(glow, hm, 0.13F, 0.012F, 32, accent, a);
                for (int k = 0; k < 8; k++) {
                    float ang = k * Mth.TWO_PI / 8;
                    RenderUtil.segment(glow, hm, Mth.cos(ang) * 0.13F, 0, Mth.sin(ang) * 0.13F, Mth.cos(ang) * 0.19F, 0,
                            Mth.sin(ang) * 0.19F, 0.018F, accent[0], accent[1], accent[2], a, a);
                }
            }
            case 1 -> { // Schild: Sechseck mit innerem Ring
                for (int k = 0; k < 6; k++) {
                    float a0 = k * Mth.TWO_PI / 6, a1 = (k + 1) * Mth.TWO_PI / 6;
                    RenderUtil.segment(glow, hm, Mth.cos(a0) * 0.18F, 0, Mth.sin(a0) * 0.18F, Mth.cos(a1) * 0.18F, 0,
                            Mth.sin(a1) * 0.18F, 0.01F, accent[0], accent[1], accent[2], a, a);
                }
                RenderUtil.flatCircle(glow, hm, 0.09F + 0.02F * pulse, 0.01F, 24, WHITE, a * 0.7F);
            }
            case 3 -> { // Farbe: Farbrad aus sechs Segmenten in Regenbogenfarben
                for (int k = 0; k < 6; k++) {
                    int rgb = Color.HSBtoRGB(k / 6.0F, 0.75F, 1.0F);
                    float[] c = {((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F};
                    float a0 = k * Mth.TWO_PI / 6 + 0.08F, a1 = (k + 1) * Mth.TWO_PI / 6 - 0.08F;
                    RenderUtil.segment(glow, hm, Mth.cos(a0) * 0.16F, 0, Mth.sin(a0) * 0.16F, Mth.cos(a1) * 0.16F, 0,
                            Mth.sin(a1) * 0.16F, 0.022F, c[0], c[1], c[2], a, a);
                }
                RenderUtil.flatCircle(glow, hm, 0.07F, 0.01F, 20, WHITE, a * 0.8F);
            }
            default -> { // Anti-Asteroid: Ring mit Schrägstrich, darin ein kleiner Brocken
                RenderUtil.flatCircle(glow, hm, 0.17F, 0.014F, 32, accent, a);
                RenderUtil.segment(glow, hm, -0.12F, 0, -0.12F, 0.12F, 0, 0.12F, 0.014F, accent[0], accent[1], accent[2], a, a);
                cube(glow, hm, 0.06F, 0.0F, -0.06F, 0.03F, 0.75F, 0.65F, 0.6F, 0.8F);
            }
        }
        pose.popPose();
    }

    /**
     * Deckender Quader mit einfacher Flächenschattierung.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param x0 kleinste x
     * @param y0 kleinste y
     * @param z0 kleinste z
     * @param x1 größte x
     * @param y1 größte y
     * @param z1 größte z
     * @param r Rot 0..1
     * @param g Grün 0..1
     * @param b Blau 0..1
     */
    private static void box(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                            float r, float g, float b) {
        float[] sh = {0.55F, 1.0F, 0.8F, 0.8F, 0.7F, 0.7F};
        RenderUtil.quad2(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r * sh[0], g * sh[0], b * sh[0], 1.0F);
        RenderUtil.quad2(vc, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r * sh[1], g * sh[1], b * sh[1], 1.0F);
        RenderUtil.quad2(vc, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r * sh[2], g * sh[2], b * sh[2], 1.0F);
        RenderUtil.quad2(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r * sh[3], g * sh[3], b * sh[3], 1.0F);
        RenderUtil.quad2(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r * sh[4], g * sh[4], b * sh[4], 1.0F);
        RenderUtil.quad2(vc, m, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r * sh[5], g * sh[5], b * sh[5], 1.0F);
    }
}
