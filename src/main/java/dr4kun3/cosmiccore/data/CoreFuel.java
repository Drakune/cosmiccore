package dr4kun3.cosmiccore.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Brennstoff für den Kern: burnTime in Ticks, power in RF/t (bei voller Resonanz, vor Boni) und ein optionaler
 * Hitzefaktor (heat, Standard 1.0) - heißere Brennstoffe treiben die Instabilität stärker.
 */
public record CoreFuel(int burnTime, int power, float heat) {
    public static final Codec<CoreFuel> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("burn_time").forGetter(CoreFuel::burnTime),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("power").forGetter(CoreFuel::power),
            Codec.floatRange(0.0F, 100.0F).optionalFieldOf("heat", 1.0F).forGetter(CoreFuel::heat)
    ).apply(i, CoreFuel::new));
}
