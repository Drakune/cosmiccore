package dr4kun3.cosmiccore.compat.geckolib;

import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Oberkörper des Void-Titanen: eine bemalte Textur, normal schattiert. Zur Keyframe-Animation kommt prozedurale
 * Sekundärbewegung: die Flügelspeichen fachern leicht, Finger krümmen sich beim Aufladen, die Kugel pulsiert.
 */
public class VoidTitanModel extends GeoModel<VoidTitan> {
    private static final int FINGERS = 4, FINGER_SEGS = 2;
    /** Markierungs-Knochen, deren Lage die Cutscene für Leuchteffekte braucht. */
    public static final String[] TRACKED = {"eye_l", "eye_r", "core", "orb", "hand_l", "hand_r", "head"}; // muss zu tools/generate_titan.py passen

    @Override
    public Identifier getModelResource(GeoRenderState state) {
        return CosmicCore.id("void_titan");
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        return CosmicCore.id("textures/entity/void_titan.png");
    }

    @Override
    public Identifier getAnimationResource(VoidTitan animatable) {
        return CosmicCore.id("void_titan");
    }

    /**
     * Eigene Bewegung der Knochen: Flügelschlag, Atmen, Kopf und Arme (im Film ruhiger).
     *
     * @param snapshots Knochenzustände dieses Bildes
     * @param ticks Animationszeit in Ticks
     * @param cinematic {@code true} im Film
     */
    static void adjust(BoneSnapshots snapshots, double ticks, boolean cinematic) {
        float t = (float) (ticks / 20.0);
        for (String side : new String[]{"l", "r"}) {
            float sgn = side.equals("l") ? 1.0F : -1.0F;
            for (int i = 0; i < 5; i++) {
                final int k = i;
                snapshots.ifPresent("wing_" + side + i, b -> b.setRotZ(sgn * 0.08F * Mth.sin(t * 1.6F + k * 0.8F)));
            }
            float grip = cinematic ? window(t, 6.5F, 9.6F) : 0.0F;
            for (int j = 0; j < FINGERS; j++) {
                for (int g = 0; g < FINGER_SEGS; g++) {
                    final float idle = 0.05F * Mth.sin(t * 2.0F + j + g);
                    final float curl = -(0.5F + 0.3F * g) * grip;
                    snapshots.ifPresent("f" + side + j + "_" + g, b -> b.setRotX(curl + idle));
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
}
