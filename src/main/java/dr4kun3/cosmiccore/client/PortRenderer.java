package dr4kun3.cosmiccore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dr4kun3.cosmiccore.CosmicCore;
import dr4kun3.cosmiccore.block.CorePhase;
import dr4kun3.cosmiccore.block.ModBlocks;
import dr4kun3.cosmiccore.block.ModProperties;
import dr4kun3.cosmiccore.block.ControllerBlock;
import dr4kun3.cosmiccore.block.entity.ControllerBlockEntity;
import dr4kun3.cosmiccore.block.entity.PortBlockEntity;
import net.minecraft.core.BlockPos;
import dr4kun3.cosmiccore.block.entity.SingularityCoreBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * Ports einer gebildeten Struktur: die Anschlussdose wird hier gezeichnet (das Blockmodell ist dann leer), damit sie
 * nach dem Formen nicht schlagartig erscheint, sondern sich mit Lichtblitz federnd und drehend materialisiert.
 */
public class PortRenderer<T extends PortBlockEntity> implements BlockEntityRenderer<T> {
    private static final float[] ENERGY = {1.0F, 0.75F, 0.3F};
    private static final float[] ITEM = {0.45F, 0.9F, 1.0F};

    /**
     * Legt den Renderer der Ports an.
     *
     * @param ctx Kontext der Blockinhalt-Renderer
     */
    public PortRenderer(BlockEntityRendererProvider.Context ctx) {}

    /**
     * Stecker-Modell je Port und Phase (wird in CosmicCoreClient als Zusatzmodell registriert).
     *
     * @param energy {@code true} fuer den Energie-Port, sonst Item-Port
     * @param phase Reaktorphase
     * @return Kennung des Zusatzmodells
     */
    public static ResourceLocation plugModel(boolean energy, CorePhase phase) {
        return CosmicCore.id("block/" + (energy ? "energy_port" : "item_port") + "_plug_"
                + phase.getSerializedName());
    }

    /**
     * Seitenteil der Steuerkonsole je Port und Phase (Port neben dem Controller, siehe Terminal).
     *
     * @param energy {@code true} fuer den Energie-Port, sonst Item-Port
     * @param phase Reaktorphase
     * @return Kennung des Zusatzmodells
     */
    public static ResourceLocation terminalModel(boolean energy, CorePhase phase) {
        return CosmicCore.id("block/" + (energy ? "energy_port" : "item_port") + "_terminal_"
                + phase.getSerializedName());
    }

    /**
     * Fortschritt des Materialisierens: folgt dem Kern des zugehoerigen Reaktors; ist der unbekannt, der eigene
     * Zeitpunkt des Formens.
     *
     * @param be der Port
     * @param level Welt
     * @param partialTick Teil-Tick
     * @return 0 (noch unsichtbar) bis 1 (ganz da)
     */
    private static float settle(PortBlockEntity be, Level level, float partialTick) {
        // eigener Zeitpunkt (Zustandswechsel am Port) - faengt Pakete ab, die einen Tick versetzt ankommen
        float own = 1.0F;
        if (be.clientAppear != Long.MIN_VALUE) {
            own = (level.getGameTime() - be.clientAppear + partialTick - SingularityCoreBlockEntity.FORM_TICKS)
                    / (float) SingularityCoreBlockEntity.SETTLE_TICKS;
        }
        float core = 1.0F;
        BlockPos cpos = be.getControllerPos();
        if (cpos != null && level.getBlockEntity(cpos) instanceof ControllerBlockEntity c && c.getSize() != null
                && level.getBlockState(cpos).hasProperty(ControllerBlock.FACING)) {
            BlockPos center = c.getSize().pattern().center(cpos, level.getBlockState(cpos).getValue(ControllerBlock.FACING));
            if (level.getBlockEntity(center) instanceof SingularityCoreBlockEntity sc) core = sc.getSettle(partialTick);
        }
        return Math.min(1.0F, Math.max(0.0F, Math.min(own, core)));
    }

