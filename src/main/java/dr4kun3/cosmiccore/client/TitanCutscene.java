package dr4kun3.cosmiccore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Kurzfilm beim ersten Ultimate-Modus: Hoch ueber dem Reaktor kreist ein ferner Planet. Seine Kruste bricht auf, ein
 * gewaltiger Void-Titan steigt kopfueber daraus hervor, bruellt, sammelt die Energie des Kosmos ueber seinem Kopf und
 * schleudert sie hinab zur Erde - seither faellt sein Strahl dauerhaft senkrecht in den Sternentrichter.
 * <p>
 * Den Titanen zeichnet GeckoLib (optional). Fehlt es, gibt es keine Cutscene - Planet und Strahl kommen trotzdem.
 * Die Kamera fuehrt ein clientseitiges Marker-Objekt, Spieler und Welt laufen normal weiter.
 */
@EventBusSubscriber(modid = CosmicCore.MODID, value = Dist.CLIENT)
public final class TitanCutscene {
    private TitanCutscene() {}

    /** Zeichnet den Titanen (Implementierung im GeckoLib-Kompatibilitaetspaket). */
    public interface TitanRenderer {
        void reset();

        void render(PoseStack pose, MultiBufferSource buffers, double titanTicks, float partialTick);
    }

    // Drehbuch (Ticks)
    private static final int ASCENT = 50, EMERGE = 110, ROAR = 200, CHARGE = 230, FIRE = 290, ARRIVE = 310, IMPACT = 320,
            AFTER = 335, LENGTH = 420;
    /** Der Titan erscheint mit EMERGE (seine Animation beginnt dort bei 0). */
    private static final int TITAN_START = EMERGE, TITAN_END = EMERGE + 280;
    /** Massstab fuer den GeckoLib-Renderer. */
    public static final float TITAN_SCALE = TitanBeam.TITAN_SCALE;
    private static final float[] VOID = {0.55F, 0.25F, 1.0F};
    private static final float[] CYAN = {0.45F, 0.95F, 1.0F};
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};

    private static TitanRenderer titanRenderer;
    private static boolean active;
    private static BlockPos core;
    private static int tick;
    private static int skipHeld;
    private static Marker camera;

    /** Vom Server beim Erwachen des ultimativen Kerns ausgeloest (pos = Kernmitte). */
    public static void start(BlockPos corePos) {
        Minecraft mc = Minecraft.getInstance();
        if (active || mc.level == null || mc.player == null) return;
        if (!ModList.get().isLoaded("geckolib")) return; // ohne GeckoLib keine Cutscene
        if (mc.player.position().distanceTo(Vec3.atCenterOf(corePos)) > 256.0) return;
        if (titanRenderer == null) {
            try {
                titanRenderer = (TitanRenderer) Class.forName("dr4kun3.cosmiccore.compat.geckolib.GeckoTitanRenderer")
                        .getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError e) {
                CosmicCore.LOGGER.warn("Void-Titan-Cutscene nicht verfuegbar: {}", e.toString());
                return;
            }
        }
        titanRenderer.reset();
        core = corePos.immutable();
        tick = 0;
        skipHeld = 0;
        camera = EntityType.MARKER.create(mc.level);
        if (camera == null) return;
        mc.setScreen(null);
        active = true;
        placeCamera(0.0F);
        mc.setCameraEntity(camera);
    }

    /** Waehrend der Cutscene zeichnet sie Riss und Strahl selbst; danach uebernimmt der Kern. */
    public static boolean hidesPermanentBeam(BlockPos pos) {
        return active && pos.equals(core);
    }

    private static void stop() {
        Minecraft mc = Minecraft.getInstance();
        active = false;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        camera = null;
    }

    // ------------------------------------------------------------------ Geometrie (relativ zur Kernmitte)

    /** Kopf des Titanen (kopfueber unter dem Planeten). */
    private static Vector3f head() {
        return new Vector3f(TitanBeam.TITAN_FEET).sub(0.0F, 70.0F / 16.0F * TITAN_SCALE, 0.0F);
    }

    private static float mouthY() {
        Minecraft mc = Minecraft.getInstance();
        boolean funnel = mc.level != null && mc.level.getBlockEntity(core)
                instanceof dr4kun3.cosmiccore.block.entity.SingularityCoreBlockEntity sc && sc.hasFunnel();
        return funnel ? 6.35F : 5.6F; // ultimativ: halbe Kantenlaenge 5
    }

    private static float ease(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static float span(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0.0F, 1.0F);
    }

    // ------------------------------------------------------------------ Kamera (Drehbuch)

    private static void placeCamera(float t) {
        Vector3f pos, look;
        Vector3f planetLow = new Vector3f(TitanBeam.PLANET).sub(0, TitanBeam.PLANET_R, 0);
        if (t < ASCENT) {
            // A: am Reaktor, der Blick wandert senkrecht hinauf - dort oben kreist ein ferner Planet
            float f = ease(span(t, 0, ASCENT - 5));
            pos = new Vector3f(10.0F, 6.0F, 14.0F);
            look = new Vector3f(0, 6, 0).lerp(TitanBeam.PLANET, f);
        } else if (t < EMERGE) {
            // B: Aufstieg durch die Wolken zum Planeten
            float f = ease(span(t, ASCENT, EMERGE));
            pos = new Vector3f(10.0F, 6.0F, 14.0F).lerp(new Vector3f(60.0F, 520.0F, 110.0F), f);
            look = new Vector3f(TitanBeam.PLANET);
        } else if (t < ROAR) {
            // C: die Kruste bricht auf, der Titan steigt kopfueber aus dem Planeten
            float f = ease(span(t, EMERGE, ROAR));
            pos = new Vector3f(60.0F, 520.0F, 110.0F).lerp(new Vector3f(36.0F, 560.0F, 70.0F), f);
            look = new Vector3f(planetLow).lerp(head(), f);
        } else if (t < CHARGE) {
            // D: Nahaufnahme - der Titan bruellt
            float f = ease(span(t, ROAR, CHARGE));
            pos = new Vector3f(head()).add(10.0F - 4.0F * f, -10.0F, 30.0F - 8.0F * f);
            look = head();
        } else if (t < FIRE) {
            // E: die Energie sammelt sich ueber seinem Kopf (Richtung Erde)
            float f = ease(span(t, CHARGE, FIRE));
            pos = new Vector3f(TitanBeam.ORB).add(Mth.lerp(f, 40.0F, 28.0F), -24.0F, Mth.lerp(f, 55.0F, 40.0F));
            look = new Vector3f(TitanBeam.ORB).lerp(head(), 0.3F);
        } else if (t < ARRIVE) {
            // F: der Schuss - am Strahl entlang hinab zur Erde
            float f = ease(span(t, FIRE, ARRIVE));
            pos = new Vector3f(TitanBeam.ORB).add(14.0F, -20.0F - 120.0F * f, 14.0F);
            look = new Vector3f(0, 0, 0);
        } else if (t < AFTER) {
            // G: am Reaktor - der Strahl schlaegt in den Trichter ein
            pos = new Vector3f(11.0F, 8.0F, 15.0F);
            look = new Vector3f(0, mouthY(), 0).lerp(new Vector3f(0, 80, 0), 1.0F - span(t, IMPACT - 6, IMPACT + 4));
        } else {
            // H: Ausklang - weite Fahrt um den Reaktor, der Strahl bleibt
            float f = ease(span(t, AFTER, LENGTH));
            float a = 0.9F + f * 0.8F;
            float d = Mth.lerp(f, 20.0F, 46.0F);
            pos = new Vector3f(Mth.cos(a) * d, Mth.lerp(f, 8.0F, 24.0F), Mth.sin(a) * d);
            look = new Vector3f(0, Mth.lerp(f, 4.0F, 30.0F), 0);
        }
        // Erschuetterung beim Einschlag
        float shake = t > IMPACT && t < IMPACT + 18 ? (1.0F - (t - IMPACT) / 18.0F) * 0.6F
                : t > ROAR + 4 && t < ROAR + 26 ? 0.35F * (1.0F - (t - ROAR - 4) / 22.0F) : 0.0F;
        if (shake > 0) {
            pos.add(RenderUtil.hash((int) (t * 7)) * shake, RenderUtil.hash((int) (t * 7) + 3) * shake, RenderUtil.hash((int) (t * 7) + 5) * shake);
        }
        double x = core.getX() + 0.5 + pos.x, y = core.getY() + 0.5 + pos.y, z = core.getZ() + 0.5 + pos.z;
        double dx = look.x - pos.x, dy = look.y - pos.y, dz = look.z - pos.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        camera.setPos(x, y, z);
        camera.xo = camera.xOld = x;
        camera.yo = camera.yOld = y;
        camera.zo = camera.zOld = z;
        camera.setYRot(yaw);
        camera.setXRot(pitch);
        camera.yRotO = yaw;
        camera.xRotO = pitch;
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        if (!active || camera == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() != camera) mc.setCameraEntity(camera);
        placeCamera(tick + event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stop();
            return;
        }
        // Schleichen gedrueckt halten = ueberspringen
        skipHeld = mc.options.keyShift.isDown() ? skipHeld + 1 : 0;
        if (skipHeld > 15 || ++tick >= LENGTH) {
            stop();
            return;
        }
        if (tick == 2) sound(SoundEvents.WARDEN_HEARTBEAT, 0.6F, 1.0F);
        if (tick == 26) sound(SoundEvents.WARDEN_HEARTBEAT, 0.55F, 1.0F);
        if (tick == ASCENT) sound(SoundEvents.ELYTRA_FLYING, 0.8F, 0.6F);
        if (tick == EMERGE) sound(SoundEvents.END_PORTAL_SPAWN, 0.5F, 0.8F);
        if (tick == EMERGE + 30) sound(SoundEvents.WITHER_SPAWN, 0.5F, 0.55F);
        if (tick == ROAR + 4) sound(SoundEvents.ENDER_DRAGON_GROWL, 0.45F, 1.0F);
        if (tick == ROAR + 6) sound(SoundEvents.RAVAGER_ROAR, 0.4F, 1.0F);
        if (tick == CHARGE) sound(SoundEvents.BEACON_ACTIVATE, 0.5F, 1.0F);
        if (tick == CHARGE + 30) sound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.7F, 1.0F);
        if (tick == FIRE) sound(SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 1.0F);
        if (tick == IMPACT) {
            sound(SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 1.0F);
            sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.0F);
        }
        if (tick == AFTER + 20) sound(SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.0F);
    }

    private static void sound(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    // ------------------------------------------------------------------ Welt: Riss, Titan, Kugel, Strahl, Einschlag

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!active || event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft mc = Minecraft.getInstance();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float t = tick + partial;
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(core.getX() + 0.5 - cam.x, core.getY() + 0.5 - cam.y, core.getZ() + 0.5 - cam.z);
        Vector3f camRel = new Vector3f((float) (cam.x - core.getX() - 0.5), (float) (cam.y - core.getY() - 0.5), (float) (cam.z - core.getZ() - 0.5));
        Matrix4f m = pose.last().pose();
        // ferne Objekte maszstabsgetreu naeher an die Kamera (sonst hinter der Sichtweite abgeschnitten)
        float ps = TitanBeam.skyScale(TitanBeam.PLANET, camRel);
        Matrix4f sky = TitanBeam.skyFrame(m, camRel, ps);

        // Planet mit aufbrechender Kruste
        float crack = span(t, EMERGE - 10, EMERGE + 30) * (1.0F - span(t, EMERGE + 100, EMERGE + 160));
        TitanBeam.planet(buffers, sky, t, crack, 1.0F);
        buffers.endBatch();

        // Titan (GeckoLib): kopfueber an der Unterseite des Planeten
        if (t >= TITAN_START && t < TITAN_END && titanRenderer != null) {
            pose.pushPose();
            pose.translate(camRel.x, camRel.y, camRel.z);
            pose.scale(ps, ps, ps);
            pose.translate(-camRel.x + TitanBeam.TITAN_FEET.x, -camRel.y + TitanBeam.TITAN_FEET.y, -camRel.z + TitanBeam.TITAN_FEET.z);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180.0F));
            titanRenderer.render(pose, buffers, t - TITAN_START, partial);
            pose.popPose();
            buffers.endBatch();
        }

        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        // Energiekugel ueber seinem Kopf
        float charge = span(t, CHARGE, FIRE);
        if (charge > 0 && t < FIRE + 6) {
            Matrix4f om = new Matrix4f(sky).translate(TitanBeam.ORB.x, TitanBeam.ORB.y, TitanBeam.ORB.z);
            float r = 1.5F + 9.0F * charge * charge;
            float pulse = 0.85F + 0.15F * Mth.sin(t * 0.9F);
            RenderUtil.glowSphere(glow, om, r * pulse, 12, 16, WHITE, 0.45F);
            RenderUtil.glowSphere(glow, om, r * 1.6F, 12, 16, CYAN, 0.2F);
            RenderUtil.glowSphere(glow, om, r * 2.5F, 10, 14, VOID, 0.1F);
            for (int k = 0; k < 20; k++) { // Energie stroemt aus dem All in die Kugel
                float s = Mth.frac(t * 0.025F + k / 20.0F);
                Vector3f dir = new Vector3f(RenderUtil.hash(k * 7), RenderUtil.hash(k * 11), RenderUtil.hash(k * 13)).normalize();
                Vector3f p = new Vector3f(dir).mul(50.0F * (1.0F - s));
                Vector3f q = new Vector3f(dir).mul(50.0F * (1.0F - s) + 5.0F);
                RenderUtil.segment(glow, om, q.x, q.y, q.z, p.x, p.y, p.z, 0.25F, CYAN[0], CYAN[1], CYAN[2], 0.0F, 0.8F * s);
            }
        }
        // Schuss: senkrecht hinab in den Trichter - danach bleibt er
        if (t >= FIRE) {
            float reach = span(t, FIRE, IMPACT);
            Vector3f from = TitanBeam.skyPoint(TitanBeam.ORB, camRel, TitanBeam.skyScale(TitanBeam.ORB, camRel));
            float glare = 1.6F - 0.8F * span(t, IMPACT, AFTER + 30);
            TitanBeam.beam(m, glow, from, new Vector3f(0, mouthY(), 0), t, reach, glare);
            if (t >= IMPACT) TitanBeam.inlet(m, glow, mouthY(), t, 0.8F);
        }
        // Einschlag in den Trichter: Lichtblitz und Schockwellen
        if (t >= IMPACT && t < IMPACT + 40) {
            float f = (t - IMPACT) / 40.0F;
            Matrix4f im = new Matrix4f(m).translate(0, mouthY(), 0);
            RenderUtil.glowSphere(glow, im, 2.0F + 12.0F * f, 14, 20, WHITE, 0.6F * (1.0F - f));
            RenderUtil.flatCircle(glow, im, 3.0F + 30.0F * f, 0.4F, 96, CYAN, 1.0F - f);
            RenderUtil.flatCircle(glow, new Matrix4f(im).rotateX(Mth.HALF_PI), 2.0F + 18.0F * f, 0.3F, 96, VOID, 1.0F - f);
        }
        buffers.endBatch(CosmicRenderTypes.glow());
        pose.popPose();
    }

    /** Waehrend der Cutscene keine Nebelwand vor dem fernen Titanen. */
    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (!active) return;
        event.setNearPlaneDistance(Math.max(event.getNearPlaneDistance(), 400.0F));
        event.setFarPlaneDistance(Math.max(event.getFarPlaneDistance(), 900.0F));
        event.setCanceled(true);
    }

    // ------------------------------------------------------------------ Bildschirm: Kinobalken, Untertitel, Blitz

    @SubscribeEvent
    public static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (active) event.setCanceled(true); // kein HUD waehrend des Films
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (active) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        float t = tick + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        // Kinobalken: fahren ein und am Ende wieder aus
        float bars = ease(span(t, 0, 15)) * (1.0F - ease(span(t, LENGTH - 20, LENGTH)));
        int bh = Math.round(h * 0.12F * bars);
        g.fill(0, 0, w, bh, 0xFF000000);
        g.fill(0, h - bh, w, h, 0xFF000000);
        // Lichtblitz beim Einschlag
        if (t >= IMPACT && t < IMPACT + 20) {
            int a = (int) (230 * (1.0F - (t - IMPACT) / 20.0F));
            g.fill(0, 0, w, h, (a << 24) | 0xFFFFFF);
        }
        // Untertitel
        subtitle(g, mc, t, 6, ASCENT - 2, "cutscene.cosmiccore.line1", w, h, bh);
        subtitle(g, mc, t, ASCENT + 4, EMERGE + 4, "cutscene.cosmiccore.line2", w, h, bh);
        subtitle(g, mc, t, EMERGE + 40, ROAR, "cutscene.cosmiccore.line3", w, h, bh);
        subtitle(g, mc, t, CHARGE + 6, FIRE - 4, "cutscene.cosmiccore.line4", w, h, bh);
        subtitle(g, mc, t, AFTER + 16, LENGTH - 14, "cutscene.cosmiccore.line5", w, h, bh);
        // Hinweis zum Ueberspringen
        Component skip = Component.translatable("cutscene.cosmiccore.skip");
        g.drawString(mc.font, skip, w - mc.font.width(skip) - 6, Math.max(4, bh / 2 - 4), 0x80B8A8D8, false);
    }

    private static void subtitle(GuiGraphics g, Minecraft mc, float t, float from, float to, String key, int w, int h, int bh) {
        if (t < from || t > to) return;
        float a = Math.min(span(t, from, from + 8), 1.0F - span(t, to - 8, to));
        if (a <= 0.02F) return;
        Component c = Component.translatable(key);
        int alpha = Math.max(8, (int) (255 * a));
        g.drawString(mc.font, c, (w - mc.font.width(c)) / 2, h - bh / 2 - 4, (alpha << 24) | 0xE8DFFF, true);
    }

    /** Spieler steht still, solange der Film laeuft. */
    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (!active) return;
        Input in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (active) stop();
    }
}
