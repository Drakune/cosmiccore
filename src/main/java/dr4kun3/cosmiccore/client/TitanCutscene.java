package dr4kun3.cosmiccore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dr4kun3.cosmiccore.CosmicCore;
import dr4kun3.cosmiccore.entity.ModEntities;
import dr4kun3.cosmiccore.entity.VoidTitanEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
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
 * Kurzfilm beim ersten Ultimate-Modus: Die Kamera steigt vom Reaktor auf, die Erde schrumpft unter ihr, der Himmel
 * wird schwarz - dann schiesst sie mit Lichtgeschwindigkeit durch einen Tunnel aus Sternenstreifen in ein anderes
 * Universum. Dort erwacht der Void-Titan (eine mehrteilige Entity, gezeichnet von GeckoLib), sammelt die Energie des
 * Kosmos und schleudert sie zur Erde; die Kamera rast am Strahl zurueck, bis er in den Sternentrichter einschlaegt.
 * Seither faellt der Strahl vom Himmel in den Trichter, solange der Ultimate-Modus laeuft.
 * <p>
 * Ohne GeckoLib gibt es keine Cutscene. Die Kamera fuehrt ein clientseitiges Marker-Objekt; der Titan ist eine
 * clientseitige Entity, die nur waehrend der Cutscene existiert.
 */
@EventBusSubscriber(modid = CosmicCore.MODID, value = Dist.CLIENT)
public final class TitanCutscene {
    private TitanCutscene() {}

