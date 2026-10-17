package dr4kun3.cosmiccore.client;

import static dr4kun3.cosmiccore.client.RenderUtil.annulusXY;
import static dr4kun3.cosmiccore.client.RenderUtil.cube;
import static dr4kun3.cosmiccore.client.RenderUtil.hash;
import static dr4kun3.cosmiccore.client.RenderUtil.lerp;
import static dr4kun3.cosmiccore.client.RenderUtil.quad2;
import static dr4kun3.cosmiccore.client.RenderUtil.quadFade;
import static dr4kun3.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dr4kun3.cosmiccore.block.CorePhase;
import dr4kun3.cosmiccore.block.ModProperties;
import dr4kun3.cosmiccore.block.entity.SingularityCoreBlockEntity;
import net.minecraft.client.Minecraft;
import dr4kun3.cosmiccore.client.render.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Das kosmische Loch im Herzen des Reaktors:
 * <ul>
 *   <li><b>Ereignishorizont:</b> tiefschwarz, waechst mit der Leistungsstufe, pulsiert bei Instabilitaet wie ein Herzschlag.</li>
 *   <li><b>Photonenring + Gravitationslinse:</b> gleissender Doppelring mit Lichtstrahlen, die Rueckseite der Scheibe
 *       woelbt sich als Lichtbogen ueber und unter das Loch - immer zum Betrachter gedreht.</li>
 *   <li><b>Akkretionsscheibe:</b> drei Lagen, innen weissgluehend, aussen violett, differentielle Rotation, Doppler-Aufhellung,
 *       Hotspots am Innenrand und eine Dunsthuelle.</li>
 *   <li><b>Materiefaeden:</b> werden von aussen durch das Schild gerissen und spiralen in die Scheibe.</li>
 *   <li><b>Jets</b> entlang der Pole; mit Himmelssicht schiesst der obere Jet ins All.</li>
 *   <li><b>Eindaemmungsfeld</b> des Emitters unter dem Kern ({@link FieldFx}) und <b>Kammer</b> ({@link ReactorShellRenderer}).</li>
 * </ul>
 * Reihenfolge der Puffer beachten: ein getBuffer() mit anderem RenderType schliesst den vorherigen Puffer ab.
 */
public class SingularityCoreRenderer extends dr4kun3.cosmiccore.client.render.LegacyBlockEntityRenderer<SingularityCoreBlockEntity> {
    private static final float[] HOT = {1.0F, 0.95F, 0.85F};
    private static final float[] WARM = {1.0F, 0.55F, 0.2F};
    private static final float[] COOL = {0.62F, 0.28F, 0.95F};
    private static final float[] JET = {0.6F, 0.8F, 1.0F};

    /** Farbwelten je Overdrive-Stufe: {heiss, warm, kuehl, Jet}. */
    private static final float[][][] PALETTES = {
            {{1.0F, 0.95F, 0.85F}, {1.0F, 0.55F, 0.2F}, {0.62F, 0.28F, 0.95F}, {0.6F, 0.8F, 1.0F}},
            {{0.9F, 0.97F, 1.0F}, {0.3F, 0.65F, 1.0F}, {0.25F, 0.2F, 1.0F}, {0.5F, 0.9F, 1.0F}},      // Ueberladung
            {{1.0F, 0.95F, 0.7F}, {1.0F, 0.72F, 0.15F}, {1.0F, 0.2F, 0.75F}, {1.0F, 0.85F, 0.4F}},    // Supernova
            {{1.0F, 1.0F, 1.0F}, {0.85F, 0.55F, 1.0F}, {0.5F, 0.25F, 1.0F}, {0.9F, 0.75F, 1.0F}},     // Quasar
            {{0.95F, 0.85F, 0.55F}, {0.85F, 0.5F, 0.08F}, {0.05F, 0.55F, 0.6F}, {0.85F, 0.7F, 0.3F}}}; // Ultimate-Modus (satt, nicht weiss)
    // pro Frame gesetzt (Rendering laeuft nur im Render-Thread)
    private static float[] pHot = HOT, pWarm = WARM, pCool = COOL, pJet = JET;
    private static final float[] CRITICAL = {1.0F, 0.1F, 0.1F};

    /**
     * Legt den Renderer des Singularitaetskerns an.
     *
     * @param context Kontext der Blockinhalt-Renderer
     */
    public SingularityCoreRenderer(BlockEntityRendererProvider.Context context) {}

