package dr4kun3.cosmiccore.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Brennstoff fuer den Kern: burnTime in Ticks, power in RF/t (bei voller Resonanz, vor Boni). */
public record CoreFuel(int burnTime, int power) {
    public static final Codec<CoreFuel> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("burn_time").forGetter(CoreFuel::burnTime),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("power").forGetter(CoreFuel::power)
    ).apply(i, CoreFuel::new));
}
