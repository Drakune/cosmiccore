package dr4kun3.cosmiccore.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Kosmischer Asteroid: zieht nachts (im End jederzeit) hoch am Himmel vorbei. Mit dem Kosmos-Laser kann man ihn
 * anvisieren und abbauen - je größer, desto länger dauert es und desto mehr Energie bringt er.
 * Wird nicht gespeichert; natürlich erschienene verschwinden nach einer Weile oder bei Tagesanbruch.
 */
public class CosmicAsteroid extends Entity {
    /**
     * Die vier Asteroiden-Arten - jede ist ein eigener Entity-Typ (z. B. {@code /summon cosmiccore:huge_asteroid}).
     *
     * @param size          Durchmesser in Blöcken
     * @param energy        gewonnene RF beim Zerbrechen
     * @param pullTicks     Dauer des Heranziehens durch den Traktorstrahl
     * @param overloadTicks Dauer der Überladung am Haltepunkt
     * @param speed         Fluggeschwindigkeit am Himmel (Blöcke pro Tick)
     * @param spawnWeight   Häufigkeit beim natürlichen Erscheinen
     */
    public enum Kind {
        SMALL("small", 2.0F, 50_000, 70, 50, 0.32, 55),
        MEDIUM("medium", 3.6F, 250_000, 110, 60, 0.22, 27),
        LARGE("large", 5.6F, 1_000_000, 160, 75, 0.14, 15),
        HUGE("huge", 11.0F, 4_000_000, 240, 110, 0.08, 3);

        public final String id;
        public final float size;
        public final int energy, pullTicks, overloadTicks, spawnWeight;
        public final double speed;

        /**
         * Legt eine Asteroidengröße an.
         *
         * @param id Name (z. B. {@code huge})
         * @param size Durchmesser in Blöcken
         * @param energy gewonnene Energie in RF
         * @param pullTicks Dauer des Heranziehens
         * @param overloadTicks Dauer der Überladung am Haltepunkt
         * @param speed Fluggeschwindigkeit in Blöcken je Tick
         * @param spawnWeight Häufigkeit beim Erscheinen
         */
        Kind(String id, float size, int energy, int pullTicks, int overloadTicks, double speed, int spawnWeight) {
            this.id = id;
            this.size = size;
            this.energy = energy;
            this.pullTicks = pullTicks;
            this.overloadTicks = overloadTicks;
            this.speed = speed;
            this.spawnWeight = spawnWeight;
        }

        /**
         * Registry-Name des Entity-Typs.
         *
         * @return z. B. {@code huge_asteroid}
         */
        public String entityName() {
            return id + "_asteroid";
        }
    }
    /** Anteil des Fortschritts (von 1000), der aufs Heranziehen entfällt; der Rest ist die Überladung. */
    private static final int PULL_PART = 700;
    public static final int MAX_LIFE = 1200;

    private static final EntityDataAccessor<Integer> DATA_HARVEST = SynchedEntityData.defineId(CosmicAsteroid.class, EntityDataSerializers.INT);

    private final Kind kind;
    private int life;
    private boolean natural; // vom Spawner erzeugt: verschwindet bei Tagesanbruch
    private int lastHit = -100;
    private Vec3 pullStart; // Server: Position beim Erfassen durch den Traktorstrahl

