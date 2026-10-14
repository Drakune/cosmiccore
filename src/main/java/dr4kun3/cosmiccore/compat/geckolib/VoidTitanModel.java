package dr4kun3.cosmiccore.compat.geckolib;

import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Modell des Void-Titanen: der Koerper wird als End-Sternenfeld gezeichnet, Adern und Augen leuchten (Glowmask). */
public class VoidTitanModel extends GeoModel<VoidTitan> {
    private static final ResourceLocation MODEL = CosmicCore.id("geo/void_titan.geo.json");
    private static final ResourceLocation TEXTURE = CosmicCore.id("textures/entity/void_titan.png");
    private static final ResourceLocation ANIMATION = CosmicCore.id("animations/void_titan.animation.json");

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
}
