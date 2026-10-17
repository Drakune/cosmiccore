package dr4kun3.cosmiccore.network;

import dr4kun3.cosmiccore.CosmicCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> Client: der ultimative Kern ist erwacht - startet die Void-Titan-Cutscene (falls GeckoLib vorhanden). */
public record AwakeningPayload(BlockPos pos, int size) implements CustomPacketPayload {
    public static final Type<AwakeningPayload> TYPE = new Type<>(CosmicCore.id("awakening"));
    public static final StreamCodec<ByteBuf, AwakeningPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AwakeningPayload::pos,
            ByteBufCodecs.VAR_INT, AwakeningPayload::size,
            AwakeningPayload::new);

    /**
     * Liefert den Pakettyp, unter dem NeoForge dieses Paket kennt.
     *
     * @return der registrierte Typ des Erwachen-Pakets
     */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
