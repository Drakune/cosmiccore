package dr4kun3.cosmiccore;

import dr4kun3.cosmiccore.block.ModBlocks;
import dr4kun3.cosmiccore.item.GuideBookItem;
import dr4kun3.cosmiccore.item.OverdriveParticleItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmicCore.MODID);

    public static final DeferredItem<Item> STAR_DUST = ITEMS.registerSimpleItem("star_dust");
    public static final DeferredItem<GuideBookItem> GUIDE_BOOK = ITEMS.registerItem("guide_book",
            GuideBookItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<BlockItem> VOID_CASING = ITEMS.registerSimpleBlockItem("void_casing", ModBlocks.VOID_CASING);
    public static final DeferredItem<BlockItem> RUNE_FRAME = ITEMS.registerSimpleBlockItem("rune_frame", ModBlocks.RUNE_FRAME);
    public static final DeferredItem<BlockItem> FOCUS_GLASS = ITEMS.registerSimpleBlockItem("focus_glass", ModBlocks.FOCUS_GLASS);
    public static final DeferredItem<BlockItem> RESONANCE_COIL = ITEMS.registerSimpleBlockItem("resonance_coil", ModBlocks.RESONANCE_COIL);
    public static final DeferredItem<BlockItem> FOCUS_CRYSTAL = ITEMS.registerSimpleBlockItem("focus_crystal", ModBlocks.FOCUS_CRYSTAL);
    public static final DeferredItem<BlockItem> SINGULARITY_CORE = ITEMS.registerSimpleBlockItem("singularity_core", ModBlocks.SINGULARITY_CORE);
    public static final DeferredItem<BlockItem> CONTROLLER = ITEMS.registerSimpleBlockItem("controller", ModBlocks.CONTROLLER);
    public static final DeferredItem<BlockItem> ENERGY_PORT = ITEMS.registerSimpleBlockItem("energy_port", ModBlocks.ENERGY_PORT);
    public static final DeferredItem<BlockItem> ITEM_PORT = ITEMS.registerSimpleBlockItem("item_port", ModBlocks.ITEM_PORT);

    public static final DeferredItem<BlockItem> STAR_FUNNEL = ITEMS.registerSimpleBlockItem("star_funnel", ModBlocks.STAR_FUNNEL);

    public static final DeferredItem<BlockItem> COSMIC_LASER = ITEMS.registerSimpleBlockItem("cosmic_laser", ModBlocks.COSMIC_LASER);

    public static final DeferredItem<OverdriveParticleItem> OVERDRIVE_PARTICLE = ITEMS.registerItem("overdrive_particle",
            OverdriveParticleItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));

    private ModItems() {}
}
