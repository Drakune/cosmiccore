package dr4kun3.cosmiccore.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dr4kun3.cosmiccore.ModItems;
import dr4kun3.cosmiccore.block.ControllerBlock;
import dr4kun3.cosmiccore.block.ModBlocks;
import dr4kun3.cosmiccore.multiblock.CorePattern;
import dr4kun3.cosmiccore.multiblock.ReactorSize;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import dr4kun3.cosmiccore.client.render.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Drehende 3D-Vorschau einer Reaktorstruktur im GUI (Groessenwahl und Handbuch) und die Liste benoetigter Bloecke. */
public final class StructurePreview {
    /** Nur statische Hilfsfunktionen - keine Instanzen. */
    private StructurePreview() {}

    /**
     * Zeichnet die Reaktorstruktur als drehendes 3D-Modell (mit Konsole, optional Trichter und Laser) in einen
     * Ausschnitt des Fensters.
     *
     * @param g Grafik-Kontext des Fensters
     * @param size Baugroesse
     * @param cx Bildschirm-x der Strukturmitte
     * @param cy Bildschirm-y der Strukturmitte
     * @param box Ausschnitt {x0, y0, x1, y1} in Bildschirmkoordinaten (Scissor)
     * @param fit Kantenlaenge in Pixeln, in die die Struktur passen soll
     * @param yaw Drehung um die Hochachse in Grad
     * @param pitch zusaetzliche Neigung in Grad
     * @param funnel Sternentrichter auf der Decke zeigen
     * @param lasers Kosmos-Laser auf der Decke zeigen
     */
    public static void render(GuiGraphicsExtractor g, ReactorSize size, int cx, int cy, int[] box, float fit, float yaw, float pitch,
                              boolean funnel, boolean lasers) {
        g.submitPictureInPictureRenderState(new State(size, fit, yaw, pitch, funnel, lasers,
                cx - (box[0] + box[2]) / 2.0F, cy - (box[1] + box[3]) / 2.0F,
                new org.joml.Matrix3x2f(g.pose()), box[0], box[1], box[2], box[3], g.peekScissorStack()));
    }

    /** Alles, was der Renderer fuer ein Bild braucht. */
    public record State(ReactorSize size, float fit, float yaw, float pitch, boolean funnel, boolean lasers, float offX, float offY,
                        org.joml.Matrix3x2f pose, int x0, int y0, int x1, int y1,
                        @javax.annotation.Nullable net.minecraft.client.gui.navigation.ScreenRectangle scissorArea,
                        @javax.annotation.Nullable net.minecraft.client.gui.navigation.ScreenRectangle bounds)
            implements net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState {
        /**
         * Legt den Zustand an und berechnet den sichtbaren Bereich aus Rechteck und Ausschnitt.
         *
         * @param size Baugroesse
         * @param fit Groesse im Fenster
         * @param yaw Drehung
         * @param pitch Neigung
         * @param funnel Trichter anzeigen
         * @param lasers Laser anzeigen
         * @param offX Verschiebung x
         * @param offY Verschiebung y
         * @param pose GUI-Matrix
         * @param x0 linke Kante
         * @param y0 obere Kante
         * @param x1 rechte Kante
         * @param y1 untere Kante
         * @param scissor Ausschnitt oder {@code null}
         */
        State(ReactorSize size, float fit, float yaw, float pitch, boolean funnel, boolean lasers, float offX, float offY,
              org.joml.Matrix3x2f pose, int x0, int y0, int x1, int y1,
              @javax.annotation.Nullable net.minecraft.client.gui.navigation.ScreenRectangle scissor) {
            this(size, fit, yaw, pitch, funnel, lasers, offX, offY, pose, x0, y0, x1, y1, scissor,
                    net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor));
        }

