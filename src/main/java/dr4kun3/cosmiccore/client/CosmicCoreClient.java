package dr4kun3.cosmiccore.client;

import dr4kun3.cosmiccore.CosmicCore;
import dr4kun3.cosmiccore.ModItems;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import dr4kun3.cosmiccore.block.entity.ControllerBlockEntity;
import dr4kun3.cosmiccore.block.entity.ModBlockEntities;
import dr4kun3.cosmiccore.data.CoreFuel;
import dr4kun3.cosmiccore.data.CoreStabilizer;
import dr4kun3.cosmiccore.data.ModDataMaps;
import dr4kun3.cosmiccore.entity.ModEntities;
import dr4kun3.cosmiccore.menu.ModMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Wird nur auf dem Client geladen (dedizierte Server fassen diese Klasse nie an). */
@EventBusSubscriber(modid = CosmicCore.MODID, value = Dist.CLIENT)
public final class CosmicCoreClient {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CORE.get(), SingularityCoreRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.RESONATOR.get(), ResonatorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CONTROLLER.get(), ControllerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.STAR_FUNNEL.get(), StarFunnelRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ENERGY_PORT.get(), PortRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ITEM_PORT.get(), PortRenderer::new);
        for (var type : ModEntities.ASTEROIDS.values()) event.registerEntityRenderer(type.get(), CosmicAsteroidRenderer::new);
    }

    /** Plug-Modelle der Ports werden vom PortRenderer gezeichnet und deshalb als Zusatzmodelle geladen. */
    @SubscribeEvent
    public static void registerModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        for (dr4kun3.cosmiccore.block.CorePhase phase : dr4kun3.cosmiccore.block.CorePhase.values()) {
            if (phase == dr4kun3.cosmiccore.block.CorePhase.DORMANT) continue;
            event.register(PortRenderer.plugModel(true, phase));
            event.register(PortRenderer.plugModel(false, phase));
        }
    }

    private static CosmicItemRenderer itemRenderer;

    /** Animierte 3D-Items (Sternenstaub, verdichteter Sternenstaub, Overdrive-Partikel, Neutronenstern, Quasar-Essenz, Module). */
    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (itemRenderer == null) itemRenderer = new CosmicItemRenderer();
                return itemRenderer;
            }
        }, ModItems.STAR_DUST.get(), ModItems.COMPRESSED_STAR_DUST.get(), ModItems.OVERDRIVE_PARTICLE.get(), ModItems.NEUTRON_FRAGMENT.get(),
                ModItems.QUASAR_ESSENCE.get(), ModItems.QUASAR_OVERDRIVE_PARTICLE.get(), ModItems.EFFICIENCY_MODULE.get(), ModItems.STABILITY_MODULE.get(),
                ModItems.ANTI_ASTEROID_MODULE.get());
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CONTROLLER.get(), ControllerScreen::new);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        CoreFuel fuel = ModDataMaps.fuel(stack);
        if (fuel != null) {
            event.getToolTip().add(Component.translatable("tooltip.cosmiccore.fuel", fuel.power(), fuel.burnTime() / 20)
                    .withStyle(ChatFormatting.DARK_PURPLE));
            if (fuel.heat() != 1.0F) {
                event.getToolTip().add(Component.translatable("tooltip.cosmiccore.fuel_heat", String.format("%.1f", fuel.heat()))
                        .withStyle(fuel.heat() > 1.0F ? ChatFormatting.RED : ChatFormatting.AQUA));
            }
        }
        if (stack.is(ModItems.EFFICIENCY_MODULE.get())) {
            event.getToolTip().add(Component.translatable("tooltip.cosmiccore.efficiency_module").withStyle(ChatFormatting.GOLD));
        } else if (stack.is(ModItems.STABILITY_MODULE.get())) {
            event.getToolTip().add(Component.translatable("tooltip.cosmiccore.stability_module").withStyle(ChatFormatting.AQUA));
        } else if (stack.is(ModItems.ANTI_ASTEROID_MODULE.get())) {
            event.getToolTip().add(Component.translatable("tooltip.cosmiccore.anti_asteroid_module",
                    dr4kun3.cosmiccore.CoreConfig.ANTI_ASTEROID_RADIUS.get()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
                CoreStabilizer stab = ModDataMaps.stabilizer(stack);
        if (stab != null) {
            event.getToolTip().add(Component.translatable("tooltip.cosmiccore.stabilizer",
                    String.format("%.0f", ControllerBlockEntity.stabilizerPoints(stab))).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    private CosmicCoreClient() {}
}