    /**
     * Unbegrenzte Sichtbarkeitsbox (der Jet reicht bis in den Himmel).
     *
     * @param be der Kern
     * @return unendliche Box
     */
    @Override
    public AABB getRenderBoundingBox(SingularityCoreBlockEntity be) {
        return AABB.INFINITE; // Jet in den Himmel
    }

    /**
     * Auch zeichnen, wenn der Block selbst nicht im Bild ist.
     *
     * @return immer {@code true}
     */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    /**
     * Sichtweite des Kerns in Bloecken.
     *
     * @return 256
     */
    @Override
    public int getViewDistance() {
        return 256;
    }

    /**
     * Zeichnet den ganzen Reaktor: Kammer und Konsole, schwarzes Loch mit Akkretionsscheibe, Photonenring und Linse,
     * Jets, Eindaemmungsfeld, Laser, Overdrive- und Ultimate-Effekte sowie Zuendung und Materialisieren.
     *
     * @param be der (primaere) Kern
     * @param partialTick Teil-Tick
     * @param pose Pose-Stapel am Block
     * @param buffers Puffer
     * @param light Lichtwert
     * @param overlay Overlay-Wert
     * @param cameraPos Position der Kamera
     */
    @Override
    public void render(SingularityCoreBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay, net.minecraft.world.phys.Vec3 cameraPos) {
        Level level = be.getLevel();
        if (level == null) return;
        CorePhase phase = be.getBlockState().getValue(ModProperties.PHASE);
        if (phase == CorePhase.DORMANT || !be.isPrimary()) return;
        RenderQuality.begin(be.getBlockPos());

        final float t = (float) (level.getGameTime() % 144000L) + partialTick;
        final boolean unstable = phase == CorePhase.UNSTABLE;
        final boolean active = phase == CorePhase.ACTIVE || unstable;
        final float growth = be.getGrowth();
        final int od = Math.max(0, Math.min(4, be.getOverdrive()));
        // Farb-Modul: jeder Kanal mit eigener Farbe ersetzt die Farbwelt, sonst gilt die normale (inkl. Overdrive)
        ColorOverride.begin(be.getColors());
        float[][] base = PALETTES[od];
        pHot = ColorOverride.get(dr4kun3.cosmiccore.color.CoreColors.DISK_INNER, base[0]);
        pWarm = ColorOverride.get(dr4kun3.cosmiccore.color.CoreColors.DISK_MID, base[1]);
        pCool = ColorOverride.get(dr4kun3.cosmiccore.color.CoreColors.DISK_OUTER, base[2]);
        pJet = ColorOverride.get(dr4kun3.cosmiccore.color.CoreColors.JETS, base[3]);
        float[][] pal = {pHot, pWarm, pCool, pJet};
        final float reach = be.getReach();
        final int half = be.getStructureSize() / 2;
        final float heat = active ? Mth.clamp(be.getInstability() / 100.0F, 0.0F, 1.0F) : 0.0F;
        final float form = be.getFormProgress(partialTick);
        final float energy = (unstable ? 1.0F : active ? 0.9F : 0.35F)
                * (unstable ? 0.75F + 0.25F * (0.5F + 0.5F * hash((int) (t * 0.8F))) : 1.0F);
        // das Loch waechst, waehrend die Bloecke eingesaugt werden
        final float coreFade = Mth.clamp(0.15F + form / 0.6F, 0.0F, 1.0F);
        final float spin = (unstable ? 1.8F : active ? 1.0F : 0.25F) * (1.0F + 0.6F * growth);
        // Herzschlag bei Instabilitaet: doppelter Puls
        float beatPhase = Mth.frac(t * (0.02F + heat * 0.03F));
        float beat = heat > 0.5F
                ? ((float) Math.exp(-beatPhase * 18) + 0.6F * (float) Math.exp(-Math.abs(beatPhase - 0.18F) * 18)) * (heat - 0.5F) * 2
                : 0.0F;

        // 0) Jet in den Himmel
        if (active && be.hasSkyLink()) {
            float[] jc = lerp(pJet, CRITICAL, heat * heat);
            int color = ARGB.color(255, (int) (jc[0] * 255), (int) (jc[1] * 255), (int) (jc[2] * 255));
            float w = 0.12F + 0.1F * growth + 0.06F * Math.max(0.0F, be.getCosmic() - 1.0F);
            if (buffers instanceof dr4kun3.cosmiccore.client.render.DeferredBuffers d) {
                BeaconRenderer.submitBeaconBeam(pose, d.collector(), BeaconRenderer.BEAM_LOCATION, 1.0F,
                        Math.floorMod(level.getGameTime(), 40) + partialTick, half + (be.hasFunnel() ? 2 : 1), 1024, color, w, w + 0.15F);
            }
        }

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);

