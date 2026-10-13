package dr4kun3.cosmiccore.datagen;

import dr4kun3.cosmiccore.ModItems;
import dr4kun3.cosmiccore.data.CoreFuel;
import dr4kun3.cosmiccore.data.CoreStabilizer;
import dr4kun3.cosmiccore.data.ModDataMaps;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.data.DataMapProvider;

/** Standard-Balance fuer Brennstoffe und Stabilisatoren (per Datapack ueberschreibbar). */
public class ModDataMapProvider extends DataMapProvider {
    public ModDataMapProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    @Override
    protected void gather(HolderLookup.Provider provider) {
        var fuel = builder(ModDataMaps.CORE_FUEL);
        fuel.add(ModItems.STAR_DUST, new CoreFuel(2400, 1200, 1.0F), false);
        fuel.add(ModItems.COMPRESSED_STAR_DUST, new CoreFuel(24000, 2600, 1.0F), false);
        fuel.add(ModItems.NEUTRON_FRAGMENT, new CoreFuel(6000, 8000, 1.4F), false);
        fuel.add(ModItems.QUASAR_ESSENCE, new CoreFuel(3000, 20000, 2.0F), false);
        fuel.add(vanilla(Items.ENDER_PEARL), new CoreFuel(1200, 400, 1.0F), false);
        fuel.add(vanilla(Items.ENDER_EYE), new CoreFuel(1800, 700, 1.0F), false);
        fuel.add(vanilla(Items.ECHO_SHARD), new CoreFuel(3600, 900, 1.0F), false);
        fuel.add(vanilla(Items.SCULK_CATALYST), new CoreFuel(2400, 600, 1.0F), false);
        fuel.add(vanilla(Items.NETHER_STAR), new CoreFuel(12000, 3000, 1.0F), false);

        var stab = builder(ModDataMaps.CORE_STABILIZER);
        stab.add(vanilla(Items.OBSIDIAN), new CoreStabilizer(1200, 0.02F), false);
        stab.add(vanilla(Items.CRYING_OBSIDIAN), new CoreStabilizer(2400, 0.035F), false);
        stab.add(vanilla(Items.NETHERITE_SCRAP), new CoreStabilizer(3600, 0.06F), false);
    }

    @SuppressWarnings("deprecation")
    private static net.minecraft.core.Holder<Item> vanilla(Item item) {
        return item.builtInRegistryHolder();
    }
}
