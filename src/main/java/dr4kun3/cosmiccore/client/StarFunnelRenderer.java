package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.cube;
import static dr4kun3.cosmiccore.client.RenderUtil.flatCircle;
import static dr4kun3.cosmiccore.client.RenderUtil.lerp;
import static dr4kun3.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dr4kun3.cosmiccore.block.CorePhase;
import dr4kun3.cosmiccore.block.ModProperties;
import dr4kun3.cosmiccore.block.entity.StarFunnelBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Der Sternentrichter saugt kosmische Energie aus dem All: Ueber dem Block oeffnet sich ein hoher, schlanker Wirbeltrichter,
 * der weit in den Himmel reicht. Spiralarme aus Sternenlicht drehen sich nach unten zum Trichter, Ringe kreisen in mehreren
 * Hoehen. Nur sichtbar, wenn der Reaktor darunter laeuft und der Himmel frei ist.
 */
public class StarFunnelRenderer implements BlockEntityRenderer<StarFunnelBlockEntity> {
    private static final float HEIGHT = 34.0F;
    private static final float TOP_RADIUS = 5.0F;
    private static final float[] SKY = {0.55F, 0.75F, 1.0F};
    private static final float[] VIOLET = {0.65F, 0.35F, 1.0F};
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};
    private static final float[] CRITICAL = {1.0F, 0.2F, 0.2F};
    // (Trichter-Koerper: siehe body())

    public StarFunnelRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public AABB getRenderBoundingBox(StarFunnelBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(TOP_RADIUS + 1, 0, TOP_RADIUS + 1).expandTowards(0, HEIGHT + 1, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(StarFunnelBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }

    /**
     * Animierter Trichter-Koerper (wie Kern, Emitter und Kristall): Kelch aus Sternenfeld, darauf gegenlaeufig
     * drehende Lichtringe und Spiralbahnen, ein gleissender Rand und Licht, das nach unten in den Hals stroemt.
     */
    private static void body(PoseStack pose, MultiBufferSource buffers, float t, float energy, boolean unstable) {
        float[] a = unstable ? CRITICAL : SKY, b = unstable ? CRITICAL : VIOLET;
        final float y0 = 0.05F, y1 = 0.9F, r0 = 0.16F, r1 = 0.5F, exp = 1.6F;
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(t * 0.6F));
        RenderUtil.starTrumpet(buffers.getBuffer(RenderType.endPortal()), pose.last().pose(), y0, y1, r0, r1, exp, 24, 6);
        pose.popPose();
        VertexConsumer vc = buffers.getBuffer(CosmicRenderTypes.glow());
        Matrix4f m = pose.last().pose();
        int rings = 6;
        for (int k = 0; k <= rings; k++) {
            float f = k / (float) rings;
            float y = Mth.lerp(f, y0, y1), r = r0 + (r1 - r0) * (float) Math.pow(f, exp) + 0.01F;
            float pulse = 0.5F + 0.5F * Mth.sin(t * 0.18F + f * 6.0F); // Licht laeuft von oben nach unten
            Matrix4f rm = new Matrix4f(m).translate(0, y, 0).rotateY(t * 0.03F * (k % 2 == 0 ? 1 : -1));
            flatCircle(vc, rm, r, k == rings ? 0.02F : 0.007F, 40, lerp(b, a, f), energy * (k == rings ? 1.0F : 0.3F + 0.5F * pulse));
        }
        for (int k = 0; k < 8; k++) {
            float px = 0, py = 0, pz = 0;
            for (int i = 0; i <= 12; i++) {
                float f = i / 12.0F;
                float ang = k * Mth.TWO_PI / 8 - f * 2.0F - t * 0.04F;
                float r = r0 + (r1 - r0) * (float) Math.pow(f, exp) + 0.012F;
                float x = Mth.cos(ang) * r, y = Mth.lerp(f, y0, y1), z = Mth.sin(ang) * r;
                if (i > 0) segment(vc, m, px, py, pz, x, y, z, 0.005F, a[0], a[1], a[2], energy * 0.6F, energy * 0.6F);
                px = x;
                py = y;
                pz = z;
            }
        }
        for (int k = 0; k < 6; k++) {
            float f = 1.0F - Mth.frac(t * 0.025F + k / 6.0F); // faellt nach unten in den Hals
            float ang = k * 1.7F + f * 3.0F;
            float r = (r0 + (r1 - r0) * (float) Math.pow(f, exp)) * 0.85F;
            cube(vc, m, Mth.cos(ang) * r, Mth.lerp(f, y0, y1), Mth.sin(ang) * r, 0.02F, WHITE[0], WHITE[1], WHITE[2], energy * Mth.sin(f * Mth.PI));
        }
        RenderUtil.glowSphere(vc, new Matrix4f(m).translate(0, y0 + 0.05F, 0), 0.12F, 8, 12, a, 0.45F * energy);
        pose.popPose();
    }

    private static float radiusAt(float h) {
        // Trompetenform: unten eng, oben weit
        float f = h / HEIGHT;
        return 0.3F + TOP_RADIUS * f * f * f;
    }

    @Override
    public void render(StarFunnelBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        BlockState below = level.getBlockState(be.getBlockPos().below());
        if (!below.hasProperty(ModProperties.PHASE)) return;
        CorePhase phase = below.getValue(ModProperties.PHASE);
        boolean running = phase == CorePhase.ACTIVE || phase == CorePhase.UNSTABLE;
        float t = (float) (level.getGameTime() % 144000L) + partialTick;
        RenderQuality.begin(be.getBlockPos());
        // verbunden mit einem gebildeten Reaktor: das Blockmodell ist ausgeblendet, hier entsteht der animierte Trichter
        if (be.getBlockState().getValue(ModProperties.LINKED)) {
            body(pose, buffers, t, running ? 1.0F : 0.4F, phase == CorePhase.UNSTABLE);
        }
        if (phase == CorePhase.DORMANT || !level.canSeeSky(be.getBlockPos().above())) return;

        float energy = running ? 0.85F : 0.25F;
        float speed = running ? 1.0F : 0.3F;
        float[] c1 = phase == CorePhase.UNSTABLE ? CRITICAL : SKY;
        float[] c2 = phase == CorePhase.UNSTABLE ? CRITICAL : VIOLET;

        pose.pushPose();
        pose.translate(0.5, 0.85, 0.5);
        VertexConsumer vc = buffers.getBuffer(CosmicRenderTypes.glow());
        Matrix4f m = pose.last().pose();

        // Spiralarme: Sternenlicht stroemt von oben nach unten in den Trichter
        int arms = 5, steps = RenderQuality.scale(48, 16);
        for (int k = 0; k < arms; k++) {
            float lx = 0, ly = 0, lz = 0;
            boolean first = true;
            for (int i = 0; i <= steps; i++) {
                float f = i / (float) steps;
                float h = f * HEIGHT;
                float r = radiusAt(h);
                float ang = k * Mth.TWO_PI / arms + f * 7.0F + t * 0.03F * speed;
                float x = Mth.cos(ang) * r, z = Mth.sin(ang) * r;
                if (!first) {
                    // Helligkeit wandert nach unten (Energie fliesst zum Reaktor)
                    float flow = 0.5F + 0.5F * Mth.sin(f * 14.0F + t * 0.15F * speed + k);
                    float a = energy * (1.0F - f * 0.75F) * (0.3F + 0.7F * flow);
                    float[] col = lerp(c1, c2, f);
                    segment(vc, m, lx, ly, lz, x, h, z, 0.012F + 0.03F * f, col[0], col[1], col[2], a, a);
                }
                lx = x;
                ly = h;
                lz = z;
                first = false;
            }
        }

        // kreisende Ringe in mehreren Hoehen (Trichterwand)
        for (int i = 1; i <= 7; i++) {
            float h = HEIGHT * i / 8.0F;
            float r = radiusAt(h);
            pose.pushPose();
            pose.translate(0.0, h, 0.0);
            float pulse = 0.5F + 0.5F * Mth.sin(t * 0.08F * speed - i * 0.9F);
            flatCircle(vc, pose.last().pose(), r, 0.008F + 0.004F * i, 64, lerp(c1, c2, i / 8.0F), energy * (0.2F + 0.4F * pulse));
            pose.popPose();
        }

        // Sternensplitter, die in den Trichter fallen
        int motes = RenderQuality.scale(running ? 24 : 8, 4);
        for (int i = 0; i < motes; i++) {
            float f = 1.0F - Mth.frac(t * 0.006F * speed + i * 0.618F);
            float h = f * HEIGHT;
            float r = radiusAt(h) * 0.9F;
            float ang = i * 2.39F + f * 5.0F + t * 0.03F * speed;
            cube(vc, m, Mth.cos(ang) * r, h, Mth.sin(ang) * r, 0.02F + 0.04F * f, WHITE[0], WHITE[1], WHITE[2], energy * (1.0F - f * 0.5F));
        }

        // gleissender Hals des Trichters
        flatCircle(vc, m, 0.3F, 0.04F, 32, WHITE, energy);
        pose.popPose();
    }
}
