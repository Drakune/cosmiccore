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
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import dev.cosmiccore.block.entity.SingularityCoreBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

/**
 * Resonanzspule und Fokuskristall als ruhige Teile des schwarzen Lochs (das Blockmodell ist bei gebildeter
 * Struktur ausgeblendet):
 * <ul>
 *   <li>Resonanzschild-Emitter: schwebendes Enderauge mit Fokusring zum Kern.</li>
 *   <li>Kristall: schwebender Sternenfeld-Oktaeder mit leuchtenden Kanten - er buendelt die Jets.</li>
 * </ul>
 * Erst Sternenfeld, dann Glow: ein getBuffer() mit anderem RenderType schliesst den vorherigen Puffer ab.
 */
public class ResonatorRenderer implements BlockEntityRenderer<ResonatorBlockEntity> {
    private static final float[] HOT = {1.0F, 0.8F, 0.55F};
    private static final float[] COOL = {0.62F, 0.28F, 0.95F};
    private static final float[] JET = {0.6F, 0.78F, 1.0F};
    private static final float[] CRITICAL = {1.0F, 0.12F, 0.12F};
    private static final float[] EYE_GLOW = {0.3F, 1.0F, 0.65F};
    private static final float[] EYE_BODY = {0.08F, 0.36F, 0.3F};
    private static final float[] IRIS_IN = {0.62F, 1.0F, 0.72F};
    private static final float[] IRIS_OUT = {0.12F, 0.7F, 0.45F};
    private static final float[] PUPIL = {0.01F, 0.02F, 0.02F};
    private static final float[] SHINE = {0.95F, 1.0F, 0.97F};

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

        // waehrend der Aufbau-Animation wird der Block gerade in den Kern gesaugt - erst danach erscheint er wieder
        int[] core = be.findCoreOffset(level.getGameTime());
        if (core != null && level.getBlockEntity(be.getBlockPos().offset(core[0], core[1], core[2])) instanceof SingularityCoreBlockEntity c
                && c.getFormProgress(partialTick) < 0.7F) {
            return;
        }

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        if (be.getBlockState().is(ModBlocks.FOCUS_CRYSTAL.get())) {
            renderCrystal(pose, buffers, t + offset, speed, energy, unstable);
        } else {
            renderCoil(pose, buffers, level, be.getBlockPos(), core, t + offset, speed, energy, rim, unstable ? 1.0F : 0.0F);
        }
        pose.popPose();
    }

    /**
     * Schild-Emitter als animiertes 3D-Enderauge: undurchsichtige Kugel mit Iris und Schlitzpupille, die dem
     * Betrachter folgt, ab und zu den Blick springen laesst und blinzelt. Die Pupille weitet sich bei Instabilitaet.
     * Darueber eine Projektorschale, die das Feld zum Kern projiziert.
     */
    private static void renderCoil(PoseStack pose, MultiBufferSource buffers, Level level, BlockPos pos, int[] core, float t,
                                   float speed, float energy, float[] rim, float heat) {
        float bob = 0.05F * Mth.sin(t * 0.07F);
        pose.pushPose();
        pose.translate(0.0, bob, 0.0);

        // Blickrichtung: zum Betrachter, mit kleinen Blickspruengen
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = cam.x - (pos.getX() + 0.5), dy = cam.y - (pos.getY() + 0.5 + bob), dz = cam.z - (pos.getZ() + 0.5);
        int look = (int) (t / 45.0F);
        float yaw = (float) Math.atan2(dx, dz) + 0.18F * hash(look * 31);
        float pitch = (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) + 0.12F * hash(look * 17 + 3);
        float blinkPhase = (t % 110.0F) / 6.0F;
        float open = blinkPhase < 1.0F ? Math.abs(1.0F - 2.0F * blinkPhase) : 1.0F;
        float dilate = 0.3F + 0.7F * heat;

        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(yaw));
        pose.mulPose(Axis.XP.rotation(-pitch));
        Matrix4f m = pose.last().pose();
        VertexConsumer solid = buffers.getBuffer(RenderType.debugQuads());
        float r = 0.24F;
        RenderUtil.solidSphere(solid, m, r, 14, 20, EYE_BODY[0], EYE_BODY[1], EYE_BODY[2]);
        capEllipse(solid, m, r * 1.004F, r * 0.62F, r * 0.62F * Math.max(0.08F, open), 0, 0, IRIS_IN, IRIS_OUT);
        capEllipse(solid, m, r * 1.008F, r * (0.07F + 0.07F * dilate), r * 0.42F * Math.max(0.05F, open), 0, 0, PUPIL, PUPIL);
        if (open > 0.5F) {
            capEllipse(solid, m, r * 1.012F, r * 0.07F, r * 0.07F, -r * 0.22F, r * 0.24F, SHINE, SHINE);
        }
        pose.popPose();

        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        float pulse = 0.6F + 0.4F * Mth.sin(t * 0.12F * speed);
        // Projektorschale ueber dem Auge, zum Kern gerichtet (der Emitter sitzt unter dem Kern)
        float dir = core != null && core[1] != 0 ? Math.signum(core[1]) : 1.0F;
        for (int k = 0; k < 3; k++) {
            float y = dir * (0.22F + k * 0.1F);
            float rr = 0.18F + k * 0.1F;
            pose.pushPose();
            pose.translate(0.0, y, 0.0);
            ring(pose, glow, rr, 0.012F, t * (k % 2 == 0 ? 3.0F : -2.4F) * speed, 0.0F, 0.0F, t + k * 20.0F,
                    k == 1 ? EYE_GLOW : rim, energy * pulse);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Ellipse (Halbachsen a, b, Mitte ox/oy) als Kappe auf der Kugel mit Radius r, Blickrichtung +Z. */
    private static void capEllipse(VertexConsumer vc, Matrix4f m, float r, float a, float b, float ox, float oy,
                                   float[] inner, float[] outer) {
        final int rings = 5, seg = 24;
        for (int i = 0; i < rings; i++) {
            float p0 = i / (float) rings, p1 = (i + 1) / (float) rings;
            float[] c = RenderUtil.lerp(inner, outer, (p0 + p1) * 0.5F);
            for (int k = 0; k < seg; k++) {
                float f0 = Mth.TWO_PI * k / seg, f1 = Mth.TWO_PI * (k + 1) / seg;
                float[] q0 = capPoint(r, ox + a * p0 * Mth.cos(f0), oy + b * p0 * Mth.sin(f0));
                float[] q1 = capPoint(r, ox + a * p1 * Mth.cos(f0), oy + b * p1 * Mth.sin(f0));
                float[] q2 = capPoint(r, ox + a * p1 * Mth.cos(f1), oy + b * p1 * Mth.sin(f1));
                float[] q3 = capPoint(r, ox + a * p0 * Mth.cos(f1), oy + b * p0 * Mth.sin(f1));
                RenderUtil.quad2(vc, m, q0[0], q0[1], q0[2], q1[0], q1[1], q1[2], q2[0], q2[1], q2[2], q3[0], q3[1], q3[2],
                        c[0], c[1], c[2], 1.0F);
            }
        }
    }

    private static float[] capPoint(float r, float x, float y) {
        return new float[]{x, y, Mth.sqrt(Math.max(0.0F, r * r - x * x - y * y))};
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
