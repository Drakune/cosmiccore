package dr4kun3.cosmiccore.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Stabilisator: wirkt duration Ticks lang und senkt die Instabilitaet um strength pro Tick. */
public record CoreStabilizer(int duration, float strength) {
    public static final Codec<CoreStabilizer> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("duration").forGetter(CoreStabilizer::duration),
            Codec.floatRange(0.0001F, 100.0F).fieldOf("strength").forGetter(CoreStabilizer::strength)
    ).apply(i, CoreStabilizer::new));
}
