package dr4kun3.cosmiccore.entity;

import dr4kun3.cosmiccore.CosmicCore;
import dr4kun3.cosmiccore.ModItems;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Standard-Beute der Asteroiden. Daraus erzeugt die Datengenerierung die Loot-Tables
 * (data/cosmiccore/loot_table/asteroid/&lt;groesse&gt;.json, per Datapack ueberschreibbar); die Rezept-Viewer zeigen sie an.
 */
public final class AsteroidLoot {
    /** Ein Pool: genau ein Eintrag mit zufaelliger Menge min-max, der mit chance (0-1) gewuerfelt wird. */
    public record Drop(Supplier<? extends Item> item, int min, int max, float chance) {}

    public record Table(String name, List<Drop> drops) {
        public net.minecraft.resources.ResourceLocation id() {
            return CosmicCore.id("asteroid/" + name);
        }
    }

    public static final List<Table> TABLES = List.of(
            new Table("small", List.of(
                    new Drop(ModItems.STAR_DUST, 1, 3, 1.0F),
                    new Drop(() -> Items.RAW_IRON, 1, 2, 0.4F),
                    new Drop(() -> Items.AMETHYST_SHARD, 1, 3, 0.3F))),
            new Table("medium", List.of(
                    new Drop(ModItems.STAR_DUST, 2, 5, 1.0F),
                    new Drop(ModItems.COMPRESSED_STAR_DUST, 1, 1, 0.25F),
                    new Drop(() -> Items.RAW_GOLD, 1, 3, 0.4F),
                    new Drop(() -> Items.RAW_IRON, 1, 3, 0.5F),
                    new Drop(() -> Items.DIAMOND, 1, 1, 0.1F))),
            new Table("large", List.of(
                    new Drop(ModItems.COMPRESSED_STAR_DUST, 1, 2, 1.0F),
                    new Drop(ModItems.NEUTRON_FRAGMENT, 1, 1, 0.35F),
                    new Drop(ModItems.QUASAR_ESSENCE, 1, 1, 0.08F),
                    new Drop(() -> Items.DIAMOND, 1, 2, 0.3F),
                    new Drop(() -> Items.NETHERITE_SCRAP, 1, 1, 0.1F),
                    new Drop(() -> Items.ECHO_SHARD, 1, 2, 0.2F))));

    private AsteroidLoot() {}
}
