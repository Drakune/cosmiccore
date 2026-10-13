package dev.cosmiccore.client;

import dev.cosmiccore.CosmicCore;
import dev.cosmiccore.block.entity.ControllerBlockEntity;
import dev.cosmiccore.block.entity.ModBlockEntities;
import dev.cosmiccore.data.CoreFuel;
import dev.cosmiccore.data.CoreStabilizer;
import dev.cosmiccore.data.ModDataMaps;
import dev.cosmiccore.entity.ModEntities;
import dev.cosmiccore.menu.ModMenus;
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
