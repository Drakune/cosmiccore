package dr4kun3.cosmiccore;

import com.mojang.logging.LogUtils;
import dr4kun3.cosmiccore.block.ModBlocks;
import dr4kun3.cosmiccore.block.entity.ModBlockEntities;
import dr4kun3.cosmiccore.data.ModDataMaps;
import dr4kun3.cosmiccore.entity.ModEntities;
import dr4kun3.cosmiccore.menu.ModMenus;
import net.minecraft.resources.Identifier;
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
        ModEntities.ENTITIES.register(modEventBus);

        modEventBus.addListener(CosmicCore::registerCapabilities);
        modEventBus.addListener(CosmicCore::registerDataMaps);
        modEventBus.addListener(dr4kun3.cosmiccore.network.ModNetwork::register);

        modContainer.registerConfig(ModConfig.Type.LOCAL, CoreConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.ENERGY_PORT.get(), (be, side) -> dr4kun3.cosmiccore.transfer.LegacyTransfer.energy(be.getEnergyView()));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.CONTROLLER.get(), (be, side) -> dr4kun3.cosmiccore.transfer.LegacyTransfer.energy(be.getEnergyView()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.ITEM_PORT.get(), (be, side) -> dr4kun3.cosmiccore.transfer.LegacyTransfer.items(be.getItemView()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.CONTROLLER.get(), (be, side) -> dr4kun3.cosmiccore.transfer.LegacyTransfer.items(be.getItemView()));
    }

    private static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(ModDataMaps.CORE_FUEL);
        event.register(ModDataMaps.CORE_STABILIZER);
    }
}
