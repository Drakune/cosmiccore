package dev.cosmiccore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.cosmiccore.block.ControllerBlock;
import dev.cosmiccore.block.CorePhase;
import dev.cosmiccore.block.ModBlocks;
import dev.cosmiccore.block.ModProperties;
import dev.cosmiccore.block.entity.ControllerBlockEntity;
import dev.cosmiccore.multiblock.CorePattern;
import dev.cosmiccore.multiblock.ReactorSize;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Bauvorschau: Solange die Struktur nicht steht und die Vorschau aktiv ist, zeigt der Controller jeden fehlenden
 * Block als halbtransparenten "Geist" an seiner Zielposition. Falsche Bloecke (oder Bloecke, wo Luft sein muss)
 * werden rot umrandet.
 */
public class ControllerRenderer implements BlockEntityRenderer<ControllerBlockEntity> {
    public ControllerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public AABB getRenderBoundingBox(ControllerBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(10.0);
    }

    @Override
    public boolean shouldRenderOffScreen(ControllerBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(ControllerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        ReactorSize size = be.getSize();
        if (level == null || size == null || !be.isPreview() || be.isFormed()) return;

        BlockState self = be.getBlockState();
        if (!self.hasProperty(ControllerBlock.FACING)) return;
        Direction facing = self.getValue(ControllerBlock.FACING);
        CorePattern pattern = size.pattern();
        BlockPos origin = be.getBlockPos();
        BlockPos center = pattern.center(origin, facing);

        float t = (level.getGameTime() % 24000L) + partialTick;
        float alpha = 0.35F + 0.15F * Mth.sin(t * 0.15F);
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        VertexConsumer ghost = new AlphaVertexConsumer(buffers.getBuffer(Sheets.translucentCullBlockSheet()), alpha);
        List<BlockPos> wrong = new ArrayList<>();

        // 1) Geisterbloecke (Linien erst danach - ein Wechsel des RenderTypes schliesst den vorherigen Puffer ab)
        pattern.forEach(center, (pos, cell) -> {
            if (cell == CorePattern.Cell.ANY || pos.equals(origin)) return;
            BlockState current = level.getBlockState(pos);
            if (CorePattern.matches(cell, current, false)) return;

            // in der Kammer stoert jeder Block (auch Gras), sonst nur Bloecke, die man nicht einfach ueberbauen kann
            if (!current.isAir() && (cell == CorePattern.Cell.AIR || !current.canBeReplaced())) {
                wrong.add(pos.immutable());
                return;
            }
            BlockState target = CorePattern.blockFor(cell);
            if (target == null) return;
            // leuchtende Variante sieht schoener aus - nur bei Bloecken, deren gebildetes Modell nicht ausgeblendet ist
            if (target.is(ModBlocks.VOID_CASING.get()) || target.is(ModBlocks.FOCUS_GLASS.get())) {
                target = target.setValue(ModProperties.PHASE, CorePhase.FORMED);
            }
            BakedModel model = dispatcher.getBlockModel(target);
            pose.pushPose();
            pose.translate(pos.getX() - origin.getX() + 0.5, pos.getY() - origin.getY() + 0.5, pos.getZ() - origin.getZ() + 0.5);
            pose.scale(0.82F, 0.82F, 0.82F);
            pose.translate(-0.5, -0.5, -0.5);
            dispatcher.getModelRenderer().renderModel(pose.last(), ghost, target, model, 1.0F, 1.0F, 1.0F,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.popPose();
        });

        // 2) falsche Bloecke rot markieren
        if (!wrong.isEmpty()) {
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            for (BlockPos pos : wrong) {
                double ox = pos.getX() - origin.getX(), oy = pos.getY() - origin.getY(), oz = pos.getZ() - origin.getZ();
                LevelRenderer.renderLineBox(pose, lines, new AABB(ox, oy, oz, ox + 1, oy + 1, oz + 1).inflate(0.01),
                        1.0F, 0.2F, 0.25F, 1.0F);
            }
        }
    }

    /** Leitet alles weiter, multipliziert aber den Alphakanal (fuer die Geisterbloecke). */
    private record AlphaVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int a) {
            delegate.setColor(red, green, blue, Math.round(a * alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
