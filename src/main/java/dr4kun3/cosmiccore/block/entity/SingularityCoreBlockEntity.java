package dr4kun3.cosmiccore.block.entity;

import dr4kun3.cosmiccore.block.CorePhase;
import dr4kun3.cosmiccore.block.ModProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Traeger der Animation: Der Renderer und die Partikel haengen hier dran, die Logik sitzt im Controller.
 * Der Controller schiebt die fuer die Darstellung noetigen Werte (Groesse, Stufe, Instabilitaet,
 * Himmelsverbindung) hierher; sie werden an die Clients synchronisiert.
 */
public class SingularityCoreBlockEntity extends BlockEntity {
    private int structureSize = 7;
    private int scale = 1;
    private int maxScale = 1;
    private int instability;      // 0..100 (gerundet, damit nicht jeder Tick ein Paket ausloest)
    private boolean skyLink;
    private int cosmic = 100;     // kosmischer Faktor in Prozent
    private int shield;           // Staerke des Eindaemmungsschilds 0..100 (Stabilisator-Vorrat)
    private boolean funnel;       // Sternentrichter auf der Decke
    private boolean laserArray;   // vier Kosmos-Laser bilden das grosse Geschuetz
    private int laserTarget = -1; // Entity-ID des anvisierten Asteroiden
    private long targetSince;     // nur clientseitig: seit wann das aktuelle Ziel erfasst ist
    private int overdrive;        // installierte Overdrive-Stufen (verwandeln das Loch)
    private int output;           // aktuelle RF/t (fuer die Konsole)
    private int energyPct;        // Pufferfuellstand 0..100 (fuer die Konsole)
    private int meltdown;         // Sekunden bis zum Kollaps (0 = kein Countdown)

    /** Spielzeit, zu der die Struktur gebildet wurde (vom Controller gesetzt, startet die Verwandlungs-Animation). */
    private long formStart = Long.MIN_VALUE;
    /** Spielzeit, zu der ein abgeschossener Asteroid sein Energiepartikel abgegeben hat (Uebergabe-Animation). */
    private long deliverStart = Long.MIN_VALUE;
    public static final int DELIVER_TICKS = 80;
    /** nur clientseitig: Zuenden des Kerns (Sterne rasen in die Mitte). */
    private CorePhase lastClientPhase;
    private long igniteStart = Long.MIN_VALUE;
    public static final int IGNITE_TICKS = 42;
    /** nur clientseitig: letzte Position des Laserziels relativ zum Kern (Quelle der Bruchstuecke). */
    @org.jetbrains.annotations.Nullable
    public net.minecraft.world.phys.Vec3 lastLaserTarget;

