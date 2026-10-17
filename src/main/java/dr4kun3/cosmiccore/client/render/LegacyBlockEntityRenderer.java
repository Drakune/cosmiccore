package dr4kun3.cosmiccore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dr4kun3.cosmiccore.client.render.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Grundlage der Block-Renderer ab Minecraft 1.21.9: Der Zustand merkt sich den Block selbst, die bisherige
 * Zeichenroutine läuft beim Einreichen und schreibt in {@link DeferredBuffers}.
 */
public abstract class LegacyBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, LegacyBlockEntityRenderer.State<T>> {
    /** Renderzustand: der Block und die Werte, die das Zeichnen braucht. */
    public static final class State<T> extends BlockEntityRenderState {
        @Nullable
        public T blockEntity;
        public float partialTick;
        public Vec3 cameraPos = Vec3.ZERO;
    }

    @Override
    public State<T> createRenderState() {
        return new State<>();
    }

    /**
     * Übernimmt Blockinhalt, Teil-Tick und Kameraposition in den Zeichenzustand.
     *
     * @param be der Blockinhalt
     * @param state Zeichenzustand
     * @param partialTick Teil-Tick
     * @param cameraPos Position der Kamera
     * @param crumbling Abbau-Overlay oder {@code null}
     */
    @Override
    public void extractRenderState(T be, State<T> state, float partialTick, Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, cameraPos, crumbling);
        state.blockEntity = be;
        state.partialTick = partialTick;
        state.cameraPos = cameraPos;
    }

    /**
     * Ruft die alte Zeichenroutine mit aufzeichnenden Puffern auf und reicht das Ergebnis ein.
     *
     * @param state Zeichenzustand
     * @param pose Pose-Stapel
     * @param collector Sammler der Zeichenaufträge
     * @param camera Zustand der Kamera
     */
    @Override
    public void submit(State<T> state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.blockEntity == null) return;
        DeferredBuffers buffers = new DeferredBuffers(collector);
        render(state.blockEntity, state.partialTick, pose, buffers, state.lightCoords, OverlayTexture.NO_OVERLAY, state.cameraPos);
        buffers.submit();
        state.blockEntity = null;
    }

    /**
     * Zeichnet den Blockinhalt wie vor dem Submit-System.
     *
     * @param be der Blockinhalt
     * @param partialTick Teil-Tick
     * @param pose Pose-Stapel am Block
     * @param buffers Puffer (zeichnen auf)
     * @param light Lichtwert
     * @param overlay Overlay-Wert
     * @param cameraPos Position der Kamera
     */
    public abstract void render(T be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay, Vec3 cameraPos);
}
