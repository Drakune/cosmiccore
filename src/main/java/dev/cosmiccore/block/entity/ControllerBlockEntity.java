package dev.cosmiccore.block.entity;

import dev.cosmiccore.CoreConfig;
import dev.cosmiccore.block.ControllerBlock;
import dev.cosmiccore.block.CorePhase;
import dev.cosmiccore.block.ModProperties;
import dev.cosmiccore.data.CoreFuel;
import dev.cosmiccore.data.CoreStabilizer;
import dev.cosmiccore.data.ModDataMaps;
import dev.cosmiccore.multiblock.CorePattern;
import java.text.NumberFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
 */
public class ControllerBlockEntity extends BlockEntity {
    public static final int SLOT_FUEL = 0;
    public static final int SLOT_STAB = 1;

    private static final float UNSTABLE_ON = 70f;
    private static final float UNSTABLE_OFF = 55f;
    private static final float STABILIZE_BELOW = 25f;   // Stabilisator wird erst ab dieser Instabilitaet verbraucht
    private static final float IDLE_DECAY = 0.05f;      // Abkuehlung pro Tick ohne Verbrennung

    private final CoreInventory inventory = new CoreInventory();
    private final IItemHandler itemView = new InsertOnlyView();
    private final IEnergyStorage energyView = new EnergyView();

    private boolean formed;
    private int energy;
    private int burnTime;
    private int burnPower;
    private int stabTime;
    private float stabStrength;
    private float instability;
    private int resonance;
    private int collapseCooldown;
    private CorePhase phase = CorePhase.DORMANT;

    // nicht gespeichert
    private int checkTimer;
    private int lastOutput;
    @Nullable
    private CorePattern.Report lastReport;

