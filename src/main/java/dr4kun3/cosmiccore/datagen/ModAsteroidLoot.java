package dr4kun3.cosmiccore.datagen;

import dr4kun3.cosmiccore.entity.AsteroidLoot;
import java.util.function.BiConsumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

/** Asteroiden-Beute aus {@link AsteroidLoot}: ein Pool je Drop, mit Zufallschance falls unter 100 %. */
public record ModAsteroidLoot(HolderLookup.Provider registries) implements LootTableSubProvider {
    /**
     * Erzeugt fuer jede Asteroidengroesse eine Loot-Table: ein Pool je moeglichem Drop, mit zufaelliger Anzahl
     * zwischen Minimum und Maximum und einer Wahrscheinlichkeit, falls diese unter 100 % liegt.
     *
     * @param out Empfaenger der fertigen Tabellen (Schluessel der Loot-Table und Tabellen-Bauplan)
     */
    @Override
    public void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> out) {
        for (AsteroidLoot.Table t : AsteroidLoot.TABLES) {
            LootTable.Builder table = LootTable.lootTable();
            for (AsteroidLoot.Drop d : t.drops()) {
                LootPool.Builder pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(d.item().get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(d.min(), d.max()))));
                if (d.chance() < 1.0F) pool.when(LootItemRandomChanceCondition.randomChance(d.chance()));
                table.withPool(pool);
            }
            out.accept(ResourceKey.create(Registries.LOOT_TABLE, t.id()), table);
        }
    }
}