    public SingularityCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CORE.get(), pos, state);
    }

    // ------------------------------------------------------------------ vom Controller gesetzt

    public void setVisuals(int structureSize, int scale, int maxScale, float instability, boolean skyLink, double cosmic,
                           int shield, boolean funnel, boolean laserArray, int laserTarget, int overdrive,
                           int output, int energyPct, int meltdown) {
        int inst = Math.round(instability);
        int cos = (int) Math.round(cosmic * 100.0);
        shield = Math.max(0, Math.min(100, shield));
        if (structureSize == this.structureSize && scale == this.scale && maxScale == this.maxScale
                && inst == this.instability && skyLink == this.skyLink && cos == this.cosmic
                && shield / 5 == this.shield / 5 && funnel == this.funnel
                && laserArray == this.laserArray && laserTarget == this.laserTarget && overdrive == this.overdrive
                && energyPct == this.energyPct && Math.abs(output - this.output) <= Math.max(10, this.output / 50)
                && meltdown == this.meltdown) {
            return;
        }
        this.meltdown = meltdown;
        this.output = output;
        this.energyPct = energyPct;
        this.overdrive = overdrive;
        this.laserArray = laserArray;
        this.laserTarget = laserTarget;
        this.shield = shield;
        this.funnel = funnel;
        this.structureSize = structureSize;
        this.scale = scale;
        this.maxScale = maxScale;
        this.instability = inst;
        this.skyLink = skyLink;
        this.cosmic = cos;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Fortschritt des Zuend-Effekts 0..1, oder -1 wenn keiner laeuft. */
    public float getIgnitePhase(float partialTick) {
        if (level == null || igniteStart == Long.MIN_VALUE) return -1.0F;
        float ph = (level.getGameTime() - igniteStart + partialTick) / IGNITE_TICKS;
        return ph < 0.0F || ph > 1.0F ? -1.0F : ph;
    }

    /** Vom Controller aufgerufen, wenn der Laser einen Asteroiden zerstoert hat. */
    public void markDelivery(long gameTime) {
        deliverStart = gameTime;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Fortschritt der Uebergabe 0..1, oder -1 wenn keine laeuft. */
    public float getDeliverPhase(float partialTick) {
        if (level == null || deliverStart == Long.MIN_VALUE) return -1.0F;
        float ph = (level.getGameTime() - deliverStart + partialTick) / DELIVER_TICKS;
        return ph < 0.0F || ph > 1.0F ? -1.0F : ph;
    }

    /** Vom Controller beim Bilden der Struktur aufgerufen. */
    public void markFormed(long gameTime) {
        formStart = gameTime;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public int getStructureSize() {
        return structureSize;
    }

    /** Abstand vom Kern zur Innenflaeche der Achsen-Spulen/Kristalle. */
    public float getReach() {
        int half = structureSize / 2;
        int focus = structureSize <= 5 ? half : half - 1;
        return focus - 0.5F;
    }

    /** 0 bei Stufe 1, 1 bei der hoechsten Stufe der Baugroesse. */
    public float getGrowth() {
        return maxScale <= 1 ? 0.0F : (scale - 1) / (float) (maxScale - 1);
    }

    public int getScale() {
        return scale;
    }

    public float getInstability() {
        return instability;
    }

    public boolean hasSkyLink() {
        return skyLink;
    }

    public float getShield() {
        return shield / 100.0F;
    }

    public int getOutput() {
        return output;
    }

    public int getMeltdown() {
        return meltdown;
    }

    public int getEnergyPct() {
        return energyPct;
    }

    public int getScaleMax() {
        return maxScale;
    }

    public int getOverdrive() {
        return overdrive;
    }

    public boolean hasLaserArray() {
        return laserArray;
    }

    public int getLaserTarget() {
        return laserTarget;
    }

    /** Aufladefortschritt 0..1 seit der Zielerfassung (1 = Strahl feuert). */
    public float getLaserCharge(float partialTick) {
        if (level == null || laserTarget < 0) return 0.0F;
        float c = (level.getGameTime() - targetSince + partialTick) / ControllerBlockEntity.LASER_CHARGE_TICKS;
        return Math.max(0.0F, Math.min(1.0F, c));
    }

    public boolean hasFunnel() {
        return funnel;
    }

    public float getCosmic() {
        return cosmic / 100.0F;
    }

    // ------------------------------------------------------------------ Partikel (Client)

    /** Fortschritt der Verwandlung 0..1 (1 = fertig bzw. keine Animation laufend). */
    public float getFormProgress(float partialTick) {
        if (level == null || formStart == Long.MIN_VALUE) return 1.0F;
        float p = (level.getGameTime() - formStart + partialTick) / (float) FORM_TICKS;
        return Math.min(1.0F, Math.max(0.0F, p));
    }

    public static final int FORM_TICKS = 110;

    public static void clientTick(Level level, BlockPos pos, BlockState state, SingularityCoreBlockEntity be) {
        CorePhase phase = state.getValue(ModProperties.PHASE);
        // Zuenden erkannt (bereit -> aktiv): Sterne rasen in die Mitte (nicht beim blossen Laden des Chunks)
        // (hoechstens einmal pro Minute - z. B. nicht bei jedem Wiederanlaufen nach vollem Puffer)
        if (be.lastClientPhase == CorePhase.FORMED && phase == CorePhase.ACTIVE
                && (be.igniteStart == Long.MIN_VALUE || level.getGameTime() - be.igniteStart > 1200)) {
            be.igniteStart = level.getGameTime();
        }
        be.lastClientPhase = phase;
        if (phase == CorePhase.DORMANT) return;
        float form = be.getFormProgress(0.0F);
        if (form < 0.55F) {
            // Sog: Teilchen stroemen aus der ganzen Struktur in den Kern
            RandomSource rr = level.random;
            int half = be.structureSize / 2;
            for (int i = 0; i < 8; i++) {
                double dx = (rr.nextDouble() - 0.5) * 2 * half, dy = (rr.nextDouble() - 0.5) * 2 * half, dz = (rr.nextDouble() - 0.5) * 2 * half;
                level.addParticle(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, dx, dy, dz);
            }
        } else if (form < 0.6F) {
            RandomSource rr = level.random;
            for (int i = 0; i < 12; i++) {
                level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        (rr.nextDouble() - 0.5) * 0.6, (rr.nextDouble() - 0.5) * 0.6, (rr.nextDouble() - 0.5) * 0.6);
            }
        }
        RandomSource r = level.random;
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        float growth = be.getGrowth();
        float reach = be.getReach();
        boolean running = phase == CorePhase.ACTIVE || phase == CorePhase.UNSTABLE;

        // Partikel die in die Singularitaet gesogen werden (PORTAL startet bei pos+vel und endet bei pos)
        int pulls = switch (phase) {
            case FORMED -> r.nextInt(4) == 0 ? 1 : 0;
            case ACTIVE -> 2 + Math.round(growth * 3);
            default -> 4 + Math.round(growth * 3);
        };
        for (int i = 0; i < pulls; i++) {
            double dx = r.nextGaussian(), dy = r.nextGaussian(), dz = r.nextGaussian();
            double len = Math.max(0.001, Math.sqrt(dx * dx + dy * dy + dz * dz));
            double rad = reach + 0.1 + r.nextDouble() * 0.6;
            level.addParticle(ParticleTypes.PORTAL, cx, cy, cz, dx / len * rad, dy / len * rad, dz / len * rad);
        }

        if (phase == CorePhase.UNSTABLE && r.nextInt(2) == 0) {
            double s = reach * 1.6;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, cx + (r.nextDouble() - 0.5) * s, cy + (r.nextDouble() - 0.5) * s,
                    cz + (r.nextDouble() - 0.5) * s, (r.nextDouble() - 0.5) * 0.2, (r.nextDouble() - 0.5) * 0.2, (r.nextDouble() - 0.5) * 0.2);
        }
        // kurz vor dem Kollaps: Flammenzungen schiessen aus dem Kern
        if (running && be.instability >= 85 && r.nextInt(3) == 0) {
            double dx = r.nextGaussian(), dy = r.nextGaussian(), dz = r.nextGaussian();
            double len = Math.max(0.001, Math.sqrt(dx * dx + dy * dy + dz * dz));
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, cx, cy, cz, dx / len * 0.25, dy / len * 0.25, dz / len * 0.25);
        }

        // Verbindung zum Kosmos: Sternenlicht faellt vom Himmel durch den Strahl in den Reaktor
        if (running && be.skyLink) {
            int falls = 1 + Math.round(be.getCosmic() * 1.5F);
            for (int i = 0; i < falls; i++) {
                if (r.nextInt(2) != 0) continue;
                double h = 12.0 + r.nextDouble() * 28.0;
                double a = r.nextDouble() * Math.PI * 2;
                double d = 0.3 + r.nextDouble() * 1.4;
                level.addParticle(ParticleTypes.END_ROD, cx + Math.cos(a) * d, cy + h, cz + Math.sin(a) * d,
                        -Math.cos(a) * d * 0.01, -0.45 - r.nextDouble() * 0.35, -Math.sin(a) * d * 0.01);
            }
        }
    }

    // ------------------------------------------------------------------ Persistenz / Sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeVisuals(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("structureSize")) structureSize = tag.getInt("structureSize");
        scale = Math.max(1, tag.getInt("scale"));
        maxScale = Math.max(1, tag.getInt("maxScale"));
        instability = tag.getInt("instability");
        skyLink = tag.getBoolean("skyLink");
        cosmic = tag.contains("cosmic") ? tag.getInt("cosmic") : 100;
        shield = tag.getInt("shield");
        funnel = tag.getBoolean("funnel");
        laserArray = tag.getBoolean("laserArray");
        overdrive = tag.getInt("overdrive");
        output = tag.getInt("output");
        energyPct = tag.getInt("energyPct");
        meltdown = tag.getInt("meltdown");
        int target = tag.contains("laserTarget") ? tag.getInt("laserTarget") : -1;
        if (target != laserTarget && level != null) targetSince = level.getGameTime(); // Aufladen beginnt
        laserTarget = target;
        formStart = tag.contains("formStart") ? tag.getLong("formStart") : Long.MIN_VALUE;
        deliverStart = tag.contains("deliverStart") ? tag.getLong("deliverStart") : Long.MIN_VALUE;
    }

    private void writeVisuals(CompoundTag tag) {
        tag.putInt("structureSize", structureSize);
        tag.putInt("scale", scale);
        tag.putInt("maxScale", maxScale);
        tag.putInt("instability", instability);
        tag.putBoolean("skyLink", skyLink);
        tag.putInt("cosmic", cosmic);
        tag.putInt("shield", shield);
        tag.putBoolean("funnel", funnel);
        tag.putBoolean("laserArray", laserArray);
        tag.putInt("overdrive", overdrive);
        tag.putInt("output", output);
        tag.putInt("energyPct", energyPct);
        tag.putInt("meltdown", meltdown);
        tag.putInt("laserTarget", laserTarget);
        tag.putLong("formStart", formStart);
        tag.putLong("deliverStart", deliverStart);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeVisuals(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
