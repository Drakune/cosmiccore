package dr4kun3.cosmiccore.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.joml.Vector3f;

/**
 * Der Void-Titan: ein kolossaler Waechter aus einem anderen Universum - gepanzerter Oberkoerper mit Kopf und Armen,
 * darunter ein langer Schlangenschweif. Wie der Enderdrache besteht er aus mehreren Teilen (Rumpf, Kopf, Arme und
 * zwoelf Schweifsegmente), jedes mit eigener Hitbox; der Schweif schlaengelt sich und folgt dem Koerper.
 * <p>
 * Er wird nur fuer die Cutscene des Ultimate-Modus clientseitig erzeugt (gezeichnet von GeckoLib, falls vorhanden) und
 * nie gespeichert. Die Entity selbst kennt GeckoLib nicht - so passen Server und Clients mit und ohne GeckoLib zusammen.
 */
public class VoidTitanEntity extends Entity {
    /** Massstab: Bloecke je Modellpixel (1 px = SCALE / 16 Bloecke). */
    public static final float SCALE = 6.0F;
    public static final int TAIL = 12;
    /** Laenge eines vollen Schweifsegments in Modellpixeln. */
    public static final float SEGMENT_PX = 11.0F;

    public final VoidTitanPart body, head, armLeft, armRight;
    public final VoidTitanPart[] tail = new VoidTitanPart[TAIL];
    private final VoidTitanPart[] parts;
    /** Nur Client: Lage animierter Knochen relativ zur Entity (vom Renderer gesetzt, z. B. "eye_l", "orb"). */
    public final java.util.Map<String, Vec3> bones = new java.util.HashMap<>();
    /** Zeit der Cutscene in Ticks (clientseitig gesetzt); negativ = freie Leerlauf-Bewegung. */
    public float scriptTime = -1.0F;

    /**
     * Legt den Titanen samt seiner Teile (Rumpf, Kopf, Arme, Schweifsegmente) an; er schwebt ohne Physik.
     *
     * @param type Entity-Typ
     * @param level Welt
     */
    public VoidTitanEntity(EntityType<? extends VoidTitanEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        float b = SCALE / 16.0F; // Bloecke je Pixel
        body = new VoidTitanPart(this, "body", 22 * b, 30 * b, 1.0F);
        head = new VoidTitanPart(this, "head", 12 * b, 12 * b, 1.0F);
        armLeft = new VoidTitanPart(this, "arm_left", 8 * b, 30 * b, 1.0F);
        armRight = new VoidTitanPart(this, "arm_right", 8 * b, 30 * b, 1.0F);
        for (int i = 0; i < TAIL; i++) {
            float s = 1.0F - 0.55F * i / (TAIL - 1);
            tail[i] = new VoidTitanPart(this, "tail" + i, 10 * b * s, 10 * b * s, s);
        }
        parts = new VoidTitanPart[4 + TAIL];
        parts[0] = body;
        parts[1] = head;
        parts[2] = armLeft;
        parts[3] = armRight;
        System.arraycopy(tail, 0, parts, 4, TAIL);
        updateParts();
    }

    /**
     * Animationszeit: im Film die Drehbuchzeit, sonst die Lebenszeit.
     *
     * @param partialTick Teil-Tick
     * @return Zeit in Ticks
     */
    public float animTime(float partialTick) {
        return scriptTime >= 0 ? scriptTime : tickCount + partialTick;
    }