    /**
     * Legt einen Asteroiden an; er fliegt ohne Physik und Schwerkraft.
     *
     * @param type Entity-Typ
     * @param level Welt
     * @param kind Größe
     */
    public CosmicAsteroid(EntityType<? extends CosmicAsteroid> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HARVEST, 0);
    }

    public Kind getKind() {
        return kind;
    }

    public int getVariant() {
        return kind.ordinal();
    }

    /** Markiert den Asteroiden als von selbst erschienen (verschwindet nach seiner Flugzeit). */
    public void markNatural() {
        natural = true;
    }

    public float getHarvest() {
        return entityData.get(DATA_HARVEST) / 1000.0F;
    }

    /**
     * Sichtbare Größe: der Traktorstrahl presst den Asteroiden beim Heranziehen zusammen.
     *
     * @return 1..0.7
     */
    public float getPullScale() {
        float h = getPull();
        return 1.0F - 0.3F * h * h * (3.0F - 2.0F * h);
    }

    public float getPull() {
        return Math.min(1.0F, entityData.get(DATA_HARVEST) / (float) PULL_PART);
    }

    public float getOverload() {
        return Math.max(0.0F, (entityData.get(DATA_HARVEST) - PULL_PART) / (float) (1000 - PULL_PART));
    }

    public float getSizeBlocks() {
        return kind.size;
    }

    public int getEnergy() {
        return kind.energy;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(kind.size, kind.size);
    }

    /**
     * Fliegt weiter (solange kein Strahl zieht), zieht auf dem Client einen Staubschweif und verschwindet nach seiner
     * Flugzeit.
     */
    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        // während der Traktorstrahl zieht, setzt der Server die Position selbst
        if (level().isClientSide || tickCount - lastHit > 1) setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        if (level().isClientSide) {
            // Staubschweif
            if (random.nextInt(2) == 0) {
                float s = getSizeBlocks() * 0.4F;
                level().addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * s,
                        getY() + getBbHeight() / 2 + (random.nextDouble() - 0.5) * s, getZ() + (random.nextDouble() - 0.5) * s,
                        -v.x * 0.2, -v.y * 0.2, -v.z * 0.2);
            }
            return;
        }
        life++;
        // vom Traktorstrahl erfasst: schwebt still, falls der Strahl kurz abreißt
        if (pullStart != null && tickCount - lastHit > 1 && getDeltaMovement().lengthSqr() > 0) {
            setDeltaMovement(Vec3.ZERO);
            hasImpulse = true;
        }
        // unter Beschuss bleibt der Asteroid am Himmel, bis er zerbricht
        if (tickCount - lastHit < 20) return;
        // ohne Laser regeneriert sich der Fortschritt langsam
        if (pullStart == null && tickCount - lastHit > 40 && entityData.get(DATA_HARVEST) > 0) {
            entityData.set(DATA_HARVEST, Math.max(0, entityData.get(DATA_HARVEST) - 5));
        }
        boolean day = level().dimension() == Level.OVERWORLD && level().isDay();
        // beschworene Asteroiden bleiben, bis sie abgeschossen (oder entladen) werden
        if (natural && (life > MAX_LIFE || (day && life > 40))) discard();
    }

    /**
     * Ein Tick Traktorstrahl (Server): zieht den Asteroiden zum Haltepunkt über dem Trichter und überlädt ihn dort.
     *
     * @param hold Haltepunkt (Mitte über dem Trichter)
     * @param high {@code true}, wenn der Haltepunkt am oberen Trichterrand liegt
     * @return gewonnene Energie in RF, sobald er zerbricht, sonst 0
     */
    public int harvestTick(Vec3 hold, boolean high) {
        if (isRemoved()) return 0;
        lastHit = tickCount;
        if (pullStart == null) pullStart = position();
        int before = entityData.get(DATA_HARVEST);
        int step = before < PULL_PART ? Math.max(1, PULL_PART / kind.pullTicks)
                : Math.max(1, (1000 - PULL_PART) / kind.overloadTicks);
        int progress = Math.min(1000, before + step);
        entityData.set(DATA_HARVEST, progress);
        float f = Math.min(1.0F, progress / (float) PULL_PART);
        float e = f * f * (3.0F - 2.0F * f);
        Vec3 dest = hold.subtract(0, getBbHeight() / 2, 0);
        Vec3 next = pullStart.add(dest.subtract(pullStart).scale(e));
        setDeltaMovement(next.subtract(position()));
        hasImpulse = true;
        setPos(next.x, next.y, next.z);
        if (level() instanceof ServerLevel sl && progress > PULL_PART && progress < 1000) {
            // Überladung: ansteigendes Summen und Knistern
            Vec3 c = position().add(0, getBbHeight() / 2, 0);
            float o = (progress - PULL_PART) / (float) (1000 - PULL_PART);
            float vol = high ? 4.0F : 1.5F;
            if (before <= PULL_PART) {
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, vol, 0.6F);
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, vol, 0.7F);
            }
            int every = o < 0.6F ? 8 : 4;
            if (tickCount % every == 0) {
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, vol, 0.6F + 1.3F * o);
            }
            if (o > 0.4F && random.nextInt(6) == 0) {
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, vol * 0.6F, 1.2F + 0.6F * o);
            }
            if (random.nextInt(3) == 0) {
                float s = getSizeBlocks() * 0.4F;
                sl.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 2, s, s, s, 0.02);
            }
        }
        if (progress < 1000) return 0;
        if (level() instanceof ServerLevel sl) {
            Vec3 c = position().add(0, getBbHeight() / 2, 0);
            float s = getSizeBlocks() * 0.3F;
            sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 40 + getVariant() * 20, s, s, s, 0.2);
            sl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 12, s, s, s, 0.1);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 40, s, s, s, 0.3);
            // Explosion am oberen Trichterrand (hoch oben: größere Hörweite) bzw. knapp über den Lasern
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, high ? 5.0F : 2.0F, 0.6F);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, high ? 3.0F : 0.7F, 1.4F);
        }
        int energy = getEnergy();
        discard();
        return energy;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 400.0 * 400.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        natural = tag.getBoolean("natural");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("natural", natural);
    }
}
