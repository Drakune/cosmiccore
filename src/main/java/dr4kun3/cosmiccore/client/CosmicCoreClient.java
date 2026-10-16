package dr4kun3.cosmiccore.client;

import dr4kun3.cosmiccore.CosmicCore;
import dr4kun3.cosmiccore.ModItems;
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
        // Void-Titan: mit GeckoLib gezeichnet; ohne GeckoLib unsichtbar (er erscheint dann ohnehin nie)
        if (net.neoforged.fml.ModList.get().isLoaded("geckolib")) {
            event.registerEntityRenderer(ModEntities.VOID_TITAN.get(), TitanRenderers::create);
        } else {
            event.registerEntityRenderer(ModEntities.VOID_TITAN.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        }
    }

    /** Plug-Modelle der Ports werden vom PortRenderer gezeichnet und deshalb als Zusatzmodelle geladen. */
    @SubscribeEvent
    public static void registerModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterStandalone event) {
        for (dr4kun3.cosmiccore.block.CorePhase phase : dr4kun3.cosmiccore.block.CorePhase.values()) {
            if (phase == dr4kun3.cosmiccore.block.CorePhase.DORMANT) continue;
            registerBlockModel(event, PortRenderer.plugModel(true, phase));
            registerBlockModel(event, PortRenderer.plugModel(false, phase));
            registerBlockModel(event, PortRenderer.terminalModel(true, phase));
            registerBlockModel(event, PortRenderer.terminalModel(false, phase));
        }
    }

    private static void registerBlockModel(net.neoforged.neoforge.client.event.ModelEvent.RegisterStandalone event,
                                           net.neoforged.neoforge.client.model.standalone.StandaloneModelKey<net.minecraft.client.renderer.block.dispatch.BlockStateModel> key) {
        event.register(key, net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel.blockStateModel(PortRenderer.modelId(key)));
    }

    /**
     * Animierte 3D-Items (Sternenstaub, Brennstoffe, Overdrive-Partikel, Module, Handbuch): ein Spezial-Renderer,
     * den die Item-Definitionen in assets/cosmiccore/items als Typ cosmiccore:cosmic_item anfordern.
     */
    /** 3D-Vorschau der Reaktorstruktur im GUI (Bild im Bild, ab Minecraft 1.21.6). */
    @SubscribeEvent
    public static void registerPipRenderers(net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent event) {
        event.register(StructurePreview.State.class, StructurePreview.Renderer::new);
    }

    @SubscribeEvent
    public static void registerSpecialRenderers(net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent event) {
        event.register(dr4kun3.cosmiccore.CosmicCore.id("cosmic_item"), CosmicItemRenderer.Unbaked.CODEC);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CONTROLLER.get(), ControllerScreen::new);
        event.register(ModMenus.ITEM_PORT.get(), ItemPortScreen::new);
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
        } else if (stack.is(ModItems.COLOR_MODULE.get())) {
            event.getToolTip().add(Component.translatable("tooltip.cosmiccore.color_module").withStyle(ChatFormatting.LIGHT_PURPLE));
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
