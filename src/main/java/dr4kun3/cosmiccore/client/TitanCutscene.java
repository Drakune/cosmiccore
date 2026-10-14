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
 * Kurzfilm zum Erwachen des ultimativen Kerns: Ueber dem Reaktor oeffnet sich ein Riss, ein gewaltiger Void-Titan
 * steigt daraus empor, sammelt die Energie des Kosmos und schiesst sie auf die Erde - seither faellt sein Strahl
 * dauerhaft in den Kern.
 * <p>
 * Den Titanen zeichnet GeckoLib (optional). Ist GeckoLib nicht installiert, gibt es keine Cutscene - der Strahl kommt
 * trotzdem. Die Kamera fuehrt ein clientseitiges Marker-Objekt, Spieler und Welt laufen normal weiter.
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
    private static final int RIFT_OPEN = 45, TITAN_START = 90, CHARGE_SHOT = 170, FIRE = 240, IMPACT = 250, AFTER = 262;
    private static final int TITAN_END = TITAN_START + 240, LENGTH = 380;
    /** Massstab des Titanen (Modell ~4 Bloecke -> ~54 Bloecke gross). */
    public static final float TITAN_SCALE = 13.0F;
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

    private static Vector3f titanFeet() {
        return new Vector3f(TitanBeam.RIFT).add(0.0F, -30.0F, 0.0F);
    }

    /** Drehung des Titanen, damit er zum Kern blickt (Modellvorderseite = -Z). */
    private static float titanYaw() {
        Vector3f f = titanFeet();
        return (float) Math.atan2(f.x, f.z);
    }

    private static Vector3f titanCenter() {
        return titanFeet().add(0.0F, 27.0F, 0.0F);
    }

    /** Energiekugel zwischen den Haenden (Knochen "orb" im Modell). */
    private static Vector3f orb() {
        Vector3f local = new Vector3f(0.0F, 30.0F / 16.0F * TITAN_SCALE, -16.0F / 16.0F * TITAN_SCALE).rotateY(titanYaw());
        return titanFeet().add(local);
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
        if (t < RIFT_OPEN) {
            // A: weite Fahrt um den erwachenden Kern
            float a = 0.6F + t * 0.012F;
            pos = new Vector3f(Mth.cos(a) * 30.0F, 20.0F - t * 0.08F, Mth.sin(a) * 30.0F);
            look = new Vector3f(0, 0, 0);
        } else if (t < TITAN_START) {
            // B: am Reaktor, der Blick wandert hinauf zum Riss
            float f = ease(span(t, RIFT_OPEN, TITAN_START - 10));
            pos = new Vector3f(-12.0F, 5.0F, 16.0F);
            look = new Vector3f(0, 4, 0).lerp(TitanBeam.RIFT, f);
        } else if (t < CHARGE_SHOT) {
            // C: der Titan steigt empor - langsame Fahrt auf ihn zu, leicht von unten
            float f = ease(span(t, TITAN_START, CHARGE_SHOT));
            Vector3f from = new Vector3f(-16.0F, 10.0F, 22.0F);
            Vector3f to = new Vector3f(titanCenter()).mul(0.45F).add(-24.0F, -10.0F, 12.0F);
            pos = from.lerp(to, f);
            look = titanCenter();
        } else if (t < FIRE) {
            // D: Nahaufnahme - die Kugel laedt sich zwischen den Haenden auf
            float f = ease(span(t, CHARGE_SHOT, FIRE));
            Vector3f toCore = new Vector3f(titanCenter()).negate().normalize();
            Vector3f side = new Vector3f(toCore).cross(0, 1, 0).normalize();
            pos = new Vector3f(orb()).add(new Vector3f(toCore).mul(30.0F - 8.0F * f)).add(side.mul(14.0F)).add(0, 4.0F, 0);
            look = orb();
        } else if (t < AFTER) {
            // E: am Kern - der Strahl kommt herab und schlaegt ein
            pos = new Vector3f(9.0F, 5.0F, 11.0F);
            look = new Vector3f(0, 0, 0).lerp(orb(), 0.5F * (1.0F - span(t, IMPACT - 4, IMPACT + 6)));
        } else {
            // F: Ausklang - die Kamera zieht sich zurueck, der Strahl bleibt
            float f = ease(span(t, AFTER, LENGTH));
            float a = 2.2F + f * 0.6F;
            float d = Mth.lerp(f, 22.0F, 48.0F);
            pos = new Vector3f(Mth.cos(a) * d, Mth.lerp(f, 10.0F, 26.0F), Mth.sin(a) * d);
            look = new Vector3f(TitanBeam.RIFT).mul(0.25F);
        }
        // Erschuetterung beim Einschlag
        float shake = t > IMPACT && t < IMPACT + 18 ? (1.0F - (t - IMPACT) / 18.0F) * 0.6F : 0.0F;
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
        if (tick == 24) sound(SoundEvents.WARDEN_HEARTBEAT, 0.55F, 1.0F);
        if (tick == RIFT_OPEN + 5) sound(SoundEvents.END_PORTAL_SPAWN, 0.6F, 0.8F);
        if (tick == TITAN_START) sound(SoundEvents.ENDER_DRAGON_GROWL, 0.45F, 0.9F);
        if (tick == TITAN_START + 30) sound(SoundEvents.WITHER_SPAWN, 0.5F, 0.6F);
        if (tick == CHARGE_SHOT) sound(SoundEvents.BEACON_ACTIVATE, 0.5F, 1.0F);
        if (tick == CHARGE_SHOT + 30) sound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.7F, 1.0F);
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

        // Titan (GeckoLib)
        if (t >= TITAN_START && t < TITAN_END && titanRenderer != null) {
            Vector3f feet = titanFeet();
            pose.pushPose();
            pose.translate(feet.x, feet.y, feet.z);
            pose.mulPose(com.mojang.math.Axis.YP.rotation(titanYaw()));
            titanRenderer.render(pose, buffers, t - TITAN_START, partial);
            pose.popPose();
            buffers.endBatch();
        }

        Matrix4f m = pose.last().pose();
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        // Riss: oeffnet sich, bleibt offen und schrumpft am Ende auf den dauerhaften Spalt
        float open = t < RIFT_OPEN ? 0.0F : t < TITAN_START ? ease(span(t, RIFT_OPEN, TITAN_START)) : 1.0F - 0.65F * ease(span(t, 300, 340));
        TitanBeam.rift(m, glow, TitanBeam.RIFT, t, open, camRel);
        // Energiekugel zwischen den Haenden
        float charge = span(t, TITAN_START + 96, FIRE);
        if (charge > 0 && t < FIRE + 6) {
            Matrix4f om = new Matrix4f(m).translate(orb().x, orb().y, orb().z);
            float r = 1.0F + 7.0F * charge * charge;
            float pulse = 0.85F + 0.15F * Mth.sin(t * 0.9F);
            RenderUtil.glowSphere(glow, om, r * pulse, 12, 16, WHITE, 0.45F);
            RenderUtil.glowSphere(glow, om, r * 1.6F, 12, 16, CYAN, 0.2F);
            RenderUtil.glowSphere(glow, om, r * 2.5F, 10, 14, VOID, 0.1F);
            // Energie stroemt aus dem All in die Kugel
            for (int k = 0; k < 18; k++) {
                float s = Mth.frac(t * 0.025F + k / 18.0F);
                Vector3f dir = new Vector3f(RenderUtil.hash(k * 7), RenderUtil.hash(k * 11), RenderUtil.hash(k * 13)).normalize();
                Vector3f p = new Vector3f(dir).mul(40.0F * (1.0F - s));
                Vector3f q = new Vector3f(dir).mul(40.0F * (1.0F - s) + 4.0F);
                RenderUtil.segment(glow, om, q.x, q.y, q.z, p.x, p.y, p.z, 0.2F, CYAN[0], CYAN[1], CYAN[2], 0.0F, 0.8F * s);
            }
        }
        // Schuss: von der Kugel in den Kern, danach dauerhaft aus dem Riss
        if (t >= FIRE) {
            float reach = span(t, FIRE, IMPACT);
            float fade = 1.0F - span(t, 300, 330);
            TitanBeam.beam(m, glow, orb(), new Vector3f(0, 0, 0), t, reach, (1.6F - 0.85F * span(t, IMPACT, AFTER + 20)) * fade);
            float handover = span(t, 300, 340);
            TitanBeam.beam(m, glow, TitanBeam.RIFT, new Vector3f(0, 0, 0), t, 1.0F, 0.75F * handover);
        }
        // Einschlag: Lichtblitz und Schockwellen am Kern
        if (t >= IMPACT && t < IMPACT + 40) {
            float f = (t - IMPACT) / 40.0F;
            RenderUtil.glowSphere(glow, m, 2.0F + 14.0F * f, 14, 20, WHITE, 0.6F * (1.0F - f));
            RenderUtil.flatCircle(glow, m, 3.0F + 30.0F * f, 0.4F, 96, CYAN, 1.0F - f);
            RenderUtil.flatCircle(glow, new Matrix4f(m).rotateX(Mth.HALF_PI), 2.0F + 20.0F * f, 0.3F, 96, VOID, 1.0F - f);
        }
        buffers.endBatch(CosmicRenderTypes.glow());
        pose.popPose();
    }

    /** Waehrend der Cutscene keine Nebelwand vor dem fernen Titanen. */
    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (!active) return;
        event.setNearPlaneDistance(Math.max(event.getNearPlaneDistance(), 250.0F));
        event.setFarPlaneDistance(Math.max(event.getFarPlaneDistance(), 500.0F));
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
        subtitle(g, mc, t, 6, RIFT_OPEN - 2, "cutscene.cosmiccore.line1", w, h, bh);
        subtitle(g, mc, t, RIFT_OPEN + 4, TITAN_START + 4, "cutscene.cosmiccore.line2", w, h, bh);
        subtitle(g, mc, t, TITAN_START + 30, CHARGE_SHOT, "cutscene.cosmiccore.line3", w, h, bh);
        subtitle(g, mc, t, CHARGE_SHOT + 10, FIRE - 4, "cutscene.cosmiccore.line4", w, h, bh);
        subtitle(g, mc, t, AFTER + 18, LENGTH - 14, "cutscene.cosmiccore.line5", w, h, bh);
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
