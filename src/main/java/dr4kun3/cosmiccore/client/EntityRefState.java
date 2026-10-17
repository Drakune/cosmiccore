package dr4kun3.cosmiccore.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/**
 * Render-State, der die Entity selbst mitführt: Asteroiden und der Void-Titan werden prozedural aus vielen
 * Entity-Werten gezeichnet - statt jeden einzeln zu kopieren, hält der Zustand die Entity für diesen Frame fest.
 */
public class EntityRefState<T extends Entity> extends EntityRenderState {
    public T entity;
}
