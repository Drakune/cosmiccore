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

    /**
     * Registriert ein Item mit festen Eigenschaften.
     *
     * @param name Registry-Name
     * @param factory erzeugt das Item aus den Eigenschaften
     * @param properties Eigenschaften des Items
     * @param <I> Item-Klasse
     * @return das registrierte Item
     */
    private static <I extends Item> DeferredItem<I> item(String name, java.util.function.Function<Item.Properties, I> factory, Item.Properties properties) {
        return ITEMS.registerItem(name, factory, () -> properties);
    }

    public static final DeferredItem<Item> STAR_DUST = ITEMS.registerSimpleItem("star_dust");
    // Brennstoff-Stufen
    public static final DeferredItem<Item> COMPRESSED_STAR_DUST = ITEMS.registerSimpleItem("compressed_star_dust");
    public static final DeferredItem<Item> NEUTRON_FRAGMENT = item("neutron_fragment", Item::new,
            new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> QUASAR_ESSENCE = item("quasar_essence", Item::new,
            new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<GuideBookItem> GUIDE_BOOK = item("guide_book",
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

    // Module fuer den Controller (bis zu 4 je Slot)
    public static final DeferredItem<Item> EFFICIENCY_MODULE = item("efficiency_module", Item::new,
            new Item.Properties().stacksTo(4).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> STABILITY_MODULE = item("stability_module", Item::new,
            new Item.Properties().stacksTo(4).rarity(Rarity.UNCOMMON));
    /** Haelt Asteroiden im Umkreis des Reaktors fern (im Controller per Schalter an/aus). */
    /** Faerbt Scheibe, Jets und Licht des Kerns um (Farbschema wird im Controller gewaehlt). */
    public static final DeferredItem<Item> COLOR_MODULE = item("color_module", Item::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ANTI_ASTEROID_MODULE = item("anti_asteroid_module", Item::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    public static final DeferredItem<OverdriveParticleItem> OVERDRIVE_PARTICLE = item("overdrive_particle",
            OverdriveParticleItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
    /** Quasar-Overdrive-Partikel: 4 davon schalten beim ultimativen Kern nach Overdrive 3 den Ultimate-Modus frei. */
    public static final DeferredItem<dr4kun3.cosmiccore.item.QuasarOverdriveItem> QUASAR_OVERDRIVE_PARTICLE = item(
            "quasar_overdrive_particle", dr4kun3.cosmiccore.item.QuasarOverdriveItem::new,
            new Item.Properties().stacksTo(4).rarity(Rarity.EPIC));

    /** Nur statische Registrierungen - keine Instanzen. */
    private ModItems() {}
}
