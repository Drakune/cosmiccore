package dr4kun3.cosmiccore.block.entity;

import dr4kun3.cosmiccore.block.CorePhase;
import dr4kun3.cosmiccore.block.ModProperties;
import dr4kun3.cosmiccore.color.CoreColors;
import dr4kun3.cosmiccore.data.NbtBridge;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Träger der Animation: Der Renderer und die Partikel hängen hier dran, die Logik sitzt im Controller.
 * Der Controller schiebt die für die Darstellung nötigen Werte (Größe, Stufe, Instabilität,
 * Himmelsverbindung) hierher; sie werden an die Clients synchronisiert.
 */
public class SingularityCoreBlockEntity extends BlockEntity {
    private int structureSize = 7;
    /** Nur der zentrale Kern zeichnet den Reaktor; Satellitenkerne des ultimativen Kerns bleiben unsichtbar. */
    private boolean primary;
    /** Verschmolzener Mittelkern des ultimativen Kerns: vom Controller gesetzt, droppt nichts. */
    private boolean fused;
    /** Ultimativer Kern: ist der Void-Titan erwacht, fällt sein Strahl dauerhaft in den Kern. */
    private boolean awakened;

    public boolean isAwakened() {
        return awakened;
    }

    /**
     * Merkt sich, ob der Titan gerufen wurde. Änderungen gehen sofort an die Clients.
     *
     * @param awakened neuer Wert
     */
    public void setAwakened(boolean awakened) {
        if (awakened == this.awakened) return;
        this.awakened = awakened;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Farbsatz vom Farb-Modul des Controllers. */
    private CoreColors.Config colors = CoreColors.DEFAULT;

    public CoreColors.Config getColors() {
        return colors;
    }

    /**
     * Setzt den Farbsatz. Änderungen gehen sofort an die Clients.
     *
     * @param c neuer Farbsatz
     */
    public void setColors(CoreColors.Config c) {
        if (c.equals(colors)) return;
        colors = c;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public boolean isFused() {
        return fused;
    }

    /**
     * Markiert den Kern als verschmolzenen Mittelkern.
     *
     * @param fused neuer Wert
     */
    public void setFused(boolean fused) {
        this.fused = fused;
        setChanged();
    }
    private int scale = 1;
    private int maxScale = 1;
    private int instability;      // 0..100 (gerundet, damit nicht jeder Tick ein Paket auslöst)
    private boolean skyLink;
    private int cosmic = 100;     // kosmischer Faktor in Prozent
    private int shield;           // Stärke des Eindämmungsschilds 0..100 (Stabilisator-Vorrat)
    private boolean funnel;       // Sternentrichter auf der Decke
    private boolean laserArray;   // vier Kosmos-Laser bilden das große Geschütz
    private int laserTarget = -1; // Entity-ID des anvisierten Asteroiden
    private long targetSince;     // nur clientseitig: seit wann das aktuelle Ziel erfasst ist
    private int overdrive;        // installierte Overdrive-Stufen (verwandeln das Loch)
    private int output;           // aktuelle RF/t (für die Konsole)
    private int energyPct;        // Pufferfüllstand 0..100 (für die Konsole)
    private int meltdown;         // Sekunden bis zum Kollaps (0 = kein Countdown)

    /** Spielzeit, zu der die Struktur gebildet wurde (vom Controller gesetzt, startet die Verwandlungs-Animation). */
    private long formStart = Long.MIN_VALUE;
    /** Spielzeit, zu der ein abgeschossener Asteroid sein Energiepartikel abgegeben hat (Übergabe-Animation). */
    private long deliverStart = Long.MIN_VALUE;
    /** Explosionsort der laufenden Übergabe relativ zur Kernmitte (vom Server festgehalten). */
    private Vec3 deliverSource;
    private float deliverSize = 2.0F; // sichtbarer Durchmesser des zerbrochenen Asteroiden
    public static final int DELIVER_TICKS = 110;
    /** Sternen-Trichter am Himmel: Höhe über der Öffnung und Radius am oberen Rand (Trompetenform). */
    public static final float FUNNEL_HEIGHT = 34.0F;
    public static final float FUNNEL_TOP_RADIUS = 5.0F;

    /**
     * Radius der Trompetenform des Sternentrichters in einer Höhe (unten eng, oben weit).
     *
     * @param h Höhe über dem Hals in Blöcken
     * @return Radius in Blöcken
     */
    public static float funnelRadius(float h) {
        float f = Math.max(0.0F, Math.min(1.0F, h / FUNNEL_HEIGHT));
        return 0.3F + FUNNEL_TOP_RADIUS * f * f * f;
    }
    /** Zeitplan der Übergabe (Anteile von DELIVER_TICKS), gemeinsam für Animation und Klänge. */
    public static final int FRAGMENTS = 24;
    public static final float FRAGMENT_TRAVEL = 0.36F;
    public static final float SINK_START = 0.8F;

    /**
     * Startzeit eines Bruchstücks bei der Übergabe.
     *
     * @param k Nummer des Bruchstücks
     * @return Zeitpunkt als Anteil der Übergabe 0..1
     */
    public static float fragmentDelay(int k) {
        return 0.05F + k / (float) FRAGMENTS * 0.4F;
    }

    /**
     * Zeitpunkt, an dem ein Bruchstück in der Trichteröffnung ankommt (für passende Klänge).
     *
     * @param k Nummer des Bruchstücks
     * @return Zeitpunkt als Anteil der Übergabe 0..1
     */
    public static float fragmentArrival(int k) {
        return fragmentDelay(k) + FRAGMENT_TRAVEL;
    }
    /** nur clientseitig: Zünden des Kerns (Sterne rasen in die Mitte). */
    private CorePhase lastClientPhase;
    private long igniteStart = Long.MIN_VALUE;
    public static final int IGNITE_TICKS = 42;
    /** nur clientseitig: letzte Position des Laserziels relativ zum Kern (Quelle der Bruchstücke). */
    @Nullable
    public Vec3 lastLaserTarget;

    /**
     * Legt den Blockinhalt des Kerns an.
     *
     * @param pos Position
     * @param state Blockzustand
     */
    public SingularityCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CORE.get(), pos, state);
    }

