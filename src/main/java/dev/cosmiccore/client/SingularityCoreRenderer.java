package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.annulusXY;
import static dev.cosmiccore.client.RenderUtil.cube;
import static dev.cosmiccore.client.RenderUtil.hash;
import static dev.cosmiccore.client.RenderUtil.lerp;
import static dev.cosmiccore.client.RenderUtil.quad2;
import static dev.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.cosmiccore.block.CorePhase;
import dev.cosmiccore.block.ModProperties;
import dev.cosmiccore.block.entity.SingularityCoreBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Die ganze Struktur als schwarzes Loch - ein zusammenhaengendes Bild statt vieler Einzeleffekte:
 * <ul>
 *   <li><b>Ereignishorizont:</b> tiefschwarze Kugel, waechst mit der Leistungsstufe.</li>
 *   <li><b>Photonenring + Gravitationslinse:</b> immer zum Betrachter gedreht; die Rueckseite der Scheibe
 *       woelbt sich als Lichtbogen ueber und unter das Loch.</li>
 *   <li><b>Akkretionsscheibe:</b> innen weissgluehend, aussen violett, innen schneller rotierend (differentiell),
 *       mit Doppler-Aufhellung auf der dem Betrachter entgegenkommenden Seite.</li>
 *   <li><b>Eindaemmungsschild:</b> die Resonanzschild-Emitter halten ein geodaetisches Kraftfeld um das Loch,
 *       siehe {@link ShieldFx}.</li>
 *   <li><b>Jets:</b> entlang der Pole zu den Fokuskristallen; mit Himmelssicht schiesst der obere Jet ins All.</li>
 *   <li><b>Huelle:</b> alle Strukturbloecke verschmelzen zu einer Sternenfeld-Panzerung, siehe {@link ReactorShellRenderer}.</li>
 * </ul>
 * Instabilitaet: die Scheibe wird heisser (rot), unruhiger und taumelt, das Loch zittert.
 * <p>
 * Reihenfolge der Puffer beachten: ein getBuffer() mit anderem RenderType schliesst den vorherigen Puffer ab.
 */
public class SingularityCoreRenderer implements BlockEntityRenderer<SingularityCoreBlockEntity> {
    private static final float[] HOT = {1.0F, 0.93F, 0.8F};
    private static final float[] WARM = {1.0F, 0.55F, 0.22F};
    private static final float[] COOL = {0.62F, 0.28F, 0.95F};
    private static final float[] JET = {0.6F, 0.78F, 1.0F};
    private static final float[] CRITICAL = {1.0F, 0.12F, 0.12F};

    public SingularityCoreRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public AABB getRenderBoundingBox(SingularityCoreBlockEntity be) {
        return AABB.INFINITE; // Jet in den Himmel
    }

