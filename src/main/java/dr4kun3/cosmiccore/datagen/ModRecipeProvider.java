package dr4kun3.cosmiccore.datagen;

import dr4kun3.cosmiccore.ModItems;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    private static ShapedRecipeBuilder shaped(ItemLike result, int count, ItemLike unlock, String... pattern) {
        ShapedRecipeBuilder b = ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result, count);
        for (String row : pattern) b.pattern(row);
        return b.unlockedBy("has_item", has(unlock));
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        shaped(ModItems.VOID_CASING, 4, Items.OBSIDIAN, "OIO", "IAI", "OIO")
                .define('O', Items.OBSIDIAN).define('I', Items.IRON_INGOT).define('A', Items.AMETHYST_SHARD).save(out);
        shaped(ModItems.RUNE_FRAME, 2, ModItems.VOID_CASING, "CAC", "ARA", "CAC")
                .define('C', ModItems.VOID_CASING).define('A', Items.AMETHYST_SHARD).define('R', Items.REDSTONE).save(out);
        shaped(ModItems.FOCUS_GLASS, 4, Items.AMETHYST_SHARD, "GGG", "GAG", "GGG")
                .define('G', Items.GLASS).define('A', Items.AMETHYST_SHARD).save(out);
        shaped(ModItems.RESONANCE_COIL, 1, ModItems.VOID_CASING, "CCC", "RVR", "CCC")
                .define('C', Items.COPPER_INGOT).define('R', Items.REDSTONE).define('V', ModItems.VOID_CASING).save(out);
        shaped(ModItems.FOCUS_CRYSTAL, 1, Items.ENDER_EYE, "ADA", "DED", "ADA")
                .define('A', Items.AMETHYST_SHARD).define('D', Items.DIAMOND).define('E', Items.ENDER_EYE).save(out);
        shaped(ModItems.SINGULARITY_CORE, 1, Items.NETHER_STAR, "OEO", "ENE", "OEO")
                .define('O', Items.CRYING_OBSIDIAN).define('E', Items.ENDER_EYE).define('N', Items.NETHER_STAR).save(out);
        shaped(ModItems.CONTROLLER, 1, Items.NETHER_STAR, "CDC", "ENE", "CDC")
                .define('C', ModItems.VOID_CASING).define('D', Items.DIAMOND).define('E', Items.ENDER_EYE)
                .define('N', Items.NETHER_STAR).save(out);
        shaped(ModItems.STAR_FUNNEL, 1, ModItems.STAR_DUST, "G G", "GSG", " V ")
                .define('G', ModItems.FOCUS_GLASS).define('S', ModItems.STAR_DUST).define('V', ModItems.VOID_CASING).save(out);
        shaped(ModItems.COSMIC_LASER, 1, ModItems.FOCUS_CRYSTAL, " C ", "ISI", "IVI")
                .define('C', ModItems.FOCUS_CRYSTAL).define('S', ModItems.STAR_DUST).define('I', Items.IRON_INGOT)
                .define('V', ModItems.VOID_CASING).save(out);
        shaped(ModItems.OVERDRIVE_PARTICLE, 1, Items.END_CRYSTAL, " E ", "ENE", " E ")
                .define('E', Items.END_CRYSTAL).define('N', Items.NETHER_STAR).save(out);
        // nur craftbar: Overdrive-Partikel in der Mitte, vier Quasar-Essenzen drumherum
        shaped(ModItems.QUASAR_OVERDRIVE_PARTICLE, 1, ModItems.QUASAR_ESSENCE, " Q ", "QOQ", " Q ")
                .define('Q', ModItems.QUASAR_ESSENCE).define('O', ModItems.OVERDRIVE_PARTICLE).save(out);
        shaped(ModItems.EFFICIENCY_MODULE, 1, ModItems.STAR_DUST, "GRG", "SVS", "GRG")
                .define('G', Items.GOLD_INGOT).define('R', Items.REDSTONE).define('S', ModItems.STAR_DUST)
                .define('V', ModItems.VOID_CASING).save(out);
        shaped(ModItems.STABILITY_MODULE, 1, ModItems.STAR_DUST, "ODO", "SVS", "ODO")
                .define('O', Items.CRYING_OBSIDIAN).define('D', Items.DIAMOND).define('S', ModItems.STAR_DUST)
                .define('V', ModItems.VOID_CASING).save(out);
        shaped(ModItems.ANTI_ASTEROID_MODULE, 1, ModItems.STAR_DUST, "PRP", "SVS", "PEP")
                .define('P', Items.PHANTOM_MEMBRANE).define('R', Items.REDSTONE).define('S', ModItems.STAR_DUST)
                .define('V', ModItems.VOID_CASING).define('E', Items.ENDER_EYE).save(out);
        shaped(ModItems.COMPRESSED_STAR_DUST, 1, ModItems.STAR_DUST, "SSS", "SSS", "SSS")
                .define('S', ModItems.STAR_DUST).save(out);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ENERGY_PORT)
                .requires(ModItems.VOID_CASING).requires(Items.REDSTONE, 2).requires(Items.COPPER_INGOT)
                .unlockedBy("has_item", has(ModItems.VOID_CASING)).save(out);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ITEM_PORT)
                .requires(ModItems.VOID_CASING).requires(Items.HOPPER)
                .unlockedBy("has_item", has(ModItems.VOID_CASING)).save(out);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.GUIDE_BOOK)
                .requires(Items.BOOK).requires(ModItems.STAR_DUST)
                .unlockedBy("has_item", has(ModItems.STAR_DUST)).save(out);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.STAR_DUST, 2)
                .requires(Items.AMETHYST_SHARD, 2).requires(Items.ENDER_PEARL).requires(Items.GLOWSTONE_DUST)
                .unlockedBy("has_item", has(Items.AMETHYST_SHARD)).save(out);
    }
}
