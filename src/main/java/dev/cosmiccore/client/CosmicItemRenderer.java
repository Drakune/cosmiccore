package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.cube;
import static dev.cosmiccore.client.RenderUtil.ring;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.cosmiccore.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Animierte 3D-Items:
 * <ul>
 *   <li>Sternenstaub: kleine schwebende Sternenfeld-Kristalle, die umeinander kreisen, mit Funkeln.</li>
 *   <li>Overdrive-Partikel: gleissender Kern aus Sternenfeld mit drei kreisenden Ringen in Gold und Violett.</li>
 * </ul>
 * Zeichenraum ist der Einheitswuerfel (0..1), die Display-Transformationen stehen im Item-Modell.
 */
public class CosmicItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final float[] GOLD = {1.0F, 0.75F, 0.25F};
    private static final float[] VIOLET = {0.7F, 0.4F, 1.0F};
    private static final float[] CYAN = {0.45F, 0.9F, 1.0F};

    public CosmicItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    private static float time() {
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(false);
        return mc.level == null ? (System.currentTimeMillis() % 100000L) / 50.0F : (mc.level.getGameTime() % 144000L) + pt;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float t = time();
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        if (stack.is(ModItems.OVERDRIVE_PARTICLE.get())) {
            renderParticle(pose, buffers, t);
        } else if (stack.is(ModItems.STAR_DUST.get())) {
            renderStarDust(pose, buffers, t);
        }
        pose.popPose();
    }

    private static void renderParticle(PoseStack pose, MultiBufferSource buffers, float t) {
        float pulse = 0.5F + 0.5F * Mth.sin(t * 0.2F);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * 2.0F));
        RenderUtil.sphere(buffers.getBuffer(RenderType.endGateway()), pose.last().pose(), 0.17F + 0.015F * pulse, 10, 14);
        pose.popPose();
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
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
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
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
}