    @Override
    public boolean shouldRenderOffScreen(SingularityCoreBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
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
        final int half = be.getStructureSize() / 2;
        final float heat = active ? Mth.clamp(be.getInstability() / 100.0F, 0.0F, 1.0F) : 0.0F;
        final float form = be.getFormProgress(partialTick);
        final float energy = (unstable ? 1.0F : active ? 0.85F : 0.3F)
                * (unstable ? 0.75F + 0.25F * (0.5F + 0.5F * hash((int) (t * 0.8F))) : 1.0F);
        // waehrend der Verwandlung blendet das schwarze Loch langsam auf
        final float coreFade = Mth.clamp((form - 0.35F) / 0.65F, 0.0F, 1.0F);
        final float spin = (unstable ? 1.7F : active ? 1.0F : 0.25F) * (1.0F + 0.5F * growth);

        // 0) mit Himmelssicht setzt sich der obere Jet als Lichtsaeule bis ins All fort
        if (active && be.hasSkyLink()) {
            float[] jc = lerp(JET, CRITICAL, heat * heat);
            int color = FastColor.ARGB32.color(255, (int) (jc[0] * 255), (int) (jc[1] * 255), (int) (jc[2] * 255));
            float w = 0.08F + 0.08F * growth + 0.05F * Math.max(0.0F, be.getCosmic() - 1.0F);
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F,
                    level.getGameTime(), half, 1024, color, w, w + 0.1F);
        }

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);

        // 1) einheitliche kosmische Huelle (ersetzt Gehaeuse, Ports, Rahmen und Glas)
        ReactorShellRenderer.Params shell = new ReactorShellRenderer.Params(level, be.getBlockPos(), be.getStructureSize(), t,
                form, Math.max(0.35F, energy), heat, active, unstable);
        ReactorShellRenderer.solid(pose, buffers, shell);

        // Groessen
        float rh = (active ? Mth.lerp(growth, 0.2F, Math.max(0.26F, reach * 0.27F)) : 0.15F) * Math.max(0.05F, coreFade);
        rh *= 1.0F + 0.03F * Mth.sin(t * 0.1F);
        float inner = rh * 1.55F;
        float outer = Math.max(inner + 0.2F, reach - 0.12F);
        outer = inner + (outer - inner) * (active ? 0.6F + 0.4F * growth : 0.45F);

        // Zittern kurz vor dem Kollaps
        pose.pushPose();
        if (heat > 0.7F) {
            float amp = (heat - 0.7F) * 0.15F;
            int s = (int) (t * 2.0F);
            pose.translate(hash(s) * amp, hash(s + 17) * amp, hash(s + 31) * amp);
        }

        // 2) Ereignishorizont
        RenderUtil.solidSphere(buffers.getBuffer(RenderType.debugQuads()), pose.last().pose(), rh, 14, 20, 0.0F, 0.0F, 0.0F);

        // 3) alles Leuchtende
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 center = Vec3.atCenterOf(be.getBlockPos());
        double dx = cam.x - center.x, dy = cam.y - center.y, dz = cam.z - center.z;
        float camYaw = (float) Math.atan2(dx, dz);
        float camPitch = (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));

        // Scheibe leicht gekippt; bei Instabilitaet taumelt sie
        float tilt = 7.0F + (heat > 0.5F ? (heat - 0.5F) * 16.0F * Mth.sin(t * 0.21F) : 0.0F);
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(tilt));
        pose.mulPose(Axis.ZP.rotationDegrees(tilt * 0.4F * Mth.sin(t * 0.013F)));
        accretionDisk(pose.last().pose(), glow, inner, outer, t, spin, energy * coreFade, heat, camYaw);
        pose.popPose();

        lensing(pose, glow, rh, inner, t, energy * coreFade, heat, camYaw, camPitch);
        ShieldFx.render(pose.last().pose(), glow, be.getStructureSize(), reach, t, energy * coreFade, heat, be.getShield(), unstable);
        if (active) jets(pose.last().pose(), glow, rh, reach, t, energy * coreFade, heat, growth);
        pose.popPose(); // Zittern

        // leuchtende Panele der Huelle
        ReactorShellRenderer.glow(pose.last().pose(), glow, shell);
        pose.popPose();
    }

    private static float[] diskColor(float f, float heat) {
        float[] c = f < 0.35F ? lerp(HOT, WARM, f / 0.35F) : lerp(WARM, COOL, (f - 0.35F) / 0.65F);
        return lerp(c, CRITICAL, heat * heat * 0.8F);
    }

    /** Flache Scheibe in der XZ-Ebene aus Ringbaendern; innen schneller (differentielle Rotation). */
    private static void accretionDisk(Matrix4f m, VertexConsumer vc, float inner, float outer, float t, float spin,
                                      float energy, float heat, float camYaw) {
        final int bands = 16, seg = 72;
        for (int b = 0; b < bands; b++) {
            float f0 = b / (float) bands, f1 = (b + 1) / (float) bands;
            float r0 = Mth.lerp(f0, inner, outer), r1 = Mth.lerp(f1, inner, outer);
            float[] col = diskColor(f0, heat);
            float base = energy * (float) Math.pow(1.0F - f0, 0.9) * Math.min(1.0F, 0.35F + b * 0.4F);
            float omega = spin * 0.05F / (float) Math.pow(0.35F + f0, 1.5);
            float rot = t * omega;
            float turb = 1.0F + heat * 2.0F;
            for (int s = 0; s < seg; s++) {
                float a0 = Mth.TWO_PI * s / seg, a1 = Mth.TWO_PI * (s + 1) / seg;
                float am = (a0 + a1) * 0.5F;
                // Materieschlieren
                float streak = 0.55F + 0.3F * Mth.sin(3.0F * (am - rot) + b * 1.7F)
                        + 0.15F * Mth.sin(11.0F * (am - rot * 1.3F) + b * 2.9F) * turb;
                // Doppler: die auf den Betrachter zukommende Seite leuchtet heller
                float doppler = 0.65F + 0.35F * Mth.sin(am - camYaw);
                float a = Mth.clamp(base * streak * doppler, 0.0F, 1.0F);
                quad2(vc, m, r0 * Mth.cos(a0), 0, r0 * Mth.sin(a0), r1 * Mth.cos(a0), 0, r1 * Mth.sin(a0),
                        r1 * Mth.cos(a1), 0, r1 * Mth.sin(a1), r0 * Mth.cos(a1), 0, r0 * Mth.sin(a1), col[0], col[1], col[2], a);
            }
        }
    }

    /** Photonenring, Gravitationslinse und Halo - als Billboard immer zum Betrachter gedreht. */
    private static void lensing(PoseStack pose, VertexConsumer vc, float rh, float inner, float t, float energy, float heat,
                                float camYaw, float camPitch) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(camYaw));
        pose.mulPose(Axis.XP.rotation(-camPitch));
        Matrix4f m = pose.last().pose();
        final int seg = 64;
        float[] ringCol = lerp(HOT, CRITICAL, heat * heat * 0.6F);
        float[] haloCol = lerp(WARM, COOL, 0.6F);
        for (int s = 0; s < seg; s++) {
            float a0 = Mth.TWO_PI * s / seg, a1 = Mth.TWO_PI * (s + 1) / seg;
            float am = (a0 + a1) * 0.5F;
            // weicher Halo um den Horizont
            annulusXY(vc, m, rh, rh * 2.3F, a0, a1, haloCol, 0.35F * energy, 0.0F);
            // Photonenring: duenn und gleissend hell
            annulusXY(vc, m, rh * 1.02F, rh * 1.1F, a0, a1, ringCol, energy, 0.2F * energy);
            // gelinste Rueckseite der Scheibe: Boegen oben (hell) und unten (schwaecher)
            float vertical = Mth.sin(am);
            float lensA = vertical > 0 ? vertical * vertical : 0.45F * vertical * vertical;
            float flow = 0.75F + 0.25F * Mth.sin(am * 5.0F - t * 0.08F);
            annulusXY(vc, m, rh * 1.15F, inner * 1.05F, a0, a1, diskColor(0.15F, heat), 0.9F * energy * lensA * flow, 0.0F);
        }
        pose.popPose();
    }

    /** Relativistische Jets entlang der Pole zu den Fokuskristallen. */
    private static void jets(Matrix4f m, VertexConsumer vc, float rh, float reach, float t, float energy, float heat, float growth) {
        float[] col = lerp(JET, CRITICAL, heat * heat * 0.7F);
        float w = 0.04F + 0.04F * growth;
        for (int side = -1; side <= 1; side += 2) {
            float y0 = side * rh * 1.05F, y1 = side * reach;
            segment(vc, m, 0, y0, 0, 0, y1, 0, w, 1.0F, 1.0F, 1.0F, energy, 0.4F * energy);
            segment(vc, m, 0, y0, 0, 0, y1, 0, w * 2.2F, col[0], col[1], col[2], 0.5F * energy, 0.15F * energy);
            // verdrillte Plasmafaeden
            for (int strand = 0; strand < 3; strand++) {
                float lx = 0, ly = y0, lz = 0;
                for (int i = 1; i <= 10; i++) {
                    float s = i / 10.0F;
                    float y = Mth.lerp(s, y0, y1);
                    float ang = t * 0.25F + s * 9.0F + strand * Mth.TWO_PI / 3;
                    float r = w * (1.2F + 1.5F * s);
                    float x = Mth.cos(ang) * r, z = Mth.sin(ang) * r;
                    float a = 0.6F * energy * (1 - s * 0.5F);
                    segment(vc, m, lx, ly, lz, x, y, z, 0.01F, col[0], col[1], col[2], a, a);
                    lx = x;
                    ly = y;
                    lz = z;
                }
            }
            // Knoten, die den Jet entlang schiessen
            for (int k = 0; k < 2; k++) {
                float s = Mth.frac(t * 0.03F + k * 0.5F);
                cube(vc, m, 0, Mth.lerp(s, y0, y1), 0, w * 1.3F * (1 - 0.5F * s), 1.0F, 1.0F, 1.0F, energy * (1 - s));
            }
        }
    }
}
