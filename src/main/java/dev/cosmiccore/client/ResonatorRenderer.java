package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.annulusXY;
import static dev.cosmiccore.client.RenderUtil.cube;
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
import org.joml.Matrix4f;

/**
 * Resonanzspule und Fokuskristall als ruhige Teile des schwarzen Lochs (das Blockmodell ist bei gebildeter
 * Struktur ausgeblendet):
 * <ul>
 *   <li>Spule: Schild-Emitter (Schuessel, gegenlaeufige Segmentringe, Fokuslinse), zum Kern
 *       ausgerichtet - aus ihm stroemt die Materie in die Akkretionsscheibe (gezeichnet vom Kern-Renderer).</li>
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

    /**
     * Schild-Emitter: eine Sternenfeld-Schuessel, davor drei gestaffelte Segmentringe,
     * die sich gegenlaeufig drehen und zum Kern hin kleiner werden, und eine Fokuslinse, aus der die Materie stroemt.
     */
    private static void renderCoil(PoseStack pose, MultiBufferSource buffers, int[] core, float t, float speed,
                                   float energy, float[] rim) {
        // lokale +Z-Achse zeigt zum Kern
        float dx = core != null ? core[0] : 1, dz = core != null ? core[2] : 0;
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(dx, dz)));
        // kompakt und zur Aussenseite versetzt, damit der Blick auf das Loch frei bleibt
        pose.translate(0.0, 0.0, -0.25);
        pose.scale(0.55F, 0.55F, 0.55F);

        // Schuessel: flache Sternenfeld-Scheibe hinten (Zylinder um die Z-Achse)
        pose.pushPose();
        pose.translate(0.0, 0.0, -0.36);
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        RenderUtil.starCylinder(buffers.getBuffer(RenderType.endPortal()), pose.last().pose(), 0.46F, 0.06F, 24);
        pose.popPose();

        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();

        // Rand der Schuessel
        for (int s = 0; s < 48; s++) {
            float a0 = Mth.TWO_PI * s / 48, a1 = Mth.TWO_PI * (s + 1) / 48;
            pose.pushPose();
            pose.translate(0.0, 0.0, -0.29);
            annulusXY(glow, pose.last().pose(), 0.44F, 0.48F, a0, a1, COOL, energy * 0.8F, energy * 0.8F);
            pose.popPose();
        }

        // drei Segmentringe, gestaffelt zum Kern hin (Kegelform), gegenlaeufig
        float[][] cols = {COOL, JET, rim};
        float[] radius = {0.4F, 0.3F, 0.2F};
        float[] z = {-0.2F, -0.05F, 0.1F};
        int[] segs = {8, 6, 4};
        for (int r = 0; r < 3; r++) {
            float rot = t * (0.04F + 0.025F * r) * speed * (r % 2 == 0 ? 1 : -1);
            pose.pushPose();
            pose.translate(0.0, 0.0, z[r]);
            Matrix4f rm = pose.last().pose();
            int n = segs[r];
            for (int k = 0; k < n; k++) {
                float start = rot + k * Mth.TWO_PI / n;
                float len = Mth.TWO_PI / n * 0.7F;
                int steps = 6;
                for (int q = 0; q < steps; q++) {
                    float a0 = start + len * q / steps, a1 = start + len * (q + 1) / steps;
                    annulusXY(glow, rm, radius[r] - 0.03F, radius[r] + 0.03F, a0, a1, cols[r], energy, energy * 0.6F);
                }
            }
            pose.popPose();
        }

        // Fokuslinse mit Streben zum innersten Ring
        float pulse = 0.6F + 0.4F * Mth.sin(t * 0.15F * speed);
        cube(glow, m, 0, 0, 0.28F, 0.05F + 0.02F * pulse, 1.0F, 1.0F, 1.0F, energy);
        for (int k = 0; k < 4; k++) {
            float ang = k * Mth.HALF_PI + Mth.PI / 4 + t * 0.02F * speed;
            RenderUtil.segment(glow, m, Mth.cos(ang) * 0.2F, Mth.sin(ang) * 0.2F, 0.1F, 0, 0, 0.28F, 0.008F,
                    rim[0], rim[1], rim[2], energy * 0.7F, energy * 0.7F);
        }
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
