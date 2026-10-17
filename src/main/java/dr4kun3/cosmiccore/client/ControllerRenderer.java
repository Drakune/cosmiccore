package dr4kun3.cosmiccore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dr4kun3.cosmiccore.block.ControllerBlock;
import dr4kun3.cosmiccore.block.ModBlocks;
import dr4kun3.cosmiccore.block.entity.ControllerBlockEntity;
import dr4kun3.cosmiccore.multiblock.CorePattern;
import dr4kun3.cosmiccore.multiblock.ReactorSize;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import dr4kun3.cosmiccore.client.render.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Bauvorschau: Solange die Struktur nicht steht und die Vorschau aktiv ist, zeigt der Controller jeden fehlenden
 * Block als halbtransparenten "Geist" an seiner Zielposition. Falsche Bloecke (oder Bloecke, wo Luft sein muss)
 * werden rot umrandet.
 */
public class ControllerRenderer extends dr4kun3.cosmiccore.client.render.LegacyBlockEntityRenderer<ControllerBlockEntity> {
    /**
     * Legt den Renderer der Bauvorschau an.
     *
     * @param context Kontext der Blockinhalt-Renderer
     */
    public ControllerRenderer(BlockEntityRendererProvider.Context context) {}

    /**
     * Sichtbarkeitsbox: die ganze geplante Struktur um den Controller.
     *
     * @param be der Controller
     * @return die Box
     */
    @Override
    public AABB getRenderBoundingBox(ControllerBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(10.0);
    }

    /**
     * Auch zeichnen, wenn der Controller selbst nicht im Bild ist.
     *
     * @return immer {@code true}
     */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    /**
     * Sichtweite der Bauvorschau in Bloecken.
     *
     * @return 96
     */
    @Override
    public int getViewDistance() {
        return 96;
    }

