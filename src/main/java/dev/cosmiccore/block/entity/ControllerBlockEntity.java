package dev.cosmiccore.block.entity;

import dev.cosmiccore.CoreConfig;
import dev.cosmiccore.ModItems;
import dev.cosmiccore.block.ControllerBlock;
import dev.cosmiccore.block.CorePhase;
import dev.cosmiccore.block.ModBlocks;
import dev.cosmiccore.block.ModProperties;
import dev.cosmiccore.data.CoreFuel;
import dev.cosmiccore.data.CoreStabilizer;
import dev.cosmiccore.data.ModDataMaps;
import dev.cosmiccore.entity.CosmicAsteroid;
import dev.cosmiccore.menu.ControllerMenu;
import dev.cosmiccore.multiblock.CorePattern;
import dev.cosmiccore.multiblock.ReactorSize;
import java.text.NumberFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Das Gehirn des Kerngenerators: prueft die Struktur, verbrennt Brennstoff, baut Resonanz auf,
 * verwaltet die Instabilitaet und spiegelt den Zustand (CorePhase) auf alle Strukturbloecke.
 * <p>
 * Die Baugroesse (5/7/9) wird einmalig beim Platzieren gewaehlt. Die Leistungsstufe (1..maxScale der
 * Baugroesse) multipliziert Leistung, Brennstoffverbrauch und Instabilitaet: mehr Strom,
 * aber schneller am Limit.
 */
