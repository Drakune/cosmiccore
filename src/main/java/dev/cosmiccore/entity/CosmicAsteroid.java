package dev.cosmiccore.entity;

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
 * anvisieren und abbauen - je groesser, desto laenger dauert es und desto mehr Energie bringt er.
 * Wird nicht gespeichert und verschwindet nach einer Weile oder bei Tagesanbruch.
 */
public class CosmicAsteroid extends Entity {
    public static final int SMALL = 0, MEDIUM = 1, LARGE = 2;
    private static final float[] SIZE = {1.4F, 2.6F, 4.2F};
    private static final int[] ENERGY = {50_000, 250_000, 1_000_000};
    private static final int[] HARVEST_TICKS = {40, 90, 180};
    public static final int MAX_LIFE = 1200;

    private static final EntityDataAccessor<Integer> DATA_SIZE = SynchedEntityData.defineId(CosmicAsteroid.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HARVEST = SynchedEntityData.defineId(CosmicAsteroid.class, EntityDataSerializers.INT);

    private int life;
    private int lastHit;

    public CosmicAsteroid(EntityType<? extends CosmicAsteroid> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SIZE, SMALL);
        builder.define(DATA_HARVEST, 0);
    }

    public int getVariant() {
        return Math.max(SMALL, Math.min(LARGE, entityData.get(DATA_SIZE)));
    }

    public void setVariant(int variant) {
        entityData.set(DATA_SIZE, variant);
        refreshDimensions();
    }

    /** Abbaufortschritt 0..1. */
    public float getHarvest() {
        return entityData.get(DATA_HARVEST) / 1000.0F;
    }

    public float getSizeBlocks() {
        return SIZE[getVariant()];
    }

    public int getEnergy() {
        return ENERGY[getVariant()];
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SIZE.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float s = SIZE[getVariant()];
        return EntityDimensions.scalable(s, s);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
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
        // unter Beschuss bleibt der Asteroid am Himmel, bis er zerbricht
        if (tickCount - lastHit < 20) return;
        // ohne Laser regeneriert sich der Fortschritt langsam
        if (tickCount - lastHit > 40 && entityData.get(DATA_HARVEST) > 0) {
            entityData.set(DATA_HARVEST, Math.max(0, entityData.get(DATA_HARVEST) - 5));
        }
        boolean day = level().dimension() == Level.OVERWORLD && level().isDay();
        if (life > MAX_LIFE || (day && life > 40)) discard();
    }

    /**
     * Ein Tick Laserbeschuss (Server). Liefert die gewonnene Energie, sobald der Asteroid zerbricht, sonst 0.
     */
    public int harvestTick() {
        if (isRemoved()) return 0;
        lastHit = tickCount;
        // der Strahl bremst den Asteroiden ab
        setDeltaMovement(getDeltaMovement().scale(0.94));
        hasImpulse = true;
        int step = Math.max(1, 1000 / HARVEST_TICKS[getVariant()]);
        int progress = Math.min(1000, entityData.get(DATA_HARVEST) + step);
        entityData.set(DATA_HARVEST, progress);
        if (progress < 1000) return 0;
        if (level() instanceof ServerLevel sl) {
            float s = getSizeBlocks();
            Vec3 c = position().add(0, getBbHeight() / 2, 0);
            sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 40 + getVariant() * 40, s * 0.4, s * 0.4, s * 0.4, 0.15);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 80, s * 0.3, s * 0.3, s * 0.3, 0.3);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.AMBIENT, 2.0F, 0.6F);
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
        setVariant(tag.getInt("variant"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("variant", getVariant());
    }
}
