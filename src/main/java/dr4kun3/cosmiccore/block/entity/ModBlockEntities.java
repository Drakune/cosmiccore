package dr4kun3.cosmiccore.block.entity;

import dr4kun3.cosmiccore.CosmicCore;
import dr4kun3.cosmiccore.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CosmicCore.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControllerBlockEntity>> CONTROLLER =
            BLOCK_ENTITIES.register("controller",
                    () -> new BlockEntityType<>(ControllerBlockEntity::new, ModBlocks.CONTROLLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SingularityCoreBlockEntity>> CORE =
            BLOCK_ENTITIES.register("singularity_core",
                    () -> new BlockEntityType<>(SingularityCoreBlockEntity::new, ModBlocks.SINGULARITY_CORE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyPortBlockEntity>> ENERGY_PORT =
            BLOCK_ENTITIES.register("energy_port",
                    () -> new BlockEntityType<>(EnergyPortBlockEntity::new, ModBlocks.ENERGY_PORT.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItemPortBlockEntity>> ITEM_PORT =
            BLOCK_ENTITIES.register("item_port",
                    () -> new BlockEntityType<>(ItemPortBlockEntity::new, ModBlocks.ITEM_PORT.get()));

    /** Gemeinsamer Typ fuer Resonanzspule und Fokuskristall (nur fuer die Animation). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResonatorBlockEntity>> RESONATOR =
            BLOCK_ENTITIES.register("resonator",
                    () -> new BlockEntityType<>(ResonatorBlockEntity::new,
                            ModBlocks.RESONANCE_COIL.get(), ModBlocks.FOCUS_CRYSTAL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StarFunnelBlockEntity>> STAR_FUNNEL =
            BLOCK_ENTITIES.register("star_funnel",
                    () -> new BlockEntityType<>(StarFunnelBlockEntity::new, ModBlocks.STAR_FUNNEL.get()));

    /** Nur statische Registrierungen - keine Instanzen. */
    private ModBlockEntities() {}
}
