package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.hash;
import static dev.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.cosmiccore.entity.CosmicAsteroid;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Asteroid: zerklueftete Felskugel aus Sternenfeld mit gluehenden Rissen und leuchtendem Schweif.
 * Beim Abbau mit dem Laser gluehen die Risse immer heller und der Brocken zittert.
 */
public class CosmicAsteroidRenderer extends EntityRenderer<CosmicAsteroid> {
    private static final float[] TRAIL = {0.55F, 0.7F, 1.0F};
    private static final float[] CRACK = {1.0F, 0.6F, 0.25F};

    public CosmicAsteroidRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(CosmicAsteroid entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(CosmicAsteroid a, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = a.tickCount + partialTick;
        float r = a.getSizeBlocks() * 0.5F;
        float harvest = a.getHarvest();
        int seed = a.getId() * 7919;

        pose.pushPose();
        pose.translate(0.0, a.getBbHeight() / 2.0, 0.0);
        if (harvest > 0) {
            float amp = 0.05F * harvest * r;
            pose.translate(hash((int) (t * 3)) * amp, hash((int) (t * 3) + 5) * amp, hash((int) (t * 3) + 9) * amp);
        }

        // Schweif entgegen der Flugrichtung (vor der Rotation, damit er in Weltrichtung liegt)
        VertexConsumer glow;
        Vec3 v = a.getDeltaMovement();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * (1.5F + (seed & 3))));
        pose.mulPose(Axis.XP.rotationDegrees(t * 0.9F));
        rock(buffers.getBuffer(RenderType.endGateway()), pose.last().pose(), r, seed);
        glow = buffers.getBuffer(CosmicRenderTypes.glow());
        // gluehende Risse
        Matrix4f m = pose.last().pose();
        float crackA = 0.35F + 0.65F * harvest;
        for (int k = 0; k < 5; k++) {
            float lat = hash(seed + k * 11) * 1.2F, lon = hash(seed + k * 23) * Mth.PI;
            float px = 0, py = 0, pz = 0;
            boolean first = true;
            for (int i = 0; i <= 5; i++) {
                float la = lat + hash(seed + k * 31 + i) * 0.35F, lo = lon + i * 0.3F;
                float rr = r * 1.02F;
                float x = rr * Mth.cos(la) * Mth.cos(lo), y = rr * Mth.sin(la), z = rr * Mth.cos(la) * Mth.sin(lo);
                if (!first) segment(glow, m, px, py, pz, x, y, z, 0.03F * r + 0.01F, CRACK[0], CRACK[1], CRACK[2], crackA, crackA);
                px = x;
                py = y;
                pz = z;
                first = false;
            }
        }
        pose.popPose();

        Matrix4f wm = pose.last().pose();
        double speed = v.length();
        if (speed > 1.0E-4) {
            Vec3 dir = v.normalize();
            float len = r * 7.0F;
            for (int i = 0; i < 4; i++) {
                float ox = hash(seed + i * 3) * r * 0.5F, oy = hash(seed + i * 5) * r * 0.5F, oz = hash(seed + i * 7) * r * 0.5F;
                segment(glow, wm, ox, oy, oz, (float) (-dir.x * len) + ox, (float) (-dir.y * len) + oy, (float) (-dir.z * len) + oz,
                        r * (0.35F - i * 0.06F), TRAIL[0], TRAIL[1], TRAIL[2], 0.45F, 0.0F);
            }
        }
        // Halo
        RenderUtil.glowSphere(glow, wm, r * (1.15F + 0.1F * harvest), 8, 12, harvest > 0 ? CRACK : TRAIL, 0.08F + 0.2F * harvest);
        pose.popPose();
        super.render(a, yaw, partialTick, pose, buffers, light);
    }

    /** Zerklueftete Kugel (Sternenfeld-Shader). */
    private static void rock(VertexConsumer vc, Matrix4f m, float r, int seed) {
        final int lat = 8, lon = 12;
        float[][][] pts = new float[lat + 1][lon][];
        for (int i = 0; i <= lat; i++) {
            float phi = (float) (Math.PI * i / lat - Math.PI / 2);
            for (int j = 0; j < lon; j++) {
                float lam = Mth.TWO_PI * j / lon;
                boolean pole = i == 0 || i == lat;
                float rr = r * (0.8F + 0.22F * hash(seed + (pole ? i * 1000 : i * 97 + j * 13)));
                float c = Mth.cos(phi);
                pts[i][j] = new float[]{rr * c * Mth.cos(lam), rr * Mth.sin(phi), rr * c * Mth.sin(lam)};
            }
        }
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                float[] a = pts[i][j], b = pts[i + 1][j], c = pts[i + 1][(j + 1) % lon], d = pts[i][(j + 1) % lon];
                vc.addVertex(m, a[0], a[1], a[2]);
                vc.addVertex(m, b[0], b[1], b[2]);
                vc.addVertex(m, c[0], c[1], c[2]);
                vc.addVertex(m, d[0], d[1], d[2]);
                vc.addVertex(m, d[0], d[1], d[2]);
                vc.addVertex(m, c[0], c[1], c[2]);
                vc.addVertex(m, b[0], b[1], b[2]);
                vc.addVertex(m, a[0], a[1], a[2]);
            }
        }
    }
}
