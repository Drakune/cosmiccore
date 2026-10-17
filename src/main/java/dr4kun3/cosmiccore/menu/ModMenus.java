package dr4kun3.cosmiccore.menu;

import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CosmicCore.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> CONTROLLER =
            MENUS.register("controller", () -> IMenuTypeExtension.create(ControllerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ItemPortMenu>> ITEM_PORT =
            MENUS.register("item_port", () -> IMenuTypeExtension.create(ItemPortMenu::new));

    /** Nur statische Registrierungen - keine Instanzen. */
    private ModMenus() {}
}
