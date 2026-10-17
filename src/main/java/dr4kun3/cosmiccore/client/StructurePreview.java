package dr4kun3.cosmiccore.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dr4kun3.cosmiccore.ModItems;
import dr4kun3.cosmiccore.block.ConsoleSide;
import dr4kun3.cosmiccore.block.ControllerBlock;
import dr4kun3.cosmiccore.block.ModBlocks;
import dr4kun3.cosmiccore.block.ModProperties;
import dr4kun3.cosmiccore.block.Terminal;
import dr4kun3.cosmiccore.multiblock.CorePattern;
import dr4kun3.cosmiccore.multiblock.ReactorSize;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Drehende 3D-Vorschau einer Reaktorstruktur im GUI (Größenwahl und Handbuch) und die Liste benötigter Blöcke. */
public final class StructurePreview {
    /** Nur statische Hilfsfunktionen - keine Instanzen. */
    private StructurePreview() {}

    /**
     * Zeichnet die Reaktorstruktur als drehendes 3D-Modell (mit Konsole, optional Trichter und Laser) in einen
     * Ausschnitt des Fensters.
     *
     * @param g Grafik-Kontext des Fensters
     * @param size Baugröße
     * @param cx Bildschirm-x der Strukturmitte
     * @param cy Bildschirm-y der Strukturmitte
     * @param box Ausschnitt {x0, y0, x1, y1} in Bildschirmkoordinaten (Scissor)
     * @param fit Kantenlänge in Pixeln, in die die Struktur passen soll
     * @param yaw Drehung um die Hochachse in Grad
     * @param pitch zusätzliche Neigung in Grad
     * @param funnel Sternentrichter auf der Decke zeigen
     * @param lasers Kosmos-Laser auf der Decke zeigen
     */
    public static void render(GuiGraphics g, ReactorSize size, int cx, int cy, int[] box, float fit, float yaw, float pitch,
                              boolean funnel, boolean lasers) {
        Minecraft mc = Minecraft.getInstance();
        CorePattern p = size.pattern();
        int half = p.half();
        float scale = fit / (size.size() * 1.75F);
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        BlockPos controller = new BlockPos(0, -half, half);

        g.enableScissor(box[0], box[1], box[2], box[3]);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 400);
        ps.scale(scale, -scale, scale);
        ps.mulPose(Axis.XP.rotationDegrees(22.0F + pitch));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        Lighting.setupFor3DItems();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        p.forEach(BlockPos.ZERO, Direction.SOUTH, (pos, cell) -> {
            if (CorePattern.isDock(pos, controller, Direction.SOUTH)) return; // frei für Rohre
            BlockState state = CorePattern.blockFor(cell);
            if (state == null) return;
            // Steuerkonsole: Item-Port links, Controller, Energie-Port rechts
            if (pos.equals(controller)) {
                state = ModBlocks.CONTROLLER.get().defaultBlockState().setValue(ControllerBlock.FACING, Direction.SOUTH)
                        .setValue(ModProperties.TERMINAL, true);
            } else if (pos.equals(controller.relative(Terminal.right(Direction.SOUTH)))) {
                state = ModBlocks.ENERGY_PORT.get().defaultBlockState()
                        .setValue(ModProperties.CONSOLE, ConsoleSide.SOUTH);
            } else if (pos.equals(controller.relative(Terminal.left(Direction.SOUTH)))) {
                state = ModBlocks.ITEM_PORT.get().defaultBlockState()
                        .setValue(ModProperties.CONSOLE, ConsoleSide.SOUTH);
            }
            block(dispatcher, ps, buffers, state, pos.getX() - 0.5F, pos.getY() - 0.5F, pos.getZ() - 0.5F);
        });
        if (lasers) {
            BlockState laser = ModBlocks.COSMIC_LASER.get().defaultBlockState();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                block(dispatcher, ps, buffers, laser, d.getStepX() - 0.5F, half + 0.5F, d.getStepZ() - 0.5F);
            }
        }
        if (funnel) {
            block(dispatcher, ps, buffers, ModBlocks.STAR_FUNNEL.get().defaultBlockState(), -0.5F, half + 0.5F, -0.5F);
        }
        g.flush();
        ps.popPose();
        g.disableScissor();
    }

    /**
     * Zeichnet einen einzelnen Block an einer Stelle der Vorschau.
     *
     * @param d Block-Renderer
     * @param ps Pose-Stapel der Vorschau
     * @param buffers Puffer
     * @param state zu zeichnender Blockzustand
     * @param x Position x (Blöcke, relativ zur Mitte)
     * @param y Position y
     * @param z Position z
     */
    private static void block(BlockRenderDispatcher d, PoseStack ps, MultiBufferSource buffers, BlockState state, float x, float y, float z) {
        ps.pushPose();
        ps.translate(x, y, z);
        d.renderSingleBlock(state, ps, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        ps.popPose();
    }

    /**
     * Die für eine Baugröße benötigten Blöcke als Stapel (Anzahl = Stapelgröße, darf über 64 liegen).
     *
     * @param size Baugröße
     * @param funnel Sternentrichter mitzählen
     * @param lasers die vier Kosmos-Laser mitzählen
     * @return Liste der Blöcke mit Anzahl
     */
    public static List<ItemStack> requiredBlocks(ReactorSize size, boolean funnel, boolean lasers) {
        CorePattern p = size.pattern();
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(ModItems.CONTROLLER.get(), 1));
        out.add(new ItemStack(ModItems.VOID_CASING.get(), p.count(CorePattern.Cell.HULL) + p.count(CorePattern.Cell.SLOT) - 1));
        out.add(new ItemStack(ModItems.RUNE_FRAME.get(), p.count(CorePattern.Cell.FRAME)));
        out.add(new ItemStack(ModItems.FOCUS_GLASS.get(), p.count(CorePattern.Cell.GLASS)));
        out.add(new ItemStack(ModItems.RESONANCE_COIL.get(), p.count(CorePattern.Cell.COIL)));
        out.add(new ItemStack(ModItems.FOCUS_CRYSTAL.get(), p.count(CorePattern.Cell.CRYSTAL)));
        out.add(new ItemStack(ModItems.SINGULARITY_CORE.get(), p.count(CorePattern.Cell.CORE) + p.satelliteCount()));
        if (funnel) out.add(new ItemStack(ModItems.STAR_FUNNEL.get(), 1));
        if (lasers) out.add(new ItemStack(ModItems.COSMIC_LASER.get(), 4));
        return out;
    }
}
