package dev.cosmiccore.entity;

import dev.cosmiccore.CosmicCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, CosmicCore.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CosmicAsteroid>> ASTEROID = ENTITIES.register("cosmic_asteroid",
            () -> EntityType.Builder.<CosmicAsteroid>of(CosmicAsteroid::new, MobCategory.MISC)
                    .sized(2.0F, 2.0F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build("cosmic_asteroid"));

    private ModEntities() {}
}
