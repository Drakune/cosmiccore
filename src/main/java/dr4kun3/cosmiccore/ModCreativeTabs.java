package dr4kun3.cosmiccore;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CosmicCore.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.cosmiccore"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ModItems.CONTROLLER.get().getDefaultInstance())
            .displayItems((params, out) -> {
                out.accept(ModItems.GUIDE_BOOK.get());
                out.accept(ModItems.CONTROLLER.get());
                out.accept(ModItems.SINGULARITY_CORE.get());
                out.accept(ModItems.VOID_CASING.get());
                out.accept(ModItems.RUNE_FRAME.get());
                out.accept(ModItems.FOCUS_GLASS.get());
                out.accept(ModItems.RESONANCE_COIL.get());
                out.accept(ModItems.FOCUS_CRYSTAL.get());
                out.accept(ModItems.ENERGY_PORT.get());
                out.accept(ModItems.ITEM_PORT.get());
                out.accept(ModItems.STAR_FUNNEL.get());
                out.accept(ModItems.STAR_DUST.get());
                out.accept(ModItems.COMPRESSED_STAR_DUST.get());
                out.accept(ModItems.NEUTRON_FRAGMENT.get());
                out.accept(ModItems.QUASAR_ESSENCE.get());
                out.accept(ModItems.COSMIC_LASER.get());
                out.accept(ModItems.OVERDRIVE_PARTICLE.get());
                out.accept(ModItems.EFFICIENCY_MODULE.get());
                out.accept(ModItems.STABILITY_MODULE.get());
            }).build());

    private ModCreativeTabs() {}
}
