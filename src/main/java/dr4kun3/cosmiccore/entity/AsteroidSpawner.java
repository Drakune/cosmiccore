package dr4kun3.cosmiccore.entity;

import dr4kun3.cosmiccore.CoreConfig;
import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Laesst nachts (Overworld, freier Himmel) und im End jederzeit Asteroiden ueber Spieler hinwegziehen. */
@EventBusSubscriber(modid = CosmicCore.MODID)
public final class AsteroidSpawner {
    private static final int MAX_NEARBY = 3;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!CoreConfig.ASTEROIDS.get() || level.getGameTime() % 100 != 0) return;
        boolean end = level.dimension() == Level.END;
        boolean night = level.dimension() == Level.OVERWORLD && !level.isDay();
        if (!end && !night) return;

        RandomSource r = level.random;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            if (!end && !level.canSeeSky(player.blockPosition().above())) continue;
            if (r.nextDouble() > CoreConfig.ASTEROID_CHANCE.get()) continue;
            AABB area = player.getBoundingBox().inflate(160, 128, 160);
            if (level.getEntitiesOfClass(CosmicAsteroid.class, area).size() >= MAX_NEARBY) continue;
            spawnNear(level, player, r);
        }
    }

    private static void spawnNear(ServerLevel level, ServerPlayer player, RandomSource r) {
        double ang = r.nextDouble() * Math.PI * 2;
        double dist = 70 + r.nextDouble() * 40;
        double y = Mth.clamp(player.getY() + 35 + r.nextDouble() * 35, level.getMinBuildHeight() + 20, level.getMaxBuildHeight() - 10);
        Vec3 start = new Vec3(player.getX() + Math.cos(ang) * dist, y, player.getZ() + Math.sin(ang) * dist);
        // Flugbahn quer ueber den Spieler hinweg (leicht versetzt)
        Vec3 target = new Vec3(player.getX() + (r.nextDouble() - 0.5) * 50, y + (r.nextDouble() - 0.5) * 10,
                player.getZ() + (r.nextDouble() - 0.5) * 50);
        CosmicAsteroid.Kind kind = pick(r);
        Vec3 vel = target.subtract(start).normalize().scale(kind.speed);

        CosmicAsteroid a = ModEntities.ASTEROIDS.get(kind).get().create(level);
        if (a == null || !level.isLoaded(BlockPos.containing(start))) return;
        a.markNatural();
        a.moveTo(start.x, start.y, start.z, r.nextFloat() * 360, 0);
        a.setDeltaMovement(vel);
        level.addFreshEntity(a);
    }

    /** Zufaellige Art nach spawnWeight (klein haeufig, sehr gross selten). */
    private static CosmicAsteroid.Kind pick(RandomSource r) {
        int total = 0;
        for (CosmicAsteroid.Kind k : CosmicAsteroid.Kind.values()) total += k.spawnWeight;
        int roll = r.nextInt(total);
        for (CosmicAsteroid.Kind k : CosmicAsteroid.Kind.values()) {
            roll -= k.spawnWeight;
            if (roll < 0) return k;
        }
        return CosmicAsteroid.Kind.SMALL;
    }

    private AsteroidSpawner() {}
}
