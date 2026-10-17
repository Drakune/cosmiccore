package dr4kun3.cosmiccore.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.neoforged.neoforge.entity.PartEntity;

/** Ein Teil des Void-Titanen (wie beim Enderdrachen): eigene Hitbox, folgt dem Koerper. */
public class VoidTitanPart extends PartEntity<VoidTitanEntity> {
    public final String name;
    private final EntityDimensions size;
    /** Laengsachse des Teils (Neigung/Drehung in Grad) - der Renderer richtet das Segment danach aus. */
    public float pitch, yaw, pitchO, yawO;
    /** Groesse des Teils relativ zum vollen Schweifsegment (der Schweif wird zur Spitze hin duenner). */
    public final float scale;

    /**
     * Legt ein Teil des Titanen an (Schweifsegment), damit Treffer und Sichtbarkeit zu seiner Groesse passen.
     *
     * @param parent der Titan
     * @param name Name des Teils
     * @param width Breite der Hitbox
     * @param height Hoehe der Hitbox
     * @param scale Groesse des Segments relativ zum Titanen
     */
    public VoidTitanPart(VoidTitanEntity parent, String name, float width, float height, float scale) {
        super(parent);
        this.name = name;
        this.size = EntityDimensions.scalable(width, height);
        this.scale = scale;
        refreshDimensions();
    }

    /**
     * Das Teil hat keine eigenen Daten zum Abgleichen.
     *
     * @param builder Bauplan der abgeglichenen Daten
     */
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    /**
     * Nichts zu laden - Teile werden nicht gespeichert.
     *
     * @param tag gespeicherte Daten
     */
    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput tag) {}

    /**
     * Nichts zu speichern - Teile werden nicht gespeichert.
     *
     * @param tag Ziel der Daten
     */
    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput tag) {}

    /**
     * Das Teil kann angeschaut und angeklickt werden.
     *
     * @return immer {@code true}
     */
    @Override
    public boolean isPickable() {
        return true;
    }

    /**
     * Der Titan ist unverwundbar.
     *
     * @param level Server-Welt
     * @param source Schadensquelle
     * @param amount Schadensmenge
     * @return immer {@code false}
     */
    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount) {
        return false; // der Titan ist unverwundbar
    }

    /**
     * Groesse der Hitbox (unabhaengig von der Haltung).
     *
     * @param pose Haltung
     * @return die Abmessungen
     */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    /**
     * Teile entstehen mit dem Titanen neu und werden nie gespeichert.
     *
     * @return immer {@code false}
     */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
