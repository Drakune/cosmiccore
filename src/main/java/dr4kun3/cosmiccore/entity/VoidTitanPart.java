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
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput tag) {}

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput tag) {}

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount) {
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