    // Drehbuch (Ticks)
    private static final int RISE = 40, LOOK_BACK = 120, BLACKOUT = 175, WARP = 200, ARRIVE = 290, UNIVERSE = 300,
            ROAR = 390, CHARGE = 430, FIRE = 490, RETURN = 500, DESCENT = 540, IMPACT = 560, AFTER = 575, LENGTH = 650;
    /** Der Titan erwacht mit UNIVERSE (seine Animation beginnt dort bei 0). */
    private static final int TITAN_START = UNIVERSE;
    /** Die Buehne im anderen Universum, weit ueber der Welt (relativ zur Kernmitte). */
    private static final Vector3f STAGE = new Vector3f(0.0F, 2600.0F, 0.0F);
    private static final Vector3f TITAN_POS = new Vector3f(STAGE).add(0.0F, -14.0F, -72.0F);
    private static final float[] VOID = {0.55F, 0.25F, 1.0F};
    private static final float[] CYAN = {0.45F, 0.95F, 1.0F};
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};
    private static final float[] MAGENTA = {1.0F, 0.35F, 0.8F};

    private static boolean active;
    private static BlockPos core;
    private static int tick;
    private static int skipHeld;
    private static Marker camera;
    private static VoidTitanEntity titan;

    /** Vom Server beim ersten Ultimate-Modus ausgeloest (pos = Kernmitte). */
    public static void start(BlockPos corePos) {
        Minecraft mc = Minecraft.getInstance();
        if (active || mc.level == null || mc.player == null) return;
        if (!ModList.get().isLoaded("geckolib")) return; // ohne GeckoLib keine Cutscene
        if (mc.player.position().distanceTo(Vec3.atCenterOf(corePos)) > 256.0) return;
        core = corePos.immutable();
        tick = 0;
        skipHeld = 0;
        camera = EntityType.MARKER.create(mc.level);
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

    static VoidTitanEntity titan() {
        return titan;
    }

    public static int currentTick() {
        return tick;
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

    /** Der Titan als clientseitige, mehrteilige Entity auf der Buehne im anderen Universum. */
    private static void spawnTitan(ClientLevel level) {
        titan = ModEntities.VOID_TITAN.get().create(level);
        if (titan == null) return;
        titan.setId(-7_700_000); // clientseitige ID (kollidiert nicht mit Server-IDs)
        Vec3 p = world(TITAN_POS);
        titan.moveTo(p.x, p.y, p.z, 180.0F, 0.0F); // Vorderseite zur Kamera
        titan.scriptTime = 0.0F;
        titan.updateParts();
        level.addEntity(titan);
    }

    private static Vec3 world(Vector3f rel) {
        return new Vec3(core.getX() + 0.5 + rel.x, core.getY() + 0.5 + rel.y, core.getZ() + 0.5 + rel.z);
    }

    private static Vector3f orb() {
        if (titan == null) return new Vector3f(TITAN_POS);
        Vec3 o = titan.orbPosition();
        return new Vector3f((float) (o.x - core.getX() - 0.5), (float) (o.y - core.getY() - 0.5), (float) (o.z - core.getZ() - 0.5));
    }

    private static Vector3f titanHead() {
        return new Vector3f(TITAN_POS).add(0.0F, 40.0F * VoidTitanEntity.SCALE / 16.0F, 0.0F);
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

    private static float easeIn(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * x;
    }

    private static float span(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0.0F, 1.0F);
    }

    private static boolean inWarp(float t) {
        return (t >= WARP && t < UNIVERSE) || (t >= RETURN && t < DESCENT);
    }

    private static boolean inUniverse(float t) {
        return t >= UNIVERSE && t < RETURN;
    }

    // ------------------------------------------------------------------ Kamera (Drehbuch)

    private static void placeCamera(float t) {
        Vector3f pos, look;
        if (t < RISE) {
            // 1: am Reaktor - ein Ruf aus dem All, der Blick hebt sich
            float f = ease(span(t, 0, RISE));
            pos = new Vector3f(10.0F, 6.0F, 14.0F);
            look = new Vector3f(0, 4, 0).lerp(new Vector3f(0, 200, 0), f);
        } else if (t < LOOK_BACK) {
            // 2: Aufstieg, immer schneller - durch die Wolken
            float f = easeIn(span(t, RISE, LOOK_BACK));
            pos = new Vector3f(10.0F, 6.0F + 520.0F * f, 14.0F);
            look = new Vector3f(pos).add(2.0F, 50.0F, 1.0F);
        } else if (t < BLACKOUT) {
            // 3: Blick zurueck - die Welt schrumpft unter der Kamera
            float f = span(t, LOOK_BACK, BLACKOUT);
            pos = new Vector3f(10.0F, 526.0F + 900.0F * f * f, 14.0F);
            float turn = ease(span(t, LOOK_BACK, LOOK_BACK + 18)) * (1.0F - ease(span(t, BLACKOUT - 22, BLACKOUT)));
            look = new Vector3f(pos).add(2.0F, 50.0F, 1.0F).lerp(new Vector3f(0, 0, 0), turn);
        } else if (t < UNIVERSE) {
            // 4: Lichtgeschwindigkeit - die Kamera blickt nach vorn (oben) in den Tunnel
            pos = new Vector3f(10.0F, 1426.0F, 14.0F);
            look = new Vector3f(pos).add(0.5F, 50.0F, 0.0F);
        } else if (t < ROAR) {
            // 5: das andere Universum - langsame Fahrt auf den erwachenden Titanen zu
            float f = ease(span(t, UNIVERSE, ROAR));
            pos = new Vector3f(STAGE).add(Mth.lerp(f, 30.0F, 14.0F), Mth.lerp(f, 8.0F, 2.0F), Mth.lerp(f, 10.0F, -18.0F));
            look = new Vector3f(TITAN_POS).add(0, 16.0F, 0);
        } else if (t < CHARGE) {
            // 6: Nahaufnahme - er bruellt
            float f = ease(span(t, ROAR, CHARGE));
            pos = new Vector3f(titanHead()).add(9.0F - 3.0F * f, -2.0F, 26.0F - 6.0F * f);
            look = titanHead();
        } else if (t < FIRE) {
            // 7: die Energie des Kosmos sammelt sich vor seiner Brust
            float f = ease(span(t, CHARGE, FIRE));
            pos = new Vector3f(orb()).add(Mth.lerp(f, 30.0F, 18.0F), 4.0F, Mth.lerp(f, 30.0F, 22.0F));
            look = new Vector3f(orb()).lerp(titanHead(), 0.25F);
        } else if (t < RETURN) {
            // 8: der Schuss - direkt auf die Kamera zu
            pos = new Vector3f(orb()).add(1.5F, 1.0F, 40.0F);
            look = orb();
        } else if (t < DESCENT) {
            // 9: am Strahl zurueck zur Erde (nach unten)
            pos = new Vector3f(1.5F, 1426.0F, 1.5F);
            look = new Vector3f(pos).add(0.5F, -50.0F, 0.0F);
        } else if (t < AFTER) {
            // 10: Sturz am Strahl hinab in den Trichter, Einschlag
            float f = ease(span(t, DESCENT, IMPACT));
            pos = new Vector3f(2.0F, 300.0F, 3.0F).lerp(new Vector3f(11.0F, 9.0F, 15.0F), f);
            look = new Vector3f(0, mouthY(), 0);
        } else {
            // 11: Ausklang - weite Fahrt um den Reaktor, der Strahl bleibt
            float f = ease(span(t, AFTER, LENGTH));
            float a = 0.9F + f * 0.8F;
            float d = Mth.lerp(f, 20.0F, 46.0F);
            pos = new Vector3f(Mth.cos(a) * d, Mth.lerp(f, 8.0F, 24.0F), Mth.sin(a) * d);
            look = new Vector3f(0, Mth.lerp(f, 4.0F, 30.0F), 0);
        }
        // Erschuetterung: Lichtgeschwindigkeit, Bruellen, Einschlag
        float shake = 0.0F;
        if (inWarp(t)) shake = 0.12F;
        if (t > ROAR + 4 && t < ROAR + 28) shake = 0.35F * (1.0F - (t - ROAR - 4) / 24.0F);
        if (t > IMPACT && t < IMPACT + 18) shake = 0.6F * (1.0F - (t - IMPACT) / 18.0F);
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
        camera.setXRot(pitch);
        camera.yRotO = yaw;
        camera.xRotO = pitch;
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
        if (tick == 2) sound(SoundEvents.WARDEN_HEARTBEAT, 0.6F, 1.0F);
        if (tick == RISE) sound(SoundEvents.ELYTRA_FLYING, 0.7F, 0.8F);
        if (tick == LOOK_BACK) sound(SoundEvents.ELYTRA_FLYING, 1.0F, 0.6F);
        if (tick == WARP) {
            sound(SoundEvents.BEACON_ACTIVATE, 0.5F, 1.0F);
            sound(SoundEvents.WARDEN_SONIC_BOOM, 1.4F, 0.5F);
        }
        if (tick == WARP + 40) sound(SoundEvents.PORTAL_TRAVEL, 1.6F, 0.3F);
        if (tick == ARRIVE) sound(SoundEvents.END_PORTAL_SPAWN, 0.6F, 0.8F);
        if (tick == UNIVERSE + 60) sound(SoundEvents.WITHER_SPAWN, 0.5F, 0.55F);
        if (tick == ROAR + 4) sound(SoundEvents.ENDER_DRAGON_GROWL, 0.45F, 1.0F);
        if (tick == ROAR + 6) sound(SoundEvents.RAVAGER_ROAR, 0.4F, 1.0F);
        if (tick == CHARGE) sound(SoundEvents.BEACON_ACTIVATE, 0.5F, 1.0F);
        if (tick == CHARGE + 30) sound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.7F, 1.0F);
        if (tick == FIRE) sound(SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 1.0F);
        if (tick == RETURN) sound(SoundEvents.PORTAL_TRAVEL, 1.8F, 0.3F);
        if (tick == IMPACT) {
            sound(SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 1.0F);
            sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.0F);
        }
        if (tick == AFTER + 20) sound(SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.0F);
    }

    private static void sound(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
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
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(core.getX() + 0.5 - cam.x, core.getY() + 0.5 - cam.y, core.getZ() + 0.5 - cam.z);
        Vector3f camRel = new Vector3f((float) (cam.x - core.getX() - 0.5), (float) (cam.y - core.getY() - 0.5), (float) (cam.z - core.getZ() - 0.5));
        Matrix4f m = pose.last().pose();
        if (sky) {
            if (inUniverse(t)) universeSky(m, buffers, camRel, t);
        } else {
            if (inWarp(t)) warp(m, buffers, camRel, t);
            if (inUniverse(t)) universeFx(m, buffers, t);
            if (t >= DESCENT) earthBeam(m, buffers, camRel, t);
        }
        pose.popPose();
    }

    /** Himmel des fremden Universums: animiertes Sternenfeld, farbige Nebel, eine fremde Galaxie und zwei Monde. */
    private static void universeSky(Matrix4f m, MultiBufferSource.BufferSource buffers, Vector3f cam, float t) {
        Matrix4f cm = new Matrix4f(m).translate(cam.x, cam.y, cam.z);
        RenderUtil.sphere(buffers.getBuffer(CosmicRenderTypes.spaceDome()), cm, 90.0F, 16, 24);
        buffers.endBatch(CosmicRenderTypes.spaceDome());
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        float[][] neb = {MAGENTA, CYAN, VOID, MAGENTA, VOID, CYAN};
        for (int i = 0; i < neb.length; i++) {
            Vector3f d = new Vector3f(RenderUtil.hash(i * 7 + 1), RenderUtil.hash(i * 11 + 2) * 0.6F, RenderUtil.hash(i * 13 + 3)).normalize();
            Matrix4f nm = new Matrix4f(cm).translate(d.x * 70, d.y * 70, d.z * 70);
            RenderUtil.glowSphere(glow, nm, 22.0F + 6.0F * (i % 3), 10, 14, neb[i], 0.07F);
            RenderUtil.glowSphere(glow, nm, 11.0F, 8, 12, neb[i], 0.06F);
        }
        // fremde Galaxie hinter dem Titanen: Spiralarme aus Lichtpunkten
        Matrix4f gm = new Matrix4f(cm).translate(-10.0F, 18.0F, -78.0F).rotateX(1.05F).rotateZ(t * 0.0015F);
        for (int arm = 0; arm < 3; arm++) {
            for (int i = 0; i < 60; i++) {
                float f = i / 60.0F;
                float r = 2.0F + 26.0F * f;
                float a = arm * Mth.TWO_PI / 3 + f * 5.0F;
                float[] c = RenderUtil.lerp(WHITE, f < 0.5F ? CYAN : MAGENTA, f);
                RenderUtil.cube(glow, gm, Mth.cos(a) * r, RenderUtil.hash(arm * 97 + i) * 0.6F, Mth.sin(a) * r,
                        0.25F + 0.25F * (1.0F - f), c[0], c[1], c[2], 0.85F * (1.0F - 0.6F * f));
            }
        }
        RenderUtil.glowSphere(glow, gm, 4.0F, 10, 14, WHITE, 0.5F);
        // zwei Monde
        float[][] moons = {{40, 30, -55, 5}, {-48, -6, -42, 3}};
        for (float[] mo : moons) {
            Matrix4f mm = new Matrix4f(cm).translate(mo[0], mo[1], mo[2]);
            RenderUtil.glowSphere(glow, mm, mo[3], 12, 16, VOID, 0.5F);
            RenderUtil.glowSphere(glow, mm, mo[3] * 1.4F, 10, 14, CYAN, 0.12F);
            RenderUtil.flatCircle(glow, new Matrix4f(mm).rotateX(0.4F), mo[3] * 2.0F, 0.15F, 64, MAGENTA, 0.5F);
        }
        buffers.endBatch(CosmicRenderTypes.glow());
    }

    /** Lichtgeschwindigkeit: schwarzes All, Sternenstreifen und Tunnelringe rasen an der Kamera vorbei. */
    private static void warp(Matrix4f m, MultiBufferSource.BufferSource buffers, Vector3f cam, float t) {
        boolean back = t >= RETURN;
        float local = back ? t - RETURN : t - WARP;
        float total = back ? DESCENT - RETURN : UNIVERSE - WARP;
        float speed = back ? 1.0F : ease(span(local, 0, 30)) * (1.0F - 0.3F * span(local, total - 15, total));
        Vector3f fwd = back ? new Vector3f(0, -1, 0) : new Vector3f(0, 1, 0);
        Vector3f u = new Vector3f(1, 0, 0), v = new Vector3f(0, 0, 1);
        Matrix4f cm = new Matrix4f(m).translate(cam.x, cam.y, cam.z);
        // schwarzes All (deckt Welt und Himmel ab)
        VertexConsumer solid = buffers.getBuffer(CosmicRenderTypes.spaceSolid());
        RenderUtil.solidSphere(solid, cm, 60.0F, 8, 12, 0.01F, 0.0F, 0.03F);
        buffers.endBatch(CosmicRenderTypes.spaceSolid());
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        int n = RenderQuality.scale(260, 90);
        for (int i = 0; i < n; i++) {
            float a = RenderUtil.hash(i * 7) * Mth.PI;
            float r = 2.0F + 24.0F * (RenderUtil.hash(i * 13) * 0.5F + 0.5F);
            float d = 55.0F * (1.0F - Mth.frac(RenderUtil.hash(i * 17) + local * 0.045F * (0.6F + speed)));
            float len = 2.0F + 22.0F * speed;
            Vector3f side = new Vector3f(u).mul(Mth.cos(a) * r).add(new Vector3f(v).mul(Mth.sin(a) * r));
            Vector3f p0 = new Vector3f(fwd).mul(d).add(side);
            Vector3f p1 = new Vector3f(fwd).mul(d + len).add(side);
            float[] c = RenderUtil.lerp(WHITE, i % 3 == 0 ? VOID : CYAN, RenderUtil.hash(i * 3) * 0.5F + 0.5F);
            float alpha = Mth.clamp(1.0F - d / 55.0F, 0.0F, 1.0F) * (0.4F + 0.6F * speed);
            RenderUtil.segment(glow, cm, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, 0.05F + 0.05F * speed, c[0], c[1], c[2], alpha, 0.0F);
        }
        for (int k = 0; k < 7; k++) { // Tunnelringe
            float d = 55.0F * (1.0F - Mth.frac(k / 7.0F + local * 0.03F * (0.5F + speed)));
            Matrix4f rm = new Matrix4f(cm).translate(fwd.x * d, fwd.y * d, fwd.z * d);
            RenderUtil.flatCircle(glow, rm, 20.0F + 4.0F * Mth.sin(k + local * 0.1F), 0.25F, 64, k % 2 == 0 ? VOID : CYAN,
                    0.5F * speed * Mth.clamp(1.0F - d / 55.0F, 0.0F, 1.0F));
        }
        if (back) { // der Strahl, auf dem die Kamera zurueckrast
            RenderUtil.segment(glow, cm, 0, -2, 0, 0, -60, 0, 0.6F, CYAN[0], CYAN[1], CYAN[2], 0.5F, 0.0F);
            RenderUtil.segment(glow, cm, 0, -2, 0, 0, -60, 0, 0.15F, 1, 1, 1, 0.9F, 0.0F);
        }
        buffers.endBatch(CosmicRenderTypes.glow());
    }

    /** Im anderen Universum: Aufladen der Kugel und der Schuss. */
    private static void universeFx(Matrix4f m, MultiBufferSource.BufferSource buffers, float t) {
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        Vector3f o = orb();
        float charge = span(t, CHARGE, FIRE);
        if (charge > 0 && t < FIRE + 4) {
            Matrix4f om = new Matrix4f(m).translate(o.x, o.y, o.z);
            float r = 0.8F + 5.0F * charge * charge;
            float pulse = 0.85F + 0.15F * Mth.sin(t * 0.9F);
            RenderUtil.glowSphere(glow, om, r * pulse, 12, 16, WHITE, 0.45F);
            RenderUtil.glowSphere(glow, om, r * 1.6F, 12, 16, CYAN, 0.2F);
            RenderUtil.glowSphere(glow, om, r * 2.5F, 10, 14, VOID, 0.1F);
            for (int k = 0; k < 24; k++) { // Energie aus dem ganzen Kosmos stroemt herbei
                float s = Mth.frac(t * 0.025F + k / 24.0F);
                Vector3f dir = new Vector3f(RenderUtil.hash(k * 7), RenderUtil.hash(k * 11), RenderUtil.hash(k * 13)).normalize();
                Vector3f p = new Vector3f(dir).mul(45.0F * (1.0F - s));
                Vector3f q = new Vector3f(dir).mul(45.0F * (1.0F - s) + 5.0F);
                RenderUtil.segment(glow, om, q.x, q.y, q.z, p.x, p.y, p.z, 0.2F, CYAN[0], CYAN[1], CYAN[2], 0.0F, 0.8F * s);
            }
        }
        if (t >= FIRE) { // Schuss Richtung Erde (zur Kamera)
            float reach = span(t, FIRE, FIRE + 6);
            TitanBeam.beam(m, glow, o, new Vector3f(o).add(0, 0, 160.0F), t, reach, 1.8F);
        }
        buffers.endBatch(CosmicRenderTypes.glow());
    }

    /** Zurueck auf der Erde: der Strahl faellt vom Tor am Himmel in den Trichter. */
    private static void earthBeam(Matrix4f m, MultiBufferSource.BufferSource buffers, Vector3f camRel, float t) {
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        float gs = TitanBeam.skyScale(TitanBeam.SKY_TOP, camRel);
        TitanBeam.skyGate(TitanBeam.skyFrame(m, camRel, gs), glow, t, 1.0F, camRel);
        Vector3f from = TitanBeam.skyPoint(TitanBeam.SKY_TOP, camRel, gs);
        float reach = span(t, DESCENT, IMPACT);
        float glare = 1.6F - 0.8F * span(t, IMPACT, AFTER + 30);
        TitanBeam.beam(m, glow, from, new Vector3f(0, mouthY(), 0), t, reach, glare);
        if (t >= IMPACT) TitanBeam.inlet(m, glow, mouthY(), t, 0.8F);
        if (t >= IMPACT && t < IMPACT + 40) { // Einschlag: Lichtblitz und Schockwellen
            float f = (t - IMPACT) / 40.0F;
            Matrix4f im = new Matrix4f(m).translate(0, mouthY(), 0);
            RenderUtil.glowSphere(glow, im, 2.0F + 12.0F * f, 14, 20, WHITE, 0.6F * (1.0F - f));
            RenderUtil.flatCircle(glow, im, 3.0F + 30.0F * f, 0.4F, 96, CYAN, 1.0F - f);
            RenderUtil.flatCircle(glow, new Matrix4f(im).rotateX(Mth.HALF_PI), 2.0F + 18.0F * f, 0.3F, 96, VOID, 1.0F - f);
        }
        buffers.endBatch(CosmicRenderTypes.glow());
    }

    /** Waehrend der Cutscene keine Nebelwand (ferne Welt beim Aufstieg, Titan im All). */
    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (!active) return;
        event.setNearPlaneDistance(Math.max(event.getNearPlaneDistance(), 600.0F));
        event.setFarPlaneDistance(Math.max(event.getFarPlaneDistance(), 1400.0F));
        event.setCanceled(true);
    }

    // ------------------------------------------------------------------ Bildschirm

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
        float bars = ease(span(t, 0, 15)) * (1.0F - ease(span(t, LENGTH - 20, LENGTH)));
        int bh = Math.round(h * 0.12F * bars);
        // Abblende ins Schwarz des Alls, Lichtblitze bei Ankunft, Schuss und Einschlag
        int black = (int) (255 * span(t, BLACKOUT, WARP) * (1.0F - span(t, WARP, WARP + 6)));
        if (black > 0) g.fill(0, 0, w, h, black << 24);
        flash(g, w, h, t, ARRIVE - 6, 22);
        flash(g, w, h, t, FIRE + 4, 12);
        flash(g, w, h, t, IMPACT, 20);
        g.fill(0, 0, w, bh, 0xFF000000);
        g.fill(0, h - bh, w, h, 0xFF000000);
        subtitle(g, mc, t, 6, RISE + 10, "cutscene.cosmiccore.line1", w, h, bh);
        subtitle(g, mc, t, LOOK_BACK + 6, BLACKOUT - 4, "cutscene.cosmiccore.line2", w, h, bh);
        subtitle(g, mc, t, WARP + 20, ARRIVE - 10, "cutscene.cosmiccore.line3", w, h, bh);
        subtitle(g, mc, t, UNIVERSE + 20, ROAR, "cutscene.cosmiccore.line4", w, h, bh);
        subtitle(g, mc, t, CHARGE + 6, FIRE - 4, "cutscene.cosmiccore.line5", w, h, bh);
        subtitle(g, mc, t, RETURN + 4, DESCENT + 10, "cutscene.cosmiccore.line6", w, h, bh);
        subtitle(g, mc, t, AFTER + 16, LENGTH - 14, "cutscene.cosmiccore.line7", w, h, bh);
        Component skip = Component.translatable("cutscene.cosmiccore.skip");
        g.drawString(mc.font, skip, w - mc.font.width(skip) - 6, Math.max(4, bh / 2 - 4), 0x80B8A8D8, false);
    }

    private static void flash(GuiGraphics g, int w, int h, float t, int at, int len) {
        if (t < at || t >= at + len) return;
        int a = (int) (230 * (1.0F - (t - at) / len));
        g.fill(0, 0, w, h, (a << 24) | 0xFFFFFF);
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
