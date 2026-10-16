package dr4kun3.cosmiccore.datagen;

import dr4kun3.cosmiccore.CosmicCore;
import dr4kun3.cosmiccore.block.ModBlocks;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

public class ModBlockTagProvider extends BlockTagsProvider {
    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, CosmicCore.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        for (var entry : ModBlocks.BLOCKS.getEntries()) {
            Block b = entry.value();
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(b);
            tag(BlockTags.NEEDS_IRON_TOOL).add(b);
        }
    }
}