        // 1) Kammer (Sternenfeld-Platten)
        ReactorShellRenderer.Params shell = new ReactorShellRenderer.Params(level, be.getBlockPos(), be.getStructureSize(), t,
                form, Math.max(0.4F, energy), heat, active, unstable, growth, od, be, be.getSettle(partialTick));
        ReactorShellRenderer.solid(pose, buffers, shell);

        // Kosmos-Laser: Energiepartikel ueber der Decke
        Vec3 laserTarget = null;
        float targetSize = 1.0F, harvest = 0.0F;
        boolean lasers = be.hasLaserArray() && form >= 1.0F;
        if (lasers && be.getLaserTarget() >= 0
                && level.getEntity(be.getLaserTarget()) instanceof dr4kun3.cosmiccore.entity.CosmicAsteroid target) {
            laserTarget = target.getPosition(partialTick).add(0, target.getBbHeight() / 2, 0)
                    .subtract(Vec3.atCenterOf(be.getBlockPos()));
            targetSize = target.getBbWidth() * target.getPullScale();
            harvest = target.getHarvest();
            be.lastLaserTarget = laserTarget;
        }

        // Groessen
        // Kern skaliert mit der Kammergroesse und waechst mit jeder Leistungsstufe
        float rh = reach * (active ? 0.2F + 0.22F * growth : 0.14F) * Math.max(0.05F, coreFade);
        rh *= 1.0F + 0.03F * Mth.sin(t * 0.1F) + 0.12F * beat + 0.15F * od;
        float inner = rh * 1.5F;
        float outer = Math.max(inner + 0.25F, reach - 0.15F);
        outer = inner + (outer - inner) * (active ? 0.7F + 0.3F * growth : 0.5F);

        pose.pushPose();
        if (heat > 0.65F) {
            float amp = (heat - 0.65F) * 0.18F;
            int s = (int) (t * 2.0F);
            pose.translate(hash(s) * amp, hash(s + 17) * amp, hash(s + 31) * amp);
        }

        // 2) Ereignishorizont: animiertes End-Sternenfeld statt reinem Schwarz
        RenderUtil.sphere(buffers.getBuffer(RenderTypes.endGateway()), pose.last().pose(), rh, 16, 24);

