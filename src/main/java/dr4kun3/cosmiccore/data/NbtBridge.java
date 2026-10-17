package dr4kun3.cosmiccore.data;

import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Brücke zwischen dem neuen ValueInput/ValueOutput und dem bisherigen NBT-Format (Speicherstände bleiben kompatibel). */
public final class NbtBridge {
    @SuppressWarnings("deprecation")
    private static final MapCodec<CompoundTag> AS_MAP = MapCodec.assumeMapUnsafe(CompoundTag.CODEC);

    /** Nur statische Hilfsfunktionen - keine Instanzen. */
    private NbtBridge() {}

    /**
     * Liest alle Einträge der Eingabe als CompoundTag.
     *
     * @param input gespeicherte Daten
     * @return die Einträge (leer, wenn keine)
     */
    public static CompoundTag read(ValueInput input) {
        return input.read(AS_MAP).orElseGet(CompoundTag::new);
    }

    /**
     * Schreibt jeden Eintrag des Tags unter seinem Schlüssel in die Ausgabe.
     *
     * @param output Ziel der Daten
     * @param tag die Einträge
     */
    public static void write(ValueOutput output, CompoundTag tag) {
        for (String key : tag.keySet()) {
            output.store(key, ExtraCodecs.NBT, tag.get(key));
        }
    }
}
