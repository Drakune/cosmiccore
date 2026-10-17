package dr4kun3.cosmiccore.compat.geckolib;

import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
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


    public static class Model extends GeoModel<VoidTitanTail> {
        @Override
        public Identifier getModelResource(GeoRenderState state) {
            return CosmicCore.id("void_titan_tail");
        }

        @Override
        public Identifier getTextureResource(GeoRenderState state) {
            return CosmicCore.id("textures/entity/void_titan.png");
        }

        @Override
        public Identifier getAnimationResource(VoidTitanTail animatable) {
            return CosmicCore.id("void_titan");
        }
    }
}
