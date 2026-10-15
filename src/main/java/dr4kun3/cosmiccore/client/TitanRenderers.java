package dr4kun3.cosmiccore.client;

import dr4kun3.cosmiccore.entity.VoidTitanEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Laedt den GeckoLib-Renderer des Titanen erst bei Bedarf (die Klasse existiert nur mit GeckoLib sinnvoll). */
final class TitanRenderers {
    private TitanRenderers() {}

    @SuppressWarnings("unchecked")
    static EntityRenderer<VoidTitanEntity> create(EntityRendererProvider.Context ctx) {
        try {
            return (EntityRenderer<VoidTitanEntity>) Class.forName("dr4kun3.cosmiccore.compat.geckolib.VoidTitanRenderer")
                    .getConstructor(EntityRendererProvider.Context.class).newInstance(ctx);
        } catch (ReflectiveOperationException | LinkageError e) {
            dr4kun3.cosmiccore.CosmicCore.LOGGER.warn("Void-Titan-Renderer nicht verfuegbar: {}", e.toString());
            return new net.minecraft.client.renderer.entity.NoopRenderer<>(ctx);
        }
    }
}
