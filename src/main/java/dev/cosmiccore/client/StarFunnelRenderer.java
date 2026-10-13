package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.cube;
import static dev.cosmiccore.client.RenderUtil.flatCircle;
import static dev.cosmiccore.client.RenderUtil.lerp;
import static dev.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.cosmiccore.block.CorePhase;
import dev.cosmiccore.block.ModProperties;
import dev.cosmiccore.block.entity.StarFunnelBlockEntity;
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
 * Der Sternentrichter saugt kosmische Energie aus dem All: Ueber dem Block oeffnet sich ein Wirbeltrichter, der nach
 * oben weiter wird. Spiralarme aus Sternenlicht drehen sich nach unten zum Trichter, Ringe kreisen in mehreren
 * Hoehen. Nur sichtbar, wenn der Reaktor darunter laeuft und der Himmel frei ist.
 */
public class StarFunnelRenderer implements BlockEntityRenderer<StarFunnelBlockEntity> {
    private static final float HEIGHT = 16.0F;
    private static final float TOP_RADIUS = 6.0F;
    private static final float[] SKY = {0.55F, 0.75F, 1.0F};
    private static final float[] VIOLET = {0.65F, 0.35F, 1.0F};
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};
    private static final float[] CRITICAL = {1.0F, 0.2F, 0.2F};

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

    private static float radiusAt(float h) {
        // Trompetenform: unten eng, oben weit
        float f = h / HEIGHT;
        return 0.35F + TOP_RADIUS * f * f;
    }

    @Override
    public void render(StarFunnelBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        BlockState below = level.getBlockState(be.getBlockPos().below());
        if (!below.hasProperty(ModProperties.PHASE)) return;
        CorePhase phase = below.getValue(ModProperties.PHASE);
        boolean running = phase == CorePhase.ACTIVE || phase == CorePhase.UNSTABLE;
        if (phase == CorePhase.DORMANT || !level.canSeeSky(be.getBlockPos().above())) return;

        float t = (float) (level.getGameTime() % 144000L) + partialTick;
        float energy = running ? 0.85F : 0.25F;
        float speed = running ? 1.0F : 0.3F;
        float[] c1 = phase == CorePhase.UNSTABLE ? CRITICAL : SKY;
        float[] c2 = phase == CorePhase.UNSTABLE ? CRITICAL : VIOLET;

        pose.pushPose();
        pose.translate(0.5, 0.85, 0.5);
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();

        // Spiralarme: Sternenlicht stroemt von oben nach unten in den Trichter
        int arms = 6, steps = 28;
        for (int k = 0; k < arms; k++) {
            float lx = 0, ly = 0, lz = 0;
            boolean first = true;
            for (int i = 0; i <= steps; i++) {
                float f = i / (float) steps;
                float h = f * HEIGHT;
                float r = radiusAt(h);
                float ang = k * Mth.TWO_PI / arms + f * 5.0F + t * 0.03F * speed;
                float x = Mth.cos(ang) * r, z = Mth.sin(ang) * r;
                if (!first) {
                    // Helligkeit wandert nach unten (Energie fliesst zum Reaktor)
                    float flow = 0.5F + 0.5F * Mth.sin(f * 14.0F + t * 0.15F * speed + k);
                    float a = energy * (1.0F - f * 0.75F) * (0.3F + 0.7F * flow);
                    float[] col = lerp(c1, c2, f);
                    segment(vc, m, lx, ly, lz, x, h, z, 0.03F + 0.05F * f, col[0], col[1], col[2], a, a);
                }
                lx = x;
                ly = h;
                lz = z;
                first = false;
            }
        }

        // kreisende Ringe in mehreren Hoehen (Trichterwand)
        for (int i = 1; i <= 6; i++) {
            float h = HEIGHT * i / 7.0F;
            float r = radiusAt(h);
            pose.pushPose();
            pose.translate(0.0, h, 0.0);
            float pulse = 0.5F + 0.5F * Mth.sin(t * 0.08F * speed - i * 0.9F);
            flatCircle(vc, pose.last().pose(), r, 0.02F + 0.01F * i, 64, lerp(c1, c2, i / 7.0F), energy * (0.25F + 0.5F * pulse));
            pose.popPose();
        }

        // Sternensplitter, die in den Trichter fallen
        int motes = running ? 24 : 8;
        for (int i = 0; i < motes; i++) {
            float f = 1.0F - Mth.frac(t * 0.006F * speed + i * 0.618F);
            float h = f * HEIGHT;
            float r = radiusAt(h) * 0.9F;
            float ang = i * 2.39F + f * 5.0F + t * 0.03F * speed;
            cube(vc, m, Mth.cos(ang) * r, h, Mth.sin(ang) * r, 0.04F + 0.05F * f, WHITE[0], WHITE[1], WHITE[2], energy * (1.0F - f * 0.5F));
        }

        // gleissender Hals des Trichters
        flatCircle(vc, m, 0.35F, 0.08F, 32, WHITE, energy);
        pose.popPose();
    }
}
