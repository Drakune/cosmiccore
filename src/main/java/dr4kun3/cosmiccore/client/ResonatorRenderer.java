package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.annulusXY;
import static dr4kun3.cosmiccore.client.RenderUtil.cube;
import static dr4kun3.cosmiccore.client.RenderUtil.hash;
import static dr4kun3.cosmiccore.client.RenderUtil.lerp;
import static dr4kun3.cosmiccore.client.RenderUtil.octahedron;
import static dr4kun3.cosmiccore.client.RenderUtil.ring;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dr4kun3.cosmiccore.block.CorePhase;
import dr4kun3.cosmiccore.block.ModBlocks;
import dr4kun3.cosmiccore.block.ModProperties;
import dr4kun3.cosmiccore.block.entity.ResonatorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import dr4kun3.cosmiccore.block.entity.SingularityCoreBlockEntity;
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
    private static final float[] FUNNEL_A = {0.35F, 0.75F, 1.0F};
    private static final float[] FUNNEL_B = {0.7F, 0.45F, 1.0F};
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
    public void render(ResonatorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay, net.minecraft.world.phys.Vec3 cameraPos) {
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
     * Schild-Emitter als runder Trichter aus purer Energie: Lichtringe formen einen Kelch, der sich zum Kern oeffnet,
     * Spiralbahnen drehen sich darin, Lichtpunkte steigen auf und im Hals glueht die Quelle. Aus seiner Oeffnung
     * steigt das Eindaemmungsfeld (FieldFx).
     */
    private static void renderCoil(PoseStack pose, MultiBufferSource buffers, Level level, BlockPos pos, int[] core, float t,
                                   float speed, float energy, float[] rim, float heat) {
        float dir = core != null && core[1] != 0 ? Math.signum(core[1]) : 1.0F;
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        pose.pushPose();
        pose.translate(0.0, -0.35 * dir, 0.0);
        if (dir < 0) pose.mulPose(Axis.XP.rotationDegrees(180.0F));
        Matrix4f m = pose.last().pose();
        float[] a = lerp(FUNNEL_A, CRITICAL, heat * 0.8F), b = lerp(FUNNEL_B, CRITICAL, heat * 0.6F);
        final float height = 0.85F;

        // Ringe des Kelchs
        int rings = 7;
        for (int k = 0; k <= rings; k++) {
            float f = k / (float) rings;
            float y = f * height, r = funnelRadius(f);
            float pulse = 0.5F + 0.5F * Mth.sin(t * 0.2F * speed - f * 6.0F);
            Matrix4f rm = new Matrix4f(m).translate(0, y, 0).rotateY(t * 0.03F * speed * (k % 2 == 0 ? 1 : -1));
            RenderUtil.flatCircle(glow, rm, r, 0.008F + 0.006F * f, 40, lerp(a, b, f), energy * (0.35F + 0.5F * pulse));
        }
        // Spiralbahnen auf der Kelchwand
        int lines = 10;
        for (int k = 0; k < lines; k++) {
            float[] prev = null;
            for (int i = 0; i <= 16; i++) {
                float f = i / 16.0F;
                float ang = k * Mth.TWO_PI / lines + f * 2.2F + t * 0.04F * speed;
                float r = funnelRadius(f);
                float[] q = {Mth.cos(ang) * r, f * height, Mth.sin(ang) * r};
                if (prev != null) {
                    float al = energy * (0.25F + 0.45F * f);
                    RenderUtil.segment(glow, m, prev[0], prev[1], prev[2], q[0], q[1], q[2], 0.006F, a[0], a[1], a[2], al, al);
                }
                prev = q;
            }
        }
        // aufsteigende Lichtpunkte
        for (int k = 0; k < 8; k++) {
            float f = Mth.frac(t * 0.02F * speed + k / 8.0F);
            float ang = k * 2.4F + f * 3.0F + t * 0.03F;
            float r = funnelRadius(f) * 0.9F;
            cube(glow, m, Mth.cos(ang) * r, f * height, Mth.sin(ang) * r, 0.025F, 1.0F, 1.0F, 1.0F, energy * Mth.sin(f * Mth.PI));
        }
        // gluehender Hals
        RenderUtil.glowSphere(glow, m, 0.1F + 0.02F * Mth.sin(t * 0.3F), 8, 12, b, 0.5F * energy);
        cube(glow, m, 0, 0, 0, 0.045F, 1.0F, 1.0F, 1.0F, energy);
        pose.popPose();
    }

    /** Radius des Kelchs: schmaler Hals, weit geoeffneter Rand (f = 0 unten, 1 oben). */
    private static float funnelRadius(float f) {
        return 0.07F + 0.43F * f * f;
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

        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        float[] edge = unstable ? CRITICAL : lerp(COOL, JET, 0.5F + 0.5F * Mth.sin(t * 0.05F));
        octahedron(glow, pose.last().pose(), 0.44F * breathe, 0.63F * breathe, 0.012F, edge, energy);
        ring(pose, glow, 0.62F, 0.01F, t * speed, 0.0F, 0.0F, t, JET, energy * 0.45F);
    }
}