    /**
     * Zeichnet einen einzelnen Port als Station: Sockel, Anschluesse, Bildschirm und Leuchteffekte. Ports direkt neben
     * dem Controller zeichnet die Steuerkonsole des Kerns.
     *
     * @param be der Port
     * @param partialTick Teil-Tick
     * @param pose Pose-Stapel an der Blockposition
     * @param buffers Puffer
     * @param light Lichtwert
     * @param overlay Overlay-Wert
     */
    @Override
    public void render(T be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        BlockState state = be.getBlockState();
        if (!state.hasProperty(ModProperties.PHASE)) return;
        CorePhase phase = state.getValue(ModProperties.PHASE);
        if (phase == CorePhase.DORMANT) return;
        // dasselbe Timing wie die Konsole: der Kern kennt den Start der Verwandlung
        float st = settle(be, level, partialTick);
        if (st <= 0.0F) return;

        boolean energy = state.is(ModBlocks.ENERGY_PORT.get());
        // Station: wie die Konsole waechst sie nach dem Formen federnd aus ihrem Sockel, Bildschirm nach aussen gewandt
        ControllerBlockEntity ctrl = null;
        SingularityCoreBlockEntity core = null;
        float yaw = 0.0F;
        BlockPos cpos = be.getControllerPos();
        if (cpos != null && level.getBlockEntity(cpos) instanceof ControllerBlockEntity c && c.getSize() != null
                && level.getBlockState(cpos).hasProperty(ControllerBlock.FACING)) {
            ctrl = c;
            BlockPos center = c.getSize().pattern().center(cpos, level.getBlockState(cpos).getValue(ControllerBlock.FACING));
            if (level.getBlockEntity(center) instanceof SingularityCoreBlockEntity sc) core = sc;
            BlockPos p = be.getBlockPos();
            // gerade nach vorn: senkrecht zu der Seite, an der der Port sitzt (nicht schraeg zur Kernmitte)
            int ox = p.getX() - center.getX(), oz = p.getZ() - center.getZ();
            yaw = Math.abs(oz) >= Math.abs(ox) ? (oz >= 0 ? 0.0F : Mth.PI) : (ox > 0 ? Mth.HALF_PI : -Mth.HALF_PI);
            // Port auf der Kante des Controllers: gehoert zur Steuerkonsole, die zeichnet der Kern
            var right = dr4kun3.cosmiccore.block.Terminal.right(level.getBlockState(cpos).getValue(ControllerBlock.FACING));
            int dx = p.getX() - cpos.getX(), dz = p.getZ() - cpos.getZ();
            // nur direkt neben dem Controller
            if (p.getY() == cpos.getY() && Math.abs(dx) + Math.abs(dz) == 1 && (right.getStepX() == 0 ? dx == 0 : dz == 0)) return;
        }
        float t = level.getGameTime() + partialTick;
        float bright = core == null ? 0.6F : Math.max(0.6F, phase == CorePhase.FORMED ? 0.6F : 1.0F);
        PortStation.Data data = new PortStation.Data(ctrl, core, t, bright, null).withPort(be);
        int conn = PortStation.connections(level, be, energy);
        float sc = SingularityCoreBlockEntity.easeOutBack(st);
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.scale(sc, sc, sc);
        pose.translate(0.0F, 0.5F, 0.0F);
        pose.mulPose(Axis.YP.rotation(yaw));
        PortStation.solid(pose, buffers, level, state, energy, conn, yaw, 0.0F, Math.min(1.0F, st), data, true, true);
        PortStation.glow(new Matrix4f(pose.last().pose()), buffers.getBuffer(CosmicRenderTypes.glow()), energy, conn, yaw, 0.0F,
                Math.min(1.0F, st), data, true, true);
        pose.popPose();
        if (st < 1.0F) {
            float[] col = energy ? ENERGY : ITEM;
            VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
            Matrix4f m = new Matrix4f(pose.last().pose()).translate(0.5F, 0.5F, 0.5F);
            RenderUtil.glowSphere(glow, m, 0.3F + 0.5F * st, 8, 12, col, 0.6F * (1.0F - st));
            RenderUtil.glowSphere(glow, m, 0.15F + 0.2F * st, 6, 10, new float[]{1, 1, 1}, 0.8F * (1.0F - st));
        }
    }
}
