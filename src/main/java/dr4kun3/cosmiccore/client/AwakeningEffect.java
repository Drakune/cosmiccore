package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.cube;
import static dr4kun3.cosmiccore.client.RenderUtil.flatCircle;
import static dr4kun3.cosmiccore.client.RenderUtil.hash;
import static dr4kun3.cosmiccore.client.RenderUtil.lerp;
import static dr4kun3.cosmiccore.client.RenderUtil.quadFade;
import static dr4kun3.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Das Erwachen des ultimativen Kerns - ein einmaliger, globaler Himmelseffekt fuer alle Spieler der Dimension
 * (ca. 16 Sekunden):
 * <ol>
 *   <li>Der Himmel blitzt auf, eine gigantische Lichtsaeule schiesst vom Reaktor ins All.</li>
 *   <li>Eine Schockwelle laeuft als Ring ueber den ganzen Himmel.</li>
 *   <li>Polarlichter legen sich rund um die Welt (um jeden Spieler herum), Sternschnuppen stuerzen zur Saeule.</li>
 *   <li>Alles klingt langsam aus.</li>
 * </ol>
 * Klaenge werden ortsunabhaengig abgespielt, damit sie jeder hoert.
 */
@EventBusSubscriber(modid = CosmicCore.MODID, value = Dist.CLIENT)
public final class AwakeningEffect {
    private AwakeningEffect() {}

