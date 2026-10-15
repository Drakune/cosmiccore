package dr4kun3.cosmiccore.datagen;

import dr4kun3.cosmiccore.CosmicCore;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Datengenerierung ({@code ./gradlew runData}): Rezepte, Loot-Tables, Tags, und Data Maps landen in
 * src/generated/resources. Texturen, Modelle und Sprachdateien erzeugt weiterhin tools/generate_resources.py.
 */
@EventBusSubscriber(modid = CosmicCore.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModDataGenerators {
    @SubscribeEvent
    public static void gather(GatherDataEvent event) {
        DataGenerator gen = event.getGenerator();
        PackOutput out = gen.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        ExistingFileHelper files = event.getExistingFileHelper();

        gen.addProvider(event.includeServer(), new ModRecipeProvider(out, lookup));
        gen.addProvider(event.includeServer(), new LootTableProvider(out, Set.of(), List.of(
                new LootTableProvider.SubProviderEntry(ModBlockLoot::new, LootContextParamSets.BLOCK),
                new LootTableProvider.SubProviderEntry(ModAsteroidLoot::new, LootContextParamSets.EMPTY)), lookup));
        gen.addProvider(event.includeServer(), new ModBlockTagProvider(out, lookup, files));
        gen.addProvider(event.includeServer(), new ModDataMapProvider(out, lookup));
    }

    private ModDataGenerators() {}
}
