package dr4kun3.cosmiccore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dr4kun3.cosmiccore.block.ControllerBlock;
import dr4kun3.cosmiccore.block.ModBlocks;
import dr4kun3.cosmiccore.block.Terminal;
import dr4kun3.cosmiccore.block.entity.ControllerBlockEntity;
import dr4kun3.cosmiccore.multiblock.CorePattern;
import dr4kun3.cosmiccore.multiblock.ReactorSize;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Bauvorschau: Solange die Struktur nicht steht und die Vorschau aktiv ist, zeigt der Controller jeden fehlenden
 * Block als halbtransparenten "Geist" an seiner Zielposition. Falsche Blöcke (oder Blöcke, wo Luft sein muss)
 * werden rot umrandet.
 */
public class ControllerRenderer implements BlockEntityRenderer<ControllerBlockEntity> {
    /**
     * Legt den Renderer der Bauvorschau an.
     *
     * @param context Kontext der Blockinhalt-Renderer
     */
    public ControllerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public AABB getRenderBoundingBox(ControllerBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(10.0);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    /**
     * Zeichnet die Bauvorschau: fehlende Blöcke als Geister, falsche rot umrandet.
     *
     * @param be der Controller
     * @param partialTick Teil-Tick
     * @param pose Pose-Stapel am Controller
     * @param buffers Puffer
     * @param light Lichtwert
     * @param overlay Overlay-Wert
     * @param cameraPos Position der Kamera
     */
    @Override
    public void render(ControllerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay, Vec3 cameraPos) {
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
        VertexConsumer ghost = new AlphaVertexConsumer(buffers.getBuffer(Sheets.translucentItemSheet()), alpha);
        List<BlockPos> wrong = new ArrayList<>();

        // 1) Geisterblöcke (Linien erst danach - ein Wechsel des RenderTypes schließt den vorherigen Puffer ab)
        pattern.forEach(center, facing, (pos, cell) -> {
            if (cell == CorePattern.Cell.ANY || pos.equals(origin) || CorePattern.isDock(pos, origin, facing)) return;
            BlockState current = level.getBlockState(pos);
            if (CorePattern.matches(cell, current, false)) return;
            // Steuerkonsole: rechts der Energie-Port, links der Item-Port
            if (pos.equals(origin.relative(Terminal.right(facing))) && current.isAir()) {
                ghost(dispatcher, pose, ghost, origin, pos, ModBlocks.ENERGY_PORT.get().defaultBlockState());
                return;
            }
            if (pos.equals(origin.relative(Terminal.left(facing))) && current.isAir()) {
                ghost(dispatcher, pose, ghost, origin, pos, ModBlocks.ITEM_PORT.get().defaultBlockState());
                return;
            }

            // in der Kammer stört jeder Block (auch Gras), sonst nur Blöcke, die man nicht einfach überbauen kann
            if (!current.isAir() && (cell == CorePattern.Cell.AIR || !current.canBeReplaced())) {
                wrong.add(pos.immutable());
                return;
            }
            BlockState target = CorePattern.blockFor(cell);
            if (target == null) return;
            BlockStateModel model = dispatcher.getBlockModel(target);
            pose.pushPose();
            pose.translate(pos.getX() - origin.getX() + 0.5, pos.getY() - origin.getY() + 0.5, pos.getZ() - origin.getZ() + 0.5);
            pose.scale(0.82F, 0.82F, 0.82F);
            pose.translate(-0.5, -0.5, -0.5);
            ModelBlockRenderer.renderModel(pose.last(), ghost, model, 1.0F, 1.0F, 1.0F,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        });

        // optionaler Sternentrichter auf der Deckenmitte
        if (be.isWithFunnel()) {
            BlockPos top = center.above(pattern.half() + 1);
            BlockState there = level.getBlockState(top);
            if (!there.is(ModBlocks.STAR_FUNNEL.get())) {
                if (!there.isAir() && !there.canBeReplaced()) {
                    wrong.add(top);
                } else {
                    BlockState funnel = ModBlocks.STAR_FUNNEL.get().defaultBlockState();
                    pose.pushPose();
                    pose.translate(top.getX() - origin.getX(), top.getY() - origin.getY(), top.getZ() - origin.getZ());
                    ModelBlockRenderer.renderModel(pose.last(), ghost, dispatcher.getBlockModel(funnel),
                            1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                }
            }
        }

        // optionale Kosmos-Laser rund um die Deckenmitte
        if (be.isWithLasers()) {
            BlockState laser = ModBlocks.COSMIC_LASER.get().defaultBlockState();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos lp = center.above(pattern.half() + 1).relative(d);
                BlockState there = level.getBlockState(lp);
                if (there.is(ModBlocks.COSMIC_LASER.get())) continue;
                if (!there.isAir() && !there.canBeReplaced()) {
                    wrong.add(lp);
                    continue;
                }
                pose.pushPose();
                pose.translate(lp.getX() - origin.getX(), lp.getY() - origin.getY(), lp.getZ() - origin.getZ());
                ModelBlockRenderer.renderModel(pose.last(), ghost, dispatcher.getBlockModel(laser),
                        1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }

        // 2) falsche Blöcke rot markieren
        if (!wrong.isEmpty()) {
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            for (BlockPos pos : wrong) {
                double ox = pos.getX() - origin.getX(), oy = pos.getY() - origin.getY(), oz = pos.getZ() - origin.getZ();
                ShapeRenderer.renderLineBox(pose, lines, new AABB(ox, oy, oz, ox + 1, oy + 1, oz + 1).inflate(0.01),
                        1.0F, 0.2F, 0.25F, 1.0F);
            }
        }
    }

    /** Leitet alles weiter, multipliziert aber den Alphakanal (für die Geisterblöcke). */
    private record AlphaVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        /**
         * Leitet den Eckpunkt unverändert weiter.
         *
         * @param x Position x
         * @param y Position y
         * @param z Position z
         * @return dieser Puffer (zum Verketten)
         */
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        /**
         * Leitet die Farbe weiter, der Alphawert wird mit der Geister-Deckkraft multipliziert.
         *
         * @param red Rot 0..255
         * @param green Grün 0..255
         * @param blue Blau 0..255
         * @param a Alpha 0..255
         * @return dieser Puffer
         */
        @Override
        public VertexConsumer setColor(int red, int green, int blue, int a) {
            delegate.setColor(red, green, blue, Math.round(a * alpha));
            return this;
        }

        /**
         * Leitet die Texturkoordinate weiter.
         *
         * @param u Koordinate u
         * @param v Koordinate v
         * @return dieser Puffer
         */
        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        /**
         * Leitet die Overlay-Koordinate weiter.
         *
         * @param u Koordinate u
         * @param v Koordinate v
         * @return dieser Puffer
         */
        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        /**
         * Leitet die Licht-Koordinate weiter.
         *
         * @param u Blocklicht
         * @param v Himmelslicht
         * @return dieser Puffer
         */
        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        /**
         * Leitet die Normale weiter.
         *
         * @param x Normale x
         * @param y Normale y
         * @param z Normale z
         * @return dieser Puffer
         */
        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }

    /**
     * Zeichnet einen fehlenden Block verkleinert und durchscheinend an seiner Zielposition.
     *
     * @param dispatcher Block-Renderer
     * @param pose Pose-Stapel am Controller
     * @param vc durchscheinender Puffer
     * @param origin Position des Controllers
     * @param pos Zielposition
     * @param target gewünschter Blockzustand
     */
    private static void ghost(BlockRenderDispatcher dispatcher, PoseStack pose, VertexConsumer vc, BlockPos origin, BlockPos pos, BlockState target) {
        BlockStateModel model = dispatcher.getBlockModel(target);
        pose.pushPose();
        pose.translate(pos.getX() - origin.getX() + 0.5, pos.getY() - origin.getY() + 0.5, pos.getZ() - origin.getZ() + 0.5);
        pose.scale(0.82F, 0.82F, 0.82F);
        pose.translate(-0.5, -0.5, -0.5);
        ModelBlockRenderer.renderModel(pose.last(), vc, model, 1.0F, 1.0F, 1.0F,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
