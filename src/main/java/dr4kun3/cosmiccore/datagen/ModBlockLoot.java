package dr4kun3.cosmiccore.datagen;

import dr4kun3.cosmiccore.block.ModBlocks;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

/** Jeder Block droppt sich selbst. */
public class ModBlockLoot extends BlockLootSubProvider {
    /**
     * Legt den Loot-Generator fuer die Bloecke an.
     *
     * @param registries Registry-Zugriff
     */
    public ModBlockLoot(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    /** Jeder Block der Mod droppt sich selbst. */
    @Override
    protected void generate() {
        for (Block b : getKnownBlocks()) dropSelf(b);
    }

    /**
     * Alle Bloecke der Mod (fuer die Vollstaendigkeitspruefung der Datengenerierung).
     *
     * @return die registrierten Bloecke
     */
    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(Holder::value).map(Block.class::cast).toList();
    }
}
