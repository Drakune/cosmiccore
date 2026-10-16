package dr4kun3.cosmiccore.compat.geckolib;

import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.resources.Identifier;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.model.GeoModel;
import com.geckolib.util.GeckoLibUtil;

/** Ein Schweifsegment des Titanen - unbewegtes Modell, die Entity gibt Lage und Groesse vor. */
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
        public Identifier getModelResource(com.geckolib.renderer.base.GeoRenderState state) {
            return CosmicCore.id("void_titan_tail");
        }

        @Override
        public Identifier getTextureResource(com.geckolib.renderer.base.GeoRenderState state) {
            return CosmicCore.id("textures/entity/void_titan.png");
        }

        @Override
        public Identifier getAnimationResource(VoidTitanTail animatable) {
            return CosmicCore.id("void_titan");
        }
    }
}
