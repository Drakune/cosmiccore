package dr4kun3.cosmiccore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dr4kun3.cosmiccore.CosmicCore;
import dr4kun3.cosmiccore.entity.ModEntities;
import dr4kun3.cosmiccore.entity.VoidTitanEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
 * Kurzfilm beim ersten Ultimate-Modus (ca. 40 s):
 * <ol>
 *   <li>Eroeffnung: Kranfahrt um den laufenden Reaktor.</li>
 *   <li>Der Ruf: ein Lichtimpuls schiesst aus dem Trichter, die Kamera folgt ihm durch die Wolken.</li>
 *   <li>Die Erde bleibt zurueck: der Erdball schrumpft, der Mond zieht vorbei, die Sonne gleisst.</li>
 *   <li>Lichtgeschwindigkeit: Sternenstreifen und ein Leuchten am Ende des Tunnels.</li>
 *   <li>Ein anderes Universum: Gasriese mit Ring, Galaxie, Doppelsonne, Nebel - der Void-Titan erwacht, bruellt
 *       (Boss-Titel), sammelt die Energie des Kosmos und feuert.</li>
 *   <li>Rueckflug am Strahl, die Erde waechst heran, Sturz durch die Atmosphaere, Einschlag im Trichter.</li>
 * </ol>
 * Ohne GeckoLib gibt es keine Cutscene. Kamera = clientseitiger Marker, Titan = clientseitige Entity.
 */
@EventBusSubscriber(modid = CosmicCore.MODID, value = Dist.CLIENT)
public final class TitanCutscene {
    private TitanCutscene() {}

