package dr4kun3.cosmiccore.compat.geckolib;

import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Modell des Void-Titanen: der Koerper wird als End-Sternenfeld gezeichnet, Adern, Augen und Hoerner leuchten (Glowmask).
 * Zur Keyframe-Animation kommt prozedurale Sekundaerbewegung: Ranken wogen, Mantelbahnen flattern, Finger kruemmen sich
 * beim Aufladen, das Herz pulsiert und der Heiligenschein kreist.
 */
public class VoidTitanModel extends GeoModel<VoidTitan> {
    private static final ResourceLocation MODEL = CosmicCore.id("geo/void_titan.geo.json");
    private static final ResourceLocation TEXTURE = CosmicCore.id("textures/entity/void_titan.png");
    private static final ResourceLocation ANIMATION = CosmicCore.id("animations/void_titan.animation.json");
    // muessen zu tools/generate_titan.py passen
    private static final int TENDRILS = 6, TENDRIL_SEGS = 5, CAPE_STRIPS = 5, CAPE_SEGS = 4, FINGERS = 4, FINGER_SEGS = 2;

    @Override
    public ResourceLocation getModelResource(VoidTitan animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(VoidTitan animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(VoidTitan animatable) {
        return ANIMATION;
    }

    @Override
    public RenderType getRenderType(VoidTitan animatable, ResourceLocation texture) {
        return RenderType.endGateway();
    }

    @Override
    public void setCustomAnimations(VoidTitan animatable, long instanceId, AnimationState<VoidTitan> state) {
        float t = (float) (animatable.ticks / 20.0); // Sekunden seit dem Erscheinen
        // Brullen (4.5-6 s) und Schuss (9-10 s) lassen Mantel und Ranken aufpeitschen
        float burst = window(t, 4.5F, 6.2F) + window(t, 9.0F, 10.3F);

        for (int k = 0; k < TENDRILS; k++) {
            for (int i = 0; i < TENDRIL_SEGS; i++) {
                float amp = (0.08F + 0.05F * i) * (1.0F + 1.5F * burst);
                float ph = t * 1.4F + k * 1.05F - i * 0.65F; // Welle laeuft die Ranke hinab
                wave(getBone("t" + k + "_" + i).orElse(null), Mth.sin(ph) * amp, Mth.cos(ph * 0.8F) * amp);
            }
        }
        for (int s = 0; s < CAPE_STRIPS; s++) {
            for (int i = 0; i < CAPE_SEGS; i++) {
                float ph = t * 1.9F + s * 0.9F - i * 0.7F;
                float flutter = (0.06F + 0.05F * i) * Mth.sin(ph) + 0.12F * burst * (i + 1) * 0.5F;
                wave(getBone("cape" + s + "_" + i).orElse(null), flutter, 0.03F * Mth.cos(ph * 1.3F));
            }
        }
        // Finger kruemmen sich um die Energie (Aufladen 5.6-9 s), sonst leichtes Zucken
        float grip = window(t, 5.6F, 9.0F);
        for (String side : new String[]{"l", "r"}) {
            for (int j = 0; j < FINGERS; j++) {
                for (int g = 0; g < FINGER_SEGS; g++) {
                    float idle = 0.05F * Mth.sin(t * 2.0F + j + g);
                    wave(getBone("f" + side + j + "_" + g).orElse(null), -(0.55F + 0.25F * g) * grip + idle, 0.0F);
                }
            }
        }
        getBone("heart").ifPresent(b -> {
            float beat = 1.0F + 0.12F * Mth.sin(t * 7.0F) + 0.25F * window(t, 5.6F, 9.0F);
            b.setScaleX(beat);
            b.setScaleY(beat);
            b.setScaleZ(beat);
        });
        getBone("halo").ifPresent(b -> b.setRotZ(b.getInitialSnapshot().getRotZ() + t * 0.9F));
    }

    /** Ausgangslage des Knochens plus Auslenkung (Radiant). */
    private static void wave(GeoBone b, float dx, float dz) {
        if (b == null) return;
        b.setRotX(b.getInitialSnapshot().getRotX() + dx);
        b.setRotZ(b.getInitialSnapshot().getRotZ() + dz);
    }

    /** Weiches Fenster 0..1..0 zwischen a und b. */
    private static float window(float t, float a, float b) {
        if (t <= a || t >= b) return 0.0F;
        return Mth.sin((t - a) / (b - a) * Mth.PI);
    }
}