    // ------------------------------------------------------------------ vom Controller gesetzt

    /**
     * Übernimmt den sichtbaren Zustand vom Controller und schickt ihn nur bei echten Änderungen an die Clients
     * (Werte sind gerundet, damit nicht jeder Tick ein Paket auslöst).
     *
     * @param structureSize Kantenlänge der Struktur
     * @param scale Leistungsstufe
     * @param maxScale höchste Leistungsstufe
     * @param instability Instabilität in Prozent
     * @param skyLink freie Sicht zum Himmel
     * @param cosmic kosmischer Faktor
     * @param shield Stabilisator-Vorrat in Prozent
     * @param funnel Sternentrichter vorhanden
     * @param laserArray Kosmos-Laser vorhanden
     * @param laserTarget Entity-ID des anvisierten Asteroiden oder -1
     * @param overdrive Overdrive-Stufe
     * @param output aktuelle Leistung in RF je Tick
     * @param energyPct Füllstand des Puffers in Prozent
     * @param meltdown Sekunden bis zum Kollaps (0 = kein Countdown)
     */
    public void setVisuals(int structureSize, int scale, int maxScale, float instability, boolean skyLink, double cosmic,
                           int shield, boolean funnel, boolean laserArray, int laserTarget, int overdrive,
                           int output, int energyPct, int meltdown) {
        int inst = Math.round(instability);
        int cos = (int) Math.round(cosmic * 100.0);
        shield = Math.max(0, Math.min(100, shield));
        boolean wasPrimary = primary;
        primary = true;
        if (wasPrimary && structureSize == this.structureSize && scale == this.scale && maxScale == this.maxScale
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
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Fortschritt des Zünd-Effekts.
     *
     * @param partialTick Teil-Tick
     * @return 0..1 oder -1, wenn keiner läuft
     */
    public float getIgnitePhase(float partialTick) {
        if (level == null || igniteStart == Long.MIN_VALUE) return -1.0F;
        float ph = (level.getGameTime() - igniteStart + partialTick) / IGNITE_TICKS;
        return ph < 0.0F || ph > 1.0F ? -1.0F : ph;
    }

    /**
     * Vom Controller aufgerufen, wenn der Laser einen Asteroiden zerstört hat: startet die Übergabe-Animation.
     *
     * @param gameTime Spielzeit des Abschusses
     * @param source Explosionsort in Weltkoordinaten
     * @param size Größe des Asteroiden
     */
    public void markDelivery(long gameTime, Vec3 source, float size) {
        deliverStart = gameTime;
        deliverSize = size;
        deliverSource = source.subtract(Vec3.atCenterOf(worldPosition));
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public float getDeliverSize() {
        return deliverSize;
    }

    public Vec3 getDeliverSource() {
        return deliverSource;
    }

    /**
     * Fortschritt der Übergabe.
     *
     * @param partialTick Teil-Tick
     * @return 0..1 oder -1, wenn keine läuft
     */
    public float getDeliverPhase(float partialTick) {
        if (level == null || deliverStart == Long.MIN_VALUE) return -1.0F;
        float ph = (level.getGameTime() - deliverStart + partialTick) / DELIVER_TICKS;
        return ph < 0.0F || ph > 1.0F ? -1.0F : ph;
    }

    /**
     * Vom Controller beim Bilden der Struktur aufgerufen: startet die Verwandlungs-Animation.
     *
     * @param gameTime Spielzeit des Bildens
     */
    public void markFormed(long gameTime) {
        formStart = gameTime;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public boolean isPrimary() {
        return primary;
    }

    public int getStructureSize() {
        return structureSize;
    }

    /**
     * Abstand vom Kern zur Innenfläche der Achsen-Spulen und Kristalle.
     *
     * @return Blöcke
     */
    public float getReach() {
        int half = structureSize / 2;
        int focus = structureSize <= 5 ? half : half - 1;
        return focus - 0.5F;
    }

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

    /**
     * Aufladefortschritt seit der Zielerfassung (1 = Strahl feuert).
     *
     * @param partialTick Teil-Tick
     * @return 0..1
     */
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

    /**
     * Fortschritt der Verwandlung beim Bilden.
     *
     * @param partialTick Teil-Tick
     * @return 0..1 (1 = fertig bzw. keine Animation)
     */
    public float getFormProgress(float partialTick) {
        if (level == null || formStart == Long.MIN_VALUE) return 1.0F;
        float p = (level.getGameTime() - formStart + partialTick) / (float) FORM_TICKS;
        return Math.min(1.0F, Math.max(0.0F, p));
    }

    public static final int FORM_TICKS = 110;
    /** Nach dem Formen: so lange materialisieren sich Konsole und Ports (Ticks). */
    public static final int SETTLE_TICKS = 30;

    /**
     * Materialisieren von Konsole und Ports nach dem Formen.
     *
     * @param partialTick Teil-Tick
     * @return 0..1 (1 = fertig bzw. beim Laden schon gebildet)
     */
    public float getSettle(float partialTick) {
        if (level == null || formStart == Long.MIN_VALUE) return 1.0F;
        float s = (level.getGameTime() - formStart + partialTick - FORM_TICKS) / (float) SETTLE_TICKS;
        return Math.min(1.0F, Math.max(0.0F, s));
    }

    /**
     * Überschwingende Einblendung (wächst kurz über 1 hinaus und federt zurück).
     *
     * @param x Fortschritt 0..1
     * @return Größe (kurz über 1)
     */
    public static float easeOutBack(float x) {
        float c1 = 1.70158F, c3 = c1 + 1.0F, d = x - 1.0F;
        return 1.0F + c3 * d * d * d + c1 * d * d;
    }

    /**
     * Client: erkennt das Zünden, spielt Klänge der Übergabe ab und erzeugt Partikel je nach Phase.
     *
     * @param level Client-Welt
     * @param pos Position
     * @param state Blockzustand
     * @param be der Kern
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state, SingularityCoreBlockEntity be) {
        CorePhase phase = state.getValue(ModProperties.PHASE);
        // Zünden erkannt (bereit -> aktiv): Sterne rasen in die Mitte (nicht beim bloßen Laden des Chunks)
        // (höchstens einmal pro Minute - z. B. nicht bei jedem Wiederanlaufen nach vollem Puffer)
        if (be.lastClientPhase == CorePhase.FORMED && phase == CorePhase.ACTIVE
                && (be.igniteStart == Long.MIN_VALUE || level.getGameTime() - be.igniteStart > 1200)) {
            be.igniteStart = level.getGameTime();
        }
        be.lastClientPhase = phase;
        if (phase == CorePhase.DORMANT || !be.primary) return;
        float form = be.getFormProgress(0.0F);
        if (form < 0.55F) {
            // Sog: Teilchen strömen aus der ganzen Struktur in den Kern
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

        if (phase == CorePhase.UNSTABLE && r.nextInt(2) == 0) {
            double s = reach * 1.6;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, cx + (r.nextDouble() - 0.5) * s, cy + (r.nextDouble() - 0.5) * s,
                    cz + (r.nextDouble() - 0.5) * s, (r.nextDouble() - 0.5) * 0.2, (r.nextDouble() - 0.5) * 0.2, (r.nextDouble() - 0.5) * 0.2);
        }
        // kurz vor dem Kollaps: Flammenzungen schießen aus dem Kern
        if (running && be.instability >= 85 && r.nextInt(3) == 0) {
            double dx = r.nextGaussian(), dy = r.nextGaussian(), dz = r.nextGaussian();
            double len = Math.max(0.001, Math.sqrt(dx * dx + dy * dy + dz * dz));
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, cx, cy, cz, dx / len * 0.25, dy / len * 0.25, dz / len * 0.25);
        }

        // Verbindung zum Kosmos: Sternenlicht fällt vom Himmel durch den Strahl in den Reaktor
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

    /**
     * Speichert Rolle und Darstellungswerte.
     *
     * @param tag Ziel der Daten
     */
    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        CompoundTag visuals = new CompoundTag();
        writeVisuals(visuals);
        NbtBridge.write(tag, visuals);
    }

    /**
     * Lädt Rolle (primär, verschmolzen) und Darstellungswerte.
     *
     * @param input gespeicherte Daten
     */
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        CompoundTag tag = NbtBridge.read(input);
        primary = tag.getBooleanOr("primary", false);
        fused = tag.getBooleanOr("fused", false);
        awakened = tag.getBooleanOr("awakened", false);
        colors = tag.contains("colors")
                ? new CoreColors.Config(tag.getIntOr("colorMask", 0), tag.getIntArray("colors").orElse(new int[0]))
                : CoreColors.DEFAULT;
        if (tag.contains("structureSize")) structureSize = tag.getIntOr("structureSize", 0);
        scale = Math.max(1, tag.getIntOr("scale", 0));
        maxScale = Math.max(1, tag.getIntOr("maxScale", 0));
        instability = tag.getIntOr("instability", 0);
        skyLink = tag.getBooleanOr("skyLink", false);
        cosmic = tag.contains("cosmic") ? tag.getIntOr("cosmic", 0) : 100;
        shield = tag.getIntOr("shield", 0);
        funnel = tag.getBooleanOr("funnel", false);
        laserArray = tag.getBooleanOr("laserArray", false);
        overdrive = tag.getIntOr("overdrive", 0);
        output = tag.getIntOr("output", 0);
        energyPct = tag.getIntOr("energyPct", 0);
        meltdown = tag.getIntOr("meltdown", 0);
        int target = tag.contains("laserTarget") ? tag.getIntOr("laserTarget", 0) : -1;
        if (target != laserTarget && level != null) targetSince = level.getGameTime(); // Aufladen beginnt
        laserTarget = target;
        formStart = tag.contains("formStart") ? tag.getLongOr("formStart", 0L) : Long.MIN_VALUE;
        deliverStart = tag.contains("deliverStart") ? tag.getLongOr("deliverStart", 0L) : Long.MIN_VALUE;
        if (tag.contains("deliverSize")) deliverSize = tag.getFloatOr("deliverSize", 0.0F);
        deliverSource = tag.contains("deliverX")
                ? new Vec3(tag.getDoubleOr("deliverX", 0.0), tag.getDoubleOr("deliverY", 0.0), tag.getDoubleOr("deliverZ", 0.0)) : null;
    }

    /**
     * Schreibt alle sichtbaren Werte (für Speichern und Client).
     *
     * @param tag Ziel der Daten
     */
    private void writeVisuals(CompoundTag tag) {
        tag.putBoolean("primary", primary);
        tag.putBoolean("fused", fused);
        tag.putBoolean("awakened", awakened);
        tag.putInt("colorMask", colors.mask());
        tag.putIntArray("colors", colors.colors());
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
        tag.putFloat("deliverSize", deliverSize);
        if (deliverSource != null) {
            tag.putDouble("deliverX", deliverSource.x);
            tag.putDouble("deliverY", deliverSource.y);
            tag.putDouble("deliverZ", deliverSource.z);
        }
    }

    /**
     * Daten für den Client: der sichtbare Zustand.
     *
     * @param registries Registry-Zugriff
     * @return die Daten
     */
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
