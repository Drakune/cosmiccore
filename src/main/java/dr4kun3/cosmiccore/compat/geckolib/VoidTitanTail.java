package dr4kun3.cosmiccore.compat.geckolib;

import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Ein Schweifsegment des Titanen - unbewegtes Modell, die Entity gibt Lage und Größe vor. */
public class VoidTitanTail implements GeoAnimatable {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return 0;
    }

    public static class Model extends GeoModel<VoidTitanTail> {
        @Override
        public ResourceLocation getModelResource(VoidTitanTail animatable, GeoRenderer<VoidTitanTail> renderer) {
            return CosmicCore.id("geo/void_titan_tail.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(VoidTitanTail animatable, GeoRenderer<VoidTitanTail> renderer) {
            return CosmicCore.id("textures/entity/void_titan.png");
        }

        @Override
        public ResourceLocation getAnimationResource(VoidTitanTail animatable) {
            return CosmicCore.id("animations/void_titan.animation.json");
        }
    }
}
