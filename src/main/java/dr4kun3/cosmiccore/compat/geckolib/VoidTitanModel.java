package dr4kun3.cosmiccore.compat.geckolib;

import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.model.GeoModel;

/**
 * Oberkoerper des Void-Titanen: eine bemalte Textur, normal schattiert. Zur Keyframe-Animation kommt prozedurale
 * Sekundaerbewegung: die Fluegelspeichen fachern leicht, Finger kruemmen sich beim Aufladen, die Kugel pulsiert.
 */
public class VoidTitanModel extends GeoModel<VoidTitan> {
    private static final int FINGERS = 4, FINGER_SEGS = 2;
    /** Markierungs-Knochen, deren Lage die Cutscene fuer Leuchteffekte braucht. */
    public static final String[] TRACKED = {"eye_l", "eye_r", "core", "orb", "hand_l", "hand_r", "head"}; // muss zu tools/generate_titan.py passen

    @Override
    public Identifier getModelResource(com.geckolib.renderer.base.GeoRenderState state) {
        return CosmicCore.id("void_titan");
    }

    @Override
    public Identifier getTextureResource(com.geckolib.renderer.base.GeoRenderState state) {
        return CosmicCore.id("textures/entity/void_titan.png");
    }

    @Override
    public Identifier getAnimationResource(VoidTitan animatable) {
        return CosmicCore.id("void_titan");
    }

    /**
     * Prozedurale Zusatzbewegung (ab GeckoLib 5.4 ueber Bone-Snapshots): Fluegelspeichen fachern, Finger kruemmen sich
     * beim Aufladen. Die Werte ersetzen die Keyframe-Drehung dieser Knochen (wie zuvor: Ruhelage + Zusatz).
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

    private static float window(float t, float a, float b) {
        if (t <= a || t >= b) return 0.0F;
        return Mth.sin((t - a) / (b - a) * Mth.PI);
    }
}
