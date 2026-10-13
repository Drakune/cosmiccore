package dev.cosmiccore.network;

import dev.cosmiccore.CosmicCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> Client: der ultimative Kern ist erwacht - spielt den globalen Himmelseffekt ab. */
public record AwakeningPayload(BlockPos pos, int size) implements CustomPacketPayload {
    public static final Type<AwakeningPayload> TYPE = new Type<>(CosmicCore.id("awakening"));
    public static final StreamCodec<ByteBuf, AwakeningPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AwakeningPayload::pos,
            ByteBufCodecs.VAR_INT, AwakeningPayload::size,
            AwakeningPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