        // 3) Licht
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
        Vec3 center = Vec3.atCenterOf(be.getBlockPos());
        double dx = cam.x - center.x, dy = cam.y - center.y, dz = cam.z - center.z;
        float camYaw = (float) Math.atan2(dx, dz);
        float camPitch = (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
        // die Overdrive-Effekte addieren viel Licht - je Stufe gedaempft, damit Details erkennbar bleiben
        float odDim = od >= 4 ? 0.55F : od == 3 ? 0.7F : od == 2 ? 0.82F : od == 1 ? 0.92F : 1.0F;
        float e = energy * coreFade * odDim;

        float tilt = 8.0F + (heat > 0.5F ? (heat - 0.5F) * 18.0F * Mth.sin(t * 0.21F) : 0.0F);
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(tilt));
        pose.mulPose(Axis.ZP.rotationDegrees(tilt * 0.4F * Mth.sin(t * 0.013F)));
        Matrix4f dm = pose.last().pose();
        // drei Lagen fuer Volumen
        int layers = RenderQuality.detail() < 0.5F ? 0 : 1; // bei wenig Detail nur die mittlere Lage
        for (int layer = -layers; layer <= layers; layer++) {
            Matrix4f lm = new Matrix4f(dm).translate(0, layer * 0.035F, 0);
            accretionDisk(lm, glow, inner, outer, t, spin * (1 + 0.4F * od), e * (layer == 0 ? 1.0F : 0.45F), heat, camYaw, layer, od);
        }
        haze(dm, glow, outer, e, heat);
        hotspots(dm, glow, inner, t, spin, e, heat);
        if (be.getStructureSize() >= 11) {
            // ultimativ: einfallende Materie und gelegentlich ein Gezeitenstrom in der Scheibenebene
            BlackHoleFx.infall(dm, glow, rh, inner, outer, t, e);
            BlackHoleFx.tidalStream(dm, glow, rh, inner, outer, t, e);
        }
        pose.popPose();
        if (od >= 1) {
            // Ueberladung: zweite, steil gekippte Scheibe im Gegenlauf
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(62.0F));
            pose.mulPose(Axis.YP.rotationDegrees(t * 0.3F));
            // inneres Band (radial getrennt von der dritten Scheibe)
            accretionDisk(pose.last().pose(), glow, inner * 1.1F, Mth.lerp(0.45F, inner, outer), t, -spin * 1.3F, e * 0.35F, heat, camYaw, 0, 0);
            pose.popPose();
        }
        if (od >= 4) {
            // Ultimate-Modus: dritte, goldene Scheibe quer zu den anderen beiden
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(-62.0F));
            pose.mulPose(Axis.YP.rotationDegrees(-t * 0.45F));
            // aeusseres Band - schneidet die zweite Scheibe nicht im selben Radius
            accretionDisk(pose.last().pose(), glow, Mth.lerp(0.55F, inner, outer), outer * 0.95F, t, spin * 1.6F, e * 0.22F, heat, camYaw, 0, 0);
            pose.popPose();
        }
        OverdriveFx.render(pose, glow, od, rh, t, e, camYaw, camPitch, pal);

        tendrils(pose.last().pose(), glow, reach, inner, t, e, heat, active);
        lensing(pose, glow, rh, inner, t, e, heat, camYaw, camPitch, beat);
        if (active) jets(pose.last().pose(), glow, rh, reach, t, e, heat, growth + 0.25F * od);
        if (be.getStructureSize() >= 11) BlackHoleFx.orbiters(pose.last().pose(), glow, rh, reach, t, e);
        if (be.getStructureSize() >= 11) UltimateFx.render(pose, glow, rh, inner, outer, reach, t, spin, e * (od >= 3 ? 0.75F : 1.0F), tilt);
        FieldFx.render(pose.last().pose(), glow, reach + 0.5F, reach, t, e, heat, be.getShield(), unstable, od);
        pose.popPose(); // Zittern

        // Zuenden: Sterne rasen aus dem Himmel in die Mitte (bei jedem Kern)
        IgnitionFx.render(pose.last().pose(), glow, be.getStructureSize(), be.getIgnitePhase(partialTick), 1.0F);
        if (active && form >= 1.0F) {
            // Mittelpunkt der Trichteroeffnung bzw. Deckenmitte
            float mouth = be.hasFunnel() ? half + 1.35F : half + 0.6F;
            IgnitionFx.ambient(pose.last().pose(), glow, be.getStructureSize(), mouth, t, 0.9F);
        }

        // Energieleitung vom Sternentrichter durch die Decke zum Fokuskristall
        if (be.hasFunnel() && form >= 1.0F) funnelLink(pose.last().pose(), glow, reach + 0.5F, half, t, Math.max(0.4F, energy), heat);

        // 4) Licht der Kammer und des Geschuetzes
        ReactorShellRenderer.glow(pose.last().pose(), glow, shell);
        if (lasers) {
            EnergyLaserFx.render(pose.last().pose(), glow, half, t, laserTarget, targetSize,
                    laserTarget != null ? be.getLaserCharge(partialTick) : 0.0F, harvest, Math.max(0.6F, energy),
                    be.getDeliverPhase(partialTick), be.getDeliverSource() != null ? be.getDeliverSource() : be.lastLaserTarget, be.hasFunnel() ? half + 1.35F : half + 0.6F, be.getDeliverSize());
        }
        // Strahl des Void-Titanen: seit dem Erwachen faellt er senkrecht vom Tor am Himmel in den Trichter -
        // aber nur, solange der Ultimate-Modus aktiv ist (waehrend der Cutscene zeichnet diese ihn selbst)
        if (be.isAwakened() && be.getOverdrive() >= 4 && !TitanCutscene.hidesPermanentBeam(be.getBlockPos())) {
            org.joml.Vector3f camRel = new org.joml.Vector3f((float) dx, (float) dy, (float) dz);
            float mouthY = be.hasFunnel() ? half + 1.35F : half + 0.6F;
            float gs = TitanBeam.skyScale(TitanBeam.SKY_TOP, camRel);
            TitanBeam.skyGate(TitanBeam.skyFrame(pose.last().pose(), camRel, gs), glow, t, 0.8F, camRel);
            org.joml.Vector3f from = TitanBeam.skyPoint(TitanBeam.SKY_TOP, camRel, gs);
            float bi = active ? 0.8F : 0.45F;
            TitanBeam.beam(pose.last().pose(), glow, from, new org.joml.Vector3f(0, mouthY, 0), t, 1.0F, bi);
            TitanBeam.inlet(pose.last().pose(), glow, mouthY, t, bi);
        }
        // 5) zuletzt: Rueckwand der Kammerkugel als reiner Tiefen-Abschluss - Wolken und Regen dahinter verschwinden,
        //    alles im Inneren (auch spaeter gezeichnete Effekte) liegt davor und bleibt sichtbar
        if (form > 0.0F) depthBackSphere(buffers.getBuffer(CosmicRenderTypes.depthBack()), pose.last().pose(), half + 0.2F);
        pose.popPose();
        ColorOverride.end();
    }

    /**
     * Kugel nur aus nach innen gewandten Flaechen (mit Culling sieht man von aussen genau die Rueckseite) - fuer den
     * unsichtbaren Tiefen-Abschluss der Kammer.
     *
     * @param vc Puffer (Tiefen-Abschluss)
     * @param m Matrix mit Ursprung in der Kernmitte
     * @param r Radius
     */
    private static void depthBackSphere(VertexConsumer vc, Matrix4f m, float r) {
        final int lat = 16, lon = 24;
        for (int i = 0; i < lat; i++) {
            float p0 = (float) (Math.PI * i / lat - Math.PI / 2), p1 = (float) (Math.PI * (i + 1) / lat - Math.PI / 2);
            for (int j = 0; j < lon; j++) {
                float l0 = Mth.TWO_PI * j / lon, l1 = Mth.TWO_PI * (j + 1) / lon;
                // Reihenfolge a, d, c, b = Vorderseite zeigt nach innen
                sphereVertex(vc, m, r, p0, l0);
                sphereVertex(vc, m, r, p0, l1);
                sphereVertex(vc, m, r, p1, l1);
                sphereVertex(vc, m, r, p1, l0);
            }
        }
    }

    /**
     * Ein Punkt auf der Kugel.
     *
     * @param vc Puffer
     * @param m Matrix
     * @param r Radius
     * @param phi Breite (Bogenmass)
     * @param lam Laenge (Bogenmass)
     */
    private static void sphereVertex(VertexConsumer vc, Matrix4f m, float r, float phi, float lam) {
        float c = Mth.cos(phi);
        vc.addVertex(m, r * c * Mth.cos(lam), r * Mth.sin(phi), r * c * Mth.sin(lam));
    }

    /**
     * Farbe der Scheibe: innen heiss, aussen kuehl, mit der Instabilitaet nach Rot verschoben.
     *
     * @param f Position von innen (0) nach aussen (1)
     * @param heat Instabilitaet 0..1
     * @return Farbe RGB 0..1
     */
    private static float[] diskColor(float f, float heat) {
        float[] c = f < 0.3F ? lerp(pHot, pWarm, f / 0.3F) : lerp(pWarm, pCool, (f - 0.3F) / 0.7F);
        return lerp(c, CRITICAL, heat * heat * 0.8F);
    }

    /**
     * Flache Akkretionsscheibe in der XZ-Ebene aus Ringbaendern; innen schneller (differentielle Rotation).
     *
     * @param m Matrix der Scheibenebene
     * @param vc Puffer fuer additives Leuchten
     * @param inner Innenradius
     * @param outer Aussenradius
     * @param t Animationszeit
     * @param spin Drehgeschwindigkeit
     * @param energy Helligkeit 0..1
     * @param heat Instabilitaet 0..1
     * @param camYaw Gierwinkel der Kamera (Doppler-Aufhellung der zukommenden Seite)
     * @param layer 0 = Hauptscheibe, 1 = zweite Scheibe ab Overdrive 1
     * @param od Overdrive-Stufe (Ringstruktur ab Stufe 2)
     */
    private static void accretionDisk(Matrix4f m, VertexConsumer vc, float inner, float outer, float t, float spin,
                                      float energy, float heat, float camYaw, int layer, int od) {
        final int bands = RenderQuality.scale(20, 8), seg = RenderQuality.scale(80, 32);
        float out = layer == 0 ? outer : inner + (outer - inner) * 0.75F; // aeussere Lagen enden frueher
        for (int b = 0; b < bands; b++) {
            float f0 = b / (float) bands, f1 = (b + 1) / (float) bands;
            float r0 = Mth.lerp(f0, inner, out), r1 = Mth.lerp(f1, inner, out);
            float[] col = diskColor(f0, heat);
            float base = energy * (float) Math.pow(1.0F - f0, 0.7) * Math.min(1.0F, 0.4F + b * 0.5F) * 1.15F;
            if (od >= 2) {
                // Supernova: die Scheibe zerfaellt in leuchtende Ringe mit Luecken
                float ringWave = 0.5F + 0.5F * Mth.sin(f0 * Mth.PI * 7.0F - t * 0.02F);
                base *= 0.45F + 0.75F * ringWave * ringWave;
            }
            float omega = spin * 0.06F / (float) Math.pow(0.3F + f0, 1.5);
            float rot = t * omega + layer * 0.7F;
            float turb = 1.0F + heat * 2.5F;
            for (int s = 0; s < seg; s++) {
                float a0 = Mth.TWO_PI * s / seg, a1 = Mth.TWO_PI * (s + 1) / seg;
                float am = (a0 + a1) * 0.5F;
                float streak = 0.5F + 0.32F * Mth.sin(3.0F * (am - rot) + b * 1.7F)
                        + 0.18F * Mth.sin(13.0F * (am - rot * 1.3F) + b * 2.9F) * turb;
                float doppler = 0.55F + 0.45F * Mth.sin(am - camYaw);
                float a = Mth.clamp(base * streak * doppler, 0.0F, 1.0F);
                quad2(vc, m, r0 * Mth.cos(a0), 0, r0 * Mth.sin(a0), r1 * Mth.cos(a0), 0, r1 * Mth.sin(a0),
                        r1 * Mth.cos(a1), 0, r1 * Mth.sin(a1), r0 * Mth.cos(a1), 0, r0 * Mth.sin(a1), col[0], col[1], col[2], a);
            }
        }
    }

    /**
     * Leuchtende Dunsthuelle am Rand der Scheibe.
     *
     * @param m Matrix der Scheibenebene
     * @param vc Puffer
     * @param outer Aussenradius der Scheibe
     * @param energy Helligkeit 0..1
     * @param heat Instabilitaet 0..1
     */
    private static void haze(Matrix4f m, VertexConsumer vc, float outer, float energy, float heat) {
        float[] col = lerp(pCool, CRITICAL, heat * heat * 0.6F);
        int seg = 48;
        for (int s = 0; s < seg; s++) {
            float a0 = Mth.TWO_PI * s / seg, a1 = Mth.TWO_PI * (s + 1) / seg;
            float r0 = outer, r1 = outer * 1.2F;
            quadFade(vc, m, r0 * Mth.cos(a0), 0, r0 * Mth.sin(a0), r0 * Mth.cos(a1), 0, r0 * Mth.sin(a1),
                    r1 * Mth.cos(a1), 0, r1 * Mth.sin(a1), r1 * Mth.cos(a0), 0, r1 * Mth.sin(a0), col[0], col[1], col[2], 0.15F * energy, 0.0F);
        }
    }

    /**
     * Hotspots: gleissende Materieklumpen, die am Innenrand umlaufen.
     *
     * @param m Matrix der Scheibenebene
     * @param vc Puffer
     * @param inner Innenradius der Scheibe
     * @param t Animationszeit
     * @param spin Drehgeschwindigkeit
     * @param energy Helligkeit 0..1
     * @param heat Instabilitaet 0..1
     */
    private static void hotspots(Matrix4f m, VertexConsumer vc, float inner, float t, float spin, float energy, float heat) {
        float[] c = diskColor(0.02F, heat);
        for (int k = 0; k < 3; k++) {
            float ang = t * 0.09F * spin + k * Mth.TWO_PI / 3;
            float r = inner * (1.1F + 0.15F * k);
            cube(vc, m, Mth.cos(ang) * r, 0, Mth.sin(ang) * r, 0.035F + 0.01F * k, c[0], c[1], c[2], energy);
            for (int s = 1; s <= 8; s++) {
                float a2 = ang - s * 0.08F;
                cube(vc, m, Mth.cos(a2) * r, 0, Mth.sin(a2) * r, 0.03F * (1 - s / 9.0F), c[0], c[1], c[2], energy * (1 - s / 9.0F));
            }
        }
    }

    /**
     * Materiefaeden werden von aussen durch das Feld gerissen und spiralen in die Scheibe (mehr bei Instabilitaet).
     *
     * @param m Matrix der Scheibenebene
     * @param vc Puffer
     * @param reach Radius der Kammer
     * @param inner Innenradius der Scheibe
     * @param t Animationszeit
     * @param energy Helligkeit 0..1
     * @param heat Instabilitaet 0..1
     * @param active nur bei laufendem Reaktor
     */
    private static void tendrils(Matrix4f m, VertexConsumer vc, float reach, float inner, float t, float energy,
                                 float heat, boolean active) {
        if (!active) return;
        int count = RenderQuality.scale(4 + Math.round(heat * 4), 2);
        for (int k = 0; k < count; k++) {
            float cyc = t / 60.0F + k * 0.37F;
            int cycle = (int) cyc;
            float life = Mth.frac(cyc);
            float startLon = hash(cycle * 31 + k) * Mth.PI, startLat = hash(cycle * 17 + k) * 0.6F;
            float a = energy * Mth.sin(life * Mth.PI) * 0.8F;
            float[] col = diskColor(Math.max(0.0F, 0.4F - life * 0.35F), heat);
            float[] prev = null;
            int n = 16;
            for (int i = 0; i <= n; i++) {
                float s = Math.min(1.0F, i / (float) n * (0.4F + life));
                float r = Mth.lerp(s, reach + 0.6F, inner);
                float lon = startLon + s * 2.4F;
                float lat = startLat * (1.0F - s);
                float[] q = {r * Mth.cos(lat) * Mth.cos(lon), r * Mth.sin(lat), r * Mth.cos(lat) * Mth.sin(lon)};
                if (prev != null) segment(vc, m, prev[0], prev[1], prev[2], q[0], q[1], q[2], 0.012F * (1 - s * 0.6F), col[0], col[1], col[2], a * s, a * s);
                prev = q;
            }
        }
    }

    /**
     * Photonenring, Gravitationslinse, Halo und Lichtstrahlen - immer zum Betrachter gedreht.
     *
     * @param pose Pose-Stapel in der Kernmitte
     * @param vc Puffer
     * @param rh Radius des Ereignishorizonts
     * @param inner Innenradius der Scheibe
     * @param t Animationszeit
     * @param energy Helligkeit 0..1
     * @param heat Instabilitaet 0..1
     * @param camYaw Gierwinkel der Kamera
     * @param camPitch Neigung der Kamera
     * @param beat Herzschlag 0..1 (pulsierendes Aufleuchten)
     */
    private static void lensing(PoseStack pose, VertexConsumer vc, float rh, float inner, float t, float energy, float heat,
                                float camYaw, float camPitch, float beat) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(camYaw));
        pose.mulPose(Axis.XP.rotation(-camPitch));
        Matrix4f m = pose.last().pose();
        final int seg = RenderQuality.scale(72, 32);
        float[] ringCol = lerp(pHot, CRITICAL, heat * heat * 0.6F);
        float[] haloCol = lerp(lerp(pWarm, pCool, 0.55F), CRITICAL, heat * heat * 0.5F);
        float boost = 1.0F + beat * 1.5F;
        for (int s = 0; s < seg; s++) {
            float a0 = Mth.TWO_PI * s / seg, a1 = Mth.TWO_PI * (s + 1) / seg;
            float am = (a0 + a1) * 0.5F;
            annulusXY(vc, m, rh, rh * 2.4F, a0, a1, haloCol, Math.min(1.0F, 0.3F * energy * boost), 0.0F);
            annulusXY(vc, m, rh, rh * 1.08F, a0, a1, ringCol, Math.min(1.0F, energy * 1.2F * boost), 0.6F * energy);
            annulusXY(vc, m, rh * 1.12F, rh * 1.17F, a0, a1, ringCol, 0.7F * energy, 0.2F * energy);
            float vertical = Mth.sin(am);
            float lensA = vertical > 0 ? vertical * vertical : 0.5F * vertical * vertical;
            float flow = 0.7F + 0.3F * Mth.sin(am * 5.0F - t * 0.1F);
            annulusXY(vc, m, rh * 1.18F, inner * 1.15F, a0, a1, diskColor(0.12F, heat), Math.min(1.0F, energy * lensA * flow), 0.0F);
        }
        // Lichtstrahlen (Linsenreflex)
        int rays = 4;
        for (int k = 0; k < rays; k++) {
            float ang = k * Mth.TWO_PI / rays + t * 0.004F;
            float len = rh * (2.0F + 0.8F * Mth.sin(t * 0.07F + k)) * boost;
            float ca = Mth.cos(ang), sa = Mth.sin(ang);
            float w = rh * 0.035F;
            quadFade(vc, m, ca * rh - sa * w, sa * rh + ca * w, 0, ca * rh + sa * w, sa * rh - ca * w, 0,
                    ca * len, sa * len, 0, ca * len, sa * len, 0, ringCol[0], ringCol[1], ringCol[2], 0.35F * energy, 0.0F);
        }
        pose.popPose();
    }

    /**
     * Leitung vom Sternentrichter (ueber der Decke) zum oberen Fokuskristall: Pulse stroemen hinab, eine Helix
     * windet sich.
     *
     * @param m Matrix mit Ursprung in der Kernmitte
     * @param vc Puffer
     * @param focus Abstand Kern -> Kristall
     * @param half halbe Kantenlaenge der Struktur
     * @param t Animationszeit
     * @param energy Helligkeit 0..1
     * @param heat Instabilitaet 0..1
     */
    private static void funnelLink(Matrix4f m, VertexConsumer vc, float focus, int half, float t, float energy, float heat) {
        float[] col = lerp(lerp(pJet, pCool, 0.4F), CRITICAL, heat * heat * 0.6F);
        float yTop = half + 0.75F, yBottom = focus + 0.3F;
        if (yTop - yBottom < 0.1F) yBottom = yTop - 0.4F; // 5x5x5: Kristall sitzt direkt unter dem Trichter
        segment(vc, m, 0, yTop, 0, 0, yBottom, 0, 0.022F, 1.0F, 1.0F, 1.0F, 0.8F * energy, 0.8F * energy);
        segment(vc, m, 0, yTop, 0, 0, yBottom, 0, 0.08F, col[0], col[1], col[2], 0.35F * energy, 0.35F * energy);
        float len = yTop - yBottom;
        for (int strand = 0; strand < 2; strand++) {
            float px = 0, py = yTop, pz = 0;
            for (int i = 0; i <= 14; i++) {
                float f = i / 14.0F;
                float ang = f * len * 6.0F + t * 0.15F + strand * Mth.PI;
                float x = Mth.cos(ang) * 0.09F, y = Mth.lerp(f, yTop, yBottom), z = Mth.sin(ang) * 0.09F;
                if (i > 0) segment(vc, m, px, py, pz, x, y, z, 0.008F, col[0], col[1], col[2], 0.7F * energy, 0.7F * energy);
                px = x;
                py = y;
                pz = z;
            }
        }
        for (int k = 0; k < 3; k++) {
            float f = Mth.frac(t * 0.04F + k / 3.0F);
            cube(vc, m, 0, Mth.lerp(f, yTop, yBottom), 0, 0.05F, 1.0F, 1.0F, 1.0F, energy * Mth.sin(f * Mth.PI));
        }
    }

    /**
     * Relativistische Jets entlang der Pole mit wandernden Knoten.
     *
     * @param m Matrix mit Ursprung in der Kernmitte
     * @param vc Puffer
     * @param rh Radius des Ereignishorizonts
     * @param reach Laenge bis zur Kammerwand
     * @param t Animationszeit
     * @param energy Helligkeit 0..1
     * @param heat Instabilitaet 0..1
     * @param growth Leistungsstufe 0..1 (breitere Jets)
     */
    private static void jets(Matrix4f m, VertexConsumer vc, float rh, float reach, float t, float energy, float heat, float growth) {
        float[] col = lerp(pJet, CRITICAL, heat * heat * 0.7F);
        float w = 0.035F + 0.03F * growth;
        for (int side = -1; side <= 1; side += 2) {
            float y0 = side * rh * 1.05F, y1 = side * (reach + 0.6F);
            segment(vc, m, 0, y0, 0, 0, y1, 0, w, 1.0F, 1.0F, 1.0F, energy, 0.5F * energy);
            segment(vc, m, 0, y0, 0, 0, y1, 0, w * 2.0F, col[0], col[1], col[2], 0.4F * energy, 0.1F * energy);
            for (int strand = 0; strand < 4; strand++) {
                float lx = 0, ly = y0, lz = 0;
                for (int i = 1; i <= 12; i++) {
                    float s = i / 12.0F;
                    float y = Mth.lerp(s, y0, y1);
                    float ang = t * 0.3F + s * 10.0F + strand * Mth.HALF_PI;
                    float r = w * (1.0F + 2.0F * s);
                    float x = Mth.cos(ang) * r, z = Mth.sin(ang) * r;
                    float a = 0.6F * energy * (1 - s * 0.5F);
                    segment(vc, m, lx, ly, lz, x, y, z, 0.012F, col[0], col[1], col[2], a, a);
                    lx = x;
                    ly = y;
                    lz = z;
                }
            }
            for (int k = 0; k < 3; k++) {
                float s = Mth.frac(t * 0.035F + k / 3.0F);
                cube(vc, m, 0, Mth.lerp(s, y0, y1), 0, w * 1.4F * (1 - 0.5F * s), 1.0F, 1.0F, 1.0F, energy * (1 - s));
            }
        }
    }
}
