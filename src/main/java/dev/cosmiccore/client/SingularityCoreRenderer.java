package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.arc;
import static dev.cosmiccore.client.RenderUtil.cube;
import static dev.cosmiccore.client.RenderUtil.glowSphere;
import static dev.cosmiccore.client.RenderUtil.hash;
import static dev.cosmiccore.client.RenderUtil.lerp;
import static dev.cosmiccore.client.RenderUtil.orbitPoint;
import static dev.cosmiccore.client.RenderUtil.ring;
import static dev.cosmiccore.client.RenderUtil.segment;
import static dev.cosmiccore.client.RenderUtil.sphere;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.cosmiccore.block.CorePhase;
import dev.cosmiccore.block.ModProperties;
import dev.cosmiccore.block.entity.SingularityCoreBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Zeichnet die Singularitaet: Sternenhimmel-Kugel (End-Gateway-Shader) mit Korona, rotierende Ringe,
 * Orbit-Sterne und bei Betrieb Energiebogen zu den Spulen sowie Strahlen zu den Fokuskristallen.
 * <ul>
 *   <li>Die Kugel und alle Ringe wachsen mit der Leistungsstufe und der Baugroesse.</li>
 *   <li>Die Farbe verschiebt sich mit der Instabilitaet von Blau/Violett ueber
 *       Orange nach Rot, und der Kern beginnt zu zittern.</li>
 *   <li>Mit Himmelsverbindung schiesst ein Sternenlicht-Strahl in den Himmel und ueber der Decke kreist
 *       eine Akkretionsscheibe.</li>
 * </ul>
 */
public class SingularityCoreRenderer implements BlockEntityRenderer<SingularityCoreBlockEntity> {
    private static final float[] BASE1 = {0.25F, 0.9F, 1.0F};
    private static final float[] BASE2 = {0.65F, 0.35F, 1.0F};
    private static final float[] BASE3 = {0.95F, 0.3F, 0.9F};
    private static final float[] HOT = {1.0F, 0.55F, 0.1F};
    private static final float[] CRITICAL = {1.0F, 0.12F, 0.15F};

    public SingularityCoreRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public AABB getRenderBoundingBox(SingularityCoreBlockEntity be) {
        return AABB.INFINITE; // Himmelsstrahl
    }

    @Override
    public boolean shouldRenderOffScreen(SingularityCoreBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    private static float[] heat(float[] base, float heat) {
        float[] c = lerp(base, HOT, (heat - 0.3F) / 0.4F);
        return lerp(c, CRITICAL, (heat - 0.7F) / 0.3F);
    }

    @Override
    public void render(SingularityCoreBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        CorePhase phase = be.getBlockState().getValue(ModProperties.PHASE);
        if (phase == CorePhase.DORMANT) return;

        final float t = (float) (level.getGameTime() % 144000L) + partialTick;
        final boolean unstable = phase == CorePhase.UNSTABLE;
        final boolean active = phase == CorePhase.ACTIVE || unstable;
        final float growth = be.getGrowth();
        final float reach = be.getReach();
        final float heatLevel = active ? be.getInstability() / 100.0F : 0.0F;
        final float speed = (unstable ? 2.2F : active ? 1.4F : 0.55F) * (1.0F + 0.35F * growth);
        final float intensity = unstable ? 1.0F : active ? 0.85F : 0.45F;
        final float flicker = unstable ? 0.55F + 0.45F * hash((int) (t * 0.9F) * 7919) * 0.5F + 0.2F * Mth.sin(t * 3.1F) : 1.0F;

        // Sternenlicht-Strahl in den Himmel (vor dem Zentrieren, BeaconRenderer zentriert selbst)
        if (active && be.hasSkyLink()) {
            float[] bc = heat(lerp(BASE2, BASE1, 0.5F + 0.5F * Mth.sin(t * 0.05F)), heatLevel);
            int color = FastColor.ARGB32.color(255, (int) (bc[0] * 255), (int) (bc[1] * 255), (int) (bc[2] * 255));
            float beam = 0.12F + 0.12F * growth + 0.08F * Math.max(0.0F, be.getCosmic() - 1.0F);
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F,
                    level.getGameTime(), 1, 1024, color, beam, beam + 0.12F);
        }

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);

