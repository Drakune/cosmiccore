package dr4kun3.cosmiccore;

import com.mojang.logging.LogUtils;
import dr4kun3.cosmiccore.block.ModBlocks;
import dr4kun3.cosmiccore.block.entity.ModBlockEntities;
import dr4kun3.cosmiccore.data.ModDataMaps;
import dr4kun3.cosmiccore.entity.ModEntities;
import dr4kun3.cosmiccore.menu.ModMenus;
import dr4kun3.cosmiccore.network.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.slf4j.Logger;

@Mod(CosmicCore.MODID)
public class CosmicCore {
    public static final String MODID = "cosmiccore";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Einstieg der Mod: registriert Blöcke, Items, Blockinhalte, Menüs, Entities, Netzwerk, Fähigkeiten, Data Maps
     * und die Einstellungen.
     *
     * @param modEventBus Ereignisbus der Mod
     * @param modContainer Container der Mod (für die Einstellungen)
     */
    public CosmicCore(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);

        modEventBus.addListener(CosmicCore::registerCapabilities);
        modEventBus.addListener(CosmicCore::registerDataMaps);
        modEventBus.addListener(ModNetwork::register);

        modContainer.registerConfig(ModConfig.Type.COMMON, CoreConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    /**
     * Kennung im Namensraum der Mod.
     *
     * @param path Pfad, z. B. {@code textures/item/star_dust.png}
     * @return {@code cosmiccore:<path>}
     */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /**
     * Stellt Energie (Energie-Port, Controller) und Items (Item-Port, Controller) für Kabel und Rohre bereit.
     *
     * @param event Ereignis zur Registrierung der Fähigkeiten
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.ENERGY_PORT.get(), (be, side) -> be.getEnergyView());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CONTROLLER.get(), (be, side) -> be.getEnergyView());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.ITEM_PORT.get(), (be, side) -> be.getItemView());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CONTROLLER.get(), (be, side) -> be.getItemView());
    }

    /**
     * Meldet die Data Maps für Brennstoffe und Stabilisatoren an.
     *
     * @param event Ereignis zur Registrierung der Data-Map-Typen
     */
    private static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(ModDataMaps.CORE_FUEL);
        event.register(ModDataMaps.CORE_STABILIZER);
    }
}
