package dev.cosmiccore;

import com.mojang.logging.LogUtils;
import dev.cosmiccore.block.ModBlocks;
import dev.cosmiccore.block.entity.ModBlockEntities;
import dev.cosmiccore.data.ModDataMaps;
import dev.cosmiccore.menu.ModMenus;
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

    public CosmicCore(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);

        modEventBus.addListener(CosmicCore::registerCapabilities);
        modEventBus.addListener(CosmicCore::registerDataMaps);

        modContainer.registerConfig(ModConfig.Type.COMMON, CoreConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.ENERGY_PORT.get(), (be, side) -> be.getEnergyView());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CONTROLLER.get(), (be, side) -> be.getEnergyView());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.ITEM_PORT.get(), (be, side) -> be.getItemView());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CONTROLLER.get(), (be, side) -> be.getItemView());
    }

    private static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(ModDataMaps.CORE_FUEL);
        event.register(ModDataMaps.CORE_STABILIZER);
    }
}
