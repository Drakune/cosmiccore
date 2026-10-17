package dr4kun3.cosmiccore.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.neoforged.neoforge.entity.PartEntity;

/** Ein Teil des Void-Titanen (wie beim Enderdrachen): eigene Hitbox, folgt dem Körper. */
public class VoidTitanPart extends PartEntity<VoidTitanEntity> {
    public final String name;
    private final EntityDimensions size;
    /** Längsachse des Teils (Neigung/Drehung in Grad) - der Renderer richtet das Segment danach aus. */
    public float pitch, yaw, pitchO, yawO;
    /** Größe des Teils relativ zum vollen Schweifsegment (der Schweif wird zur Spitze hin dünner). */
    public final float scale;

    /**
     * Legt ein Teil des Titanen an (Schweifsegment), damit Treffer und Sichtbarkeit zu seiner Größe passen.
     *
     * @param parent der Titan
     * @param name Name des Teils
     * @param width Breite der Hitbox
     * @param height Höhe der Hitbox
     * @param scale Größe des Segments relativ zum Titanen
     */
    public VoidTitanPart(VoidTitanEntity parent, String name, float width, float height, float scale) {
        super(parent);
        this.name = name;
        this.size = EntityDimensions.scalable(width, height);
        this.scale = scale;
        refreshDimensions();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false; // der Titan ist unverwundbar
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
