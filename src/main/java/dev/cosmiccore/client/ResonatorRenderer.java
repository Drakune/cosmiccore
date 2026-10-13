package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.hash;
import static dev.cosmiccore.client.RenderUtil.lerp;
import static dev.cosmiccore.client.RenderUtil.octahedron;
import static dev.cosmiccore.client.RenderUtil.ring;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.cosmiccore.block.CorePhase;
import dev.cosmiccore.block.ModBlocks;
import dev.cosmiccore.block.ModProperties;
import dev.cosmiccore.block.entity.ResonatorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Resonanzspule und Fokuskristall als ruhige Teile des schwarzen Lochs (das Blockmodell ist bei gebildeter
 * Struktur ausgeblendet):
 * <ul>
 *   <li>Spule: Sternenfeld-Torus, zum Kern ausgerichtet, mit gluehendem Rand - aus ihm stroemt die Materie
 *       in die Akkretionsscheibe (gezeichnet vom Kern-Renderer).</li>
 *   <li>Kristall: schwebender Sternenfeld-Oktaeder mit leuchtenden Kanten - er buendelt die Jets.</li>
 * </ul>
 * Erst Sternenfeld, dann Glow: ein getBuffer() mit anderem RenderType schliesst den vorherigen Puffer ab.
 */
public class ResonatorRenderer implements BlockEntityRenderer<ResonatorBlockEntity> {
    private static final float[] HOT = {1.0F, 0.8F, 0.55F};
    private static final float[] COOL = {0.62F, 0.28F, 0.95F};
    private static final float[] JET = {0.6F, 0.78F, 1.0F};
    private static final float[] CRITICAL = {1.0F, 0.12F, 0.12F};

    public ResonatorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public AABB getRenderBoundingBox(ResonatorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(0.5);
    }

    @Override
    public void render(ResonatorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        CorePhase phase = be.getBlockState().getValue(ModProperties.PHASE);
        if (phase == CorePhase.DORMANT) return;

        final float t = (float) (level.getGameTime() % 144000L) + partialTick;
        final boolean unstable = phase == CorePhase.UNSTABLE;
        final boolean active = phase == CorePhase.ACTIVE || unstable;
        final float speed = unstable ? 1.8F : active ? 1.0F : 0.3F;
        final float offset = (be.getBlockPos().hashCode() & 0xFF) * 0.37F;
        final float energy = (unstable ? 1.0F : active ? 0.8F : 0.3F)
                * (unstable ? 0.7F + 0.3F * (0.5F + 0.5F * hash((int) (t * 1.1F) + (int) offset)) : 1.0F);
        final float[] rim = unstable ? CRITICAL : active ? HOT : COOL;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        if (be.getBlockState().is(ModBlocks.FOCUS_CRYSTAL.get())) {
            renderCrystal(pose, buffers, t + offset, speed, energy, unstable);
        } else {
            renderCoil(pose, buffers, be.findCoreOffset(level.getGameTime()), t + offset, speed, energy, rim);
        }
        pose.popPose();
    }

    private static void renderCoil(PoseStack pose, MultiBufferSource buffers, int[] core, float t, float speed,
                                   float energy, float[] rim) {
        // lokale +Z-Achse zeigt zum Kern, der Ring steht quer dazu
        float dx = core != null ? core[0] : 1, dz = core != null ? core[2] : 0;
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(dx, dz)));
        pose.mulPose(Axis.ZP.rotationDegrees(t * 0.6F * speed));

        RenderUtil.starTorus(buffers.getBuffer(RenderType.endPortal()), pose.last().pose(), 0.36F, 0.11F, 28, 10);

        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        float pulse = 0.6F + 0.4F * Mth.sin(t * 0.12F * speed);
        // innerer Rand glueht (dort tritt die Materie aus), aeusserer Rand ist ein feiner violetter Saum
        ring(pose, glow, 0.25F, 0.02F, 0.0F, 90.0F, 0.0F, t, rim, energy * pulse);
        ring(pose, glow, 0.48F, 0.01F, 0.0F, 90.0F, 0.0F, t + 30.0F, COOL, energy * 0.5F);
    }

    private static void renderCrystal(PoseStack pose, MultiBufferSource buffers, float t, float speed, float energy,
                                      boolean unstable) {
        pose.translate(0.0, 0.04 * Mth.sin(t * 0.06F), 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(t * 0.8F * speed));
        float breathe = 1.0F + 0.04F * Mth.sin(t * 0.15F * speed);

        RenderUtil.starOctahedron(buffers.getBuffer(RenderType.endGateway()), pose.last().pose(), 0.42F * breathe, 0.6F * breathe);

        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        float[] edge = unstable ? CRITICAL : lerp(COOL, JET, 0.5F + 0.5F * Mth.sin(t * 0.05F));
        octahedron(glow, pose.last().pose(), 0.44F * breathe, 0.63F * breathe, 0.012F, edge, energy);
        ring(pose, glow, 0.62F, 0.01F, t * speed, 0.0F, 0.0F, t, JET, energy * 0.45F);
    }
}
