package dr4kun3.cosmiccore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Zeichnet Blockmodelle direkt als Quads - ab Minecraft 26.1 gibt es dafuer keine fertige Einzelblock-Routine mehr.
 * Ersetzt die bisherigen Aufrufe von renderSingleBlock und ModelBlockRenderer.renderModel.
 */
public final class BlockQuads {
    private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};

    private BlockQuads() {}

    /** Das Modell eines Blockzustands. */
    public static BlockStateModel model(BlockState state) {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
    }

    /** Alle Quads eines Modells in einen Puffer (Farbe weiss). */
    public static void render(PoseStack.Pose pose, VertexConsumer vc, BlockStateModel model, int light, int overlay) {
        QuadInstance inst = instance(light, overlay);
        for (BakedQuad q : quads(model)) vc.putBakedQuad(pose, q, inst);
    }

    /** Einen Blockzustand zeichnen, je Quad im passenden Render-Typ (fest, ausgeschnitten, durchscheinend). */
    public static void render(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        render(state, pose, buffers, light, overlay, true);
    }

    /**
     * Wie oben; ohne feste Flaechen, wenn {@code solid} false ist. Bis 1.21.11 kamen feste Blockflaechen aus den
     * Block-Renderern der Kammer nie auf den Bildschirm - so bleibt das Aussehen gleich.
     */
    public static void render(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay, boolean solid) {
        List<BakedQuad> all = quads(model(state));
        QuadInstance inst = instance(light, overlay);
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            if (!solid && layer == ChunkSectionLayer.SOLID) continue;
            VertexConsumer vc = null;
            for (BakedQuad q : all) {
                if (q.materialInfo().layer() != layer) continue;
                if (vc == null) vc = buffers.getBuffer(switch (layer) {
                    case SOLID -> RenderTypes.solidMovingBlock();
                    case CUTOUT -> RenderTypes.cutoutMovingBlock();
                    case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
                });
                vc.putBakedQuad(pose.last(), q, inst);
            }
        }
    }

    private static QuadInstance instance(int light, int overlay) {
        QuadInstance inst = new QuadInstance();
        inst.setColor(-1);
        inst.setLightCoords(light);
        inst.setOverlayCoords(overlay);
        return inst;
    }

    private static List<BakedQuad> quads(BlockStateModel model) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42L), parts);
        List<BakedQuad> out = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction d : SIDES) out.addAll(part.getQuads(d));
        }
        return out;
    }
}
