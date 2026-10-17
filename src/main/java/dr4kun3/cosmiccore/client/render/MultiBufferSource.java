package dr4kun3.cosmiccore.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Liefert je Render-Typ einen Puffer. Minecraft hat diese Schnittstelle ab 26.2 entfernt; die Zeichenroutinen nutzen sie weiter. */
@FunctionalInterface
public interface MultiBufferSource {
    /**
     * Puffer für einen Render-Typ.
     *
     * @param type Render-Typ
     * @return der Puffer
     */
    VertexConsumer getBuffer(RenderType type);

    /**
     * Schließt den Abschnitt dieses Typs ab: Folgendes wird danach gezeichnet (wie das frühere endBatch).
     *
     * @param type Render-Typ
     */
    default void endBatch(RenderType type) {}
}
