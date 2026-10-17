package dr4kun3.cosmiccore.entity;

import dr4kun3.cosmiccore.CoreConfig;
import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import dr4kun3.cosmiccore.block.entity.ControllerBlockEntity;
import dr4kun3.cosmiccore.multiblock.ReactorRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Laesst nachts (Overworld) und im End jederzeit Asteroiden ueber gebildete Reaktoren hinwegziehen - aber nur, wenn
 * ein Spieler in der Naehe ist und kein aktives Anti-Asteroiden-Modul im Umkreis steht.
 */
@EventBusSubscriber(modid = CosmicCore.MODID)
public final class AsteroidSpawner {
    private static final int MAX_NEARBY = 3;

    /**
     * Alle 5 Sekunden: nachts in der Oberwelt (und im End) laesst ueber Reaktoren gelegentlich Asteroiden
     * vorbeiziehen - nicht in Bereichen mit Anti-Asteroiden-Modul.
     *
     * @param event Ende eines Welt-Ticks
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!CoreConfig.ASTEROIDS.get() || level.getGameTime() % 100 != 0) return;
        boolean end = level.dimension() == Level.END;
        boolean night = level.dimension() == Level.OVERWORLD && !level.isDay();
        if (!end && !night) return;

        List<ControllerBlockEntity> reactors = new ArrayList<>();
        List<BlockPos> shields = new ArrayList<>();
        for (BlockPos pos : ReactorRegistry.controllers(level)) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof ControllerBlockEntity c)) continue;
            if (c.isAsteroidShieldActive()) shields.add(pos);
            if (c.isFormed()) reactors.add(c);
        }
        if (reactors.isEmpty()) return;

        RandomSource r = level.random;
        double playerRange = CoreConfig.REACTOR_ASTEROID_RADIUS.get();
        double shieldRange = CoreConfig.ANTI_ASTEROID_RADIUS.get();
        for (ControllerBlockEntity c : reactors) {
            BlockPos pos = c.getBlockPos();
            Vec3 center = Vec3.atCenterOf(pos);
            if (shielded(pos, shields, shieldRange)) continue;
            Player player = level.getNearestPlayer(center.x, center.y, center.z, playerRange, p -> !p.isSpectator());
            if (player == null) continue;
            if (!end && !c.hasSkyLink()) continue;
            if (r.nextDouble() > CoreConfig.ASTEROID_CHANCE.get()) continue;
            AABB area = new AABB(center, center).inflate(160, 128, 160);
            if (level.getEntitiesOfClass(CosmicAsteroid.class, area).size() >= MAX_NEARBY) continue;
            spawnOver(level, center, shields, shieldRange, r);
        }
    }

    /**
     * Liegt eine Position im Schutzbereich eines Anti-Asteroiden-Moduls?
     *
     * @param pos zu pruefende Position
     * @param shields Positionen der geschuetzten Reaktoren
     * @param range Schutzradius in Bloecken
     * @return {@code true}, wenn geschuetzt
     */
    private static boolean shielded(BlockPos pos, List<BlockPos> shields, double range) {
        for (BlockPos s : shields) {
            if (s.distSqr(pos) <= range * range) return true;
        }
        return false;
    }

    /**
     * Erzeugt einen Asteroiden mit Flugbahn quer ueber den Reaktor hinweg (leicht versetzt); Start ausserhalb
     * geschuetzter Bereiche.
     *
     * @param level Server-Welt
     * @param reactor Mitte des Reaktors
     * @param shields Positionen geschuetzter Reaktoren
     * @param shieldRange Schutzradius
     * @param r Zufallsquelle
     */
    private static void spawnOver(ServerLevel level, Vec3 reactor, List<BlockPos> shields, double shieldRange, RandomSource r) {
        double ang = r.nextDouble() * Math.PI * 2;
        double dist = 70 + r.nextDouble() * 40;
        double y = Mth.clamp(reactor.y + 40 + r.nextDouble() * 35, level.getMinY() + 20, level.getMaxY() - 10);
        Vec3 start = new Vec3(reactor.x + Math.cos(ang) * dist, y, reactor.z + Math.sin(ang) * dist);
        if (shielded(BlockPos.containing(start), shields, shieldRange)) return;
        Vec3 target = new Vec3(reactor.x + (r.nextDouble() - 0.5) * 40, y + (r.nextDouble() - 0.5) * 10,
                reactor.z + (r.nextDouble() - 0.5) * 40);
        CosmicAsteroid.Kind kind = pick(r);
        Vec3 vel = target.subtract(start).normalize().scale(kind.speed);

        CosmicAsteroid a = ModEntities.ASTEROIDS.get(kind).get().create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if (a == null || !level.isLoaded(BlockPos.containing(start))) return;
        a.markNatural();
        a.moveTo(start.x, start.y, start.z, r.nextFloat() * 360, 0);
        a.setDeltaMovement(vel);
        level.addFreshEntity(a);
    }

    /**
     * Waehlt eine Asteroidengroesse nach Gewicht (klein haeufig, sehr gross selten).
     *
     * @param r Zufallsquelle
     * @return die gewaehlte Groesse
     */
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

    /** Nur ein Ereignis-Abonnent - keine Instanzen. */
    private AsteroidSpawner() {}
}
