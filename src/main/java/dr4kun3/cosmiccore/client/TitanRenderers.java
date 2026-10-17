package dr4kun3.cosmiccore.client;

import dr4kun3.cosmiccore.entity.VoidTitanEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Laedt den GeckoLib-Renderer des Titanen erst bei Bedarf (die Klasse existiert nur mit GeckoLib sinnvoll). */
final class TitanRenderers {
    /** Nur statische Hilfsfunktion - keine Instanzen. */
    private TitanRenderers() {}

    /**
     * Erzeugt den Renderer des Void-Titanen. Er liegt im GeckoLib-Teil und wird nur geladen, wenn GeckoLib
     * installiert ist; sonst gibt es einen unsichtbaren Ersatz.
     *
     * @param ctx Kontext der Entity-Renderer
     * @return der GeckoLib-Renderer oder ein Renderer, der nichts zeichnet
     */
    @SuppressWarnings("unchecked")
    static EntityRenderer<VoidTitanEntity, ?> create(EntityRendererProvider.Context ctx) {
        try {
            return (EntityRenderer<VoidTitanEntity, ?>) Class.forName("dr4kun3.cosmiccore.compat.geckolib.VoidTitanRenderer")
                    .getConstructor(EntityRendererProvider.Context.class).newInstance(ctx);
        } catch (ReflectiveOperationException | LinkageError e) {
            dr4kun3.cosmiccore.CosmicCore.LOGGER.warn("Void-Titan-Renderer nicht verfuegbar: {}", e.toString());
            return new net.minecraft.client.renderer.entity.NoopRenderer<>(ctx);
        }
    }
}
