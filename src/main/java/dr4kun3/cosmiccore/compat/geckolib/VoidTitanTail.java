package dr4kun3.cosmiccore.compat.geckolib;

import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Ein Schweifsegment des Titanen - unbewegtes Modell, die Entity gibt Lage und Groesse vor. */
public class VoidTitanTail implements GeoAnimatable {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /**
     * Das Schweifsegment hat keine eigene Animation.
     *
     * @param controllers Sammelstelle der Controller (bleibt leer)
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    /**
     * Zwischenspeicher der Animationszustaende.
     *
     * @return der Zwischenspeicher
     */
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /**
     * Unbewegtes Modell - die Zeit spielt keine Rolle.
     *
     * @param object das gezeichnete Objekt (nicht verwendet)
     * @return immer 0
     */
    @Override
    public double getTick(Object object) {
        return 0;
    }

    public static class Model extends GeoModel<VoidTitanTail> {
        /**
         * Modell eines Schweifsegments.
         *
         * @param animatable das Segment (nicht verwendet)
         * @return Pfad der Modelldatei
         */
        @Override
        public ResourceLocation getModelResource(VoidTitanTail animatable) {
            return CosmicCore.id("geo/void_titan_tail.geo.json");
        }

        /**
         * Gleiche Textur wie der Oberkoerper.
         *
         * @param animatable das Segment (nicht verwendet)
         * @return Pfad der Textur
         */
        @Override
        public ResourceLocation getTextureResource(VoidTitanTail animatable) {
            return CosmicCore.id("textures/entity/void_titan.png");
        }

        /**
         * Animationsdatei (wird vom Segment nicht genutzt, GeckoLib verlangt sie).
         *
         * @param animatable das Segment (nicht verwendet)
         * @return Pfad der Animationsdatei
         */
        @Override
        public ResourceLocation getAnimationResource(VoidTitanTail animatable) {
            return CosmicCore.id("animations/void_titan.animation.json");
        }
    }
}
