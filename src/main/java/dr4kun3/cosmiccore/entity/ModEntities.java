package dr4kun3.cosmiccore.entity;

import dr4kun3.cosmiccore.CosmicCore;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, CosmicCore.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CosmicAsteroid>> SMALL_ASTEROID = asteroid(CosmicAsteroid.Kind.SMALL);
    public static final DeferredHolder<EntityType<?>, EntityType<CosmicAsteroid>> MEDIUM_ASTEROID = asteroid(CosmicAsteroid.Kind.MEDIUM);
    public static final DeferredHolder<EntityType<?>, EntityType<CosmicAsteroid>> LARGE_ASTEROID = asteroid(CosmicAsteroid.Kind.LARGE);
    public static final DeferredHolder<EntityType<?>, EntityType<CosmicAsteroid>> HUGE_ASTEROID = asteroid(CosmicAsteroid.Kind.HUGE);

    /** Entity-Typ je Asteroiden-Art. */
    public static final Map<CosmicAsteroid.Kind, DeferredHolder<EntityType<?>, EntityType<CosmicAsteroid>>> ASTEROIDS = new EnumMap<>(Map.of(
            CosmicAsteroid.Kind.SMALL, SMALL_ASTEROID, CosmicAsteroid.Kind.MEDIUM, MEDIUM_ASTEROID,
            CosmicAsteroid.Kind.LARGE, LARGE_ASTEROID, CosmicAsteroid.Kind.HUGE, HUGE_ASTEROID));

    private static DeferredHolder<EntityType<?>, EntityType<CosmicAsteroid>> asteroid(CosmicAsteroid.Kind kind) {
        return ENTITIES.register(kind.entityName(),
                () -> EntityType.Builder.<CosmicAsteroid>of((type, level) -> new CosmicAsteroid(type, level, kind), MobCategory.MISC)
                        .sized(kind.size, kind.size)
                        .clientTrackingRange(kind == CosmicAsteroid.Kind.HUGE ? 24 : 16)
                        .updateInterval(1)
                        .fireImmune()
                        .build(kind.entityName()));
    }

    private ModEntities() {}
}