        float[] c1 = unstable ? lerp(heat(BASE1, heatLevel), CRITICAL, 0.5F) : heat(BASE1, heatLevel);
        float[] c2 = unstable ? lerp(heat(BASE2, heatLevel), CRITICAL, 0.3F) : heat(BASE2, heatLevel);
        float[] c3 = unstable ? lerp(heat(BASE3, heatLevel), CRITICAL, 0.5F) : heat(BASE3, heatLevel);

        // Die ganze Struktur als kosmisches Geraet: erst die Sternenfeld-Teile (eigene RenderTypes) ...
        CosmicMachineFx.Params fx = new CosmicMachineFx.Params(be.getStructureSize() / 2, t, speed, intensity, flicker,
                growth, active, c1, c2, c3);
        CosmicMachineFx.starfield(pose, buffers, fx);

        pose.pushPose();
        // kurz vor dem Kollaps zittert der Kern
        if (heatLevel > 0.7F) {
            float amp = (heatLevel - 0.7F) * 0.2F;
            int s = (int) (t * 2.0F);
            pose.translate(hash(s) * amp, hash(s + 17) * amp, hash(s + 31) * amp);
        }

        // 1) Kugel mit Sternenhimmel-Shader - waechst mit der Leistungsstufe
        float maxRadius = Math.max(0.3F, reach * 0.36F);
        float radius = active ? Mth.lerp(growth, 0.26F, maxRadius) : 0.22F;
        radius += 0.035F * Mth.sin(t * 0.18F * speed);
        if (unstable) radius += 0.03F * Mth.sin(t * 2.3F);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * 0.8F * speed));
        sphere(buffers.getBuffer(RenderType.endGateway()), pose.last().pose(), radius, 12, 18);
        pose.popPose();

        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();

        // Korona
        if (active) {
            float pulse = 0.5F + 0.5F * Mth.sin(t * 0.2F * speed);
            glowSphere(glow, m, radius * (1.12F + 0.05F * pulse), 10, 16, c2, (0.10F + 0.08F * pulse) * flicker);
            glowSphere(glow, m, radius * (1.3F + 0.08F * pulse), 10, 16, c1, (0.05F + 0.05F * heatLevel) * flicker);
        }

        // 2) Ringe + Orbit-Sterne + Strahlen (additiv leuchtend)
        float rs = (reach / 1.5F) * (0.85F + 0.15F * growth);
        ring(pose, glow, 0.95F * rs, 0.035F * rs, t * 1.3F * speed, 70.0F, 0.0F, t, c1, intensity * flicker);
        ring(pose, glow, 1.15F * rs, 0.03F * rs, -t * 1.0F * speed, 20.0F, 55.0F, t + 40.0F, c2, intensity * flicker);
        ring(pose, glow, 1.35F * rs, 0.045F * rs, t * 0.7F * speed, -40.0F, 120.0F, t + 90.0F, c3, intensity * flicker * 0.9F);
        if (growth > 0.45F) {
            ring(pose, glow, 0.75F * rs, 0.025F * rs, -t * 1.8F * speed, -65.0F, -30.0F, t + 13.0F, c3, intensity * flicker * growth);
        }

        int stars = 7 + Math.round(growth * 8);
        for (int i = 0; i < stars; i++) {
            float orbit = (0.65F + 0.8F * ((i * 37 % 11) / 10.0F)) * rs;
            float inc = (i * 53 % 90) - 45.0F;
            float node = i * 51.4F;
            float ang = t * (0.06F + 0.015F * (i % 7)) * speed + i * 1.7F;
            float[] p = orbitPoint(orbit, ang, inc, node);
            float s = 0.028F + 0.01F * Mth.sin(t * 0.3F + i);
            cube(glow, m, p[0], p[1], p[2], s, 0.85F, 0.95F, 1.0F, 0.9F * flicker);
            float[] q = orbitPoint(orbit, ang - 0.28F, inc, node);
            segment(glow, m, p[0], p[1], p[2], q[0], q[1], q[2], 0.018F, 0.5F, 0.7F, 1.0F, 0.7F * flicker, 0.0F);
        }

        if (active) {
            int seed = (int) (t * 0.5F);
            float w = (0.035F + 0.015F * Mth.sin(t * 0.5F)) * (1.0F + growth);
            // vertikale Strahlen zu den Fokuskristallen
            segment(glow, m, 0, radius * 0.7F, 0, 0, reach, 0, w, c2[0], c2[1], c2[2], 0.9F * flicker, 0.15F);
            segment(glow, m, 0, -radius * 0.7F, 0, 0, -reach, 0, w, c2[0], c2[1], c2[2], 0.9F * flicker, 0.15F);
            // gezackte Energiebogen zu den Spulen
            arc(glow, m, reach, 0, 0, seed * 3 + 1, c1, flicker, unstable);
            arc(glow, m, -reach, 0, 0, seed * 3 + 2, c1, flicker, unstable);
            arc(glow, m, 0, 0, reach, seed * 3 + 3, c1, flicker, unstable);
            arc(glow, m, 0, 0, -reach, seed * 3 + 4, c1, flicker, unstable);
            if (be.getStructureSize() >= 9) {
                float d = (be.getStructureSize() / 2 - 1) - 0.45F;
                arc(glow, m, d, 0, d, seed * 3 + 5, c3, flicker, unstable);
                arc(glow, m, -d, 0, d, seed * 3 + 6, c3, flicker, unstable);
                arc(glow, m, d, 0, -d, seed * 3 + 7, c3, flicker, unstable);
                arc(glow, m, -d, 0, -d, seed * 3 + 8, c3, flicker, unstable);
            }
        }

        // Akkretionsscheibe ueber der Decke: der Reaktor saugt Sternenlicht aus dem Kosmos
        if (active && be.hasSkyLink()) {
            int half = be.getStructureSize() / 2;
            float cosmic = Mth.clamp(be.getCosmic(), 0.5F, 3.0F);
            pose.pushPose();
            pose.translate(0.0, half + 1.3, 0.0);
            ring(pose, glow, half + 0.6F, 0.06F, t * 0.4F, 0.0F, 0.0F, t, c2, 0.55F * cosmic * flicker);
            ring(pose, glow, half - 0.2F, 0.04F, -t * 0.7F, 0.0F, 0.0F, t + 30.0F, c1, 0.45F * cosmic * flicker);
            ring(pose, glow, half + 1.3F, 0.025F, t * 0.25F, 0.0F, 0.0F, t + 60.0F, c3, 0.35F * cosmic * flicker);
            Matrix4f top = pose.last().pose();
            int spokes = 6 + half;
            for (int i = 0; i < spokes; i++) {
                float a = t * 0.02F + i * Mth.TWO_PI / spokes;
                float r = half + 0.6F;
                float sx = Mth.cos(a) * r, sz = Mth.sin(a) * r;
                // Sternenlicht spiralt von der Scheibe zum Zentrum (und dann durch den Strahl nach unten)
                float a2 = a + 0.8F;
                segment(glow, top, sx, 0, sz, Mth.cos(a2) * 0.3F, -0.4F, Mth.sin(a2) * 0.3F, 0.03F,
                        c1[0], c1[1], c1[2], 0.0F, 0.6F * flicker);
                cube(glow, top, sx, 0, sz, 0.05F, 1.0F, 1.0F, 1.0F, 0.9F * flicker);
            }
            pose.popPose();
        }

        pose.popPose(); // Zittern

        // ... dann die leuchtenden Teile der Maschine
        CosmicMachineFx.glow(pose, buffers.getBuffer(RenderType.lightning()), fx);
        pose.popPose();
    }
}
