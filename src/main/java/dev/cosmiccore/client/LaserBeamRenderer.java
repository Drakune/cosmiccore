package dev.cosmiccore.client;

import static dev.cosmiccore.client.RenderUtil.cube;
import static dev.cosmiccore.client.RenderUtil.segment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.cosmiccore.CosmicCore;
import dev.cosmiccore.entity.CosmicAsteroid;
import dev.cosmiccore.item.CosmicLaserItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Zeichnet den Strahl des Kosmos-Lasers fuer alle Spieler, die ihn gerade benutzen. */
@EventBusSubscriber(modid = CosmicCore.MODID, value = Dist.CLIENT)
public final class LaserBeamRenderer {
    private static final float[] OUTER = {0.45F, 0.75F, 1.0F};
    private static final float[] HIT = {1.0F, 0.6F, 0.25F};

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        boolean drew = false;

        for (Player p : mc.level.players()) {
            if (!p.isUsingItem() || !(p.getUseItem().getItem() instanceof CosmicLaserItem)) continue;
            Vec3 eye = p.getEyePosition(pt);
            Vec3 look = p.getViewVector(pt);
            // Startpunkt: etwas vor und neben dem Spieler (Hand)
            boolean firstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
            Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
            float side = p.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            Vec3 start = eye.add(look.scale(firstPerson ? 0.6 : 0.4)).add(right.scale(side * (firstPerson ? 0.25 : 0.35)))
                    .add(0, firstPerson ? -0.2 : -0.35, 0);
            CosmicAsteroid target = CosmicLaserItem.findTarget(p);
            Vec3 end = target != null ? target.getPosition(pt).add(0, target.getBbHeight() / 2, 0)
                    : eye.add(look.scale(CosmicLaserItem.RANGE));

            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            Matrix4f m = pose.last().pose();
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            float t = mc.level.getGameTime() + pt;
            float flick = 0.8F + 0.2F * Mth.sin(t * 1.7F);
            float sx = (float) start.x, sy = (float) start.y, sz = (float) start.z;
            float ex = (float) end.x, ey = (float) end.y, ez = (float) end.z;
            segment(vc, m, sx, sy, sz, ex, ey, ez, 0.03F, 1.0F, 1.0F, 1.0F, flick, target != null ? flick : 0.1F);
            segment(vc, m, sx, sy, sz, ex, ey, ez, 0.09F, OUTER[0], OUTER[1], OUTER[2], 0.45F * flick, target != null ? 0.45F : 0.0F);
            cube(vc, m, sx, sy, sz, 0.06F, 1.0F, 1.0F, 1.0F, 0.9F);
            if (target != null) {
                float s = 0.3F + 0.15F * Mth.sin(t * 0.9F);
                cube(vc, m, ex, ey, ez, s, HIT[0], HIT[1], HIT[2], 0.7F);
            }
            pose.popPose();
            drew = true;
        }
        if (drew) buffers.endBatch(RenderType.lightning());
    }

    private LaserBeamRenderer() {}
}