public class ControllerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_FUEL = 0;
    public static final int SLOT_STAB = 1;

    public static final float UNSTABLE_ON = 70f;
    private static final float UNSTABLE_OFF = 55f;
    private static final float ALARM_AT = 85f;
    /** Zeit zwischen 100 % Instabilitaet und dem Kollaps. */
    public static final int MELTDOWN_TICKS = 200;
    /** Das Eindaemmungsfeld haelt die Instabilitaet auf diesem Wert, solange Stabilisatoren vorhanden sind. */
    public static final float FIELD_TARGET = 20f;
    /** Zusaetzliche Kuehlung pro Tick und Prozentpunkt ueber dem Zielwert (zieht die Instabilitaet zurueck). */
    private static final float FIELD_PULL = 0.01f;
    private static final float IDLE_DECAY = 0.05f;      // Abkuehlung pro Tick ohne Verbrennung

    private final CoreInventory inventory = new CoreInventory(this::setChanged);
    private final IItemHandler itemView = new InsertOnlyView();
    private final IEnergyStorage energyView = new EnergyView();
    private final ContainerData dataAccess = new DataAccess();

    /** null = Baugroesse noch nicht gewaehlt (frisch platziert). */
    @Nullable
    private ReactorSize size;
    private int scale = 1;
    private boolean enabled = true;
    private boolean preview;
    /** Bei der Groessenwahl angehakt: Sternentrichter gehoert zur Bauvorschau und zum Kreativ-Aufbau. */
    private boolean withFunnel;
    /** Bei der Groessenwahl angehakt: vier Kosmos-Laser gehoeren zur Bauvorschau und zum Kreativ-Aufbau. */
    private boolean withLasers;
    /** Installierte Overdrive-Partikel: je Stufe Leistung x2, Instabilitaet x3. */
    private int overdrive;

    private boolean formed;
    /** Gespeicherte Energie (long: bei Stufe 10 bis zu 2,5 Mrd. RF). */
    private long energy;
    private int burnTime;
    private int burnTotal;
    private int burnPower;
    /** Verbleibende Kuehlpunkte im Eindaemmungsfeld (aus verbrauchten Stabilisatoren). */
    private float field;
    /** Kuehlpunkte des zuletzt verbrauchten Stabilisators (nur fuer die Anzeige). */
    private float fieldMax;
    private float instability;
    private int resonance;
    private int collapseCooldown;
    private int meltdown;
    private CorePhase phase = CorePhase.DORMANT;

    // nicht gespeichert
    private int checkTimer;
    private int lastOutput;
    private boolean skyLink;
    /** Sternentrichter direkt auf der Deckenmitte: verdoppelt den Himmelsbonus. */
    private boolean funnel;
    /** Vier Kosmos-Laser rund um die Deckenmitte bilden ein Laser-Array, das Asteroiden abschiesst. */
    private boolean laserArray;
    private int laserTarget = -1;
    /** Aufladezeit nach der Zielerfassung (Partikel buendeln sich, erst dann feuert der Strahl). */
    private int laserCharge;
    public static final int LASER_CHARGE_TICKS = 20;
    private double lastCosmic = 1.0;
    @Nullable
    private CorePattern.Report lastReport;

    public ControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTROLLER.get(), pos, state);
    }

    // ------------------------------------------------------------------ API fuer Ports / Capabilities / GUI

    public boolean isFormed() {
        return formed;
    }

    public long getEnergy() {
        return energy;
    }

    @Nullable
    public ReactorSize getSize() {
        return size;
    }

    public boolean isPreview() {
        return preview;
    }

    public boolean isWithFunnel() {
        return withFunnel;
    }

    public boolean isWithLasers() {
        return withLasers;
    }

    /** Kapazitaet waechst mit der Leistungsstufe: Stufe x energyPerLevel (Standard 250 Mio. RF). */
    public long getCapacity() {
        return CoreConfig.ENERGY_PER_LEVEL.get() * Math.max(1, scale);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IEnergyStorage getEnergyView() {
        return energyView;
    }

    public IItemHandler getItemView() {
        return itemView;
    }

    public int extractEnergy(int max, boolean simulate) {
        if (!formed || max <= 0) return 0;
        int out = (int) Math.min(energy, max);
        if (!simulate && out > 0) {
            energy -= out;
            setChanged();
        }
        return out;
    }

    /** Nimmt Energie von aussen auf (z. B. aus dem Kosmos-Laser). Liefert die tatsaechlich aufgenommene Menge. */
    public int receiveCosmicEnergy(int amount) {
        if (!formed || amount <= 0) return 0;
        int accepted = (int) Math.max(0, Math.min(amount, getCapacity() - energy));
        if (accepted > 0) {
            energy += accepted;
            setChanged();
        }
        return accepted;
    }

    public ItemStack insertFromPlayer(ItemStack stack) {
        ItemStack rest = inventory.insertItem(SLOT_FUEL, stack, false);
        return inventory.insertItem(SLOT_STAB, rest, false);
    }

    @Nullable
    private CorePattern pattern() {
        return size == null ? null : size.pattern();
    }

    // ------------------------------------------------------------------ GUI

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cosmiccore.controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
        return new ControllerMenu(id, playerInv, this, dataAccess);
    }

    /** Knopfdruck aus der GUI (serverseitig, ueber den Vanilla-Mechanismus clickMenuButton). */
    public boolean handleButton(Player player, int id) {
        if (!(level instanceof ServerLevel sl)) return false;
        int sizes = ReactorSize.values().length;
        if (id >= ControllerMenu.BTN_SIZE_FIRST && id < ControllerMenu.BTN_SIZE_FIRST + 4 * sizes) {
            if (size != null) return false; // nur einmalig waehlbar
            int index = id - ControllerMenu.BTN_SIZE_FIRST;
            size = ReactorSize.values()[index % sizes];
            withFunnel = (index / sizes) % 2 == 1;
            withLasers = index / (2 * sizes) == 1;
            scale = 1;
            preview = true; // direkt zeigen, wo was hin muss
            checkTimer = 0;
            sl.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.8F);
            changedAndSync();
            return true;
        }
        if (size == null) return false;
        // Leistungsstufe und Overdrive lassen sich erst einstellen, wenn der Generator gezuendet ist
        boolean running = formed && enabled && collapseCooldown <= 0;
        if (!running && (id == ControllerMenu.BTN_SCALE_DOWN || id == ControllerMenu.BTN_SCALE_UP
                || id == ControllerMenu.BTN_OVERDRIVE_UP || id == ControllerMenu.BTN_OVERDRIVE_DOWN)) {
            player.displayClientMessage(Component.translatable("message.cosmiccore.not_running").withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        switch (id) {
            case ControllerMenu.BTN_SCALE_DOWN -> scale = Math.max(1, scale - 1);
            case ControllerMenu.BTN_SCALE_UP -> scale = Math.min(size.maxScale(), scale + 1);
            case ControllerMenu.BTN_TOGGLE -> enabled = !enabled;
            case ControllerMenu.BTN_PREVIEW -> preview = !preview;
            case ControllerMenu.BTN_OVERDRIVE_UP -> {
                if (!formed || overdrive >= size.maxOverdrive()) return false;
                if (!player.getAbilities().instabuild && !consumeParticle(player)) {
                    player.displayClientMessage(Component.translatable("message.cosmiccore.overdrive.missing")
                            .withStyle(ChatFormatting.RED), true);
                    return false;
                }
                overdrive++;
                Vec3 c = Vec3.atCenterOf(center(sl));
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.5F, 0.6F + 0.2F * overdrive);
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 0.6F, 1.6F);
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 200, 1.0, 1.0, 1.0, 0.8);
            }
            case ControllerMenu.BTN_OVERDRIVE_DOWN -> {
                if (overdrive <= 0) return false;
                overdrive--; // das Partikel ist verbraucht und kommt nicht zurueck
                Vec3 c = Vec3.atCenterOf(center(sl));
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5F, 0.5F);
            }
            case ControllerMenu.BTN_BUILD -> {
                if (!player.getAbilities().instabuild) return false;
                buildStructure(sl);
            }
            default -> {
                return false;
            }
        }
        sl.playSound(null, worldPosition, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3F, 1.2F);
        changedAndSync();
        pushVisuals(sl);
        return true;
    }

    /** Nimmt ein Overdrive-Partikel aus dem Inventar des Spielers. */
    private static boolean consumeParticle(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(ModItems.OVERDRIVE_PARTICLE.get())) {
                st.shrink(1);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ Tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, ControllerBlockEntity be) {
        if (level instanceof ServerLevel sl) be.tick(sl);
    }

    private void tick(ServerLevel level) {
        if (size == null) return; // wartet auf die Wahl der Baugroesse
        if (--checkTimer <= 0) {
            checkTimer = CoreConfig.VALIDATION_INTERVAL.get();
            revalidate(level);
        }
        if (!formed) {
            lastOutput = 0;
            return;
        }
        simulate(level);
        if (laserArray) tickLasers(level);
        if (level.getGameTime() % 10 == 0) pushVisuals(level);
    }

    private double outputScale() {
        double sizeFactor = size == null ? 1.0 : size.outputFactor();
        return Math.pow(scale, CoreConfig.SCALE_OUTPUT_EXPONENT.get()) * sizeFactor * Math.pow(2.0, overdrive);
    }

    private double instabilityScale() {
        double sizeFactor = size == null ? 1.0 : size.instabilityFactor();
        return Math.pow(scale, CoreConfig.SCALE_INSTABILITY_EXPONENT.get()) * sizeFactor * Math.pow(3.0, overdrive);
    }

    private void simulate(ServerLevel level) {
        final long capacity = getCapacity();
        final int ramp = CoreConfig.RAMP_UP_TICKS.get();
        boolean burning = false;
        float gain = 0f;
        lastOutput = 0;
        lastCosmic = cosmicOutput(level);

        if (collapseCooldown > 0) {
            collapseCooldown--;
            resonance = 0;
        } else if (enabled) {
            // Brennstoff nachlegen - nur wenn noch Platz im Puffer ist
            if (burnTime <= 0 && energy < capacity) {
                CoreFuel fuel = ModDataMaps.fuel(inventory.getStackInSlot(SLOT_FUEL));
                if (fuel != null) {
                    inventory.extractItem(SLOT_FUEL, 1, false);
                    burnTime = fuel.burnTime();
                    burnTotal = burnTime;
                    burnPower = fuel.power();
                    if (resonance == 0) {
                        Vec3 c = Vec3.atCenterOf(center(level));
                        level.playSound(null, c.x, c.y, c.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.2F, 0.7F);
                    }
                    setChanged();
                }
            }
            if (burnTime > 0 && energy < capacity) {
                burning = true;
                // hoehere Stufe = schnellerer Verbrauch
                burnTime = Math.max(0, burnTime - scale);
                resonance = Math.min(resonance + 1, ramp);
                double factor = 0.2 + 0.8 * resonance / (double) ramp;
                double gen = burnPower * factor * outputScale() * lastCosmic * CoreConfig.OUTPUT_MULTIPLIER.get();
                int out = (int) Math.min(Integer.MAX_VALUE, Math.round(gen));
                energy = Math.min(capacity, energy + out);
                lastOutput = out;
                gain = (float) (CoreConfig.INSTABILITY_RATE.get() * (burnPower / 1000.0)
                        * instabilityScale() * cosmicInstability(level));
                // ab der roten Zone steigt die Instabilitaet gebremst - es bleibt Zeit zu reagieren
                if (instability > UNSTABLE_ON) gain *= 0.4f;
                instability += gain;
                setChanged();
            } else if (burnTime <= 0) {
                resonance = Math.max(0, resonance - 2);
            }
        } else {
            resonance = Math.max(0, resonance - 2);
        }

        // Eindaemmungsfeld: ab dem Zielwert wird genau so viel Kuehlung verbraucht, wie die Hitze verlangt,
        // plus ein Zug zurueck auf den Zielwert. Fehlen die Punkte, wird automatisch der naechste Stabilisator
        // aus dem Slot verbraucht. Hoehere Stufen kosten also mehr Stabilisatoren - gefaehrlich wird es erst,
        // wenn der Vorrat ausgeht.
        if (instability > FIELD_TARGET) {
            float demand = gain + (instability - FIELD_TARGET) * FIELD_PULL;
            while (field < demand && consumeStabilizer()) {
                // weiter nachladen, bis der Bedarf gedeckt ist oder der Slot leer ist
            }
            float used = Math.min(field, demand);
            field -= used;
            instability -= used;
            if (used > 0) setChanged();
        }
        if (!burning) instability -= IDLE_DECAY;
        instability = Mth.clamp(instability, 0f, 100f);

        // Kollaps-Countdown: bei 100 % bleiben MELTDOWN_TICKS, um den Kern noch zu retten (unter 95 % = abgewendet)
        if (instability >= 100f) {
            meltdown++;
            if (meltdown == 1 || meltdown % 20 == 0) {
                Vec3 c = Vec3.atCenterOf(center(level));
                level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 3.0F, 1.2F);
                for (Player pl : level.getEntitiesOfClass(Player.class, new AABB(c, c).inflate(32))) {
                    pl.displayClientMessage(Component.translatable("message.cosmiccore.meltdown",
                            (MELTDOWN_TICKS - meltdown) / 20 + 1).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                }
            }
            if (meltdown >= MELTDOWN_TICKS) {
                meltdown = 0;
                collapse(level);
                return;
            }
        } else if (meltdown > 0 && instability < 95f) {
            meltdown = 0; // Kollaps abgewendet
        }

        // Phase bestimmen (mit Hysterese, damit die Bloecke nicht flackern)
        // voller Puffer: der Reaktor pausiert (kein Verbrauch) und wird auch so dargestellt
        boolean active = enabled && burnTime > 0 && collapseCooldown <= 0 && energy < capacity;
        CorePhase target = CorePhase.FORMED;
        if (active) {
            boolean unstable = instability >= UNSTABLE_ON || (phase == CorePhase.UNSTABLE && instability >= UNSTABLE_OFF);
            target = unstable ? CorePhase.UNSTABLE : CorePhase.ACTIVE;
        }
        if (target != phase) {
            phase = target;
            applyPhase(level);
            setChanged();
        }

        // Atmosphaere
        if (phase == CorePhase.ACTIVE || phase == CorePhase.UNSTABLE) {
            long t = level.getGameTime();
            Vec3 c = Vec3.atCenterOf(center(level));
            if (t % 100 == 0) {
                level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.6F, 0.6F);
            }
            if (t % 47 == 0) {
                level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F,
                        0.5F + level.random.nextFloat() * 0.4F);
            }
            // Alarm: Herzschlag wird schneller, je naeher der Kollaps ist
            if (instability >= ALARM_AT) {
                int period = instability >= 95f ? 8 : instability >= 90f ? 14 : 20;
                if (t % period == 0) {
                    level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 2.0F, 0.8F);
                }
            }
        }
    }

    /** Verbraucht einen Stabilisator aus dem Slot und laedt das Feld. Groessere Reaktoren nutzen ihn besser. */
    private boolean consumeStabilizer() {
        CoreStabilizer stab = ModDataMaps.stabilizer(inventory.getStackInSlot(SLOT_STAB));
        if (stab == null || size == null) return false;
        inventory.extractItem(SLOT_STAB, 1, false);
        float points = (float) (stabilizerPoints(stab) * size.stabilizerFactor());
        field += points;
        fieldMax = Math.max(points, field);
        return true;
    }

    /** Kuehlpunkte eines Stabilisators (vor dem Groessenfaktor): Wirkdauer x Staerke aus der Data Map. */
    public static double stabilizerPoints(CoreStabilizer stab) {
        return stab.duration() * (double) stab.strength() * CoreConfig.STABILIZER_MULTIPLIER.get();
    }

    private void collapse(ServerLevel level) {
        BlockPos core = center(level);
        Vec3 c = Vec3.atCenterOf(core);
        int structure = size == null ? 7 : size.size();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 4.0F, 0.6F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4.0F, 0.5F);
        level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y, c.z, 40 + structure * 10, structure * 0.25, structure * 0.25, structure * 0.25, 0.08);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 200, 0.5, 0.5, 0.5, 1.2);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);

        double damage = CoreConfig.COLLAPSE_DAMAGE.get() * (1.0 + 0.25 * (scale - 1));
        double radius = 4.0 + structure * 1.5;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius))) {
            e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 240));
            e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160));
            if (damage > 0) e.hurt(level.damageSources().magic(), (float) damage);
        }

        // Der Kern zerbricht
        if (CoreConfig.COLLAPSE_DESTROYS_CORE.get() && level.getBlockState(core).is(ModBlocks.SINGULARITY_CORE.get())) {
            level.levelEvent(2001, core, Block.getId(level.getBlockState(core)));
            level.setBlock(core, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (CoreConfig.COLLAPSE_DESTROYS_BLOCKS.get()) {
            level.explode(null, c.x, c.y, c.z, 3.0F + structure * 0.5F + scale * 0.25F, Level.ExplosionInteraction.BLOCK);
        }

        burnTime = 0;
        burnTotal = 0;
        burnPower = 0;
        resonance = 0;
        field = 0f;
        instability = 35f;
        collapseCooldown = CoreConfig.COLLAPSE_COOLDOWN.get();
        lastOutput = 0;
        phase = CorePhase.FORMED;
        applyPhase(level);
        setChanged();
        // ohne Kern zerfaellt die Struktur sofort
        revalidate(level);
    }

    // ------------------------------------------------------------------ Laser-Array

    private static final double LASER_RANGE = 200.0;

    /** Schaltet die Modelle der vier Laser um (im Verbund ausgeblendet, das grosse Geschuetz wird gerendert). */
    /** @param top Position ueber der Deckenmitte (dort sitzt ggf. der Sternentrichter, die Laser drumherum). */
    private static void setLasersLinked(ServerLevel level, BlockPos top, boolean linked) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos lp = top.relative(d);
            if (!level.hasChunkAt(lp)) continue;
            BlockState s = level.getBlockState(lp);
            if (s.is(ModBlocks.COSMIC_LASER.get()) && s.getValue(ModProperties.LINKED) != linked) {
                level.setBlock(lp, s.setValue(ModProperties.LINKED, linked), Block.UPDATE_ALL);
            }
        }
    }

    /** Erfasst Asteroiden, laedt auf und schiesst sie mit dem Strahl ab. Die freigesetzte Energie fliesst in den Puffer. */
    private void tickLasers(ServerLevel level) {
        CorePattern p = pattern();
        if (p == null) return;
        // Bahnmitte der Energiepartikel ueber dem Trichter (muss zu EnergyLaserFx.HEIGHT passen)
        Vec3 muzzle = Vec3.atCenterOf(center(level)).add(0, p.half() + 2.2, 0);
        CosmicAsteroid target = laserTarget >= 0 && level.getEntity(laserTarget) instanceof CosmicAsteroid a && a.isAlive()
                && a.position().distanceTo(muzzle) < LASER_RANGE ? a : null;
        if (target == null) {
            if (laserTarget != -1) {
                laserTarget = -1;
                pushVisuals(level);
            }
            if (level.getGameTime() % 20 != 0) return;
            AABB area = new AABB(muzzle, muzzle).inflate(LASER_RANGE);
            double best = Double.MAX_VALUE;
            for (CosmicAsteroid a : level.getEntitiesOfClass(CosmicAsteroid.class, area)) {
                double d = a.position().distanceToSqr(muzzle);
                if (a.getY() > muzzle.y && d < best) {
                    best = d;
                    target = a;
                }
            }
            if (target == null) return;
            laserTarget = target.getId();
            laserCharge = 0;
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 1.6F);
            pushVisuals(level);
        }
        // erst aufladen, dann feuern
        if (laserCharge < LASER_CHARGE_TICKS) {
            laserCharge++;
            if (laserCharge == LASER_CHARGE_TICKS) {
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5F, 1.8F);
            }
            return;
        }
        int gained = target.harvestTick();
        if (gained > 0) {
            receiveCosmicEnergy(gained);
            // grosses Energiepartikel sammelt sich ueber dem Trichter und wird an den Kern uebergeben
            if (level.getBlockEntity(center(level)) instanceof SingularityCoreBlockEntity core) core.markDelivery(level.getGameTime());
            laserTarget = -1;
            pushVisuals(level);
        }
    }

    /** Uebergibt die Darstellungswerte an den Singularitaetskern (der sie an die Clients schickt). */
    private void pushVisuals(ServerLevel level) {
        if (!formed || size == null) return;
        BlockPos core = center(level);
        if (level.hasChunkAt(core) && level.getBlockEntity(core) instanceof SingularityCoreBlockEntity be) {
            boolean running = phase == CorePhase.ACTIVE || phase == CorePhase.UNSTABLE;
            ItemStack stab = inventory.getStackInSlot(SLOT_STAB);
            int shield = (ModDataMaps.stabilizer(stab) != null ? Math.min(75, stab.getCount() * 75 / 16) : 0) + (field > 0 ? 25 : 0);
            be.setVisuals(size.size(), scale, size.maxScale(), instability, skyLink && running, lastCosmic, shield, funnel,
                    laserArray, laserTarget, overdrive, lastOutput,
                    (int) Math.min(100, Math.round(100.0 * energy / Math.max(1, getCapacity()))),
                    meltdown <= 0 ? 0 : (MELTDOWN_TICKS - meltdown) / 20 + 1);
        }
    }

    // ------------------------------------------------------------------ Kosmische Modifikatoren

    private static boolean isNight(ServerLevel level) {
        long t = level.getDayTime() % 24000L;
        return t >= 13000L && t < 23000L;
    }

    /** Moonphase 0 = Vollmond, 4 = Neumond (gleiche Formel wie Vanilla). */
    private static int moonPhase(ServerLevel level) {
        return (int) ((level.getDayTime() / 24000L) % 8L);
    }

    /**
     * Sternenlicht: nur mit freier Sicht zum Himmel ueber der Decke gibt es den Himmelsbonus und den Nacht-/Mondbonus.
     * Im End wirkt die Leere immer, mit Himmelssicht noch staerker.
     */
    public double cosmicOutput(ServerLevel level) {
        if (!CoreConfig.COSMIC_MODIFIERS.get()) return 1.0;
        double sky = skyLink ? CoreConfig.SKY_LINK_BONUS.get() * (funnel ? 2.0 : 1.0) : 0.0;
        if (level.dimension() == Level.END) return 1.4 + sky;
        if (level.dimension() == Level.OVERWORLD && skyLink) {
            double f = 1.0 + sky;
            if (isNight(level)) {
                double moonLight = Math.abs(moonPhase(level) - 4) / 4.0; // 1.0 Vollmond ... 0.0 Neumond
                f += 0.25 + 0.25 * moonLight;
            }
            return f;
        }
        return 1.0;
    }

    private double cosmicInstability(ServerLevel level) {
        if (!CoreConfig.COSMIC_MODIFIERS.get()) return 1.0;
        if (level.dimension() == Level.END) return 1.25;
        if (level.dimension() == Level.OVERWORLD && skyLink && isNight(level) && moonPhase(level) == 4) return 1.3;
        return 1.0;
    }

    // ------------------------------------------------------------------ Struktur

    private Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(ControllerBlock.FACING) ? s.getValue(ControllerBlock.FACING) : Direction.NORTH;
    }

    private BlockPos center(ServerLevel level) {
        CorePattern p = pattern();
        return p == null ? worldPosition : p.center(worldPosition, level.getBlockState(worldPosition).getValue(ControllerBlock.FACING));
    }

    private void revalidate(ServerLevel level) {
        CorePattern pattern = pattern();
        if (pattern == null) return;
        BlockState state = level.getBlockState(worldPosition);
        if (!state.hasProperty(ControllerBlock.FACING)) return;
        Direction current = state.getValue(ControllerBlock.FACING);
        Direction[] order = {current, current.getClockWise(), current.getOpposite(), current.getCounterClockWise()};

        CorePattern.Report best = null;
        Direction bestDir = current;
        for (Direction d : order) {
            CorePattern.Report r = pattern.validate(level, worldPosition, d);
            if (r.unloaded()) return; // Chunk nicht geladen: nichts aendern
            if (r.valid()) {
                best = r;
                bestDir = d;
                break;
            }
            // Bei Gleichstand bleibt die aktuelle Ausrichtung (wichtig fuer die Bauvorschau)
            if (best == null || r.mismatches() < best.mismatches()) {
                best = r;
                bestDir = d;
            }
        }
        lastReport = best;

        if (best != null && best.valid()) {
            if (bestDir != current) {
                // Der Controller richtet sich selbst nach aussen aus
                level.setBlock(worldPosition, state.setValue(ControllerBlock.FACING, bestDir), Block.UPDATE_ALL);
            }
            BlockPos above = pattern.center(worldPosition, bestDir).above(pattern.half() + 1);
            funnel = level.getBlockState(above).is(ModBlocks.STAR_FUNNEL.get());
            boolean lasers = true;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (!level.getBlockState(above.relative(d)).is(ModBlocks.COSMIC_LASER.get())) lasers = false;
            }
            if (lasers != laserArray) {
                laserArray = lasers;
                laserTarget = -1;
                setLasersLinked(level, above, lasers);
            }
            skyLink = level.canSeeSky(funnel ? above.above() : above);
            if (!formed) {
                onFormed(level);
            } else {
                linkPorts(level, true);
            }
        } else {
            skyLink = false;
            funnel = false;
            if (laserArray) {
                laserArray = false;
                laserTarget = -1;
                setLasersLinked(level, center(level).above(pattern.half() + 1), false);
            }
            if (formed) onUnformed(level);
        }
    }

    private void onFormed(ServerLevel level) {
        formed = true;
        phase = CorePhase.FORMED;
        applyPhase(level);
        linkPorts(level, true);
        Vec3 c = Vec3.atCenterOf(center(level));
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 0.6F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.6F, 1.4F);
        pushVisuals(level);
        if (level.getBlockEntity(center(level)) instanceof SingularityCoreBlockEntity core) {
            core.markFormed(level.getGameTime()); // startet die Verwandlungs-Animation auf den Clients
        }
        changedAndSync();
    }

    private void onUnformed(ServerLevel level) {
        formed = false;
        meltdown = 0;
        overdrive = 0; // Overdrive gilt nur fuer den gebildeten Reaktor - die Partikel sind verbraucht
        phase = CorePhase.DORMANT;
        resonance = 0;
        lastOutput = 0;
        applyPhase(level);
        linkPorts(level, false);
        Vec3 c = Vec3.atCenterOf(center(level));
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5F, 0.6F);
        changedAndSync();
    }

    /** Spiegelt die aktuelle Phase auf alle Strukturbloecke (Textur-Variante, Licht, Animation). */
    private void applyPhase(ServerLevel level) {
        CorePattern p = pattern();
        if (p != null) setPhaseAround(level, p, center(level), phase, worldPosition);
    }

    private static void setPhaseAround(ServerLevel level, CorePattern pattern, BlockPos center, CorePhase target, @Nullable BlockPos skip) {
        float radius = pattern.shellRadius();
        pattern.forEach(center, (pos, cell) -> {
            if (!CorePattern.isBlockCell(cell) || pos.equals(skip) || !level.hasChunkAt(pos)) return;
            BlockState s = level.getBlockState(pos);
            BlockState n = s;
            if (n.hasProperty(ModProperties.PHASE)) n = n.setValue(ModProperties.PHASE, target);
            if (n.hasProperty(ModProperties.PHANTOM)) {
                // ausserhalb der sichtbaren Kugel: bei gebildeter Struktur kein Koerper
                int dx = pos.getX() - center.getX(), dy = pos.getY() - center.getY(), dz = pos.getZ() - center.getZ();
                boolean outside = dx * dx + dy * dy + dz * dz > radius * radius;
                n = n.setValue(ModProperties.PHANTOM, target != CorePhase.DORMANT && outside);
            }
            if (n != s) level.setBlock(pos, n, Block.UPDATE_CLIENTS);
        });
        // der Controller selbst ist Teil des Musters und wird hier ebenfalls gesetzt
        if (skip != null && level.hasChunkAt(skip)) {
            BlockState self = level.getBlockState(skip);
            if (self.hasProperty(ModProperties.PHASE) && self.getValue(ModProperties.PHASE) != target) {
                level.setBlock(skip, self.setValue(ModProperties.PHASE, target), Block.UPDATE_CLIENTS);
            }
        }
    }

    private void linkPorts(ServerLevel level, boolean link) {
        CorePattern p = pattern();
        if (p == null) return;
        p.forEach(center(level), (pos, cell) -> {
            if (cell != CorePattern.Cell.HULL && cell != CorePattern.Cell.SLOT) return;
            if (!level.hasChunkAt(pos)) return;
            if (level.getBlockEntity(pos) instanceof PortBlockEntity port) {
                port.link(link ? worldPosition : null);
            }
        });
    }

    /**
     * Kreativ-Modus: setzt die komplette Struktur in der aktuellen Ausrichtung. Luft-Zellen werden freigeraeumt,
     * "egal"-Zellen bleiben unberuehrt. Gegenueber dem Controller entsteht ein Energie-Port, links davon ein Item-Port.
     */
    private void buildStructure(ServerLevel level) {
        CorePattern p = pattern();
        if (p == null || formed) return;
        Direction facing = facing();
        BlockPos center = p.center(worldPosition, facing);
        BlockPos energyPort = center.relative(facing.getOpposite(), p.half()).below(p.half());
        BlockPos itemPort = center.relative(facing.getClockWise(), p.half()).below(p.half());
        p.forEach(center, (pos, cell) -> {
            if (cell == CorePattern.Cell.ANY || pos.equals(worldPosition)) return;
            BlockState target;
            if (cell == CorePattern.Cell.AIR) {
                target = Blocks.AIR.defaultBlockState();
            } else if (pos.equals(energyPort)) {
                target = ModBlocks.ENERGY_PORT.get().defaultBlockState();
            } else if (pos.equals(itemPort)) {
                target = ModBlocks.ITEM_PORT.get().defaultBlockState();
            } else {
                target = CorePattern.blockFor(cell);
            }
            if (target == null) return;
            BlockState current = level.getBlockState(pos);
            boolean port = pos.equals(energyPort) || pos.equals(itemPort);
            if (current.is(target.getBlock()) || (!port && CorePattern.matches(cell, current, false))) {
                return; // passt schon
            }
            level.setBlock(pos, target, Block.UPDATE_ALL);
        });
        if (withLasers) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos lp = center.above(p.half() + 1).relative(d);
                BlockState there = level.getBlockState(lp);
                if (there.isAir() || there.canBeReplaced()) {
                    level.setBlock(lp, ModBlocks.COSMIC_LASER.get().defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        if (withFunnel) {
            BlockPos top = center.above(p.half() + 1);
            BlockState there = level.getBlockState(top);
            if (there.isAir() || there.canBeReplaced()) {
                level.setBlock(top, ModBlocks.STAR_FUNNEL.get().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        Vec3 c = Vec3.atCenterOf(center);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 60, p.half(), p.half(), p.half(), 0.02);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.BLOCKS, 1.0F, 1.0F);
        preview = false;
        checkTimer = 0;
        revalidate(level);
    }

    /** Wird vom Block aufgerufen, bevor der Controller verschwindet. */
    public void onControllerRemoved(ServerLevel level, BlockState oldState) {
        CorePattern p = pattern();
        if (p != null && oldState.hasProperty(ControllerBlock.FACING)) {
            BlockPos center = p.center(worldPosition, oldState.getValue(ControllerBlock.FACING));
            p.forEach(center, (pos, cell) -> {
                if (!CorePattern.isBlockCell(cell) || pos.equals(worldPosition) || !level.hasChunkAt(pos)) return;
                BlockState s = level.getBlockState(pos);
                BlockState n = s;
                if (n.hasProperty(ModProperties.PHASE)) n = n.setValue(ModProperties.PHASE, CorePhase.DORMANT);
                if (n.hasProperty(ModProperties.PHANTOM)) n = n.setValue(ModProperties.PHANTOM, false);
                if (n != s) level.setBlock(pos, n, Block.UPDATE_CLIENTS);
                if ((cell == CorePattern.Cell.HULL || cell == CorePattern.Cell.SLOT)
                        && level.getBlockEntity(pos) instanceof PortBlockEntity port) {
                    port.link(null);
                }
            });
        }
        if (p != null && oldState.hasProperty(ControllerBlock.FACING)) {
            setLasersLinked(level, p.center(worldPosition, oldState.getValue(ControllerBlock.FACING)).above(p.half() + 1), false);
        }
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack.copy());
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    // ------------------------------------------------------------------ Statusanzeige (Schleichen + Rechtsklick)

    private static String fmt(long n) {
        return NumberFormat.getIntegerInstance().format(n);
    }

    public void sendStatus(ServerLevel level, Player player) {
        player.displayClientMessage(Component.translatable("message.cosmiccore.status.header")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
        if (size == null) {
            player.displayClientMessage(Component.translatable("message.cosmiccore.status.no_size").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        revalidate(level);

        if (!formed) {
            CorePattern.Report r = lastReport;
            if (r == null) {
                player.displayClientMessage(Component.translatable("message.cosmiccore.status.pending").withStyle(ChatFormatting.GRAY), false);
                return;
            }
            player.displayClientMessage(Component.translatable("message.cosmiccore.status.invalid", r.mismatches())
                    .withStyle(ChatFormatting.RED), false);
            BlockPos e = r.firstError();
            if (e != null) {
                player.displayClientMessage(Component.translatable("message.cosmiccore.status.error_at",
                        e.getX(), e.getY(), e.getZ(), r.expected()).withStyle(ChatFormatting.GRAY), false);
                level.sendParticles(ParticleTypes.END_ROD, e.getX() + 0.5, e.getY() + 0.5, e.getZ() + 0.5, 15, 0.3, 0.3, 0.3, 0.02);
            }
            return;
        }

        player.displayClientMessage(Component.translatable("message.cosmiccore.status.formed").withStyle(ChatFormatting.GREEN), false);
        player.displayClientMessage(Component.translatable("message.cosmiccore.status.energy",
                fmt(energy), fmt(getCapacity())), false);
        player.displayClientMessage(Component.translatable("message.cosmiccore.status.output",
                fmt(lastOutput), Math.round(100f * resonance / CoreConfig.RAMP_UP_TICKS.get())), false);
        ChatFormatting color = instability < 40f ? ChatFormatting.GREEN : instability < UNSTABLE_ON ? ChatFormatting.YELLOW : ChatFormatting.RED;
        player.displayClientMessage(Component.translatable("message.cosmiccore.status.instability",
                String.format("%.1f", instability)).withStyle(color), false);
        player.displayClientMessage(Component.translatable("message.cosmiccore.status.cosmic",
                String.format("%.2f", cosmicOutput(level))), false);
        if (collapseCooldown > 0) {
            player.displayClientMessage(Component.translatable("message.cosmiccore.status.collapsed", collapseCooldown / 20)
                    .withStyle(ChatFormatting.DARK_RED), false);
        }
    }

    // ------------------------------------------------------------------ Persistenz / Sync

    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("size", size == null ? 0 : size.size());
        tag.putInt("scale", scale);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("preview", preview);
        tag.putBoolean("withFunnel", withFunnel);
        tag.putBoolean("withLasers", withLasers);
        tag.putInt("overdrive", overdrive);
        tag.putBoolean("formed", formed);
        tag.putLong("energy", energy);
        tag.putInt("burnTime", burnTime);
        tag.putInt("burnTotal", burnTotal);
        tag.putInt("burnPower", burnPower);
        tag.putFloat("field", field);
        tag.putFloat("fieldMax", fieldMax);
        tag.putFloat("instability", instability);
        tag.putInt("resonance", resonance);
        tag.putInt("collapseCooldown", collapseCooldown);
        tag.putInt("meltdown", meltdown);
        tag.putString("phase", phase.getSerializedName());
        tag.put("inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Controller aus aelteren Versionen (ohne Groessenwahl) waren immer 7x7x7
        size = tag.contains("size") ? ReactorSize.bySize(tag.getInt("size")) : ReactorSize.MEDIUM;
        scale = Math.max(1, tag.getInt("scale"));
        if (size != null) scale = Math.min(scale, size.maxScale());
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        preview = tag.getBoolean("preview");
        withFunnel = tag.getBoolean("withFunnel");
        withLasers = tag.getBoolean("withLasers");
        overdrive = Math.max(0, tag.getInt("overdrive"));
        if (size != null) overdrive = Math.min(overdrive, size.maxOverdrive());
        formed = tag.getBoolean("formed");
        energy = tag.getLong("energy");
        burnTime = tag.getInt("burnTime");
        burnTotal = tag.getInt("burnTotal");
        burnPower = tag.getInt("burnPower");
        field = tag.getFloat("field");
        fieldMax = tag.getFloat("fieldMax");
        instability = tag.getFloat("instability");
        resonance = tag.getInt("resonance");
        collapseCooldown = tag.getInt("collapseCooldown");
        meltdown = tag.getInt("meltdown");
        phase = CorePhase.byName(tag.getString("phase"));
        if (tag.contains("inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        }
    }

    /** Der Client braucht nur, was die Bauvorschau zeichnen muss. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("size", size == null ? 0 : size.size());
        tag.putBoolean("preview", preview);
        tag.putBoolean("withFunnel", withFunnel);
        tag.putBoolean("withLasers", withLasers);
        tag.putBoolean("formed", formed);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        size = ReactorSize.bySize(tag.getInt("size"));
        preview = tag.getBoolean("preview");
        withFunnel = tag.getBoolean("withFunnel");
        withLasers = tag.getBoolean("withLasers");
        formed = tag.getBoolean("formed");
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------------ innere Klassen

    /** Fuel- und Stabilisator-Slot. Auch clientseitig im Menue benutzt (dort ohne Block-Entity). */
    public static class CoreInventory extends ItemStackHandler {
        private final Runnable onChange;

        public CoreInventory(Runnable onChange) {
            super(2);
            this.onChange = onChange;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == SLOT_FUEL ? ModDataMaps.fuel(stack) != null : ModDataMaps.stabilizer(stack) != null;
        }

        @Override
        protected void onContentsChanged(int slot) {
            onChange.run();
        }
    }

    /** Werte fuer die GUI. Ganzzahlen > 16 Bit werden in zwei Haelften uebertragen (Vanilla sendet nur Shorts). */
    private final class DataAccess implements ContainerData {
        @Override
        public int get(int index) {
            return switch (index) {
                // Energie und Kapazitaet in kRF (passt so auch bei 2,5 Mrd. RF in 32 Bit)
                case ControllerMenu.D_ENERGY_LO -> (int) (energy / 1000) & 0xFFFF;
                case ControllerMenu.D_ENERGY_HI -> (int) (energy / 1000) >>> 16;
                case ControllerMenu.D_CAPACITY_LO -> (int) (getCapacity() / 1000) & 0xFFFF;
                case ControllerMenu.D_CAPACITY_HI -> (int) (getCapacity() / 1000) >>> 16;
                case ControllerMenu.D_OUTPUT_LO -> lastOutput & 0xFFFF;
                case ControllerMenu.D_OUTPUT_HI -> lastOutput >>> 16;
                case ControllerMenu.D_INSTABILITY -> Math.round(instability * 100f);
                case ControllerMenu.D_RESONANCE -> Math.round(100f * resonance / CoreConfig.RAMP_UP_TICKS.get());
                case ControllerMenu.D_SCALE -> scale;
                case ControllerMenu.D_MAX_SCALE -> size == null ? 0 : size.maxScale();
                case ControllerMenu.D_SIZE -> size == null ? 0 : size.size();
                case ControllerMenu.D_FLAGS -> (formed ? ControllerMenu.F_FORMED : 0)
                        | (enabled ? ControllerMenu.F_ENABLED : 0)
                        | (preview ? ControllerMenu.F_PREVIEW : 0)
                        | (skyLink ? ControllerMenu.F_SKY : 0)
                        | (lastOutput > 0 ? ControllerMenu.F_BURNING : 0)
                        | (funnel ? ControllerMenu.F_FUNNEL : 0)
                        | (laserArray ? ControllerMenu.F_LASER : 0)
                        | (formed && enabled && collapseCooldown <= 0 ? ControllerMenu.F_RUNNING : 0)
                        | (energy >= getCapacity() ? ControllerMenu.F_FULL : 0)
                        | (burnTime > 0 || !inventory.getStackInSlot(SLOT_FUEL).isEmpty() ? ControllerMenu.F_HAS_FUEL : 0);
                case ControllerMenu.D_COSMIC -> (int) Math.round(lastCosmic * 100.0);
                case ControllerMenu.D_BURN -> burnTotal <= 0 ? 0 : Math.round(1000f * burnTime / burnTotal);
                case ControllerMenu.D_STAB -> fieldMax <= 0 ? 0 : Math.round(1000f * Math.min(1f, field / fieldMax));
                case ControllerMenu.D_COLLAPSE -> collapseCooldown / 20;
                case ControllerMenu.D_OVERDRIVE -> overdrive;
                case ControllerMenu.D_MELTDOWN -> meltdown <= 0 ? 0 : (MELTDOWN_TICKS - meltdown) / 20 + 1;
                case ControllerMenu.D_ERRORS -> lastReport == null || formed ? 0 : Math.min(32767, lastReport.mismatches());
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return ControllerMenu.DATA_COUNT;
        }
    }

    /** Fuer Trichter/Rohre: nur einfuegen, nie entnehmen. */
    private final class InsertOnlyView implements IItemHandler {
        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return inventory.isItemValid(slot, stack);
        }
    }

    private final class EnergyView implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return ControllerBlockEntity.this.extractEnergy(toExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, energy);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, getCapacity());
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }
}
