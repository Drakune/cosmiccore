package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.cube;
import static dev.cosmiccore.client.RenderUtil.hash;
import static dev.cosmiccore.client.RenderUtil.octahedron;
import static dev.cosmiccore.client.RenderUtil.orbitPoint;
import static dev.cosmiccore.client.RenderUtil.ring;
import static dev.cosmiccore.client.RenderUtil.segment;

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
 * Animation von Resonanzspule und Fokuskristall.
 * <ul>
 *   <li>Spule: zwei gegenlaeufige Feldringe, umlaufende Funken und ein pulsierender Kern-Glow.</li>
 *   <li>Kristall: schwebende, rotierende Kristallform (Oktaeder) mit Halo und Sternsplittern.</li>
 * </ul>
 * Geschwindigkeit, Farbe und Helligkeit haengen an der Phase der Struktur.
 */
public class ResonatorRenderer implements BlockEntityRenderer<ResonatorBlockEntity> {
    private static final float[] VIOLET = {0.6F, 0.35F, 1.0F};
    private static final float[] CYAN = {0.25F, 0.9F, 1.0F};
    private static final float[] MAGENTA = {0.95F, 0.35F, 0.9F};
    private static final float[] RED = {1.0F, 0.15F, 0.2F};
    private static final float[] PINK = {1.0F, 0.55F, 0.6F};

    public ResonatorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public AABB getRenderBoundingBox(ResonatorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.0);
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
        final float speed = unstable ? 2.4F : active ? 1.5F : 0.5F;
        final float intensity = unstable ? 1.0F : active ? 0.85F : 0.4F;
        // eigener Takt pro Block, damit nicht alle synchron pulsieren
        final float offset = (be.getBlockPos().hashCode() & 0xFF) * 0.37F;
        final float flicker = unstable ? 0.6F + 0.4F * (0.5F + 0.5F * hash((int) (t * 1.3F) + (int) offset)) : 1.0F;

        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        if (be.getBlockState().is(ModBlocks.FOCUS_CRYSTAL.get())) {
            renderCrystal(pose, glow, t + offset, speed, intensity * flicker, active, unstable);
        } else {
            renderCoil(pose, glow, t + offset, speed, intensity * flicker, active, unstable);
        }
        pose.popPose();
    }

    private static void renderCoil(PoseStack pose, VertexConsumer glow, float t, float speed, float intensity,
                                   boolean active, boolean unstable) {
        float[] a = unstable ? RED : CYAN;
        float[] b = unstable ? PINK : VIOLET;
        float pulse = 0.5F + 0.5F * Mth.sin(t * 0.25F * speed);

        // zwei gegenlaeufige Feldringe ober- und unterhalb der Wicklungsmitte
        pose.pushPose();
        pose.translate(0.0, 0.22 + 0.05 * Mth.sin(t * 0.1F * speed), 0.0);
        ring(pose, glow, 0.68F + 0.04F * pulse, 0.03F, t * 3.0F * speed, 0.0F, 0.0F, t, a, intensity);
        pose.popPose();
        pose.pushPose();
        pose.translate(0.0, -0.22 - 0.05 * Mth.sin(t * 0.1F * speed), 0.0);
        ring(pose, glow, 0.68F + 0.04F * (1 - pulse), 0.03F, -t * 3.0F * speed, 0.0F, 0.0F, t + 20.0F, b, intensity);
        pose.popPose();

        Matrix4f m = pose.last().pose();
        // umlaufende Funken auf einer Helix um die Spule
        int sparks = active ? 6 : 3;
        for (int i = 0; i < sparks; i++) {
            float ang = t * 0.15F * speed + i * Mth.TWO_PI / sparks;
            float y = Mth.sin(t * 0.08F * speed + i) * 0.45F;
            float r = 0.62F;
            float x = Mth.cos(ang) * r, z = Mth.sin(ang) * r;
            cube(glow, m, x, y, z, 0.03F, 0.9F, 0.97F, 1.0F, intensity);
            float ang2 = ang - 0.35F;
            segment(glow, m, x, y, z, Mth.cos(ang2) * r, y, Mth.sin(ang2) * r, 0.015F, a[0], a[1], a[2], intensity * 0.8F, 0.0F);
        }

        // Kurzschluss-Blitze zwischen den Ringen, wenn aktiv
        if (active) {
            int seed = (int) (t * 0.6F);
            for (int k = 0; k < (unstable ? 3 : 1); k++) {
                float ang = hash(seed * 13 + k) * Mth.PI;
                float x = Mth.cos(ang) * 0.68F, z = Mth.sin(ang) * 0.68F;
                float mx = x + hash(seed * 7 + k) * 0.12F, mz = z + hash(seed * 5 + k) * 0.12F;
                segment(glow, m, x, 0.22F, z, mx, 0.0F, mz, 0.012F, 1.0F, 1.0F, 1.0F, intensity, intensity);
                segment(glow, m, mx, 0.0F, mz, x, -0.22F, z, 0.012F, 1.0F, 1.0F, 1.0F, intensity, intensity);
            }
        }
    }

    private static void renderCrystal(PoseStack pose, VertexConsumer glow, float t, float speed, float intensity,
                                      boolean active, boolean unstable) {
        float[] a = unstable ? RED : MAGENTA;
        float[] b = unstable ? PINK : CYAN;
        float bob = 0.05F * Mth.sin(t * 0.08F * speed);

        // schwebende Kristallform, rotiert und atmet
        pose.pushPose();
        pose.translate(0.0, bob, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(t * 1.6F * speed));
        float breathe = 1.0F + 0.06F * Mth.sin(t * 0.2F * speed);
        octahedron(glow, pose.last().pose(), 0.62F * breathe, 0.85F * breathe, 0.022F, a, intensity);
        pose.mulPose(Axis.YP.rotationDegrees(45.0F));
        octahedron(glow, pose.last().pose(), 0.5F * breathe, 0.7F * breathe, 0.015F, b, intensity * 0.6F);
        pose.popPose();

        // schraeger Halo
        ring(pose, glow, 0.82F, 0.025F, -t * 2.0F * speed, 25.0F, 0.0F, t, b, intensity * 0.8F);
        if (active) {
            ring(pose, glow, 0.92F, 0.02F, t * 2.6F * speed, -25.0F, 90.0F, t + 50.0F, a, intensity * 0.7F);
        }

        // Sternsplitter im Orbit
        Matrix4f m = pose.last().pose();
        int shards = active ? 5 : 3;
        for (int i = 0; i < shards; i++) {
            float[] p = orbitPoint(0.75F + 0.1F * (i % 2), t * 0.07F * speed + i * 1.3F, (i * 47 % 80) - 40.0F, i * 72.0F);
            float s = 0.025F + 0.012F * Mth.sin(t * 0.3F + i);
            cube(glow, m, p[0], p[1], p[2], s, 1.0F, 0.95F, 1.0F, intensity);
        }
    }
}
