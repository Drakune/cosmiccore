package dr4kun3.cosmiccore.entity;

import dr4kun3.cosmiccore.CosmicCore;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
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

    /** Void-Titan der Ultimate-Cutscene (mehrteilig, wird nur clientseitig erzeugt, nie gespeichert). */
    public static final DeferredHolder<EntityType<?>, EntityType<VoidTitanEntity>> VOID_TITAN = ENTITIES.register("void_titan",
            () -> EntityType.Builder.<VoidTitanEntity>of(VoidTitanEntity::new, MobCategory.MISC)
                    .sized(8.0F, 12.0F)
                    .clientTrackingRange(32)
                    .fireImmune()
                    .noSummon()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, CosmicCore.id("void_titan"))));

    /** Entity-Typ je Asteroiden-Art. */
    public static final Map<CosmicAsteroid.Kind, DeferredHolder<EntityType<?>, EntityType<CosmicAsteroid>>> ASTEROIDS = new EnumMap<>(Map.of(
            CosmicAsteroid.Kind.SMALL, SMALL_ASTEROID, CosmicAsteroid.Kind.MEDIUM, MEDIUM_ASTEROID,
            CosmicAsteroid.Kind.LARGE, LARGE_ASTEROID, CosmicAsteroid.Kind.HUGE, HUGE_ASTEROID));

    /**
     * Registriert eine Asteroidengröße als eigenen Entity-Typ (Größe, Sichtweite und Aktualisierung passend).
     *
     * @param kind Asteroidengröße
     * @return Halter des registrierten Entity-Typs
     */
    private static DeferredHolder<EntityType<?>, EntityType<CosmicAsteroid>> asteroid(CosmicAsteroid.Kind kind) {
        return ENTITIES.register(kind.entityName(),
                () -> EntityType.Builder.<CosmicAsteroid>of((type, level) -> new CosmicAsteroid(type, level, kind), MobCategory.MISC)
                        .sized(kind.size, kind.size)
                        .clientTrackingRange(kind == CosmicAsteroid.Kind.HUGE ? 24 : 16)
                        .updateInterval(1)
                        .fireImmune()
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, CosmicCore.id(kind.entityName()))));
    }

    /** Nur statische Registrierungen - keine Instanzen. */
    private ModEntities() {}
}
