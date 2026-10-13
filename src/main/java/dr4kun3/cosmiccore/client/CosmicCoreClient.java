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
        event.registerEntityRenderer(ModEntities.ASTEROID.get(), CosmicAsteroidRenderer::new);
    }

    private static CosmicItemRenderer itemRenderer;

    /** Animierte 3D-Items (Sternenstaub, Overdrive-Partikel). */
    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (itemRenderer == null) itemRenderer = new CosmicItemRenderer();
                return itemRenderer;
            }
        }, ModItems.STAR_DUST.get(), ModItems.OVERDRIVE_PARTICLE.get());
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
        }
        CoreStabilizer stab = ModDataMaps.stabilizer(stack);
        if (stab != null) {
            event.getToolTip().add(Component.translatable("tooltip.cosmiccore.stabilizer",
                    String.format("%.0f", ControllerBlockEntity.stabilizerPoints(stab))).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    private CosmicCoreClient() {}
}
