package dr4kun3.cosmiccore;

import dr4kun3.cosmiccore.block.entity.ControllerBlockEntity;
import dr4kun3.cosmiccore.network.AwakeningPayload;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** /cosmiccore cutscene - spielt die Void-Titan-Cutscene des naechsten erwachten ultimativen Kerns erneut ab. */
@EventBusSubscriber(modid = CosmicCore.MODID)
public final class ModCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cosmiccore")
                .then(Commands.literal("cutscene").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    BlockPos best = null;
                    double bestDist = 64.0 * 64.0;
                    for (BlockPos pos : dr4kun3.cosmiccore.multiblock.ReactorRegistry.controllers(player.level())) {
                        if (player.level().getBlockEntity(pos) instanceof ControllerBlockEntity c && c.isAwakened()) {
                            double d = pos.distSqr(player.blockPosition());
                            if (d < bestDist) {
                                bestDist = d;
                                best = c.corePos();
                            }
                        }
                    }
                    if (best == null) {
                        ctx.getSource().sendFailure(Component.translatable("message.cosmiccore.cutscene.none"));
                        return 0;
                    }
                    PacketDistributor.sendToPlayer(player, new AwakeningPayload(best, 11));
                    return 1;
                })));
    }

    private ModCommands() {}
}