    /**
     * Vergibt die Kennung und dahinter fortlaufend die der Teile.
     *
     * @param id Kennung des Titanen
     */
    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);
    }

    /**
     * Der Titan besteht aus mehreren Teilen.
     *
     * @return immer {@code true}
     */
    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    /**
     * Die Teile des Titanen.
     *
     * @return Rumpf, Kopf, Arme und Schweifsegmente
     */
    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    /** Jeden Tick die Teile mitbewegen. */
    @Override
    public void tick() {
        super.tick();
        updateParts();
    }

    /**
     * Teile nachfuehren: Rumpf, Kopf und Arme sitzen fest am Koerper, der Schweif bildet eine schlaengelnde S-Kurve,
     * die vom Becken nach hinten unten haengt (die Welle laeuft von der Wurzel zur Spitze).
     */
    public void updateParts() {
        float t = scriptTime >= 0 ? scriptTime : tickCount;
        float yawRad = getYRot() * Mth.DEG_TO_RAD;
        float b = SCALE / 16.0F;
        place(body, new Vector3f(0, 2 * b, 0), yawRad);
        place(head, new Vector3f(0, 36 * b, -2 * b), yawRad);
        place(armLeft, new Vector3f(16 * b, 2 * b, 0), yawRad);
        place(armRight, new Vector3f(-16 * b, 2 * b, 0), yawRad);
        // Schweif: zusammengerollt (am Anfang der Cutscene) bis frei schwingend
        float coil = scriptTime >= 0 ? Mth.clamp(1.0F - (scriptTime - 60.0F) / 60.0F, 0.0F, 1.0F) : 0.0F;
        Vector3f p = new Vector3f(0, -2 * b, 0);
        for (int i = 0; i < TAIL; i++) {
            VoidTitanPart part = tail[i];
            float f = i / (float) (TAIL - 1);
            float pitch = 25.0F + 55.0F * f + 22.0F * Mth.sin(t * 0.07F - i * 0.55F) + coil * 70.0F * f;
            float yaw = 26.0F * Mth.sin(t * 0.045F - i * 0.42F) * (0.4F + f) + coil * 140.0F * f;
            Vector3f dir = new Vector3f(0, -1, 0).rotateX(-pitch * Mth.DEG_TO_RAD).rotateY(yaw * Mth.DEG_TO_RAD);
            float len = SEGMENT_PX * b * part.scale;
            Vector3f center = new Vector3f(p).add(new Vector3f(dir).mul(len * 0.5F));
            part.pitchO = part.pitch;
            part.yawO = part.yaw;
            part.pitch = pitch;
            part.yaw = yaw;
            place(part, center, yawRad);
            p.add(new Vector3f(dir).mul(len));
        }
    }

    /**
     * Setzt ein Teil an eine Stelle relativ zum Titanen (mit dessen Drehung) und merkt die alte Lage fuer weiche
     * Bewegung.
     *
     * @param part das Teil
     * @param local Versatz relativ zum Titanen
     * @param yawRad Drehung des Titanen in Bogenmass
     */
    private void place(VoidTitanPart part, Vector3f local, float yawRad) {
        Vector3f w = new Vector3f(local).rotateY(yawRad);
        part.xo = part.getX();
        part.yo = part.getY();
        part.zo = part.getZ();
        part.xOld = part.xo;
        part.yOld = part.yo;
        part.zOld = part.zo;
        part.setPos(getX() + w.x, getY() + w.y - part.getBbHeight() / 2.0, getZ() + w.z);
    }

    /**
     * Keine abgeglichenen Daten - der Film laeuft auf jedem Client selbst.
     *
     * @param builder Bauplan der abgeglichenen Daten
     */
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    /**
     * Nichts zu laden - der Titan wird nie gespeichert.
     *
     * @param tag gespeicherte Daten
     */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    /**
     * Nichts zu speichern - der Titan wird nie gespeichert.
     *
     * @param tag Ziel der Daten
     */
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

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
        return false;
    }

    /**
     * Der Titan existiert nur waehrend des Films.
     *
     * @return immer {@code false}
     */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /**
     * Sehr grosse Sichtweite (bis 1024 Bloecke), er erscheint im All weit entfernt.
     *
     * @param distance quadrierte Entfernung zur Kamera
     * @return {@code true}, wenn er gezeichnet werden soll
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 1024.0 * 1024.0;
    }

    /**
     * Position der Energiekugel vor der Brust - hier beginnt der Strahl.
     *
     * @return Weltkoordinaten
     */
    public Vec3 orbPosition() {
        float b = SCALE / 16.0F;
        Vector3f w = new Vector3f(0, 30 * b, -26 * b).rotateY(getYRot() * Mth.DEG_TO_RAD);
        return position().add(w.x, w.y, w.z);
    }
}
