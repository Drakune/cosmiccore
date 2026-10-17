package dr4kun3.cosmiccore.compat.geckolib;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dr4kun3.cosmiccore.entity.VoidTitanEntity;
import dr4kun3.cosmiccore.entity.VoidTitanPart;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import com.geckolib.renderer.GeoObjectRenderer;

/**
 * Zeichnet den mehrteiligen Void-Titanen mit GeckoLib: den animierten Oberkoerper an der Entity-Position und jedes
 * Schweifsegment einzeln an seinem Teil (wie beim Enderdrachen), ausgerichtet entlang des Schweifs.
 * Wird nur registriert, wenn GeckoLib installiert ist.
 */
public class VoidTitanRenderer extends EntityRenderer<VoidTitanEntity, dr4kun3.cosmiccore.client.EntityRefState<VoidTitanEntity>> {
    private final Body body;
    private final GeoObjectRenderer<VoidTitanTail, Void, com.geckolib.renderer.base.GeoRenderState> tail;
    private final VoidTitanTail tailAnimatable = new VoidTitanTail();
    private final Map<VoidTitanEntity, VoidTitan> states = new WeakHashMap<>();

    /**
     * Legt den Renderer an: Oberkoerper (skaliert) und Schweifsegment als GeckoLib-Modelle, ohne Schatten.
     *
     * @param ctx Kontext der Entity-Renderer
     */
    public VoidTitanRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        body = new Body();
        body.withScale(VoidTitanEntity.SCALE);
        tail = new GeoObjectRenderer<>(new VoidTitanTail.Model());
    }

    /**
     * Sichtbarkeitsbox weit ueber die Hitbox hinaus (Fluegel und Schweif).
     *
     * @param entity der Titan
     * @param partialTicks Teil-Tick
     * @return die Box
     */
    @Override
    protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(VoidTitanEntity entity, float partialTicks) {
        return entity.getBoundingBox().inflate(70.0);
    }

    /**
     * Neuer Zeichenzustand, der auf die Entity verweist.
     *
     * @return der Zustand
     */
    @Override
    public dr4kun3.cosmiccore.client.EntityRefState<VoidTitanEntity> createRenderState() {
        return new dr4kun3.cosmiccore.client.EntityRefState<>();
    }

    /**
     * Uebernimmt die Entity in den Zeichenzustand.
     *
     * @param entity der Titan
     * @param state Zeichenzustand
     * @param partialTick Teil-Tick
     */
    @Override
    public void extractRenderState(VoidTitanEntity entity, dr4kun3.cosmiccore.client.EntityRefState<VoidTitanEntity> state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.entity = entity;
    }

    /**
     * Zeichnet den Titanen ueber den Submit-Sammler.
     *
     * @param state Zeichenzustand
     * @param pose Pose-Stapel
     * @param collector Sammler der Zeichenauftraege
     * @param camera Zustand der Kamera
     */
    @Override
    public void submit(dr4kun3.cosmiccore.client.EntityRefState<VoidTitanEntity> state, PoseStack pose,
                       net.minecraft.client.renderer.SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        if (state.entity != null) render(state.entity, state.partialTick, pose, collector, camera);
    }

    /**
     * Reicht Oberkoerper und Schweifsegmente ein und merkt die Lage der Leuchtpunkte.
     *
     * @param entity der Titan
     * @param partialTick Teil-Tick
     * @param pose Pose-Stapel
     * @param collector Sammler der Zeichenauftraege
     * @param camera Zustand der Kamera
     */
    private void render(VoidTitanEntity entity, float partialTick, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
                        net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        int full = net.minecraft.util.LightCoordsUtil.FULL_BRIGHT;
        float yawRad = entity.getYRot() * Mth.DEG_TO_RAD;
        // Oberkoerper
        VoidTitan state = states.computeIfAbsent(entity, e -> new VoidTitan());
        state.cinematic = entity.scriptTime >= 0;
        state.ticks = entity.animTime(partialTick);
        pose.pushPose();
        pose.rotate(Axis.YP.rotation(yawRad));
        // GeckoLib verschiebt Objekte nach dem Skalieren um (0.5, 0.51, 0.5) - hier ausgleichen
        float k = VoidTitanEntity.SCALE;
        // Bis GeckoLib 5.2 kam dieser Versatz unskaliert dazu - die Zwischensequenz ist auf diese Lage abgestimmt
        float legacy = k - 1.0F;
        pose.translate(-0.5F * (k + legacy), -0.51F * (k + legacy), -0.5F * (k + legacy));
        // Lage der Markierungs-Knochen fuer die Leuchteffekte der Cutscene (relativ zur Entity): GeckoLib meldet sie
        // beim Zeichnen, also mit einem Bild Verzoegerung
        body.performRenderPass(state, null, pose, collector, camera, full, 0, (info, snapshots) -> {
            VoidTitanModel.adjust(snapshots, state.ticks, state.cinematic);
            for (String name : VoidTitanModel.TRACKED) {
                info.addBonePositionListener(name, (world, model, local) -> {
                    if (local == null) return;
                    org.joml.Vector3f v = new org.joml.Vector3f((float) local.x, (float) local.y, (float) local.z);
                    v.add(-0.5F * (k + legacy), -0.51F * (k + legacy), -0.5F * (k + legacy)).rotateY(yawRad);
                    entity.bones.put(name, new Vec3(v.x, v.y, v.z));
                });
            }
        });
        pose.popPose();
        // Schweifsegmente an ihren Teilen
        Vec3 origin = entity.getPosition(partialTick);
        for (VoidTitanPart part : entity.tail) {
            Vec3 p = part.getPosition(partialTick).add(0, part.getBbHeight() / 2.0, 0).subtract(origin);
            float pitch = Mth.lerp(partialTick, part.pitchO, part.pitch);
            float py = Mth.lerp(partialTick, part.yawO, part.yaw);
            float s = VoidTitanEntity.SCALE * part.scale;
            pose.pushPose();
            pose.translate(p.x, p.y, p.z);
            pose.rotate(Axis.YP.rotation(yawRad));
            pose.rotate(Axis.YP.rotationDegrees(py));
            pose.rotate(Axis.XP.rotationDegrees(-pitch));
            // Segmentmodell haengt nach unten (0 bis -11 px) - auf die Teilmitte zentrieren
            pose.translate(0, VoidTitanEntity.SEGMENT_PX / 16.0F * s * 0.5F, 0);
            pose.scale(s, s, s);
            pose.translate(-0.5F, -0.51F, -0.5F); // GeckoLib-Versatz ausgleichen
            tail.performRenderPass(tailAnimatable, null, pose, collector, camera, full, 0);
            pose.popPose();
        }
    }

    /** Oberkoerper: die Animationszeit kommt von der Entity (Zwischensequenz), nicht aus der Spielzeit. */
    private static final class Body extends GeoObjectRenderer<VoidTitan, Void, com.geckolib.renderer.base.GeoRenderState> {
        /** Legt den Renderer des Oberkoerpers an. */
        Body() {
            super(new VoidTitanModel());
        }

        /**
         * Gibt die Animationszeit an den Zeichenzustand weiter.
         *
         * @param animatable der Titan
         * @param relatedObject ungenutzt
         * @param renderState Zeichenzustand
         * @param partialTick Teil-Tick
         */
        @Override
        public void addRenderData(VoidTitan animatable, Void relatedObject, com.geckolib.renderer.base.GeoRenderState renderState, float partialTick) {
            renderState.addGeckolibData(com.geckolib.constant.DataTickets.TICK, animatable.ticks);
        }
    }

    /**
     * Standard-Overlay ohne Treffer-Faerbung.
     *
     * @return Overlay-Wert
     */
    @SuppressWarnings("unused")
    private static int overlay() {
        return OverlayTexture.NO_OVERLAY;
    }
}