        /**
         * Keine zusaetzliche Skalierung.
         *
         * @return 1
         */
        @Override
        public float scale() {
            return 1.0F;
        }
    }

    /** Rendert die Struktur in eine Textur (Bild im Bild). */
    public static final class Renderer extends net.minecraft.client.gui.render.pip.PictureInPictureRenderer<State> {
        /** Legt den Bild-im-Bild-Renderer an. */
        public Renderer() {
        }

        /**
         * Klasse des Zustands, den dieser Renderer zeichnet.
         *
         * @return State.class
         */
        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        /**
         * Senkrechte Verschiebung: Struktur mittig im Bild.
         *
         * @param height Hoehe des Bildes
         * @param guiScale GUI-Skalierung
         * @return halbe Hoehe
         */
        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2.0F;
        }

        /**
         * Name der Textur (fuer Debug-Werkzeuge).
         *
         * @return Bezeichnung
         */
        @Override
        protected String getTextureLabel() {
            return "cosmiccore structure preview";
        }

        /**
         * Zeichnet die Struktur gedreht und skaliert in die Textur.
         *
         * @param st Zustand (Groesse, Drehung, Ausschnitt)
         * @param ps Pose-Stapel
         * @param collector Sammler der Zeichenauftraege
         */
        @Override
        protected void renderToTexture(State st, PoseStack ps, net.minecraft.client.renderer.SubmitNodeCollector collector) {
            Minecraft mc = Minecraft.getInstance();
            mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
            CorePattern p = st.size().pattern();
            int half = p.half();
            float scale = st.fit() / (st.size().size() * 1.75F);
            BlockPos controller = new BlockPos(0, -half, half);
            ps.translate(st.offX(), st.offY(), 0);
            ps.scale(scale, -scale, -scale); // der Bild-im-Bild-Renderer spiegelt Z bereits
            ps.mulPose(Axis.XP.rotationDegrees(22.0F + st.pitch()));
            ps.mulPose(Axis.YP.rotationDegrees(st.yaw()));
            var buffers = new dr4kun3.cosmiccore.client.render.DeferredBuffers(collector);
            p.forEach(BlockPos.ZERO, Direction.SOUTH, (pos, cell) -> {
                if (CorePattern.isDock(pos, controller, Direction.SOUTH)) return; // frei fuer Rohre
                BlockState state = CorePattern.blockFor(cell);
                if (state == null) return;
                // Steuerkonsole: Item-Port links, Controller, Energie-Port rechts
                if (pos.equals(controller)) {
                    state = ModBlocks.CONTROLLER.get().defaultBlockState().setValue(ControllerBlock.FACING, Direction.SOUTH)
                            .setValue(dr4kun3.cosmiccore.block.ModProperties.TERMINAL, true);
                } else if (pos.equals(controller.relative(dr4kun3.cosmiccore.block.Terminal.right(Direction.SOUTH)))) {
                    state = ModBlocks.ENERGY_PORT.get().defaultBlockState()
                            .setValue(dr4kun3.cosmiccore.block.ModProperties.CONSOLE, dr4kun3.cosmiccore.block.ConsoleSide.SOUTH);
                } else if (pos.equals(controller.relative(dr4kun3.cosmiccore.block.Terminal.left(Direction.SOUTH)))) {
                    state = ModBlocks.ITEM_PORT.get().defaultBlockState()
                            .setValue(dr4kun3.cosmiccore.block.ModProperties.CONSOLE, dr4kun3.cosmiccore.block.ConsoleSide.SOUTH);
                }
                block(ps, buffers, state, pos.getX() - 0.5F, pos.getY() - 0.5F, pos.getZ() - 0.5F);
            });
            if (st.lasers()) {
                BlockState laser = ModBlocks.COSMIC_LASER.get().defaultBlockState();
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    block(ps, buffers, laser, d.getStepX() - 0.5F, half + 0.5F, d.getStepZ() - 0.5F);
                }
            }
            if (st.funnel()) {
                block(ps, buffers, ModBlocks.STAR_FUNNEL.get().defaultBlockState(), -0.5F, half + 0.5F, -0.5F);
            }
            buffers.submit();
        }
    }

    /**
     * Zeichnet einen Block der Vorschau.
     *
     * @param ps Pose-Stapel
     * @param buffers Puffer
     * @param state Blockzustand
     * @param x Versatz x
     * @param y Versatz y
     * @param z Versatz z
     */
    private static void block(PoseStack ps, MultiBufferSource buffers, BlockState state, float x, float y, float z) {
        ps.pushPose();
        ps.translate(x, y, z);
        dr4kun3.cosmiccore.client.render.BlockQuads.render(state, ps, buffers, net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    /**
     * Die fuer eine Baugroesse benoetigten Bloecke als Stapel (Anzahl = Stapelgroesse, darf ueber 64 liegen).
     *
     * @param size Baugroesse
     * @param funnel Sternentrichter mitzaehlen
     * @param lasers die vier Kosmos-Laser mitzaehlen
     * @return Liste der Bloecke mit Anzahl
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