    public ControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTROLLER.get(), pos, state);
    }

    // ------------------------------------------------------------------ API fuer Ports / Capabilities

    public boolean isFormed() {
        return formed;
    }

    public int getEnergy() {
        return energy;
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

    // ------------------------------------------------------------------ Tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, ControllerBlockEntity be) {
        if (level instanceof ServerLevel sl) be.tick(sl);
    }

    private void tick(ServerLevel level) {
        if (--checkTimer <= 0) {
            checkTimer = CoreConfig.VALIDATION_INTERVAL.get();
            revalidate(level);
        }
        if (!formed) {
            lastOutput = 0;
            return;
        }
        simulate(level);
    }

    private void simulate(ServerLevel level) {
        final int capacity = CoreConfig.ENERGY_CAPACITY.get();
        final int ramp = CoreConfig.RAMP_UP_TICKS.get();
        boolean burning = false;
        lastOutput = 0;

        if (collapseCooldown > 0) {
            collapseCooldown--;
            resonance = 0;
        } else {
            // Brennstoff nachlegen - nur wenn noch Platz im Puffer ist
            if (burnTime <= 0 && energy < capacity) {
                CoreFuel fuel = ModDataMaps.fuel(inventory.getStackInSlot(SLOT_FUEL));
                if (fuel != null) {
                    inventory.extractItem(SLOT_FUEL, 1, false);
                    burnTime = fuel.burnTime();
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
                burnTime--;
                resonance = Math.min(resonance + 1, ramp);
                double factor = 0.2 + 0.8 * resonance / (double) ramp;
                double gen = burnPower * factor * cosmicOutput(level) * CoreConfig.OUTPUT_MULTIPLIER.get();
                int out = (int) Math.min(Integer.MAX_VALUE, Math.round(gen));
                energy = (int) Math.min((long) capacity, (long) energy + out);
                lastOutput = out;
                instability += (float) (CoreConfig.INSTABILITY_RATE.get() * (burnPower / 1000.0) * cosmicInstability(level));
                setChanged();
            } else if (burnTime <= 0) {
                resonance = Math.max(0, resonance - 2);
            }
        }

        // Stabilisator
        if (stabTime <= 0 && instability > STABILIZE_BELOW) {
            CoreStabilizer stab = ModDataMaps.stabilizer(inventory.getStackInSlot(SLOT_STAB));
            if (stab != null) {
                inventory.extractItem(SLOT_STAB, 1, false);
                stabTime = stab.duration();
                stabStrength = stab.strength();
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
        boolean active = burnTime > 0 && collapseCooldown <= 0;
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
        }
    }

    private void collapse(ServerLevel level) {
        Vec3 c = Vec3.atCenterOf(center(level));
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 4.0F, 0.6F);
        level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y, c.z, 80, 1.8, 1.8, 1.8, 0.08);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);

        double damage = CoreConfig.COLLAPSE_DAMAGE.get();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(10.0))) {
            e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 240));
            e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160));
            if (damage > 0) e.hurt(level.damageSources().magic(), (float) damage);
        }
        if (CoreConfig.COLLAPSE_DESTROYS_BLOCKS.get()) {
            level.explode(null, c.x, c.y, c.z, 5.0F, Level.ExplosionInteraction.BLOCK);
        }

        burnTime = 0;
        burnPower = 0;
        resonance = 0;
        stabTime = 0;
        instability = 35f;
        collapseCooldown = CoreConfig.COLLAPSE_COOLDOWN.get();
        lastOutput = 0;
        phase = CorePhase.FORMED;
        applyPhase(level);
        setChanged();
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

    public double cosmicOutput(ServerLevel level) {
        if (!CoreConfig.COSMIC_MODIFIERS.get()) return 1.0;
        if (level.dimension() == Level.END) return 1.4;
        if (level.dimension() == Level.OVERWORLD && isNight(level)) {
            double moonLight = Math.abs(moonPhase(level) - 4) / 4.0; // 1.0 Vollmond ... 0.0 Neumond
            return 1.25 + 0.25 * moonLight;
        }
        return 1.0;
    }

    private double cosmicInstability(ServerLevel level) {
        if (!CoreConfig.COSMIC_MODIFIERS.get()) return 1.0;
        if (level.dimension() == Level.END) return 1.25;
        if (level.dimension() == Level.OVERWORLD && isNight(level) && moonPhase(level) == 4) return 1.3;
        return 1.0;
    }

    // ------------------------------------------------------------------ Struktur

    private BlockPos center(ServerLevel level) {
        return CorePattern.center(worldPosition, level.getBlockState(worldPosition).getValue(ControllerBlock.FACING));
    }

    private void revalidate(ServerLevel level) {
        BlockState state = level.getBlockState(worldPosition);
        if (!state.hasProperty(ControllerBlock.FACING)) return;
        Direction current = state.getValue(ControllerBlock.FACING);
        Direction[] order = {current, current.getClockWise(), current.getOpposite(), current.getCounterClockWise()};

        CorePattern.Report best = null;
        Direction bestDir = current;
        for (Direction d : order) {
            CorePattern.Report r = CorePattern.validate(level, worldPosition, d);
            if (r.unloaded()) return; // Chunk nicht geladen: nichts aendern
            if (r.valid()) {
                best = r;
                bestDir = d;
                break;
            }
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
            if (!formed) {
                onFormed(level);
            } else {
                linkPorts(level, true);
            }
        } else if (formed) {
            onUnformed(level);
        }
    }

    private void onFormed(ServerLevel level) {
        formed = true;
        phase = CorePhase.FORMED;
        applyPhase(level);
        linkPorts(level, true);
        Vec3 c = Vec3.atCenterOf(center(level));
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 0.6F);
        setChanged();
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
        setChanged();
    }

    /** Spiegelt die aktuelle Phase auf alle Strukturbloecke (Textur-Variante, Licht, Animation). */
    private void applyPhase(ServerLevel level) {
        setPhaseAround(level, center(level), phase, worldPosition);
    }

    private static void setPhaseAround(ServerLevel level, BlockPos center, CorePhase target, @Nullable BlockPos skip) {
        CorePattern.forEach(center, (pos, cell) -> {
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
        CorePattern.forEach(center(level), (pos, cell) -> {
            if (cell != CorePattern.Cell.HULL && cell != CorePattern.Cell.SLOT) return;
            if (!level.hasChunkAt(pos)) return;
            if (level.getBlockEntity(pos) instanceof PortBlockEntity port) {
                port.link(link ? worldPosition : null);
            }
        });
    }

    /** Wird vom Block aufgerufen, bevor der Controller verschwindet. */
    public void onControllerRemoved(ServerLevel level, BlockState oldState) {
        if (oldState.hasProperty(ControllerBlock.FACING)) {
            BlockPos center = CorePattern.center(worldPosition, oldState.getValue(ControllerBlock.FACING));
            CorePattern.forEach(center, (pos, cell) -> {
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

    // ------------------------------------------------------------------ Statusanzeige

    private static String fmt(long n) {
        return NumberFormat.getIntegerInstance().format(n);
    }

    public void sendStatus(ServerLevel level, Player player) {
        revalidate(level);
        player.displayClientMessage(Component.translatable("message.cosmiccore.status.header")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);

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
                fmt(energy), fmt(CoreConfig.ENERGY_CAPACITY.get())), false);
        player.displayClientMessage(Component.translatable("message.cosmiccore.status.output",
                fmt(lastOutput), Math.round(100f * resonance / CoreConfig.RAMP_UP_TICKS.get())), false);

        ChatFormatting color = instability < 40f ? ChatFormatting.GREEN : instability < UNSTABLE_ON ? ChatFormatting.YELLOW : ChatFormatting.RED;
        player.displayClientMessage(Component.translatable("message.cosmiccore.status.instability",
                String.format("%.1f", instability)).withStyle(color), false);

        player.displayClientMessage(Component.translatable("message.cosmiccore.status.fuel",
                inventory.getStackInSlot(SLOT_FUEL).getCount(), burnTime / 20), false);
        player.displayClientMessage(Component.translatable("message.cosmiccore.status.stabilizer",
                inventory.getStackInSlot(SLOT_STAB).getCount(), stabTime / 20), false);
        player.displayClientMessage(Component.translatable("message.cosmiccore.status.cosmic",
                String.format("%.2f", cosmicOutput(level))), false);
        if (collapseCooldown > 0) {
            player.displayClientMessage(Component.translatable("message.cosmiccore.status.collapsed", collapseCooldown / 20)
                    .withStyle(ChatFormatting.DARK_RED), false);
        }
    }

    // ------------------------------------------------------------------ Persistenz

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("formed", formed);
        tag.putInt("energy", energy);
        tag.putInt("burnTime", burnTime);
        tag.putInt("burnPower", burnPower);
        tag.putInt("stabTime", stabTime);
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
        formed = tag.getBoolean("formed");
        energy = tag.getInt("energy");
        burnTime = tag.getInt("burnTime");
        burnPower = tag.getInt("burnPower");
        stabTime = tag.getInt("stabTime");
        stabStrength = tag.getFloat("stabStrength");
        instability = tag.getFloat("instability");
        resonance = tag.getInt("resonance");
        collapseCooldown = tag.getInt("collapseCooldown");
        phase = CorePhase.byName(tag.getString("phase"));
        if (tag.contains("inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        }
    }

    // ------------------------------------------------------------------ innere Klassen

    private final class CoreInventory extends ItemStackHandler {
        CoreInventory() {
            super(2);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == SLOT_FUEL ? ModDataMaps.fuel(stack) != null : ModDataMaps.stabilizer(stack) != null;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
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
            return CoreConfig.ENERGY_CAPACITY.get();
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
