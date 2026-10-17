package dr4kun3.cosmiccore.compat.geckolib;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dr4kun3.cosmiccore.entity.VoidTitanEntity;
import dr4kun3.cosmiccore.entity.VoidTitanPart;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

/**
 * Zeichnet den mehrteiligen Void-Titanen mit GeckoLib: den animierten Oberkoerper an der Entity-Position und jedes
 * Schweifsegment einzeln an seinem Teil (wie beim Enderdrachen), ausgerichtet entlang des Schweifs.
 * Wird nur registriert, wenn GeckoLib installiert ist.
 */
public class VoidTitanRenderer extends EntityRenderer<VoidTitanEntity, dr4kun3.cosmiccore.client.EntityRefState<VoidTitanEntity>> {
    private final GeoObjectRenderer<VoidTitan> body;
    private final GeoObjectRenderer<VoidTitanTail> tail;
    private final VoidTitanTail tailAnimatable = new VoidTitanTail();
    private final Map<VoidTitanEntity, VoidTitan> states = new WeakHashMap<>();

    /**
     * Legt den Renderer an: Oberkoerper (skaliert) und Schweifsegment als GeckoLib-Modelle, ohne Schatten.
     *
     * @param ctx Kontext der Entity-Renderer
     */
    public VoidTitanRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        body = new GeoObjectRenderer<>(new VoidTitanModel()).withScale(VoidTitanEntity.SCALE);
        tail = new GeoObjectRenderer<>(new VoidTitanTail.Model());
    }

    /**
     * Sichtbarkeitsbox weit ueber die Hitbox hinaus (Fluegel und Schweif).
     *
     * @param entity der Titan
     * @return die Box
     */
    @Override
    protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(VoidTitanEntity entity) {
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
     * Zeichnet den Titanen aus dem Zeichenzustand.
     *
     * @param state Zeichenzustand
     * @param pose Pose-Stapel
     * @param buffers Puffer
     * @param light Lichtwert
     */
    @Override
    public void render(dr4kun3.cosmiccore.client.EntityRefState<VoidTitanEntity> state, PoseStack pose, MultiBufferSource buffers, int light) {
        if (state.entity != null) render(state.entity, 0.0F, state.partialTick, pose, buffers, light);
    }

    /**
     * Zeichnet den Oberkoerper an der Entity-Position, merkt sich die Lage der Leuchtpunkte (Augen, Haende, Kern) fuer
     * die Zwischensequenz und zeichnet jedes Schweifsegment an seinem Teil.
     *
     * @param entity der Titan
     * @param yaw Gierwinkel (nicht verwendet, die Entity liefert ihn selbst)
     * @param partialTick Teil-Tick fuer fluessige Bewegung
     * @param pose Pose-Stapel an der Entity-Position
     * @param buffers Puffer
     * @param light Lichtwert (der Titan leuchtet immer voll)
     */
    private void render(VoidTitanEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        int full = LightTexture.FULL_BRIGHT;
        float yawRad = entity.getYRot() * Mth.DEG_TO_RAD;
        // Oberkoerper
        VoidTitan state = states.computeIfAbsent(entity, e -> new VoidTitan());
        state.cinematic = entity.scriptTime >= 0;
        state.ticks = entity.animTime(partialTick);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(yawRad));
        // GeckoLib verschiebt Objekte nach dem Skalieren um (0.5, 0.51, 0.5) - hier ausgleichen
        float k = VoidTitanEntity.SCALE;
        pose.translate(-0.5F * k, -0.51F * k, -0.5F * k);
        VoidTitanModel.current = state;
        body.render(pose, state, buffers, null, null, full, partialTick);
        pose.popPose();
        // Lage der Markierungs-Knochen fuer die Leuchteffekte der Cutscene (relativ zur Entity)
        for (String name : VoidTitanModel.TRACKED) {
            body.getGeoModel().getBone(name).ifPresent(b -> {
                // Drehpunkt des Knochens durch seine (animierte) Matrix
                org.joml.Vector3f v = b.getLocalSpaceMatrix().transformPosition(
                        new org.joml.Vector3f(b.getPivotX() / 16.0F, b.getPivotY() / 16.0F, b.getPivotZ() / 16.0F));
                v.add(-0.5F * k, -0.51F * k, -0.5F * k).rotateY(yawRad);
                entity.bones.put(name, new Vec3(v.x, v.y, v.z));
            });
        }
        // Schweifsegmente an ihren Teilen
        Vec3 origin = entity.getPosition(partialTick);
        for (VoidTitanPart part : entity.tail) {
            Vec3 p = part.getPosition(partialTick).add(0, part.getBbHeight() / 2.0, 0).subtract(origin);
            float pitch = Mth.lerp(partialTick, part.pitchO, part.pitch);
            float py = Mth.lerp(partialTick, part.yawO, part.yaw);
            float s = VoidTitanEntity.SCALE * part.scale;
            pose.pushPose();
            pose.translate(p.x, p.y, p.z);
            pose.mulPose(Axis.YP.rotation(yawRad));
            pose.mulPose(Axis.YP.rotationDegrees(py));
            pose.mulPose(Axis.XP.rotationDegrees(-pitch));
            // Segmentmodell haengt nach unten (0 bis -11 px) - auf die Teilmitte zentrieren
            pose.translate(0, VoidTitanEntity.SEGMENT_PX / 16.0F * s * 0.5F, 0);
            pose.scale(s, s, s);
            pose.translate(-0.5F, -0.51F, -0.5F); // GeckoLib-Versatz ausgleichen
            tail.render(pose, tailAnimatable, buffers, null, null, full, partialTick);
            pose.popPose();
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