    private static final int DURATION = 320;
    private static final float[] VIOLET = {0.7F, 0.4F, 1.0F};
    private static final float[] CYAN = {0.35F, 0.95F, 1.0F};
    private static final float[] GOLD = {1.0F, 0.8F, 0.35F};
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};

    private static BlockPos origin;
    private static int age = -1;

    public static void start(BlockPos pos, int size) {
        origin = pos;
        age = 0;
    }

    private static void sound(SoundEvent ev, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, volume));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (age < 0) return;
        if (Minecraft.getInstance().level == null) {
            age = -1;
            return;
        }
        switch (age) {
            case 0 -> {
                sound(SoundEvents.END_PORTAL_SPAWN, 0.5F, 1.0F);
                sound(SoundEvents.BEACON_ACTIVATE, 0.5F, 1.0F);
            }
            case 30 -> sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 0.8F);
            case 60 -> sound(SoundEvents.ENDER_DRAGON_GROWL, 0.5F, 0.5F);
            case 140 -> sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 1.0F);
            case 220 -> sound(SoundEvents.BEACON_AMBIENT, 0.5F, 1.0F);
            default -> {}
        }
        if (++age > DURATION) age = -1;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (age < 0 || event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = age + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float ph = t / DURATION;
        // Huellkurve: schnell an, lange halten, sanft aus
        float env = Mth.clamp(t / 15.0F, 0.0F, 1.0F) * Mth.clamp((DURATION - t) / 80.0F, 0.0F, 1.0F);
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(CosmicRenderTypes.glow());

        // 1) Himmelsblitz: der ganze Himmel leuchtet kurz auf
        float flash = Mth.clamp(1.0F - t / 40.0F, 0.0F, 1.0F);
        if (flash > 0.0F) {
            RenderUtil.glowSphere(vc, new Matrix4f(m).translate((float) cam.x, (float) cam.y, (float) cam.z), 60.0F, 16, 24,
                    lerp(WHITE, VIOLET, 0.4F), 0.2F * flash);
        }

        // 2) Lichtsaeule vom Reaktor ins All (nur wenn in Sichtweite)
        double dist = cam.distanceTo(Vec3.atCenterOf(origin));
        if (dist < 700) {
            float ox = origin.getX() + 0.5F, oy = origin.getY() + 0.5F, oz = origin.getZ() + 0.5F;
            float grow = Mth.clamp(t / 25.0F, 0.0F, 1.0F);
            float top = oy + 20.0F + 160.0F * grow;
            float w = 0.4F + 1.4F * grow * (0.9F + 0.1F * Mth.sin(t * 0.5F));
            // runder Strahl aus ineinanderliegenden Lichtroehren (von allen Seiten gleich)
            float pulse = 0.85F + 0.15F * Mth.sin(t * 0.6F);
            RenderUtil.tube(vc, m, ox, oy, oz, top, w * 0.3F, 16, WHITE, env * pulse, env * 0.7F);
            RenderUtil.tube(vc, m, ox, oy, oz, top, w * 0.7F, 20, CYAN, 0.45F * env, 0.25F * env);
            RenderUtil.tube(vc, m, ox, oy, oz, top, w * 1.4F, 24, VIOLET, 0.18F * env, 0.06F * env);
            RenderUtil.tube(vc, m, ox, oy, oz, top, w * 2.6F, 24, VIOLET, 0.07F * env, 0.0F);
            // Energieknoten, die die Saeule hinaufschiessen
            for (int k = 0; k < 6; k++) {
                float f = Mth.frac(t * 0.02F + k / 6.0F);
                RenderUtil.glowSphere(vc, new Matrix4f(m).translate(ox, Mth.lerp(f, oy, top), oz), w * 0.8F, 8, 12, WHITE,
                        0.35F * env * (1.0F - f));
            }
            // Spiralfaeden um die Saeule
            for (int s = 0; s < 3; s++) {
                float px = ox, py = oy, pz = oz;
                for (int i = 1; i <= 60; i++) {
                    float f = i / 60.0F;
                    float y = Mth.lerp(f, oy, top);
                    float ang = f * 40.0F - t * 0.2F + s * Mth.TWO_PI / 3;
                    float r = w * (0.8F + 1.2F * f);
                    float x = ox + Mth.cos(ang) * r, z = oz + Mth.sin(ang) * r;
                    segment(vc, m, px, py, pz, x, y, z, 0.1F, GOLD[0], GOLD[1], GOLD[2], 0.6F * env * (1 - f), 0.6F * env * (1 - f));
                    px = x;
                    py = y;
                    pz = z;
                }
            }
            // Bodenwelle am Reaktor
            float shock = Mth.clamp(t / 60.0F, 0.0F, 1.0F);
            flatCircle(vc, new Matrix4f(m).translate(ox, oy, oz), 3.0F + 30.0F * shock, 0.25F, 128, WHITE, env * (1.0F - shock));
        }

        // 3) Schockwelle ueber den Himmel (Ring um den Spieler, hoch oben)
        float sky = (float) cam.y + 45.0F;
        for (int k = 0; k < 3; k++) {
            float s = Mth.clamp((t - k * 25.0F) / 140.0F, 0.0F, 1.0F);
            if (s <= 0.0F || s >= 1.0F) continue;
            float r = 8.0F + 90.0F * s;
            flatCircle(vc, new Matrix4f(m).translate((float) cam.x, sky - 15.0F * s, (float) cam.z), r, 0.6F + 1.5F * s, 160,
                    k == 1 ? GOLD : CYAN, env * (1.0F - s) * 0.8F);
        }

        // 4) Polarlichter rund um die Welt (Vorhaenge um den Spieler)
        float aur = Mth.clamp((t - 30.0F) / 60.0F, 0.0F, 1.0F) * env;
        if (aur > 0.0F) {
            int bands = 96;
            float radius = 70.0F;
            for (int i = 0; i < bands; i++) {
                float a0 = Mth.TWO_PI * i / bands, a1 = Mth.TWO_PI * (i + 1) / bands;
                float wave0 = Mth.sin(a0 * 5.0F + t * 0.04F) * 8.0F, wave1 = Mth.sin(a1 * 5.0F + t * 0.04F) * 8.0F;
                float r0 = radius + Mth.sin(a0 * 3.0F + t * 0.02F) * 10.0F, r1 = radius + Mth.sin(a1 * 3.0F + t * 0.02F) * 10.0F;
                float x0 = (float) cam.x + Mth.cos(a0) * r0, z0 = (float) cam.z + Mth.sin(a0) * r0;
                float x1 = (float) cam.x + Mth.cos(a1) * r1, z1 = (float) cam.z + Mth.sin(a1) * r1;
                float yb = (float) cam.y + 25.0F, yt = (float) cam.y + 60.0F;
                float[] c = lerp(lerp(CYAN, VIOLET, 0.5F + 0.5F * Mth.sin(a0 * 2.0F + t * 0.03F)), GOLD, 0.15F);
                float a = aur * (0.25F + 0.2F * Mth.sin(a0 * 7.0F + t * 0.08F));
                quadFade(vc, m, x0, yb + wave0, z0, x1, yb + wave1, z1, x1, yt + wave1, z1, x0, yt + wave0, z0,
                        c[0], c[1], c[2], a, 0.0F);
            }
        }

        // 5) Sternschnuppen stuerzen zur Lichtsaeule
        if (dist < 700) {
            float ox = origin.getX() + 0.5F, oz = origin.getZ() + 0.5F, top = origin.getY() + 50.0F;
            for (int k = 0; k < 40; k++) {
                float s = Mth.frac(t * 0.012F + k * 0.137F);
                float ang = hash(k * 31) * Mth.PI;
                float startR = 30.0F + 25.0F * (0.5F + 0.5F * hash(k * 7));
                float r = startR * (1.0F - s);
                float x = ox + Mth.cos(ang) * r, z = oz + Mth.sin(ang) * r;
                float y = top + 20.0F * hash(k * 13) * (1.0F - s);
                float tx = ox + Mth.cos(ang) * (r + 4.0F), tz = oz + Mth.sin(ang) * (r + 4.0F);
                segment(vc, m, tx, y + 1.5F, tz, x, y, z, 0.12F, WHITE[0], WHITE[1], WHITE[2], 0.0F, env * Mth.sin(s * Mth.PI));
                cube(vc, m, x, y, z, 0.25F, GOLD[0], GOLD[1], GOLD[2], env * Mth.sin(s * Mth.PI));
            }
        }

        pose.popPose();
        buffers.endBatch(CosmicRenderTypes.glow());
    }
}
