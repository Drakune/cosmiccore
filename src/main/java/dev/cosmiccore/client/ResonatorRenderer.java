package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.cube;
import static dev.cosmiccore.client.RenderUtil.flatCircle;
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
 * Animation von Resonanzspule und Fokuskristall. Das Blockmodell ist bei gebildeter Struktur ausgeblendet;
 * der Renderer ersetzt es komplett.
 * <ul>
 *   <li>Spule = Gravitations-Torus: schwebender Sternenfeld-Ring, der zum Kern ausgerichtet ist, mit rotierender
 *       Spiralscheibe in der Mitte, Kardanring und einer Plasma-Doppelhelix, die in den Kern stroemt.</li>
 *   <li>Kristall: schwebender Sternenfeld-Oktaeder mit leuchtenden Kanten, Halo, Splittern und gestaffelten
 *       Linsenringen, die das Licht auf den Kern buendeln.</li>
 * </ul>
 * Geschwindigkeit, Farbe und Helligkeit haengen an der Phase der Struktur.
 * <p>
 * Wichtig: Puffer erst holen, wenn sie gebraucht werden. Ein getBuffer() mit anderem RenderType schliesst den
 * vorherigen (nicht festen) Puffer ab - weiteres Schreiben hinein stuerzt ab. Deshalb: erst Sternenfeld, dann Glow.
 */
public class ResonatorRenderer implements BlockEntityRenderer<ResonatorBlockEntity> {
    private static final float[] VIOLET = {0.6F, 0.35F, 1.0F};
    private static final float[] CYAN = {0.25F, 0.9F, 1.0F};
    private static final float[] MAGENTA = {0.95F, 0.35F, 0.9F};
    private static final float[] WHITE = {0.95F, 0.97F, 1.0F};
    private static final float[] RED = {1.0F, 0.15F, 0.2F};
    private static final float[] PINK = {1.0F, 0.55F, 0.6F};

