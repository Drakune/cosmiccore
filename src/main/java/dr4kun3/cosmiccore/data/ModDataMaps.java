package dr4kun3.cosmiccore.data;

import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jetbrains.annotations.Nullable;

public final class ModDataMaps {
    public static final DataMapType<Item, CoreFuel> CORE_FUEL = DataMapType.builder(
            CosmicCore.id("core_fuel"), Registries.ITEM, CoreFuel.CODEC)
            .synced(CoreFuel.CODEC, false)
            .build();

    public static final DataMapType<Item, CoreStabilizer> CORE_STABILIZER = DataMapType.builder(
            CosmicCore.id("core_stabilizer"), Registries.ITEM, CoreStabilizer.CODEC)
            .synced(CoreStabilizer.CODEC, false)
            .build();

    /**
     * Brennstoffwerte eines Items laut Data Map.
     *
     * @param stack zu prüfender Stapel
     * @return Brenndauer, Leistung und Hitze oder {@code null}, wenn das Item kein Brennstoff ist
     */
    @Nullable
    public static CoreFuel fuel(ItemStack stack) {
        return stack.isEmpty() ? null : stack.typeHolder().getData(CORE_FUEL);
    }

    /**
     * Stabilisatorwerte eines Items laut Data Map.
     *
     * @param stack zu prüfender Stapel
     * @return Kühlwerte oder {@code null}, wenn das Item kein Stabilisator ist
     */
    @Nullable
    public static CoreStabilizer stabilizer(ItemStack stack) {
        return stack.isEmpty() ? null : stack.typeHolder().getData(CORE_STABILIZER);
    }

    /** Nur statische Definitionen - keine Instanzen. */
    private ModDataMaps() {}
}
