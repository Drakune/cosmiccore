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
import net.minecraft.client.resources.model.ModelResourceLocation;
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

    public PortRenderer(BlockEntityRendererProvider.Context ctx) {}

    /** Plug-Modelle je Port und Phase (werden in CosmicCoreClient als Zusatzmodelle registriert). */
    public static ModelResourceLocation plugModel(boolean energy, CorePhase phase) {
        return ModelResourceLocation.standalone(CosmicCore.id("block/" + (energy ? "energy_port" : "item_port") + "_plug_"
                + phase.getSerializedName()));
    }

    /** Seitenteil der Steuerkonsole je Port und Phase (Port neben dem Controller, siehe Terminal). */
    public static ModelResourceLocation terminalModel(boolean energy, CorePhase phase) {
        return ModelResourceLocation.standalone(CosmicCore.id("block/" + (energy ? "energy_port" : "item_port") + "_terminal_"
                + phase.getSerializedName()));
    }

    /** Materialisieren 0..1 aus dem Kern des zugehoerigen Reaktors; unbekannt = eigener Zeitpunkt des Formens. */
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
        net.minecraft.core.Direction console = state.getValue(ModProperties.CONSOLE).direction();
        if (console != null) {
            terminal(be, state, phase, energy, console, st, level, pose, buffers, light, overlay);
            return;
        }
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(plugModel(energy, phase));
        float sc = SingularityCoreBlockEntity.easeOutBack(st);
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees((1.0F - st) * 270.0F));
        pose.scale(sc, sc, sc);
        pose.translate(-0.5F, -0.5F, -0.5F);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                buffers.getBuffer(RenderType.cutout()), state, model, 1.0F, 1.0F, 1.0F, light, overlay, ModelData.EMPTY, RenderType.cutout());
        pose.popPose();

        if (st < 1.0F) {
            float[] col = energy ? ENERGY : ITEM;
            VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
            Matrix4f m = new Matrix4f(pose.last().pose()).translate(0.5F, 0.5F, 0.5F);
            RenderUtil.glowSphere(glow, m, 0.3F + 0.5F * st, 8, 12, col, 0.6F * (1.0F - st));
            RenderUtil.glowSphere(glow, m, 0.15F + 0.2F * st, 6, 10, new float[]{1, 1, 1}, 0.8F * (1.0F - st));
            RenderUtil.flatCircle(glow, new Matrix4f(m).translate(0, -0.5F + st, 0), 0.6F, 0.03F, 32, col, 1.0F - st);
        }
    }

    /**
     * Seitenteil der Steuerkonsole: bleibt als massiver Block stehen (Rohre docken aussen an) und ist ueber eine
     * Lichtleitung mit dem schwebenden Bildschirm des Controllers verbunden.
     */
    private static void terminal(PortBlockEntity be, BlockState state, CorePhase phase, boolean energy, net.minecraft.core.Direction facing,
                                 float st, Level level, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(terminalModel(energy, phase));
        float yRot = switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
        // waehrend des Formens: kurzes Einrasten (leichtes Nachfedern statt Drehen)
        float sc = 0.85F + 0.15F * SingularityCoreBlockEntity.easeOutBack(st);
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot));
        pose.scale(sc, sc, sc);
        pose.translate(-0.5F, -0.5F, -0.5F);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                buffers.getBuffer(RenderType.cutout()), state, model, 1.0F, 1.0F, 1.0F, light, overlay, ModelData.EMPTY, RenderType.cutout());
        pose.popPose();

        // Lichtleitung zum Controller-Sockel und Energiefluss darin
        float[] col = energy ? ENERGY : ITEM;
        net.minecraft.core.Direction toController = energy ? facing.getClockWise() : facing.getCounterClockWise();
        float dx = toController.getStepX(), dz = toController.getStepZ();
        float t = level.getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        VertexConsumer glow = buffers.getBuffer(CosmicRenderTypes.glow());
        Matrix4f m = pose.last().pose();
        float y = 0.22F, a = st;
        float x0 = 0.5F + dx * 0.5F, z0 = 0.5F + dz * 0.5F, x1 = 0.5F + dx * 1.35F, z1 = 0.5F + dz * 1.35F;
        RenderUtil.segment(glow, m, x0, y, z0, x1, y, z1, 0.05F, col[0], col[1], col[2], 0.5F * a, 0.3F * a);
        RenderUtil.segment(glow, m, x0, y, z0, x1, y, z1, 0.015F, 1.0F, 1.0F, 1.0F, 0.9F * a, 0.6F * a);
        float s = net.minecraft.util.Mth.frac(t * 0.05F);
        float ps = energy ? 1.0F - s : s; // Energie fliesst nach aussen, Items nach innen
        RenderUtil.cube(glow, m, net.minecraft.util.Mth.lerp(ps, x0, x1), y, net.minecraft.util.Mth.lerp(ps, z0, z1), 0.035F,
                1.0F, 1.0F, 1.0F, a * net.minecraft.util.Mth.sin(ps * net.minecraft.util.Mth.PI));
        if (st < 1.0F) {
            Matrix4f c = new Matrix4f(m).translate(0.5F, 0.5F, 0.5F);
            RenderUtil.glowSphere(glow, c, 0.4F + 0.4F * st, 8, 12, col, 0.5F * (1.0F - st));
        }
    }
}