    public ResonatorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public AABB getRenderBoundingBox(ResonatorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(4.0); // Plasmastrom/Linsen reichen bis zum Kern
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
        final float intensity = unstable ? 1.0F : active ? 0.9F : 0.45F;
        // eigener Takt pro Block, damit nicht alle synchron pulsieren
        final float offset = (be.getBlockPos().hashCode() & 0xFF) * 0.37F;
        final float flicker = unstable ? 0.6F + 0.4F * (0.5F + 0.5F * hash((int) (t * 1.3F) + (int) offset)) : 1.0F;
        int[] core = be.findCoreOffset(level.getGameTime());

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        if (be.getBlockState().is(ModBlocks.FOCUS_CRYSTAL.get())) {
            renderCrystal(pose, buffers, core, t + offset, speed, intensity * flicker, active, unstable);
        } else {
            renderCoil(pose, buffers, core, t + offset, speed, intensity * flicker, active, unstable);
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ Gravitations-Torus

    private static void renderCoil(PoseStack pose, MultiBufferSource buffers, int[] core, float t, float speed,
                                   float intensity, boolean active, boolean unstable) {
        float[] a = unstable ? RED : CYAN;
        float[] b = unstable ? PINK : VIOLET;
        float[] c = unstable ? PINK : MAGENTA;

        // lokale +Z-Achse zeigt zum Kern
        float dx = core != null ? core[0] : 1, dz = core != null ? core[2] : 0;
        float dist = Mth.sqrt(dx * dx + dz * dz);
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(dx, dz)));
        pose.translate(0.0, 0.04 * Mth.sin(t * 0.07F * speed), 0.0); // leichtes Schweben

        // 1) Torus aus Sternenfeld, rollt um die eigene Achse
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(t * 0.8F * speed));
        RenderUtil.starTorus(buffers.getBuffer(RenderType.endPortal()), pose.last().pose(), 0.38F, 0.09F, 28, 10);
        pose.popPose();

        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());

        // 2) Raender des Torus leuchten (Ringe in der XY-Ebene)
        float pulse = 0.5F + 0.5F * Mth.sin(t * 0.25F * speed);
        ring(pose, glow, 0.38F, 0.11F + 0.015F * pulse, t * 1.5F * speed, 90.0F, 0.0F, t, b, intensity * 0.35F);
        ring(pose, glow, 0.26F, 0.012F, -t * 3.0F * speed, 90.0F, 0.0F, t + 20.0F, a, intensity);
        ring(pose, glow, 0.50F, 0.012F, t * 2.2F * speed, 90.0F, 0.0F, t + 40.0F, a, intensity * 0.8F);

        Matrix4f m = pose.last().pose();

        // 3) Spiralscheibe im Inneren (wie eine Galaxie / ein Wurmloch)
        int arms = 3;
        for (int k = 0; k < arms; k++) {
            float px = 0, py = 0;
            for (int i = 1; i <= 10; i++) {
                float r = 0.02F + 0.022F * i;
                float ang = -t * 0.12F * speed + k * Mth.TWO_PI / arms + r * 11.0F;
                float nx = Mth.cos(ang) * r, ny = Mth.sin(ang) * r;
                float fade = 1.0F - i / 11.0F;
                segment(glow, m, px, py, 0, nx, ny, 0, 0.012F + 0.006F * fade, c[0], c[1], c[2],
                        intensity * (0.4F + 0.6F * fade), intensity * (0.3F + 0.6F * fade));
                px = nx;
                py = ny;
            }
        }
        cube(glow, m, 0, 0, 0, 0.035F + 0.012F * pulse, 1.0F, 1.0F, 1.0F, intensity);

        // 4) Kardanring: kippt langsam um die Hochachse (Gyroskop-Effekt) mit Ankerpunkten
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * 1.7F * speed));
        ring(pose, glow, 0.6F, 0.014F, t * 1.1F * speed, 90.0F, 0.0F, t + 60.0F, WHITE, intensity * 0.6F);
        Matrix4f g = pose.last().pose();
        for (int i = 0; i < 4; i++) {
            float ang = i * Mth.HALF_PI + t * 0.02F * speed;
            cube(glow, g, Mth.cos(ang) * 0.6F, Mth.sin(ang) * 0.6F, 0.0F, 0.03F, b[0], b[1], b[2], intensity);
        }
        pose.popPose();

        // 5) Plasma-Doppelhelix in Richtung Kern
        if (core != null) {
            float len = dist - 0.55F;
            int n = Math.max(8, Math.round(len * 10));
            float strandAlpha = active ? intensity : intensity * 0.35F;
            for (int strand = 0; strand < 2; strand++) {
                float[] col = strand == 0 ? a : c;
                float lx = 0, ly = 0, lz = 0.05F;
                for (int i = 1; i <= n; i++) {
                    float s = i / (float) n;
                    float z = 0.05F + s * len;
                    float ang = t * 0.35F * speed + z * 7.0F + strand * Mth.PI;
                    float r = 0.2F * (1.0F - 0.75F * s);
                    float x = Mth.cos(ang) * r, y = Mth.sin(ang) * r;
                    segment(glow, m, lx, ly, lz, x, y, z, 0.016F, col[0], col[1], col[2],
                            strandAlpha * (1.0F - 0.4F * s), strandAlpha * (1.0F - 0.4F * s));
                    lx = x;
                    ly = y;
                    lz = z;
                }
            }
            // Lichtpulse wandern zum Kern
            int pulses = active ? 3 : 1;
            for (int k = 0; k < pulses; k++) {
                float s = Mth.frac(t * 0.025F * speed + k / (float) pulses);
                float z = 0.05F + s * len;
                cube(glow, m, 0, 0, z, 0.05F * (1.0F - 0.5F * s), 1.0F, 1.0F, 1.0F, intensity * (1.0F - s * 0.5F));
            }
        }

        // 6) Kurzschluss-Blitze ueber den Torus, wenn aktiv
        if (active) {
            int seed = (int) (t * 0.6F);
            for (int k = 0; k < (unstable ? 3 : 1); k++) {
                float a0 = hash(seed * 13 + k) * Mth.PI, a1 = a0 + 0.9F + 0.6F * hash(seed * 3 + k);
                float mx = Mth.cos((a0 + a1) * 0.5F) * 0.62F, my = Mth.sin((a0 + a1) * 0.5F) * 0.62F;
                segment(glow, m, Mth.cos(a0) * 0.4F, Mth.sin(a0) * 0.4F, 0, mx, my, 0.1F, 0.012F, 1, 1, 1, intensity, intensity);
                segment(glow, m, mx, my, 0.1F, Mth.cos(a1) * 0.4F, Mth.sin(a1) * 0.4F, 0, 0.012F, 1, 1, 1, intensity, intensity);
            }
        }
    }

    // ------------------------------------------------------------------ Fokuskristall

    private static void renderCrystal(PoseStack pose, MultiBufferSource buffers, int[] core, float t, float speed,
                                      float intensity, boolean active, boolean unstable) {
        float[] a = unstable ? RED : MAGENTA;
        float[] b = unstable ? PINK : CYAN;
        float bob = 0.05F * Mth.sin(t * 0.08F * speed);

        // schwebende Kristallform, rotiert und atmet
        pose.pushPose();
        pose.translate(0.0, bob, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(t * 1.6F * speed));
        float breathe = 1.0F + 0.06F * Mth.sin(t * 0.2F * speed);
        RenderUtil.starOctahedron(buffers.getBuffer(RenderType.endGateway()), pose.last().pose(), 0.5F * breathe, 0.7F * breathe);
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
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

        // Linsenringe zwischen Kristall und Kern: wandern nach innen und werden dabei kleiner
        if (core != null && core[1] != 0) {
            float dir = Math.signum(core[1]);
            float len = Math.abs(core[1]) - 0.6F;
            int lenses = 4;
            for (int k = 0; k < lenses; k++) {
                float s = Mth.frac(t * (active ? 0.012F : 0.004F) * speed + k / (float) lenses);
                float y = dir * (0.55F + s * len);
                float fade = Mth.sin(s * Mth.PI);
                float r = 0.42F * (1.0F - 0.55F * s);
                pose.pushPose();
                pose.translate(0.0, y, 0.0);
                pose.mulPose(Axis.YP.rotationDegrees(t * 2.0F + k * 30.0F));
                Matrix4f lm = pose.last().pose();
                flatCircle(glow, lm, r, 0.012F, 32, k % 2 == 0 ? a : b, intensity * fade);
                // Lichtspeichen in der Linse
                for (int sp = 0; sp < 6; sp++) {
                    float ang = sp * Mth.TWO_PI / 6;
                    RenderUtil.flatLine(glow, lm, Mth.cos(ang) * r * 0.4F, Mth.sin(ang) * r * 0.4F,
                            Mth.cos(ang) * r, Mth.sin(ang) * r, 0.006F, WHITE, intensity * fade * 0.6F);
                }
                pose.popPose();
            }
        }
    }
}
