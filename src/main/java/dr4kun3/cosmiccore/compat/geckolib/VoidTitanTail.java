package dr4kun3.cosmiccore.compat.geckolib;

import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
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


    public static class Model extends GeoModel<VoidTitanTail> {
        /**
         * Geometrie eines Schweifsegments.
         *
         * @param state Zeichenzustand
         * @return ID der Geometrie
         */
        @Override
        public Identifier getModelResource(software.bernie.geckolib.renderer.base.GeoRenderState state) {
            return CosmicCore.id("void_titan_tail");
        }

        /**
         * Textur (die des Titanen).
         *
         * @param state Zeichenzustand
         * @return Pfad der Textur
         */
        @Override
        public Identifier getTextureResource(software.bernie.geckolib.renderer.base.GeoRenderState state) {
            return CosmicCore.id("textures/entity/void_titan.png");
        }

        /**
         * Animationsdatei (wird vom Segment nicht genutzt, GeckoLib verlangt sie).
         *
         * @param animatable das Segment (nicht verwendet)
         * @return Pfad der Animationsdatei
         */
        @Override
        public Identifier getAnimationResource(VoidTitanTail animatable) {
            return CosmicCore.id("void_titan");
        }
    }
}
