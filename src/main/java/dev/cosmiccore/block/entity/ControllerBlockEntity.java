package dev.cosmiccore.block.entity;

import dev.cosmiccore.CoreConfig;
import dev.cosmiccore.block.ControllerBlock;
import dev.cosmiccore.block.CorePhase;
import dev.cosmiccore.block.ModBlocks;
import dev.cosmiccore.block.ModProperties;
import dev.cosmiccore.data.CoreFuel;
import dev.cosmiccore.data.CoreStabilizer;
import dev.cosmiccore.data.ModDataMaps;
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
 * Baugroesse) multipliziert Leistung, Brennstoffverbrauch und Instabilitaet - mehr Strom, aber schneller am Limit.
 */
public class ControllerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_FUEL = 0;
    public static final int SLOT_STAB = 1;

    public static final float UNSTABLE_ON = 70f;
    private static final float UNSTABLE_OFF = 55f;
    private static final float ALARM_AT = 85f;
    private static final float STABILIZE_BELOW = 25f;   // Stabilisator wird erst ab dieser Instabilitaet verbraucht
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

    private boolean formed;
    private int energy;
    private int burnTime;
    private int burnTotal;
    private int burnPower;
    private int stabTime;
    private int stabTotal;
    private float stabStrength;
    private float instability;
    private int resonance;
    private int collapseCooldown;
    private CorePhase phase = CorePhase.DORMANT;

    // nicht gespeichert
    private int checkTimer;
    private int lastOutput;
    private boolean skyLink;
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

    public int getEnergy() {
        return energy;
    }

    @Nullable
    public ReactorSize getSize() {
        return size;
    }

    public boolean isPreview() {
        return preview;
    }

    public int getCapacity() {
        double factor = size == null ? 1.0 : size.capacityFactor();
        return (int) Math.min(Integer.MAX_VALUE, Math.round(CoreConfig.ENERGY_CAPACITY.get() * factor));
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
        int out = Math.min(energy, max);
        if (!simulate && out > 0) {
            energy -= out;
            setChanged();
        }
        return out;
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
        if (id >= ControllerMenu.BTN_SIZE_FIRST && id < ControllerMenu.BTN_SIZE_FIRST + ReactorSize.values().length) {
            if (size != null) return false; // nur einmalig waehlbar
            size = ReactorSize.values()[id - ControllerMenu.BTN_SIZE_FIRST];
            scale = 1;
            preview = true; // direkt zeigen, wo was hin muss
            checkTimer = 0;
            sl.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.8F);
            changedAndSync();
            return true;
        }
        if (size == null) return false;
        switch (id) {
            case ControllerMenu.BTN_SCALE_DOWN -> scale = Math.max(1, scale - 1);
            case ControllerMenu.BTN_SCALE_UP -> scale = Math.min(size.maxScale(), scale + 1);
            case ControllerMenu.BTN_TOGGLE -> enabled = !enabled;
            case ControllerMenu.BTN_PREVIEW -> preview = !preview;
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
        if (level.getGameTime() % 10 == 0) pushVisuals(level);
    }

    private double outputScale() {
        return Math.pow(scale, CoreConfig.SCALE_OUTPUT_EXPONENT.get());
    }

    private double instabilityScale() {
        double sizeFactor = size == null ? 1.0 : size.instabilityFactor();
        return Math.pow(scale, CoreConfig.SCALE_INSTABILITY_EXPONENT.get()) * sizeFactor;
    }

    private void simulate(ServerLevel level) {
        final int capacity = getCapacity();
        final int ramp = CoreConfig.RAMP_UP_TICKS.get();
        boolean burning = false;
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
                energy = (int) Math.min((long) capacity, (long) energy + out);
                lastOutput = out;
                instability += (float) (CoreConfig.INSTABILITY_RATE.get() * (burnPower / 1000.0)
                        * instabilityScale() * cosmicInstability(level));
                setChanged();
            } else if (burnTime <= 0) {
                resonance = Math.max(0, resonance - 2);
            }
        } else {
            resonance = Math.max(0, resonance - 2);
        }

        // Stabilisator - groessere Reaktoren verteilen ihn auf mehr Spulen und nutzen ihn besser
        if (stabTime <= 0 && instability > STABILIZE_BELOW) {
            CoreStabilizer stab = ModDataMaps.stabilizer(inventory.getStackInSlot(SLOT_STAB));
            if (stab != null) {
                inventory.extractItem(SLOT_STAB, 1, false);
                stabTime = stab.duration();
                stabTotal = stabTime;
                stabStrength = (float) (stab.strength() * size.stabilizerFactor());
                setChanged();
            }
        }
        if (stabTime > 0) {
            stabTime--;
            instability -= stabStrength;
        }
        if (!burning) instability -= IDLE_DECAY;
        instability = Mth.clamp(instability, 0f, 100f);

        if (instability >= 100f) {
            collapse(level);
            return;
        }

        // Phase bestimmen (mit Hysterese, damit die Bloecke nicht flackern)
        boolean active = enabled && burnTime > 0 && collapseCooldown <= 0;
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
        stabTime = 0;
        instability = 35f;
        collapseCooldown = CoreConfig.COLLAPSE_COOLDOWN.get();
        lastOutput = 0;
        phase = CorePhase.FORMED;
        applyPhase(level);
        setChanged();
        // ohne Kern zerfaellt die Struktur sofort
        revalidate(level);
    }

    /** Uebergibt die Darstellungswerte an den Singularitaetskern (der sie an die Clients schickt). */
    private void pushVisuals(ServerLevel level) {
        if (!formed || size == null) return;
        BlockPos core = center(level);
        if (level.hasChunkAt(core) && level.getBlockEntity(core) instanceof SingularityCoreBlockEntity be) {
            boolean running = phase == CorePhase.ACTIVE || phase == CorePhase.UNSTABLE;
            be.setVisuals(size.size(), scale, size.maxScale(), instability, skyLink && running, lastCosmic);
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
        double sky = skyLink ? CoreConfig.SKY_LINK_BONUS.get() : 0.0;
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
            skyLink = level.canSeeSky(above);
            if (!formed) {
                onFormed(level);
            } else {
                linkPorts(level, true);
            }
        } else {
            skyLink = false;
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
        changedAndSync();
        pushVisuals(level);
    }

    private void onUnformed(ServerLevel level) {
        formed = false;
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
        pattern.forEach(center, (pos, cell) -> {
            if (!CorePattern.isBlockCell(cell) || pos.equals(skip) || !level.hasChunkAt(pos)) return;
            BlockState s = level.getBlockState(pos);
            if (s.hasProperty(ModProperties.PHASE) && s.getValue(ModProperties.PHASE) != target) {
                level.setBlock(pos, s.setValue(ModProperties.PHASE, target), Block.UPDATE_CLIENTS);
            }
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
                if (s.hasProperty(ModProperties.PHASE) && s.getValue(ModProperties.PHASE) != CorePhase.DORMANT) {
                    level.setBlock(pos, s.setValue(ModProperties.PHASE, CorePhase.DORMANT), Block.UPDATE_CLIENTS);
                }
                if ((cell == CorePattern.Cell.HULL || cell == CorePattern.Cell.SLOT)
                        && level.getBlockEntity(pos) instanceof PortBlockEntity port) {
                    port.link(null);
                }
            });
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
        tag.putBoolean("formed", formed);
        tag.putInt("energy", energy);
        tag.putInt("burnTime", burnTime);
        tag.putInt("burnTotal", burnTotal);
        tag.putInt("burnPower", burnPower);
        tag.putInt("stabTime", stabTime);
        tag.putInt("stabTotal", stabTotal);
        tag.putFloat("stabStrength", stabStrength);
        tag.putFloat("instability", instability);
        tag.putInt("resonance", resonance);
        tag.putInt("collapseCooldown", collapseCooldown);
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
        formed = tag.getBoolean("formed");
        energy = tag.getInt("energy");
        burnTime = tag.getInt("burnTime");
        burnTotal = tag.getInt("burnTotal");
        burnPower = tag.getInt("burnPower");
        stabTime = tag.getInt("stabTime");
        stabTotal = tag.getInt("stabTotal");
        stabStrength = tag.getFloat("stabStrength");
        instability = tag.getFloat("instability");
        resonance = tag.getInt("resonance");
        collapseCooldown = tag.getInt("collapseCooldown");
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
        tag.putBoolean("formed", formed);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        size = ReactorSize.bySize(tag.getInt("size"));
        preview = tag.getBoolean("preview");
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
                case ControllerMenu.D_ENERGY_LO -> energy & 0xFFFF;
                case ControllerMenu.D_ENERGY_HI -> energy >>> 16;
                case ControllerMenu.D_CAPACITY_LO -> getCapacity() & 0xFFFF;
                case ControllerMenu.D_CAPACITY_HI -> getCapacity() >>> 16;
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
                        | (lastOutput > 0 ? ControllerMenu.F_BURNING : 0);
                case ControllerMenu.D_COSMIC -> (int) Math.round(lastCosmic * 100.0);
                case ControllerMenu.D_BURN -> burnTotal <= 0 ? 0 : Math.round(1000f * burnTime / burnTotal);
                case ControllerMenu.D_STAB -> stabTotal <= 0 ? 0 : Math.round(1000f * stabTime / stabTotal);
                case ControllerMenu.D_COLLAPSE -> collapseCooldown / 20;
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
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return getCapacity();
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
