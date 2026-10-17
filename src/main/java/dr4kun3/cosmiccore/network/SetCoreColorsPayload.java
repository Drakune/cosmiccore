package dr4kun3.cosmiccore.network;

import dr4kun3.cosmiccore.CosmicCore;
import dr4kun3.cosmiccore.block.entity.ControllerBlockEntity;
import dr4kun3.cosmiccore.color.CoreColors;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> Server: neuer Farbsatz aus dem Farbmenü für den Controller an pos. */
public record SetCoreColorsPayload(BlockPos pos, int mask, List<Integer> colors) implements CustomPacketPayload {
    public static final Type<SetCoreColorsPayload> TYPE = new Type<>(CosmicCore.id("set_core_colors"));
    public static final StreamCodec<ByteBuf, SetCoreColorsPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetCoreColorsPayload::pos,
            ByteBufCodecs.VAR_INT, SetCoreColorsPayload::mask,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(CoreColors.CHANNELS)), SetCoreColorsPayload::colors,
            SetCoreColorsPayload::new);

    /**
     * Baut das Paket aus einem Farbsatz.
     *
     * @param pos Position des Controllers
     * @param c der neue Farbsatz
     * @return das Paket für den Server
     */
    public static SetCoreColorsPayload of(BlockPos pos, CoreColors.Config c) {
        return new SetCoreColorsPayload(pos, c.mask(), Arrays.stream(c.colors()).boxed().toList());
    }

    /**
     * Server: übernimmt die Farben nur in Reichweite (8 Blöcke) und nur mit eingesetztem Farb-Modul.
     *
     * @param p das empfangene Paket
     * @param ctx Kontext mit dem sendenden Spieler
     */
    public static void handle(SetCoreColorsPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (player.distanceToSqr(Vec3.atCenterOf(p.pos())) > 64.0 || !player.level().isLoaded(p.pos())) return;
            if (!(player.level().getBlockEntity(p.pos()) instanceof ControllerBlockEntity c) || !c.hasColorModule()) return;
            int[] colors = new int[CoreColors.CHANNELS];
            for (int i = 0; i < Math.min(colors.length, p.colors().size()); i++) colors[i] = p.colors().get(i);
            c.setColors(new CoreColors.Config(p.mask(), colors));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
