package dr4kun3.cosmiccore.compat.geckolib;

import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Oberkörper des Void-Titanen: eine bemalte Textur, normal schattiert. Zur Keyframe-Animation kommt prozedurale
 * Sekundärbewegung: die Flügelspeichen fachern leicht, Finger krümmen sich beim Aufladen, die Kugel pulsiert.
 */
public class VoidTitanModel extends GeoModel<VoidTitan> {
    private static final int FINGERS = 4, FINGER_SEGS = 2;
    /** Markierungs-Knochen, deren Lage die Cutscene für Leuchteffekte braucht. */
    public static final String[] TRACKED = {"eye_l", "eye_r", "core", "orb", "hand_l", "hand_r", "head"}; // muss zu tools/generate_titan.py passen

    @Override
    public ResourceLocation getModelResource(VoidTitan animatable) {
        return CosmicCore.id("geo/void_titan.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(VoidTitan animatable) {
        return CosmicCore.id("textures/entity/void_titan.png");
    }

    @Override
    public ResourceLocation getAnimationResource(VoidTitan animatable) {
        return CosmicCore.id("animations/void_titan.animation.json");
    }

    /**
     * Prozedurale Zusatzbewegung zur Keyframe-Animation: Flügelspeichen fachern, Finger krümmen sich beim Aufladen;
     * außerdem werden die Leuchtpunkte (Augen, Hände, Kern) zum Mitverfolgen markiert.
     *
     * @param animatable Zustand des gezeichneten Titanen
     * @param instanceId Kennung der Instanz
     * @param state Animationszustand dieses Bildes
     */
    @Override
    public void setCustomAnimations(VoidTitan animatable, long instanceId, AnimationState<VoidTitan> state) {
        float t = (float) (animatable.ticks / 20.0);
        for (String name : TRACKED) getBone(name).ifPresent(b -> b.setTrackingMatrices(true));
        for (String side : new String[]{"l", "r"}) {
            float sgn = side.equals("l") ? 1.0F : -1.0F;
            for (int i = 0; i < 5; i++) {
                final int k = i;
                getBone("wing_" + side + i).ifPresent(b -> b.setRotZ(b.getInitialSnapshot().getRotZ()
                        + sgn * 0.08F * Mth.sin(t * 1.6F + k * 0.8F)));
            }
            float grip = animatable.cinematic ? window(t, 6.5F, 9.6F) : 0.0F;
            for (int j = 0; j < FINGERS; j++) {
                for (int g = 0; g < FINGER_SEGS; g++) {
                    final float idle = 0.05F * Mth.sin(t * 2.0F + j + g);
                    final float curl = -(0.5F + 0.3F * g) * grip;
                    getBone("f" + side + j + "_" + g).ifPresent(b -> b.setRotX(b.getInitialSnapshot().getRotX() + curl + idle));
                }
            }
        }
    }

    /**
     * Weicher Bogen zwischen zwei Zeitpunkten (0 außerhalb, 1 in der Mitte).
     *
     * @param t aktuelle Zeit in Sekunden
     * @param a Beginn
     * @param b Ende
     * @return 0..1
     */
    private static float window(float t, float a, float b) {
        if (t <= a || t >= b) return 0.0F;
        return Mth.sin((t - a) / (b - a) * Mth.PI);
    }

    /**
     * Hält den Import von GeoBone für die Lambdas oben (wird nie aufgerufen).
     *
     * @param b beliebiger Knochen
     */
    @SuppressWarnings("unused")
    private static void unused(GeoBone b) {}
}