    /**
     * Zeichnet die Bauvorschau: fehlende Bloecke als Geister, falsche rot umrandet.
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
    public void render(ControllerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay, net.minecraft.world.phys.Vec3 cameraPos) {
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
        VertexConsumer ghost = new AlphaVertexConsumer(buffers.getBuffer(Sheets.translucentItemSheet()), alpha);
        List<BlockPos> wrong = new ArrayList<>();

        // 1) Geisterbloecke (Linien erst danach - ein Wechsel des RenderTypes schliesst den vorherigen Puffer ab)
        pattern.forEach(center, facing, (pos, cell) -> {
            if (cell == CorePattern.Cell.ANY || pos.equals(origin) || CorePattern.isDock(pos, origin, facing)) return;
            BlockState current = level.getBlockState(pos);
            if (CorePattern.matches(cell, current, false)) return;
            // Steuerkonsole: rechts der Energie-Port, links der Item-Port
            if (pos.equals(origin.relative(dr4kun3.cosmiccore.block.Terminal.right(facing))) && current.isAir()) {
                ghost(pose, ghost, origin, pos, dr4kun3.cosmiccore.block.ModBlocks.ENERGY_PORT.get().defaultBlockState());
                return;
            }
            if (pos.equals(origin.relative(dr4kun3.cosmiccore.block.Terminal.left(facing))) && current.isAir()) {
                ghost(pose, ghost, origin, pos, dr4kun3.cosmiccore.block.ModBlocks.ITEM_PORT.get().defaultBlockState());
                return;
            }

            // in der Kammer stoert jeder Block (auch Gras), sonst nur Bloecke, die man nicht einfach ueberbauen kann
            if (!current.isAir() && (cell == CorePattern.Cell.AIR || !current.canBeReplaced())) {
                wrong.add(pos.immutable());
                return;
            }
            BlockState target = CorePattern.blockFor(cell);
            if (target == null) return;
            BlockStateModel model = dr4kun3.cosmiccore.client.render.BlockQuads.model(target);
            pose.pushPose();
            pose.translate(pos.getX() - origin.getX() + 0.5, pos.getY() - origin.getY() + 0.5, pos.getZ() - origin.getZ() + 0.5);
            pose.scale(0.82F, 0.82F, 0.82F);
            pose.translate(-0.5, -0.5, -0.5);
            dr4kun3.cosmiccore.client.render.BlockQuads.render(pose.last(), ghost, model, net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
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
                    dr4kun3.cosmiccore.client.render.BlockQuads.render(pose.last(), ghost, dr4kun3.cosmiccore.client.render.BlockQuads.model(funnel), net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                }
            }
        }

        // optionale Kosmos-Laser rund um die Deckenmitte
        if (be.isWithLasers()) {
            BlockState laser = ModBlocks.COSMIC_LASER.get().defaultBlockState();
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos lp = center.above(pattern.half() + 1).relative(d);
                BlockState there = level.getBlockState(lp);
                if (there.is(ModBlocks.COSMIC_LASER.get())) continue;
                if (!there.isAir() && !there.canBeReplaced()) {
                    wrong.add(lp);
                    continue;
                }
                pose.pushPose();
                pose.translate(lp.getX() - origin.getX(), lp.getY() - origin.getY(), lp.getZ() - origin.getZ());
                dr4kun3.cosmiccore.client.render.BlockQuads.render(pose.last(), ghost, dr4kun3.cosmiccore.client.render.BlockQuads.model(laser), net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }

        // 2) falsche Bloecke rot markieren
        if (!wrong.isEmpty()) {
            VertexConsumer lines = buffers.getBuffer(RenderTypes.lines());
            for (BlockPos pos : wrong) {
                double ox = pos.getX() - origin.getX(), oy = pos.getY() - origin.getY(), oz = pos.getZ() - origin.getZ();
                lineBox(pose.last(), lines, new AABB(ox, oy, oz, ox + 1, oy + 1, oz + 1).inflate(0.01), 0xFFFF3340, 2.0F);
            }
        }
    }

    /**
     * Die zwoelf Kanten einer Box als Linien.
     *
     * @param pose aktuelle Pose
     * @param vc Linien-Puffer
     * @param b die Box
     * @param color Farbe 0xAARRGGBB
     * @param width Breite in Pixeln
     */
    private static void lineBox(PoseStack.Pose pose, VertexConsumer vc, AABB b, int color, float width) {
        float[] xs = {(float) b.minX, (float) b.maxX}, ys = {(float) b.minY, (float) b.maxY}, zs = {(float) b.minZ, (float) b.maxZ};
        for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) {
            line(pose, vc, xs[0], ys[i], zs[j], xs[1], ys[i], zs[j], color, width);
            line(pose, vc, xs[i], ys[0], zs[j], xs[i], ys[1], zs[j], color, width);
            line(pose, vc, xs[i], ys[j], zs[0], xs[i], ys[j], zs[1], color, width);
        }
    }

    /**
     * Eine Linie mit Normale und Breite.
     *
     * @param pose aktuelle Pose
     * @param vc Linien-Puffer
     * @param x0 Start x
     * @param y0 Start y
     * @param z0 Start z
     * @param x1 Ende x
     * @param y1 Ende y
     * @param z1 Ende z
     * @param color Farbe 0xAARRGGBB
     * @param width Breite in Pixeln
     */
    private static void line(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int color, float width) {
        org.joml.Vector3f n = new org.joml.Vector3f(x1 - x0, y1 - y0, z1 - z0).normalize();
        vc.addVertex(pose, x0, y0, z0).setColor(color).setNormal(pose, n.x, n.y, n.z).setLineWidth(width);
        vc.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, n.x, n.y, n.z).setLineWidth(width);
    }

    /** Leitet alles weiter, multipliziert aber den Alphakanal (fuer die Geisterbloecke). */
    private record AlphaVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        /**
         * Leitet den Eckpunkt unveraendert weiter.
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
         * @param green Gruen 0..255
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
         * Setzt die Farbe aus einem ARGB-Wert (die Deckkraft wird dabei abgeschwaecht).
         *
         * @param argb Farbe 0xAARRGGBB
         * @return dieser Puffer
         */
        @Override
        public VertexConsumer setColor(int argb) {
            return setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
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
         * Leitet die dritte Texturkoordinate weiter.
         *
         * @param u u-Koordinate
         * @param v v-Koordinate
         * @return dieser Puffer
         */
        @Override
        public VertexConsumer setUv3(float u, float v) {
            delegate.setUv3(u, v);
            return this;
        }

        /**
         * Leitet die Linienbreite weiter.
         *
         * @param width Breite in Pixeln
         * @return dieser Puffer
         */
        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
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
     * Zeichnet einen fehlenden Block als durchscheinenden Geist an seiner Sollposition.
     *
     * @param pose Pose-Stapel am Controller
     * @param vc Puffer (durchscheinend)
     * @param origin Position des Controllers
     * @param pos Sollposition
     * @param target erwarteter Blockzustand
     */
    private static void ghost(PoseStack pose, VertexConsumer vc, BlockPos origin, BlockPos pos, BlockState target) {
        BlockStateModel model = dr4kun3.cosmiccore.client.render.BlockQuads.model(target);
        pose.pushPose();
        pose.translate(pos.getX() - origin.getX() + 0.5, pos.getY() - origin.getY() + 0.5, pos.getZ() - origin.getZ() + 0.5);
        pose.scale(0.82F, 0.82F, 0.82F);
        pose.translate(-0.5, -0.5, -0.5);
        dr4kun3.cosmiccore.client.render.BlockQuads.render(pose.last(), vc, model, net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
