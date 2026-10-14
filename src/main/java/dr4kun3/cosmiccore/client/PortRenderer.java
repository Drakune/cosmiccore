package dr4kun3.cosmiccore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dr4kun3.cosmiccore.CosmicCore;
import dr4kun3.cosmiccore.block.CorePhase;
import dr4kun3.cosmiccore.block.ModBlocks;
import dr4kun3.cosmiccore.block.ModProperties;
import dr4kun3.cosmiccore.block.entity.PortBlockEntity;
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

    @Override
    public void render(T be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        BlockState state = be.getBlockState();
        if (!state.hasProperty(ModProperties.PHASE)) return;
        CorePhase phase = state.getValue(ModProperties.PHASE);
        if (phase == CorePhase.DORMANT) {
            be.clientSeenPhase = CorePhase.DORMANT;
            be.clientAppear = Long.MIN_VALUE;
            return;
        }
        long now = level.getGameTime();
        if (be.clientAppear == Long.MIN_VALUE) {
            // gerade gebildet (vorher als inaktiv gesehen) -> animieren; beim Laden einer fertigen Struktur sofort da
            be.clientAppear = be.clientSeenPhase == CorePhase.DORMANT ? now : now - 100_000L;
        }
        be.clientSeenPhase = phase;
        // die Dose erscheint erst, wenn das Einsaugen und Entstehen der Kammer vorbei ist
        float st = (now - be.clientAppear + partialTick - SingularityCoreBlockEntity.FORM_TICKS)
                / (float) SingularityCoreBlockEntity.SETTLE_TICKS;
        st = Math.min(1.0F, Math.max(0.0F, st));
        if (st <= 0.0F) return;

        boolean energy = state.is(ModBlocks.ENERGY_PORT.get());
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
}