    // Drehbuch (Ticks)
    private static final int CALL = 80, RISE = 120, SPACE = 210, WARP = 300, UNIVERSE = 390, AWAKE = 450, ROAR = 485,
            CHARGE = 515, FIRE = 582, RETURN = 600, HOME = 640, ATMO = 660, IMPACT = 700, AFTER = 706, LENGTH = 800;
    private static final int TITAN_START = UNIVERSE;
    /** Buehne im anderen Universum, weit ueber der Welt (relativ zur Kernmitte). */
    private static final Vector3f STAGE = new Vector3f(0.0F, 2600.0F, 0.0F);
    private static final Vector3f TITAN_POS = new Vector3f(STAGE).add(0.0F, 0.0F, -60.0F);
    private static final Vector3f SUN_DIR = new Vector3f(0.55F, 0.35F, -0.75F).normalize();
    private static final float[] VOID = {0.55F, 0.25F, 1.0F};
    private static final float[] CYAN = {0.45F, 0.95F, 1.0F};
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};
    private static final float[] GOLD = {1.0F, 0.8F, 0.45F};
    private static final float[] MAGENTA = {1.0F, 0.35F, 0.8F};

    private static boolean active;
    private static BlockPos core;
    private static int tick;
    private static int skipHeld;
    private static Marker camera;
    private static VoidTitanEntity titan;
    private static float cameraRoll;

    // ------------------------------------------------------------------ Steuerung

    /** Vom Server beim ersten Ultimate-Modus ausgeloest (pos = Kernmitte). */
    public static void start(BlockPos corePos) {
        Minecraft mc = Minecraft.getInstance();
        if (active || mc.level == null || mc.player == null) return;
        if (!ModList.get().isLoaded("geckolib")) return; // ohne GeckoLib keine Cutscene
        if (mc.player.position().distanceTo(Vec3.atCenterOf(corePos)) > 256.0) return;
        core = corePos.immutable();
        tick = 0;
        skipHeld = 0;
        camera = EntityType.MARKER.create(mc.level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
        if (camera == null) return;
        spawnTitan(mc.level);
        mc.setScreen(null);
        active = true;
        placeCamera(0.0F);
        mc.setCameraEntity(camera);
    }

    public static boolean isActive() {
        return active;
    }

    public static int currentTick() {
        return tick;
    }

    static VoidTitanEntity titan() {
        return titan;
    }

    /** Nur fuer den Entwicklungs-Test: an eine Stelle des Drehbuchs springen. */
    static void debugJumpTo(int t) {
        if (active) tick = Math.max(0, Math.min(LENGTH - 1, t));
    }

    /** Waehrend der Cutscene zeichnet sie den Strahl selbst; danach uebernimmt der Kern. */
    public static boolean hidesPermanentBeam(BlockPos pos) {
        return active && pos.equals(core);
    }

    private static void stop() {
        Minecraft mc = Minecraft.getInstance();
        active = false;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        camera = null;
        if (titan != null && mc.level != null) mc.level.removeEntity(titan.getId(), Entity.RemovalReason.DISCARDED);
        titan = null;
    }

    private static void spawnTitan(ClientLevel level) {
        titan = ModEntities.VOID_TITAN.get().create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
        if (titan == null) return;
        titan.setId(-7_700_000); // clientseitige ID
        Vec3 p = world(TITAN_POS);
        titan.snapTo(p.x, p.y, p.z, 180.0F, 0.0F); // Vorderseite zur Buehnenmitte
        titan.scriptTime = 0.0F;
        titan.updateParts();
        level.addEntity(titan);
    }

    // ------------------------------------------------------------------ Hilfen

    private static Vec3 world(Vector3f rel) {
        return new Vec3(core.getX() + 0.5 + rel.x, core.getY() + 0.5 + rel.y, core.getZ() + 0.5 + rel.z);
    }

    /** Animierter Knochen des Titanen relativ zur Kernmitte (Fallback: Ruhelage). */
    private static Vector3f bone(String name, Vector3f fallback) {
        if (titan == null) return new Vector3f(fallback);
        Vec3 b = titan.bones.get(name);
        if (b == null) return new Vector3f(fallback);
        return new Vector3f(TITAN_POS).add((float) b.x, (float) b.y, (float) b.z);
    }

    private static Vector3f head() {
        return bone("head", new Vector3f(TITAN_POS).add(0, 15, 1));
    }

    private static Vector3f orb() {
        return bone("orb", new Vector3f(TITAN_POS).add(0, 8.25F, 11.25F));
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

    private static float easeInOut(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x < 0.5F ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    private static float easeIn(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * x;
    }

    private static float span(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0.0F, 1.0F);
    }

    private static boolean in(float t, int a, int b) {
        return t >= a && t < b;
    }

    // ------------------------------------------------------------------ Kamera

    private static void placeCamera(float t) {
        Vector3f pos, look;
        float roll = 0.0F;
        if (t < CALL) {
            // 1: Eroeffnung - langsame Kranfahrt um den Reaktor
            float f = easeInOut(span(t, 0, CALL));
            float a = 0.4F + 0.9F * f;
            float d = Mth.lerp(f, 30.0F, 22.0F);
            pos = new Vector3f(Mth.cos(a) * d, Mth.lerp(f, 4.0F, 12.0F), Mth.sin(a) * d);
            look = new Vector3f(0, 1.0F, 0);
        } else if (t < RISE) {
            // 2: der Ruf - an den Trichter heran, Blick folgt dem Lichtimpuls nach oben
            float f = easeInOut(span(t, CALL, RISE));
            float a = 1.3F;
            pos = new Vector3f(Mth.cos(a) * 22.0F, 12.0F, Mth.sin(a) * 22.0F).lerp(new Vector3f(6.0F, 10.0F, 8.0F), f);
            look = new Vector3f(0, 1.0F, 0).lerp(new Vector3f(0, 60.0F, 0), easeIn(span(t, CALL + 10, RISE)));
        } else if (t < SPACE) {
            // 3: Aufstieg hinter dem Impuls her, immer schneller, durch die Wolken
            float f = easeIn(span(t, RISE, SPACE));
            pos = new Vector3f(6.0F, 10.0F + 680.0F * f, 8.0F);
            look = new Vector3f(pos).add(-2.0F, 40.0F, -3.0F);
            roll = 4.0F * f;
        } else if (t < WARP) {
            // 4: im All - Blick zurueck auf die schrumpfende Erde
            float f = easeInOut(span(t, SPACE, WARP));
            pos = new Vector3f(6.0F, 900.0F, 8.0F);
            Vector3f lookDown = new Vector3f(pos).add(0.3F, -40.0F, 18.0F);
            Vector3f lookAway = new Vector3f(pos).add(0.0F, 30.0F, -40.0F);
            look = lookDown.lerp(lookAway, easeInOut(span(t, WARP - 25, WARP)));
            roll = -6.0F * (1.0F - f);
        } else if (t < UNIVERSE) {
            // 5: Lichtgeschwindigkeit - Blick nach vorn in den Tunnel
            pos = new Vector3f(6.0F, 900.0F, 8.0F);
            look = new Vector3f(pos).add(0.0F, 30.0F, -40.0F);
            roll = 8.0F * Mth.sin(t * 0.05F) * span(t, WARP, WARP + 30);
        } else if (t < AWAKE) {
            // 6: Enthuellung - weite Fahrt auf den schlafenden Titanen
            float f = easeInOut(span(t, UNIVERSE, AWAKE));
            pos = new Vector3f(TITAN_POS).add(Mth.lerp(f, -40.0F, -24.0F), Mth.lerp(f, 12.0F, 4.0F), Mth.lerp(f, 52.0F, 38.0F));
            look = new Vector3f(TITAN_POS).add(0, -4.0F, 0);
        } else if (t < ROAR) {
            // 7: Erwachen - Untersicht, die Kamera schleicht naeher
            float f = easeInOut(span(t, AWAKE, ROAR));
            pos = new Vector3f(TITAN_POS).add(Mth.lerp(f, 22.0F, 16.0F), Mth.lerp(f, -6.0F, -3.0F), Mth.lerp(f, 40.0F, 30.0F));
            look = head();
        } else if (t < CHARGE) {
            // 8: Bruellen - Nahaufnahme des Gesichts
            float f = easeInOut(span(t, ROAR, CHARGE));
            pos = new Vector3f(head()).add(Mth.lerp(f, 12.0F, 8.0F), 0.5F, Mth.lerp(f, 24.0F, 19.0F));
            look = head();
        } else if (t < FIRE) {
            // 9: Aufladen - Dreiviertel-Untersicht, die Energie strudelt in die Kugel
            float f = easeInOut(span(t, CHARGE, FIRE));
            pos = new Vector3f(TITAN_POS).add(Mth.lerp(f, 24.0F, 16.0F), -5.0F, Mth.lerp(f, 30.0F, 24.0F));
            look = new Vector3f(orb()).lerp(head(), 0.35F);
        } else if (t < RETURN) {
            // 10: der Schuss - frontal, der Strahl kommt auf die Kamera zu
            pos = new Vector3f(orb()).add(2.0F, 1.5F, 36.0F);
            look = orb();
        } else if (t < ATMO) {
            // 11: am Strahl zurueck - die Erde waechst heran
            pos = new Vector3f(6.0F, 900.0F, 8.0F);
            look = new Vector3f(pos).add(0.2F, -40.0F, 8.0F);
            roll = 10.0F * Mth.sin(t * 0.07F);
        } else if (t < AFTER) {
            // 12: Sturz durch die Atmosphaere am Strahl hinab, Einschlag
            float f = easeIn(span(t, ATMO, IMPACT));
            pos = new Vector3f(3.0F, 320.0F, 5.0F).lerp(new Vector3f(14.0F, 7.0F, 18.0F), f);
            look = new Vector3f(0, mouthY(), 0);
        } else {
            // 13: Ausklang - Kran nach oben und zurueck, der Strahl bleibt
            float f = easeInOut(span(t, AFTER, LENGTH));
            float a = 0.9F + f * 0.7F;
            float d = Mth.lerp(f, 20.0F, 38.0F);
            pos = new Vector3f(Mth.cos(a) * d, Mth.lerp(f, 5.0F, 16.0F), Mth.sin(a) * d);
            look = new Vector3f(0, Mth.lerp(f, 2.0F, 12.0F), 0);
        }
        // Handkamera-Gefuehl (sehr leicht) und Erschuetterungen
        float hand = 0.08F;
        pos.add(Mth.sin(t * 0.13F) * hand, Mth.sin(t * 0.11F + 1.3F) * hand, Mth.sin(t * 0.09F + 2.1F) * hand);
        float shake = 0.0F;
        if (in(t, WARP, UNIVERSE) || in(t, RETURN, ATMO)) shake = 0.1F;
        if (in(t, ROAR, ROAR + 26)) shake = 0.45F * (1.0F - (t - ROAR) / 26.0F);
        if (in(t, FIRE, RETURN)) shake = 0.35F;
        if (in(t, IMPACT, IMPACT + 22)) shake = 0.8F * (1.0F - (t - IMPACT) / 22.0F);
        if (shake > 0) {
            int s = (int) (t * 7);
            pos.add(RenderUtil.hash(s) * shake, RenderUtil.hash(s + 3) * shake, RenderUtil.hash(s + 5) * shake);
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
        camera.setXRot(Mth.clamp(pitch, -89.9F, 89.9F));
        camera.yRotO = yaw;
        camera.xRotO = camera.getXRot();
        cameraRoll = roll;
    }

    /** Leichte Kameraneigung (Roll) fuer Dynamik. */
    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (active) event.setRoll(cameraRoll);
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        if (!active || camera == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() != camera) mc.setCameraEntity(camera);
        float t = tick + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (titan != null) titan.scriptTime = Math.max(0.0F, t - TITAN_START);
        placeCamera(t);
    }

    // ------------------------------------------------------------------ Takt: Klang und Partikel

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stop();
            return;
        }
        skipHeld = mc.options.keyShift.isDown() ? skipHeld + 1 : 0; // Schleichen halten = ueberspringen
        if (skipHeld > 15 || ++tick >= LENGTH) {
            stop();
            return;
        }
        if (titan != null) {
            titan.scriptTime = Math.max(0.0F, tick - TITAN_START);
            titan.updateParts();
        }
        switch (tick) {
            case 2 -> sound(SoundEvents.BEACON_AMBIENT, 0.6F, 1.0F);
            case CALL -> {
                sound(SoundEvents.BEACON_ACTIVATE, 0.7F, 1.0F);
                sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1.0F);
            }
            case CALL + 30 -> sound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.5F, 1.0F);
            case RISE + 10 -> sound(SoundEvents.ELYTRA_FLYING, 0.8F, 0.7F);
            case SPACE -> sound(SoundEvents.AMBIENT_CAVE.value(), 0.6F, 0.7F);
            case WARP -> sound(SoundEvents.WARDEN_SONIC_BOOM, 1.4F, 0.45F);
            case WARP + 20 -> sound(SoundEvents.PORTAL_TRAVEL, 1.6F, 0.25F);
            case UNIVERSE - 4 -> sound(SoundEvents.END_PORTAL_SPAWN, 0.6F, 0.8F);
            case AWAKE -> sound(SoundEvents.WARDEN_HEARTBEAT, 0.5F, 1.0F);
            case AWAKE + 18 -> sound(SoundEvents.WARDEN_HEARTBEAT, 0.5F, 1.0F);
            case ROAR -> {
                sound(SoundEvents.ENDER_DRAGON_GROWL, 0.45F, 1.0F);
                sound(SoundEvents.RAVAGER_ROAR, 0.35F, 1.0F);
            }
            case ROAR + 22 -> sound(SoundEvents.WITHER_SPAWN, 0.6F, 0.4F);
            case CHARGE -> sound(SoundEvents.BEACON_ACTIVATE, 0.5F, 1.0F);
            case CHARGE + 30 -> sound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.7F, 1.0F);
            case FIRE -> {
                sound(SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 1.0F);
                sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.4F, 0.6F);
            }
            case RETURN + 4 -> sound(SoundEvents.PORTAL_TRAVEL, 1.8F, 0.25F);
            case ATMO -> sound(SoundEvents.FIRECHARGE_USE, 0.5F, 0.8F);
            case IMPACT -> {
                sound(SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 1.0F);
                sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.0F);
                impactParticles(mc.level);
            }
            case AFTER + 30 -> sound(SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.0F);
            default -> {}
        }
    }

    private static void sound(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    /** Echte Partikel beim Einschlag: Blitz, Explosion, Funkenregen. */
    private static void impactParticles(ClientLevel level) {
        Vec3 c = world(new Vector3f(0, mouthY(), 0));
        level.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0, 0, 0);
        level.addParticle(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 0, 0, 0);
        level.addParticle(ParticleTypes.SONIC_BOOM, c.x, c.y + 1, c.z, 0, 0, 0);
        for (int i = 0; i < 160; i++) {
            double a = i * 0.618 * Mth.TWO_PI, up = 0.2 + 0.8 * (RenderUtil.hash(i) * 0.5 + 0.5);
            double sp = 0.4 + 0.6 * (RenderUtil.hash(i * 3) * 0.5 + 0.5);
            level.addParticle(i % 3 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.END_ROD, c.x, c.y + 1, c.z,
                    Math.cos(a) * sp, up * sp, Math.sin(a) * sp);
        }
    }

    // ------------------------------------------------------------------ Welt

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!active) return;
        boolean sky = event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY;
        if (!sky && event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft mc = Minecraft.getInstance();
        float t = tick + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Vector3f right = new Vector3f(event.getCamera().getLeftVector()).negate();
        Vector3f up = new Vector3f(event.getCamera().getUpVector());
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(core.getX() + 0.5 - cam.x, core.getY() + 0.5 - cam.y, core.getZ() + 0.5 - cam.z);
        Vector3f camRel = new Vector3f((float) (cam.x - core.getX() - 0.5), (float) (cam.y - core.getY() - 0.5), (float) (cam.z - core.getZ() - 0.5));
        Matrix4f m = pose.last().pose();
        Matrix4f cm = new Matrix4f(m).translate(camRel.x, camRel.y, camRel.z); // kamerazentriert (Kulissen)
        if (sky) {
            // Kulisse des fremden Universums liegt hinter dem Titanen (Entities werden danach gezeichnet)
            if (in(t, UNIVERSE, RETURN)) universeSky(cm, buffers, right, up, t);
        } else {
            if (in(t, CALL, SPACE + 10)) callPulse(m, buffers, t);
            if (in(t, SPACE, WARP + 40)) earthView(cm, buffers, right, up, t, false);
            if (in(t, WARP, UNIVERSE)) warpView(cm, buffers, t, false);
            if (in(t, UNIVERSE, RETURN)) universeFx(m, buffers, right, up, t);
            if (in(t, RETURN, ATMO)) {
                warpView(cm, buffers, t, true);
                if (t >= HOME) earthView(cm, buffers, right, up, t, true);
            }
            if (t >= ATMO) earthBeam(m, buffers, camRel, t);
        }
        pose.popPose();
    }

    /** Der Ruf: ein Lichtimpuls schiesst aus dem Trichter senkrecht in den Himmel. */
    private static void callPulse(Matrix4f m, MultiBufferSource.BufferSource buffers, float t) {
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        float f = easeIn(span(t, CALL + 20, SPACE));
        float y = mouthY() + 2.0F + 720.0F * f;
        Matrix4f pm = new Matrix4f(m).translate(0, y, 0);
        float charge = span(t, CALL, CALL + 20);
        RenderUtil.glowSphere(glow, pm, 0.3F + 0.35F * charge, 10, 14, WHITE, 0.7F);
        RenderUtil.glowSphere(glow, pm, 0.7F + 0.6F * charge, 10, 14, CYAN, 0.2F);
        RenderUtil.segment(glow, m, 0, Math.max(mouthY(), y - 40.0F * f - 4.0F), 0, 0, y, 0, 0.5F, CYAN[0], CYAN[1], CYAN[2], 0.0F, 0.7F);
        if (t < CALL + 24) { // Aufladen im Trichter
            for (int k = 0; k < 3; k++) {
                float ph = Mth.frac(t * 0.08F + k / 3.0F);
                RenderUtil.flatCircle(glow, new Matrix4f(m).translate(0, mouthY() + 3.0F * (1.0F - ph), 0), 0.3F + 3.0F * (1.0F - ph),
                        0.08F, 48, CYAN, charge * ph);
            }
        }
        buffers.endBatch(CosmicRenderTypes.glow());
    }

    /** Die Erde aus dem All: schrumpfend (Hinflug) bzw. wachsend (Rueckflug), dazu Mond, Sonne und Sterne. */
    private static void earthView(Matrix4f cm, MultiBufferSource.BufferSource buffers, Vector3f right, Vector3f up, float t, boolean back) {
        float f = back ? span(t, HOME, ATMO) : span(t, SPACE, WARP + 20);
        float near = back ? (float) Math.pow(f, 2.2) : 1.0F - (float) Math.pow(f, 0.45);
        float radius = Mth.lerp(near, 5.0F, 62.0F);
        Vector3f earth = new Vector3f(0.0F, -78.0F, back ? 6.0F : 30.0F);
        float rot = t * 0.004F;
        VertexConsumer solid = buffers.getBuffer(CosmicRenderTypes.spaceSolid());
        CutsceneScenery.gradientDome(solid, cm, 90.0F, 0xFF02030A, 0xFF05081A, 0xFF02030A);
        buffers.endBatch(CosmicRenderTypes.spaceSolid());
        VertexConsumer sg = buffers.getBuffer(CosmicRenderTypes.spaceGlow());
        CutsceneScenery.stars(sg, cm, right, up, 11, RenderQuality.scale(1400, 500), 86.0F, t, 0.9F);
        CutsceneScenery.nebula(sg, cm, right, up, new Vector3f(-0.4F, 0.6F, -0.7F), 84.0F, 40.0F, VOID, MAGENTA, 3, 14, 0.05F);
        CutsceneScenery.sun(sg, cm, right, up, new Vector3f(SUN_DIR).mul(84.0F), 2.2F, GOLD, 1.0F);
        buffers.endBatch(CosmicRenderTypes.spaceGlow());
        solid = buffers.getBuffer(CosmicRenderTypes.spaceSolid());
        CutsceneScenery.earth(solid, cm, earth, radius, rot, SUN_DIR);
        if (!back) { // der Mond zieht vorbei
            float mf = span(t, SPACE + 25, WARP);
            Vector3f moon = new Vector3f(-50.0F, -40.0F, 40.0F).lerp(new Vector3f(46.0F, -28.0F, -20.0F), mf);
            CutsceneScenery.moon(solid, cm, moon, 7.0F * (1.0F - 0.5F * mf), SUN_DIR);
        }
        buffers.endBatch(CosmicRenderTypes.spaceSolid());
        sg = buffers.getBuffer(CosmicRenderTypes.spaceGlow());
        CutsceneScenery.earthAtmosphere(sg, cm, right, up, earth, radius, rot, SUN_DIR);
        buffers.endBatch(CosmicRenderTypes.spaceGlow());
    }

    /** Lichtgeschwindigkeits-Tunnel (Hinflug nach vorn, Rueckflug nach unten mit dem Strahl voraus). */
    private static void warpView(Matrix4f cm, MultiBufferSource.BufferSource buffers, float t, boolean back) {
        float local = back ? t - RETURN : t - WARP;
        float total = back ? ATMO - RETURN : UNIVERSE - WARP;
        float speed = back ? 1.0F - 0.5F * span(local, total - 20, total) : easeIn(span(local, 0, 40));
        float alpha = back ? 1.0F - span(t, HOME, ATMO) : 1.0F;
        Vector3f fwd = back ? new Vector3f(0.0F, -0.98F, 0.2F).normalize() : new Vector3f(0.0F, 0.6F, -0.8F).normalize();
        if (!back) { // der Tunnel blendet ueber die schwindende Erde ein
            VertexConsumer tint = buffers.getBuffer(CosmicRenderTypes.spaceTint());
            int a = (int) (255 * span(t, WARP, WARP + 30));
            CutsceneScenery.colorSphere(tint, cm, new Vector3f(), 88.0F, 8, 12, 0.0F, (x, y, z) -> (a << 24) | 0x020210);
            buffers.endBatch(CosmicRenderTypes.spaceTint());
        } else {
            VertexConsumer solid = buffers.getBuffer(CosmicRenderTypes.spaceSolid());
            CutsceneScenery.gradientDome(solid, cm, 90.0F, 0xFF020210, 0xFF04061A, 0xFF020210);
            buffers.endBatch(CosmicRenderTypes.spaceSolid());
        }
        VertexConsumer sg = buffers.getBuffer(CosmicRenderTypes.spaceGlow());
        CutsceneScenery.warp(sg, cm, fwd, local, speed, alpha);
        if (back) { // der Strahl, auf dem die Kamera zurueckrast
            Vector3f a = new Vector3f(fwd).mul(3.0F), b = new Vector3f(fwd).mul(85.0F);
            RenderUtil.segment(sg, cm, a.x, a.y, a.z, b.x, b.y, b.z, 0.9F, CYAN[0], CYAN[1], CYAN[2], 0.35F * alpha, 0.0F);
            RenderUtil.segment(sg, cm, a.x, a.y, a.z, b.x, b.y, b.z, 0.2F, 1, 1, 1, 0.8F * alpha, 0.0F);
        }
        buffers.endBatch(CosmicRenderTypes.spaceGlow());
    }

    /** Himmel des fremden Universums: Farbverlauf, Sterne, Nebel, Galaxie, Doppelsonne und ein beringter Gasriese. */
    private static void universeSky(Matrix4f cm, MultiBufferSource.BufferSource buffers, Vector3f right, Vector3f up, float t) {
        VertexConsumer solid = buffers.getBuffer(CosmicRenderTypes.spaceSolid());
        CutsceneScenery.gradientDome(solid, cm, 90.0F, 0xFF06041A, 0xFF1A0A30, 0xFF03020C);
        buffers.endBatch(CosmicRenderTypes.spaceSolid());
        VertexConsumer sg = buffers.getBuffer(CosmicRenderTypes.spaceGlow());
        CutsceneScenery.stars(sg, cm, right, up, 77, RenderQuality.scale(1800, 600), 86.0F, t, 1.0F);
        CutsceneScenery.nebula(sg, cm, right, up, new Vector3f(-0.3F, 0.35F, -1.0F), 82.0F, 46.0F, MAGENTA, VOID, 5, 26, 0.06F);
        CutsceneScenery.nebula(sg, cm, right, up, new Vector3f(0.9F, 0.15F, -0.3F), 82.0F, 34.0F, CYAN, VOID, 9, 18, 0.05F);
        CutsceneScenery.nebula(sg, cm, right, up, new Vector3f(0.1F, -0.4F, 1.0F), 82.0F, 40.0F, VOID, MAGENTA, 13, 18, 0.045F);
        CutsceneScenery.galaxy(sg, cm, right, up, new Vector3f(0.45F, 0.55F, -0.7F).normalize().mul(80.0F), 22.0F, 1.1F, 0.4F, t * 0.002F);
        Vector3f sunA = new Vector3f(0.75F, 0.18F, -0.62F).normalize().mul(84.0F);
        Vector3f sunB = new Vector3f(0.82F, 0.26F, -0.5F).normalize().mul(84.0F);
        CutsceneScenery.sun(sg, cm, right, up, sunA, 2.4F, new float[]{1.0F, 0.6F, 0.3F}, 1.0F);
        CutsceneScenery.sun(sg, cm, right, up, sunB, 1.3F, new float[]{0.4F, 0.7F, 1.0F}, 0.9F);
        buffers.endBatch(CosmicRenderTypes.spaceGlow());
        // Gasriese mit Ring (hintere Ringhaelfte, Planet, vordere Ringhaelfte)
        Vector3f giant = new Vector3f(-0.7F, -0.42F, 0.25F).normalize().mul(78.0F);
        Vector3f light = new Vector3f(sunA).sub(giant).normalize();
        VertexConsumer tint = buffers.getBuffer(CosmicRenderTypes.spaceTint());
        CutsceneScenery.ring(tint, cm, giant, 38.0F, 62.0F, 0.35F, 0.25F, false, new Vector3f());
        buffers.endBatch(CosmicRenderTypes.spaceTint());
        solid = buffers.getBuffer(CosmicRenderTypes.spaceSolid());
        CutsceneScenery.gasGiant(solid, cm, giant, 30.0F, t * 0.001F, light);
        buffers.endBatch(CosmicRenderTypes.spaceSolid());
        tint = buffers.getBuffer(CosmicRenderTypes.spaceTint());
        CutsceneScenery.ring(tint, cm, giant, 38.0F, 62.0F, 0.35F, 0.25F, true, new Vector3f());
        buffers.endBatch(CosmicRenderTypes.spaceTint());
        sg = buffers.getBuffer(CosmicRenderTypes.spaceGlow());
        CutsceneScenery.disc(sg, cm, giant, right, up, 29.0F, 34.0F, new float[]{1.0F, 0.6F, 0.4F}, 0.25F, 0.0F); // Atmosphaerensaum
        buffers.endBatch(CosmicRenderTypes.spaceGlow());
    }

    /** Im fremden Universum: Aura, Augen, Herzkern, Bruell-Schockwelle, Aufladen und Schuss. */
    private static void universeFx(Matrix4f m, MultiBufferSource.BufferSource buffers, Vector3f right, Vector3f up, float t) {
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        // Aura: violette Lichtpunkte steigen spiralfoermig um den Titanen auf
        for (int k = 0; k < 70; k++) {
            float s = Mth.frac(t * 0.006F + k / 70.0F);
            float a = k * 2.39996F + s * 4.0F;
            float r = 10.0F + 14.0F * (RenderUtil.hash(k) * 0.5F + 0.5F);
            Vector3f p = new Vector3f(TITAN_POS).add(Mth.cos(a) * r, -30.0F + 55.0F * s, Mth.sin(a) * r);
            RenderUtil.cube(glow, m, p.x, p.y, p.z, 0.12F, VOID[0], VOID[1], VOID[2], 0.7F * Mth.sin(s * Mth.PI));
        }
        // Augen und Herzkern erwachen
        float awake = ease(span(t, AWAKE, AWAKE + 20));
        for (String eye : new String[]{"eye_l", "eye_r"}) {
            Vector3f e = bone(eye, new Vector3f(TITAN_POS).add(0, 15, 3));
            Matrix4f em = new Matrix4f(m).translate(e.x, e.y, e.z);
            RenderUtil.glowSphere(glow, em, 0.25F + 0.35F * awake, 8, 10, WHITE, 0.15F + 0.75F * awake);
            RenderUtil.glowSphere(glow, em, 0.8F + 1.2F * awake, 8, 10, CYAN, 0.08F + 0.3F * awake);
            if (awake > 0.5F) CutsceneScenery.streak(glow, m, e, right, up, 2.5F * awake, 0.05F, CYAN, 0.6F * awake);
        }
        Vector3f coreB = bone("core", new Vector3f(TITAN_POS).add(0, 9, 4));
        float beat = 0.7F + 0.3F * Mth.sin(t * (0.15F + 0.25F * awake));
        RenderUtil.glowSphere(glow, new Matrix4f(m).translate(coreB.x, coreB.y, coreB.z), 0.6F + 1.4F * awake * beat, 8, 12, CYAN, 0.2F + 0.4F * awake);
        // Bruellen: Schockwellen-Ringe vom Kopf aus
        if (in(t, ROAR, ROAR + 40)) {
            for (int k = 0; k < 3; k++) {
                float ph = span(t, ROAR + k * 7, ROAR + 26 + k * 7);
                if (ph <= 0 || ph >= 1) continue;
                Vector3f h = head();
                Matrix4f hm = new Matrix4f(m).translate(h.x, h.y, h.z).rotateX(Mth.HALF_PI);
                RenderUtil.flatCircle(glow, hm, 2.0F + 34.0F * ph, 0.2F + 0.4F * (1 - ph), 96, k == 1 ? CYAN : VOID, (1.0F - ph) * 0.8F);
            }
        }
        // Aufladen: Energie strudelt aus dem All in die Kugel zwischen den Haenden
        Vector3f o = orb();
        float charge = span(t, CHARGE, FIRE);
        if (charge > 0 && t < FIRE + 5) {
            Matrix4f om = new Matrix4f(m).translate(o.x, o.y, o.z);
            float r = 0.5F + 2.2F * charge * charge;
            float pulse = 0.85F + 0.15F * Mth.sin(t * 0.9F);
            RenderUtil.glowSphere(glow, om, r * pulse * 0.6F, 12, 16, WHITE, 0.6F);
            RenderUtil.glowSphere(glow, om, r * 1.2F, 12, 16, CYAN, 0.25F);
            RenderUtil.glowSphere(glow, om, r * 1.7F, 10, 14, VOID, 0.08F);
            for (int k = 0; k < 36; k++) { // Spiralstroeme
                float s = Mth.frac(t * 0.02F + k / 36.0F);
                float ang = k * 2.39996F + s * 9.0F;
                float el = RenderUtil.hash(k * 5) * 0.9F;
                float rr = 40.0F * (1.0F - s) * (1.0F - s) + r;
                Vector3f p = new Vector3f(Mth.cos(ang) * Mth.cos(el) * rr, Mth.sin(el) * rr, Mth.sin(ang) * Mth.cos(el) * rr);
                float s2 = Math.max(0.0F, s - 0.04F);
                float rr2 = 40.0F * (1.0F - s2) * (1.0F - s2) + r, ang2 = k * 2.39996F + s2 * 9.0F;
                Vector3f q = new Vector3f(Mth.cos(ang2) * Mth.cos(el) * rr2, Mth.sin(el) * rr2, Mth.sin(ang2) * Mth.cos(el) * rr2);
                float[] c = RenderUtil.lerp(CYAN, k % 3 == 0 ? MAGENTA : WHITE, s);
                RenderUtil.segment(glow, om, q.x, q.y, q.z, p.x, p.y, p.z, 0.1F + 0.15F * s, c[0], c[1], c[2], 0.0F, 0.9F * s);
            }
            // Blitze zwischen den Haenden und der Kugel
            for (String hand : new String[]{"hand_l", "hand_r"}) {
                Vector3f h = bone(hand, o);
                lightning(glow, m, h, o, (int) (t * 1.5F) + hand.length() * 31 + hand.charAt(5), charge);
            }
        }
        if (t >= FIRE) { // Schuss - frontal zur Kamera
            float reach = span(t, FIRE, FIRE + 5);
            TitanBeam.beam(m, glow, o, new Vector3f(o).add(0, 0, 200.0F), t, reach, 2.2F);
            Matrix4f om = new Matrix4f(m).translate(o.x, o.y, o.z);
            RenderUtil.glowSphere(glow, om, 3.0F + 2.0F * Mth.sin(t * 1.3F), 12, 16, WHITE, 0.6F);
        }
        buffers.endBatch(CosmicRenderTypes.glow());
    }

    private static void lightning(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, int seed, float alpha) {
        Vector3f prev = new Vector3f(a);
        for (int i = 1; i <= 6; i++) {
            float f = i / 6.0F;
            Vector3f p = new Vector3f(a).lerp(b, f);
            if (i < 6) p.add(RenderUtil.hash(seed * 7 + i) * 1.2F, RenderUtil.hash(seed * 11 + i) * 1.2F, RenderUtil.hash(seed * 13 + i) * 1.2F);
            RenderUtil.segment(vc, m, prev.x, prev.y, prev.z, p.x, p.y, p.z, 0.05F, 0.85F, 0.95F, 1.0F, alpha, alpha);
            prev = p;
        }
    }

    /** Zurueck auf der Erde: der Strahl faellt vom Tor am Himmel in den Trichter, Einschlag mit Bodenwelle. */
    private static void earthBeam(Matrix4f m, MultiBufferSource.BufferSource buffers, Vector3f camRel, float t) {
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        float gs = TitanBeam.skyScale(TitanBeam.SKY_TOP, camRel);
        TitanBeam.skyGate(TitanBeam.skyFrame(m, camRel, gs), glow, t, 1.0F, camRel);
        Vector3f from = TitanBeam.skyPoint(TitanBeam.SKY_TOP, camRel, gs);
        float glare = 1.8F - 1.0F * span(t, IMPACT, AFTER + 40);
        // der Strahl eilt der Kamera voraus und erreicht den Trichter mit dem Einschlag
        float reach = span(t, ATMO, IMPACT);
        TitanBeam.beam(m, glow, from, new Vector3f(0, mouthY(), 0), t, Math.max(reach, 0.02F), glare);
        if (t >= IMPACT) TitanBeam.inlet(m, glow, mouthY(), t, 0.8F);
        if (in(t, IMPACT, IMPACT + 50)) {
            float f = (t - IMPACT) / 50.0F;
            float e = 1.0F - (float) Math.pow(1.0F - f, 3);
            Matrix4f im = new Matrix4f(m).translate(0, mouthY(), 0);
            RenderUtil.glowSphere(glow, im, 1.0F + 3.0F * e, 14, 20, WHITE, 0.45F * (1.0F - f) * (1.0F - f));
            RenderUtil.glowSphere(glow, im, 2.0F + 6.0F * e, 12, 16, CYAN, 0.12F * (1.0F - f));
            // Bodenwelle auf Hoehe des Bodens unter dem Reaktor
            Matrix4f gm = new Matrix4f(m).translate(0, -5.4F, 0);
            for (int k = 0; k < 3; k++) {
                float g = span(t, IMPACT + k * 5, IMPACT + 40 + k * 5);
                if (g <= 0 || g >= 1) continue;
                float ge = 1.0F - (float) Math.pow(1.0F - g, 2);
                RenderUtil.flatCircle(glow, gm, 4.0F + 46.0F * ge, 0.6F * (1.0F - g) + 0.1F, 128, k == 1 ? VOID : CYAN, (1.0F - g) * 0.9F);
            }
            RenderUtil.flatCircle(glow, new Matrix4f(im).rotateX(Mth.HALF_PI), 2.0F + 20.0F * e, 0.3F, 96, VOID, 1.0F - f);
        }
        buffers.endBatch(CosmicRenderTypes.glow());
    }

    /** Waehrend der Cutscene keine Nebelwand. */
    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (!active) return;
        event.setNearPlaneDistance(Math.max(event.getNearPlaneDistance(), 600.0F));
        event.setFarPlaneDistance(Math.max(event.getFarPlaneDistance(), 1400.0F));
        event.setCanceled(true);
    }

    // ------------------------------------------------------------------ Bildschirm: Breitbild, Vignette, Titel, Untertitel

    @SubscribeEvent
    public static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (active) event.setCanceled(true);
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
        // Vignette
        int vig = (int) (h * 0.35F);
        g.fillGradient(0, 0, w, vig, 0x70000000, 0x00000000);
        g.fillGradient(0, h - vig, w, h, 0x00000000, 0x70000000);
        int bw = Math.max(1, w / 120);
        for (int i = 0; i < 24; i++) {
            int a = (int) (0x50 * (1.0F - i / 24.0F));
            g.fill(i * bw, 0, (i + 1) * bw, h, a << 24);
            g.fill(w - (i + 1) * bw, 0, w - i * bw, h, a << 24);
        }
        // Blenden: Ein-/Ausblende, Uebergaenge ins All, in das andere Universum und zurueck
        fade(g, w, h, 0x000000, 1.0F - span(t, 0, 18));
        fade(g, w, h, 0x000000, span(t, LENGTH - 22, LENGTH));
        fade(g, w, h, 0xFFFFFF, bell(t, SPACE - 6, SPACE, SPACE + 10) * 0.9F);
        fade(g, w, h, 0xFFFFFF, bell(t, UNIVERSE - 12, UNIVERSE, UNIVERSE + 16));
        fade(g, w, h, 0xFFFFFF, bell(t, FIRE + 2, FIRE + 8, RETURN + 2));
        fade(g, w, h, 0xFFFFFF, bell(t, ATMO - 6, ATMO, ATMO + 10) * 0.9F);
        fade(g, w, h, 0xFFFFFF, bell(t, IMPACT, IMPACT + 1, IMPACT + 20));
        // Breitbild 2.39:1
        float bars = ease(span(t, 0, 20)) * (1.0F - ease(span(t, LENGTH - 25, LENGTH)));
        int bh = Math.round(Math.max(0.0F, (h - w / 2.39F) / 2.0F) * bars);
        bh = Math.max(bh, Math.round(h * 0.08F * bars));
        g.fill(0, 0, w, bh, 0xFF000000);
        g.fill(0, h - bh, w, h, 0xFF000000);
        bossTitle(g, mc, t, w, h);
        subtitle(g, mc, t, 22, CALL - 6, "cutscene.cosmiccore.line1", w, h, bh);
        subtitle(g, mc, t, RISE + 10, SPACE - 8, "cutscene.cosmiccore.line2", w, h, bh);
        subtitle(g, mc, t, WARP + 20, UNIVERSE - 14, "cutscene.cosmiccore.line3", w, h, bh);
        subtitle(g, mc, t, UNIVERSE + 18, AWAKE - 4, "cutscene.cosmiccore.line4", w, h, bh);
        subtitle(g, mc, t, CHARGE + 8, FIRE - 4, "cutscene.cosmiccore.line5", w, h, bh);
        subtitle(g, mc, t, RETURN + 6, ATMO - 4, "cutscene.cosmiccore.line6", w, h, bh);
        subtitle(g, mc, t, AFTER + 20, LENGTH - 24, "cutscene.cosmiccore.line7", w, h, bh);
        Component skip = Component.translatable("cutscene.cosmiccore.skip");
        g.drawString(mc.font, skip, w - mc.font.width(skip) - 8, Math.max(4, bh / 2 - 4), 0x70B8A8D8, false);
    }

    /** Glockenkurve: 0 vor a, 1 bei b, 0 ab c. */
    private static float bell(float t, float a, float b, float c) {
        if (t <= a || t >= c) return 0.0F;
        return t < b ? span(t, a, b) : 1.0F - span(t, b, c);
    }

    private static void fade(GuiGraphics g, int w, int h, int rgb, float a) {
        if (a <= 0.003F) return;
        g.fill(0, 0, w, h, ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | rgb);
    }

    /** Boss-Vorstellung wie bei grossen Endgegnern: Name gross, Beiname darunter, Zierlinien. */
    private static void bossTitle(GuiGraphics g, Minecraft mc, float t, int w, int h) {
        float a = Math.min(span(t, ROAR + 8, ROAR + 22), 1.0F - span(t, CHARGE + 30, CHARGE + 45));
        if (a <= 0.02F) return;
        Component name = Component.translatable("cutscene.cosmiccore.boss_name");
        Component sub = Component.translatable("cutscene.cosmiccore.boss_title");
        float sc = 3.2F * (1.0F + 0.06F * span(t, ROAR + 8, CHARGE + 45));
        int alpha = Math.max(8, (int) (255 * a));
        g.pose().pushPose();
        g.pose().translate(w / 2.0F, h * 0.24F, 0);
        g.pose().scale(sc, sc, 1);
        int nw = mc.font.width(name);
        g.drawString(mc.font, name, -nw / 2, -4, (alpha << 24) | 0xE6D2FF, true);
        g.pose().popPose();
        int lineY = (int) (h * 0.24F + 6 * sc);
        int half = (int) (nw * sc / 2 + 10);
        int lw = (int) (60 * a);
        g.fill(w / 2 - half - lw, lineY, w / 2 - half, lineY + 1, (alpha << 24) | 0xB080FF);
        g.fill(w / 2 + half, lineY, w / 2 + half + lw, lineY + 1, (alpha << 24) | 0xB080FF);
        g.pose().pushPose();
        g.pose().translate(w / 2.0F, lineY + 6, 0);
        g.pose().scale(1.4F, 1.4F, 1);
        g.drawString(mc.font, sub, -mc.font.width(sub) / 2, 0, (alpha << 24) | 0x9FE8FF, true);
        g.pose().popPose();
    }

    private static void subtitle(GuiGraphics g, Minecraft mc, float t, float from, float to, String key, int w, int h, int bh) {
        if (t < from || t > to) return;
        float a = Math.min(span(t, from, from + 10), 1.0F - span(t, to - 10, to));
        if (a <= 0.02F) return;
        Component c = Component.translatable(key);
        int alpha = Math.max(8, (int) (255 * a));
        float sc = 1.3F;
        g.pose().pushPose();
        g.pose().translate(w / 2.0F, h - Math.max(bh, 20) / 2.0F - 4 * sc, 0);
        g.pose().scale(sc, sc, 1);
        g.drawString(mc.font, c, -mc.font.width(c) / 2, 0, (alpha << 24) | 0xEDE6FF, true);
        g.pose().popPose();
    }

    /** Spieler steht still, solange der Film laeuft. */
    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (!active) return;
        ClientInput in = event.getInput();
        // Bewegungsvektor ist geschuetzt: per Reflexion auf null setzen (Feldname in Mojang-Namen, gilt auch im Spiel)
        try {
            var f = ClientInput.class.getDeclaredField("moveVector");
            f.setAccessible(true);
            f.set(in, net.minecraft.world.phys.Vec2.ZERO);
        } catch (ReflectiveOperationException ignored) {
        }
        in.keyPresses = net.minecraft.world.entity.player.Input.EMPTY;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (active) stop();
    }
}
