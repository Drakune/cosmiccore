package dr4kun3.cosmiccore.data;

import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Bruecke zwischen dem neuen ValueInput/ValueOutput und dem bisherigen NBT-Format (Speicherstaende bleiben kompatibel). */
public final class NbtBridge {
    @SuppressWarnings("deprecation")
    private static final MapCodec<CompoundTag> AS_MAP = MapCodec.assumeMapUnsafe(CompoundTag.CODEC);

    private NbtBridge() {}

    /** Liest alle Eintraege der Eingabe als CompoundTag. */
    public static CompoundTag read(ValueInput input) {
        return input.read(AS_MAP).orElseGet(CompoundTag::new);
    }

    /** Schreibt jeden Eintrag des Tags unter seinem Schluessel in die Ausgabe. */
    public static void write(ValueOutput output, CompoundTag tag) {
        for (String key : tag.keySet()) {
            output.store(key, ExtraCodecs.NBT, tag.get(key));
        }
    }
}
