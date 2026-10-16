package dr4kun3.cosmiccore.block.entity;

import dr4kun3.cosmiccore.transfer.*;

import dr4kun3.cosmiccore.CoreConfig;
import dr4kun3.cosmiccore.ModItems;
import dr4kun3.cosmiccore.block.ControllerBlock;
import dr4kun3.cosmiccore.block.CorePhase;
import dr4kun3.cosmiccore.block.ModBlocks;
import dr4kun3.cosmiccore.block.ModProperties;
import dr4kun3.cosmiccore.data.CoreFuel;
import dr4kun3.cosmiccore.data.CoreStabilizer;
import dr4kun3.cosmiccore.data.ModDataMaps;
import dr4kun3.cosmiccore.entity.CosmicAsteroid;
import dr4kun3.cosmiccore.menu.ControllerMenu;
import dr4kun3.cosmiccore.multiblock.CorePattern;
import dr4kun3.cosmiccore.multiblock.ReactorRegistry;
import dr4kun3.cosmiccore.multiblock.ReactorSize;
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
    /** Modul-Slots: Effizienz (weniger Brennstoff) und Stabilitaet (weniger Instabilitaet). */
    public static final int SLOT_EFFICIENCY = 2;
    public static final int SLOT_STABILITY = 3;
    /** Beute-Slots fuer Asteroiden-Beute (nur entnehmbar). */
    public static final int SLOT_LOOT_FIRST = 4;
    public static final int LOOT_SLOTS = 6;
    public static final int LOOT_END = SLOT_LOOT_FIRST + LOOT_SLOTS;
    /** Anti-Asteroiden-Modul (hinten angehaengt, damit aeltere Speicherstaende ihre Beute-Slots behalten). */
    public static final int SLOT_ANTI = LOOT_END;
    /** Farb-Modul (ebenfalls hinten angehaengt). */
    public static final int SLOT_COLOR = SLOT_ANTI + 1;
    public static final int INVENTORY_SIZE = SLOT_COLOR + 1;

    public static final int MAX_MODULES = 4;

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
    /** Fuer die Seitenfluegel der Steuerkonsole: Brennstoff, Stabilisator und Beute (an Clients synchronisiert). */
    private ItemStack clientFuel = ItemStack.EMPTY, clientStab = ItemStack.EMPTY;
    private int clientLoot;
    private int consoleSignature;
    private final java.util.List<ItemStack> clientLootItems = new java.util.ArrayList<>();
    /** Restlaufzeit in Sekunden bei den aktuellen Einstellungen (jede Sekunde neu berechnet); siehe TIME_*. */
    private int fuelSeconds = TIME_NONE, stabSeconds = TIME_NONE;
    /** Gibt es einen Energie-Port in der Struktur? Dann zeigt der Controller Brennstoff statt Leistung/Puffer. */
    private boolean energyPort;
    /** Sonderwerte der Restlaufzeit: nicht berechenbar, pausiert (Puffer voll), abgeschaltet. 0 = leer. */
    public static final int TIME_NONE = -1, TIME_PAUSED = -2, TIME_OFF = -3;
    private final ItemHandler itemView = new InsertOnlyView();
    private final EnergyStore energyView = new EnergyView();
    private final ContainerData dataAccess = new DataAccess();

    /** null = Baugroesse noch nicht gewaehlt (frisch platziert). */
    @Nullable
    private ReactorSize size;
    private int scale = 1;
    private boolean enabled = true;
    private boolean preview;
    /** Bei der Groessenwahl angehakt: Sternentrichter gehoert zur Bauvorschau und zum Kreativ-Aufbau. */
    private boolean withFunnel;
    /** Schalter fuer das Anti-Asteroiden-Modul - ohne eingesetztes Modul immer aus. */
    private boolean antiAsteroid;
    /** Farbsatz des Farb-Moduls (je Effekt eine Farbe) - wirkt nur mit eingesetztem Modul. */
    private dr4kun3.cosmiccore.color.CoreColors.Config colors = dr4kun3.cosmiccore.color.CoreColors.DEFAULT;

    public dr4kun3.cosmiccore.color.CoreColors.Config getColors() {
        return colors;
    }

    /** Vom Farbmenue (ueber das Netzwerk) gesetzt. */
    public void setColors(dr4kun3.cosmiccore.color.CoreColors.Config c) {
        colors = c;
        setChanged();
        if (level instanceof ServerLevel sl) {
            pushVisuals(sl);
            sl.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5F, 1.2F);
        }
    }
    /** Dauerhaft freigeschaltete Leistungsstufen (1 ist immer frei; jede weitere kostet einmalig ein Item). */
    private int unlockedScale = 1;
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
    /** Hitzefaktor des aktuell brennenden Brennstoffs. */
    private float burnHeat = 1.0f;
    private float burnFrac;
    /** Verbleibende Kuehlpunkte im Eindaemmungsfeld (aus verbrauchten Stabilisatoren). */
    private float field;
    /** Kuehlpunkte des zuletzt verbrauchten Stabilisators (nur fuer die Anzeige). */
    private float fieldMax;
    private float instability;
    private int resonance;
    private int collapseCooldown;
    private int meltdown;
    /** Ultimativer Kern: das einmalige Erwachen (globaler Himmelseffekt) wurde bereits ausgeloest. */
    private boolean awakened;
    private CorePhase phase = CorePhase.DORMANT;
    /** Ticks ohne Erzeugung - erst nach einer Nachlaufzeit wird "bereit" angezeigt (kein Flackern beim Brennstoffwechsel). */
    private int idleTicks;
    private static final int IDLE_GRACE = 40;

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
    /** Ticks seit dem letzten Abschuss (Uebergabe-Animation mit Klaengen), -1 = keine. */
    private int deliverTicks = -1;
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

    /** Freie Sicht zum Himmel ueber dem Kern (bzw. dem Trichter). */
    public boolean hasSkyLink() {
        return skyLink;
    }

    /**
     * Nur fuer den Entwicklungs-Test der Cutscene: setzt einen fertigen ultimativen Kern mit Trichter und Lasern,
     * Brennstoff, Stufe 10 und Ultimate-Modus (ohne die Cutscene selbst auszuloesen). Liefert die Kernmitte.
     */
    public static BlockPos debugSetupUltimate(ServerLevel level, BlockPos controllerPos) {
        level.setBlock(controllerPos, ModBlocks.CONTROLLER.get().defaultBlockState()
                .setValue(dr4kun3.cosmiccore.block.ControllerBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(controllerPos) instanceof ControllerBlockEntity c)) return controllerPos;
        c.size = ReactorSize.ULTIMATE;
        c.withFunnel = true;
        c.withLasers = true;
        c.buildStructure(level);
        c.enabled = true;
        c.scale = c.size.maxScale();
        c.unlockedScale = c.scale;
        c.overdrive = c.size.maxOverdriveTotal();
        c.awakened = true;
        c.inventory.setStackInSlot(SLOT_FUEL, new ItemStack(ModItems.QUASAR_ESSENCE.get(), 64));
        c.inventory.setStackInSlot(SLOT_STAB, new ItemStack(net.minecraft.world.item.Items.NETHERITE_SCRAP, 64));
        c.checkTimer = 0;
        c.setChanged();
        return c.corePos();
    }

    /** Ultimativer Kern: ist der Void-Titan schon erwacht? */
    public boolean isAwakened() {
        return awakened && size != null && size.isUltimate();
    }

    /** Position der Kernmitte (nur bei gewaehlter Groesse sinnvoll). */
    public BlockPos corePos() {
        return size == null || level == null ? worldPosition : size.pattern().center(worldPosition, facing());
    }

    public boolean hasColorModule() {
        return inventory.getStackInSlot(SLOT_COLOR).is(ModItems.COLOR_MODULE.get());
    }

    public boolean hasAntiAsteroidModule() {
        return inventory.getStackInSlot(SLOT_ANTI).is(ModItems.ANTI_ASTEROID_MODULE.get());
    }

    /** Eingesetzt und eingeschaltet: im Umkreis erscheinen keine Asteroiden. */
    public boolean isAsteroidShieldActive() {
        return antiAsteroid && hasAntiAsteroidModule();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) ReactorRegistry.add(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) ReactorRegistry.remove(level, worldPosition);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide()) ReactorRegistry.remove(level, worldPosition);
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
        return size == null ? CoreConfig.BUFFER_PER_TIER.get() : size.buffer();
    }

    public StackHandler getInventory() {
        return inventory;
    }

    public EnergyStore getEnergyView() {
        return energyView;
    }

    public ItemHandler getItemView() {
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
        // ohne Brennstoff im Kern laeuft nichts - auch keine kosmische Energie
        if (!formed || amount <= 0 || burnTime <= 0) return 0;
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
        if (id == ControllerMenu.BTN_STORAGE) {
            ItemPortBlockEntity port = firstItemPort(sl);
            if (port == null) return false;
            BlockPos back = worldPosition;
            // eigenes Menue mit Rueckweg - beim Schliessen oeffnet der Server wieder diese Steuerung
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (cid, inv, pl) -> new dr4kun3.cosmiccore.menu.ItemPortMenu(cid, inv, port, back), port.getDisplayName()), buf -> {
                buf.writeBlockPos(port.getBlockPos());
                buf.writeBoolean(true);
                buf.writeBlockPos(back);
            });
            return true;
        }
        if (id == ControllerMenu.BTN_ANTI_ASTEROID) {
            if (!hasAntiAsteroidModule()) return false; // Schalter ist ohne Modul gesperrt
            antiAsteroid = !antiAsteroid;
            setChanged();
            sl.playSound(null, worldPosition, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3F, antiAsteroid ? 1.4F : 0.9F);
            return true;
        }
        // Leistungsstufe und Overdrive lassen sich erst einstellen, wenn der Generator gezuendet ist
        boolean running = formed && enabled && collapseCooldown <= 0;
        if (!running && (id == ControllerMenu.BTN_SCALE_DOWN || id == ControllerMenu.BTN_SCALE_UP
                || id == ControllerMenu.BTN_OVERDRIVE_UP || id == ControllerMenu.BTN_OVERDRIVE_DOWN)) {
            player.sendOverlayMessage(Component.translatable("message.cosmiccore.not_running").withStyle(ChatFormatting.YELLOW));
            return false;
        }
        switch (id) {
            case ControllerMenu.BTN_SCALE_DOWN -> scale = Math.max(1, scale - 1);
            case ControllerMenu.BTN_SCALE_UP -> {
                if (scale >= size.maxScale()) return false;
                if (scale + 1 > unlockedScale) {
                    // naechste Stufe dauerhaft freischalten: kostet einmalig das Item der Baugroesse
                    ItemStack key = new ItemStack(size.unlockItem());
                    if (!player.getAbilities().instabuild && !consumeOne(player, size.unlockItem())) {
                        player.sendOverlayMessage(Component.translatable("message.cosmiccore.unlock.missing",
                                key.getHoverName(), scale + 1).withStyle(ChatFormatting.RED));
                        return false;
                    }
                    unlockedScale = scale + 1;
                    player.sendOverlayMessage(Component.translatable("message.cosmiccore.unlock.done", unlockedScale)
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                    Vec3 uc = Vec3.atCenterOf(center(sl));
                    sl.playSound(null, uc.x, uc.y, uc.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5F, 0.8F + 0.08F * unlockedScale);
                    sl.playSound(null, uc.x, uc.y, uc.z, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6F, 1.4F);
                    sl.sendParticles(ParticleTypes.END_ROD, uc.x, uc.y, uc.z, 30, 0.5, 0.5, 0.5, 0.08);
                }
                scale++;
            }
            case ControllerMenu.BTN_TOGGLE -> enabled = !enabled;
            case ControllerMenu.BTN_PREVIEW -> preview = !preview;
            case ControllerMenu.BTN_OVERDRIVE_UP -> {
                if (!formed || overdrive >= size.maxOverdriveTotal()) return false;
                if (overdrive >= size.maxOverdrive()) {
                    // Ultimate-Modus: nur der ultimative Kern nach Overdrive 3, kostet 4 Quasar-Overdrive-Partikel
                    if (!player.getAbilities().instabuild && !consumeItems(player, ModItems.QUASAR_OVERDRIVE_PARTICLE.get(), ULTIMATE_COST)) {
                        player.sendOverlayMessage(Component.translatable("message.cosmiccore.ultimate.missing", ULTIMATE_COST)
                                .withStyle(ChatFormatting.RED));
                        return false;
                    }
                    overdrive++;
                    // beim ersten Ultimate-Modus erwacht der Void-Titan auf seinem fernen Planeten (Cutscene)
                    if (!awakened) awaken(sl);
                    Vec3 c = Vec3.atCenterOf(center(sl));
                    sl.playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 3.0F, 0.5F);
                    sl.playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.BLOCKS, 1.5F, 1.4F);
                    sl.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2.0F, 0.6F);
                    sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 300, 2.0, 2.0, 2.0, 0.4);
                    sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 300, 1.5, 1.5, 1.5, 1.0);
                    break;
                }
                if (!player.getAbilities().instabuild && !consumeParticle(player)) {
                    player.sendOverlayMessage(Component.translatable("message.cosmiccore.overdrive.missing")
                            .withStyle(ChatFormatting.RED));
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
    /** Kosten des Ultimate-Modus in Quasar-Overdrive-Partikeln. */
    public static final int ULTIMATE_COST = 4;

    /** Verbraucht genau count Stueck - aber nur, wenn so viele im Inventar sind. */
    private static boolean consumeItems(Player player, net.minecraft.world.item.Item item, int count) {
        Inventory inv = player.getInventory();
        int have = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) have += inv.getItem(i).getCount();
        if (have < count) return false;
        int left = count;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.is(item)) continue;
            int take = Math.min(left, st.getCount());
            st.shrink(take);
            left -= take;
        }
        return true;
    }

    private static boolean consumeOne(Player player, net.minecraft.world.item.Item item) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(item)) {
                st.shrink(1);
                return true;
            }
        }
        return false;
    }

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
        energy = Math.min(energy, getCapacity()); // Puffer haengt an der Baustufe (z. B. nach Config-Aenderung)
        if (antiAsteroid && !hasAntiAsteroidModule()) {
            antiAsteroid = false; // Modul entnommen: Abwehr aus, Asteroiden erscheinen wieder
            setChanged();
        }
        if (level.getGameTime() % 20 == 0) {
            updateForecast(level);
            energyPort = formed && scanEnergyPort(level);
            if (formed) flushLoot(level);
        }
        if (formed && level.getGameTime() % 20 == 0) {
            // Inhalt fuer Konsole und Port-Stationen nur bei Aenderung senden
            int sig = java.util.Objects.hash(inventory.getStackInSlot(SLOT_FUEL).getItem(), inventory.getStackInSlot(SLOT_FUEL).getCount(),
                    inventory.getStackInSlot(SLOT_STAB).getItem(), inventory.getStackInSlot(SLOT_STAB).getCount(), lootCount(),
                    fuelSeconds, stabSeconds, energyPort, lootSignature());
            if (sig != consoleSignature) {
                consoleSignature = sig;
                changedAndSync();
            }
        }
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
        tickDelivery(level);
        if (level.getGameTime() % 10 == 0) pushVisuals(level);
    }

    private int moduleCount(int slot, net.minecraft.world.item.Item module) {
        ItemStack st = inventory.getStackInSlot(slot);
        return st.is(module) ? Math.min(MAX_MODULES, st.getCount()) : 0;
    }

    /** Effizienz-Module: je -15 % Brennstoffverbrauch. */
    private float fuelUseFactor() {
        return 1.0f - 0.15f * moduleCount(SLOT_EFFICIENCY, ModItems.EFFICIENCY_MODULE.get());
    }

    /** Stabilitaets-Module: je -12 % Instabilitaet. */
    private float stabilityFactor() {
        return 1.0f - 0.12f * moduleCount(SLOT_STABILITY, ModItems.STABILITY_MODULE.get());
    }

    /** Haltewert des Eindaemmungsfelds - Stabilitaets-Module senken ihn um je 3 Prozentpunkte. */
    private float fieldTarget() {
        return FIELD_TARGET - 3f * moduleCount(SLOT_STABILITY, ModItems.STABILITY_MODULE.get());
    }

    private double outputScale() {
        double sizeFactor = size == null ? 1.0 : size.outputFactor();
        return Math.pow(scale, CoreConfig.SCALE_OUTPUT_EXPONENT.get()) * sizeFactor * Math.pow(2.0, overdrive);
    }

    private double instabilityScale() {
        double sizeFactor = size == null ? 1.0 : size.instabilityFactor();
        return Math.pow(scale, CoreConfig.SCALE_INSTABILITY_EXPONENT.get()) * sizeFactor
                * (overdrive <= 3 ? Math.pow(3.0, overdrive) : 27.0 * 2.0); // Ultimate-Modus: x2 statt x3
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
                    burnHeat = fuel.heat();
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
                // Effizienz-Module senken den Verbrauch (Bruchteile werden angespart)
                burnFrac += scale * fuelUseFactor();
                int use = (int) burnFrac;
                burnFrac -= use;
                burnTime = Math.max(0, burnTime - use);
                resonance = Math.min(resonance + 1, ramp);
                double factor = 0.2 + 0.8 * resonance / (double) ramp;
                double gen = burnPower * factor * outputScale() * lastCosmic * CoreConfig.OUTPUT_MULTIPLIER.get();
                int out = (int) Math.min(Integer.MAX_VALUE, Math.round(gen));
                energy = Math.min(capacity, energy + out);
                lastOutput = out;
                gain = (float) (CoreConfig.INSTABILITY_RATE.get() * (burnPower / 1000.0) * burnHeat
                        * instabilityScale() * cosmicInstability(level));
                // ab der roten Zone steigt die Instabilitaet gebremst - es bleibt Zeit zu reagieren
                if (instability > UNSTABLE_ON) gain *= 0.4f;
                gain *= stabilityFactor();
                instability += gain;
                setChanged();
            } else if (burnTime <= 0) {
                resonance = Math.max(0, resonance - 2);
            }
        } else {
            resonance = Math.max(0, resonance - 2);
        }

        // Ultimativer Kern: Vakuum-Energie aus dem All - nur solange Brennstoff brennt, ohne zusaetzliche Instabilitaet
        if (size.isUltimate() && enabled && skyLink && burnTime > 0 && collapseCooldown <= 0 && energy < capacity) {
            int vacuum = (int) Math.round(CoreConfig.VACUUM_ENERGY.get() * scale * lastCosmic * CoreConfig.OUTPUT_MULTIPLIER.get());
            energy = Math.min(capacity, energy + vacuum);
            lastOutput += vacuum;
        }

        // Eindaemmungsfeld: ab dem Zielwert wird genau so viel Kuehlung verbraucht, wie die Hitze verlangt,
        // plus ein Zug zurueck auf den Zielwert. Fehlen die Punkte, wird automatisch der naechste Stabilisator
        // aus dem Slot verbraucht. Hoehere Stufen kosten also mehr Stabilisatoren - gefaehrlich wird es erst,
        // wenn der Vorrat ausgeht.
        float hold = fieldTarget();
        if (instability > hold) {
            float demand = gain + (instability - hold) * FIELD_PULL;
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
                    pl.sendOverlayMessage(Component.translatable("message.cosmiccore.meltdown",
                            (MELTDOWN_TICKS - meltdown) / 20 + 1).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
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
        // Nachlaufzeit: Brennstoffwechsel und ein Puffer, der um "voll" pendelt, wechseln sonst jeden Tick die Phase -
        // das schaltet alle Bloecke und den Kern um und laesst die Animation flackern bzw. neu starten
        boolean generating = enabled && burnTime > 0 && collapseCooldown <= 0 && energy < capacity;
        idleTicks = generating ? 0 : idleTicks + 1;
        boolean active = enabled && collapseCooldown <= 0 && (generating || idleTicks <= IDLE_GRACE);
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
                        0.5F + level.getRandom().nextFloat() * 0.4F);
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
            e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 160));
            if (damage > 0) e.hurt(level.damageSources().magic(), (float) damage);
        }

        // Der Kern zerbricht
        if (CoreConfig.COLLAPSE_DESTROYS_CORE.get() && level.getBlockState(core).is(ModBlocks.SINGULARITY_CORE.get())) {
            level.levelEvent(2001, core, Block.getId(level.getBlockState(core)));
            level.setBlock(core, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            // ultimativ: der Kern bestand aus den vier Satelliten - sie zerbrechen mit
            CorePattern cp = pattern();
            if (cp != null) {
                for (BlockPos sat : cp.satellites(core)) {
                    if (level.getBlockState(sat).is(ModBlocks.SINGULARITY_CORE.get())) {
                        level.levelEvent(2001, sat, Block.getId(level.getBlockState(sat)));
                        level.setBlock(sat, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
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
    /** Haltepunkt, an dem der Traktorstrahl den Asteroiden zerbricht: so weit ueber der Bahnmitte der Laser. */
    public static final double HOLD_ABOVE = 1.6;

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

    /**
     * Asteroiden-Beute: wuerfelt die Loot-Tabelle cosmiccore:asteroid/small|medium|large (per Datapack anpassbar)
     * und legt sie in die Beute-Slots. Was nicht passt, faellt am Controller heraus.
     */
    private void collectLoot(ServerLevel level, CosmicAsteroid.Kind kind) {
        String name = kind.id;
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                dr4kun3.cosmiccore.CosmicCore.id("asteroid/" + name));
        var table = level.getServer().reloadableRegistries().getLootTable(key);
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.EMPTY);
        for (ItemStack drop : table.getRandomItems(params)) {
            ItemStack rest = storeInPorts(level, drop);
            // Ueberlauf: verdeckter Puffer im Controller, wird in die Ports nachgeschoben, sobald dort Platz ist
            for (int i = SLOT_LOOT_FIRST; i < LOOT_END && !rest.isEmpty(); i++) {
                rest = inventory.insertItem(i, rest, false);
            }
            if (!rest.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, rest);
            }
        }
    }

    /** Schaltet das Modell des Sternentrichters um (verbunden = animierter Trichter wird gerendert). */
    private static void setFunnelLinked(ServerLevel level, BlockPos top, boolean linked) {
        if (!level.hasChunkAt(top)) return;
        BlockState s = level.getBlockState(top);
        if (s.is(ModBlocks.STAR_FUNNEL.get()) && s.getValue(ModProperties.LINKED) != linked) {
            level.setBlock(top, s.setValue(ModProperties.LINKED, linked), Block.UPDATE_ALL);
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
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0F, 1.6F);
            pushVisuals(level);
        }
        // erst aufladen, dann feuern
        if (laserCharge < LASER_CHARGE_TICKS) {
            laserCharge++;
            if (laserCharge == LASER_CHARGE_TICKS) {
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0F, 1.8F);
            }
            return;
        }
        CosmicAsteroid.Kind variant = target.getKind();
        float burstSize = target.getSizeBlocks() * target.getPullScale();
        // mit Sternen-Trichter: am oberen Rand des Trichters zerbrechen, sonst knapp ueber den Lasern
        Vec3 hold = funnel ? Vec3.atCenterOf(center(level)).add(0, p.half() + 1.35 + SingularityCoreBlockEntity.FUNNEL_HEIGHT, 0)
                : muzzle.add(0, HOLD_ABOVE + target.getSizeBlocks() * 0.35, 0);
        int gained = target.harvestTick(hold, funnel);
        if (gained > 0) {
            // der Laser sammelt nur Beute - sie wird nicht automatisch verwertet (keine direkte Energie)
            collectLoot(level, variant);
            // grosses Energiepartikel sammelt sich ueber dem Trichter und wird an den Kern uebergeben
            if (level.getBlockEntity(center(level)) instanceof SingularityCoreBlockEntity core) core.markDelivery(level.getGameTime(), hold, burstSize);
            deliverTicks = 0;
            laserTarget = -1;
            pushVisuals(level);
        }
    }

    /**
     * Klaenge passend zur Uebergabe-Animation (Lautstaerke > 1 = groessere Hoerweite):
     * jedes ankommende Bruchstueck ein heller Ton (steigende Tonhoehe), dann das Formen der Energiekugel und
     * schliesslich ihre Aufnahme im Kern.
     */
    private void tickDelivery(ServerLevel level) {
        if (deliverTicks < 0) return;
        deliverTicks++;
        CorePattern p = pattern();
        if (p == null) {
            deliverTicks = -1;
            return;
        }
        Vec3 c = Vec3.atCenterOf(center(level));
        Vec3 mid = c.add(0, p.half() + (funnel ? 1.35 : 0.6), 0); // Trichteroeffnung
        int total = SingularityCoreBlockEntity.DELIVER_TICKS;
        // Ankunft der Bruchstuecke in der Trichteroeffnung (muss zu EnergyLaserFx.arrival passen)
        for (int k = 0; k < 24; k += 2) {
            if (deliverTicks == Math.round(SingularityCoreBlockEntity.fragmentArrival(k) * total)) {
                // Lautstaerke > 1 erhoeht nur die Reichweite - lauter wird es durch uebereinanderliegende Klaenge
                float pitch = 0.9F + k * 0.04F;
                level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 3.0F, pitch);
                level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 3.0F, pitch * 1.5F);
                level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6F, 0.7F + k * 0.05F);
            }
        }
        int formed = Math.round(SingularityCoreBlockEntity.SINK_START * total); // Energie faellt in den Kern
        if (deliverTicks == formed) {
            level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 3.0F, 1.4F);
            level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 3.0F, 0.8F);
        } else if (deliverTicks >= SingularityCoreBlockEntity.DELIVER_TICKS) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.CONDUIT_ACTIVATE, SoundSource.BLOCKS, 4.0F, 1.3F);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 2.5F, 1.2F);
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30, 0.3, 0.3, 0.3, 0.15);
            deliverTicks = -1;
        }
    }

    /**
     * Einmaliges Erwachen des ultimativen Kerns: alle Spieler der Dimension sehen den Himmelseffekt
     * (Lichtsaeule ins All, Himmelsblitz, Polarlichter rund um die Welt, Schockwelle, Sternschnuppen).
     */
    private void awaken(ServerLevel level) {
        awakened = true;
        setChanged();
        pushVisuals(level);
        BlockPos c = center(level);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersInDimension(level,
                new dr4kun3.cosmiccore.network.AwakeningPayload(c, size.size()));
    }

    /** Uebergibt die Darstellungswerte an den Singularitaetskern (der sie an die Clients schickt). */
    private void pushVisuals(ServerLevel level) {
        if (!formed || size == null) return;
        BlockPos core = center(level);
        if (level.hasChunkAt(core) && level.getBlockEntity(core) instanceof SingularityCoreBlockEntity be) {
            be.setColors(hasColorModule() ? colors : dr4kun3.cosmiccore.color.CoreColors.DEFAULT);
            be.setAwakened(awakened && size.isUltimate());
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
        long t = level.getOverworldClockTime() % 24000L;
        return t >= 13000L && t < 23000L;
    }

    /** Moonphase 0 = Vollmond, 4 = Neumond (gleiche Formel wie Vanilla). */
    private static int moonPhase(ServerLevel level) {
        return (int) ((level.getOverworldClockTime() / 24000L) % 8L);
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
            setFunnelLinked(level, above, funnel);
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
                // verschmolzener Mittelkern abgebaut, die vier Satelliten stehen aber noch: er bildet sich neu
                BlockPos mid = pattern.center(worldPosition, bestDir);
                if (pattern.hasFusedCore() && level.getBlockState(mid).isAir()) {
                    level.setBlock(mid, ModBlocks.SINGULARITY_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
                    if (level.getBlockEntity(mid) instanceof SingularityCoreBlockEntity fused) {
                        fused.setFused(true);
                        fused.markFormed(level.getGameTime());
                    }
                    applyPhase(level);
                    pushVisuals(level);
                }
            }
        } else {
            skyLink = false;
            funnel = false;
            if (laserArray) {
                laserArray = false;
                laserTarget = -1;
                setLasersLinked(level, center(level).above(pattern.half() + 1), false);
            }
            setFunnelLinked(level, center(level).above(pattern.half() + 1), false);
            if (formed) onUnformed(level);
        }
    }

    private void onFormed(ServerLevel level) {
        formed = true;
        // ultimativ: die vier Satellitenkerne verschmelzen zum Kern in der Mitte
        CorePattern fp = pattern();
        if (fp != null && fp.hasFusedCore()) {
            BlockPos mid = center(level);
            if (level.getBlockState(mid).isAir()) {
                level.setBlock(mid, ModBlocks.SINGULARITY_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
            }
            if (level.getBlockEntity(mid) instanceof SingularityCoreBlockEntity fused) fused.setFused(true);
        }
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
        removeFusedCore(level, center(level));
        Vec3 c = Vec3.atCenterOf(center(level));
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5F, 0.6F);
        changedAndSync();
    }

    /** Der verschmolzene Mittelkern des ultimativen Kerns existiert nur, solange die Struktur steht (kein Drop). */
    private static void removeFusedCore(ServerLevel level, BlockPos mid) {
        if (level.hasChunkAt(mid) && level.getBlockEntity(mid) instanceof SingularityCoreBlockEntity core && core.isFused()) {
            level.setBlock(mid, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Spiegelt die aktuelle Phase auf alle Strukturbloecke (Textur-Variante, Licht, Animation). */
    private void applyPhase(ServerLevel level) {
        CorePattern p = pattern();
        if (p != null) setPhaseAround(level, p, center(level), facing(), phase, worldPosition);
    }

    private static void setPhaseAround(ServerLevel level, CorePattern pattern, BlockPos center, Direction facing, CorePhase target,
                                       @Nullable BlockPos skip) {
        float radius = pattern.shellRadius();
        pattern.forEach(center, facing, (pos, cell) -> {
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
     * "egal"-Zellen bleiben unberuehrt. Rechts neben dem Controller (von vorn gesehen) entsteht der Energie-Port, links
     * der Item-Port - zusammen bilden die drei eine Steuerkonsole, an deren Seiten Rohre und Kabel andocken.
     */
    private void buildStructure(ServerLevel level) {
        CorePattern p = pattern();
        if (p == null || formed) return;
        Direction facing = facing();
        BlockPos center = p.center(worldPosition, facing);
        BlockPos energyPort = worldPosition.relative(dr4kun3.cosmiccore.block.Terminal.right(facing));
        BlockPos itemPort = worldPosition.relative(dr4kun3.cosmiccore.block.Terminal.left(facing));
        p.forEach(center, facing, (pos, cell) -> {
            if (cell == CorePattern.Cell.ANY || pos.equals(worldPosition)) return;
            if (CorePattern.isDock(pos, worldPosition, facing)) return; // frei fuer Rohre und Kabel
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
            // Zustand aus den Nachbarn ableiten (Ports erkennen so die Steuerkonsole)
            level.setBlock(pos, Block.updateFromNeighbourShapes(target, level, pos), Block.UPDATE_ALL);
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

    /** Controller wird entfernt: Struktur zuruecksetzen und Inventar fallen lassen (ab 1.21.5 statt Block#onRemove). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel sl) onControllerRemoved(sl, state);
    }

    /** Wird vom Block aufgerufen, bevor der Controller verschwindet. */
    public void onControllerRemoved(ServerLevel level, BlockState oldState) {
        CorePattern p = pattern();
        if (p != null && oldState.hasProperty(ControllerBlock.FACING)) {
            BlockPos center = p.center(worldPosition, oldState.getValue(ControllerBlock.FACING));
            p.forEach(center, oldState.getValue(ControllerBlock.FACING), (pos, cell) -> {
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
            removeFusedCore(level, p.center(worldPosition, oldState.getValue(ControllerBlock.FACING)));
            BlockPos top = p.center(worldPosition, oldState.getValue(ControllerBlock.FACING)).above(p.half() + 1);
            setLasersLinked(level, top, false);
            setFunnelLinked(level, top, false);
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
        player.sendSystemMessage(Component.translatable("message.cosmiccore.status.header")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        if (size == null) {
            player.sendSystemMessage(Component.translatable("message.cosmiccore.status.no_size").withStyle(ChatFormatting.GRAY));
            return;
        }
        revalidate(level);

        if (!formed) {
            CorePattern.Report r = lastReport;
            if (r == null) {
                player.sendSystemMessage(Component.translatable("message.cosmiccore.status.pending").withStyle(ChatFormatting.GRAY));
                return;
            }
            player.sendSystemMessage(Component.translatable("message.cosmiccore.status.invalid", r.mismatches())
                    .withStyle(ChatFormatting.RED));
            BlockPos e = r.firstError();
            if (e != null) {
                player.sendSystemMessage(Component.translatable("message.cosmiccore.status.error_at",
                        e.getX(), e.getY(), e.getZ(), r.expected()).withStyle(ChatFormatting.GRAY));
                level.sendParticles(ParticleTypes.END_ROD, e.getX() + 0.5, e.getY() + 0.5, e.getZ() + 0.5, 15, 0.3, 0.3, 0.3, 0.02);
            }
            return;
        }

        player.sendSystemMessage(Component.translatable("message.cosmiccore.status.formed").withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.translatable("message.cosmiccore.status.energy",
                fmt(energy), fmt(getCapacity())));
        player.sendSystemMessage(Component.translatable("message.cosmiccore.status.output",
                fmt(lastOutput), Math.round(100f * resonance / CoreConfig.RAMP_UP_TICKS.get())));
        ChatFormatting color = instability < 40f ? ChatFormatting.GREEN : instability < UNSTABLE_ON ? ChatFormatting.YELLOW : ChatFormatting.RED;
        player.sendSystemMessage(Component.translatable("message.cosmiccore.status.instability",
                String.format("%.1f", instability)).withStyle(color));
        player.sendSystemMessage(Component.translatable("message.cosmiccore.status.cosmic",
                String.format("%.2f", cosmicOutput(level))));
        if (collapseCooldown > 0) {
            player.sendSystemMessage(Component.translatable("message.cosmiccore.status.collapsed", collapseCooldown / 20)
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    // ------------------------------------------------------------------ Persistenz / Sync

    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
        super.saveAdditional(tag);
        tag.putInt("size", size == null ? 0 : size.size());
        tag.putInt("scale", scale);
        tag.putInt("unlockedScale", unlockedScale);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("preview", preview);
        tag.putBoolean("withFunnel", withFunnel);
        tag.putBoolean("withLasers", withLasers);
        tag.putBoolean("antiAsteroid", antiAsteroid);
        tag.putInt("colorMask", colors.mask());
        tag.putIntArray("colors", colors.colors());
        tag.putInt("overdrive", overdrive);
        tag.putBoolean("formed", formed);
        tag.putLong("energy", energy);
        tag.putInt("burnTime", burnTime);
        tag.putInt("burnTotal", burnTotal);
        tag.putInt("burnPower", burnPower);
        tag.putFloat("burnHeat", burnHeat);
        tag.putFloat("field", field);
        tag.putFloat("fieldMax", fieldMax);
        tag.putFloat("instability", instability);
        tag.putInt("resonance", resonance);
        tag.putInt("collapseCooldown", collapseCooldown);
        tag.putInt("meltdown", meltdown);
        tag.putBoolean("awakened", awakened);
        tag.putString("phase", phase.getSerializedName());
        inventory.serialize(tag.child("inventory"));
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        CompoundTag tag = dr4kun3.cosmiccore.data.NbtBridge.read(input);
        // Controller aus aelteren Versionen (ohne Groessenwahl) waren immer 7x7x7
        size = tag.contains("size") ? ReactorSize.bySize(tag.getIntOr("size", 0)) : ReactorSize.MEDIUM;
        scale = Math.max(1, tag.getIntOr("scale", 0));
        // aeltere Speicherstaende: bereits erreichte Stufen gelten als freigeschaltet
        unlockedScale = tag.contains("unlockedScale") ? Math.max(1, tag.getIntOr("unlockedScale", 0)) : scale;
        if (size != null) scale = Math.min(scale, size.maxScale());
        enabled = !tag.contains("enabled") || tag.getBooleanOr("enabled", false);
        preview = tag.getBooleanOr("preview", false);
        antiAsteroid = tag.getBooleanOr("antiAsteroid", false);
        if (tag.contains("colors")) {
            colors = new dr4kun3.cosmiccore.color.CoreColors.Config(tag.getIntOr("colorMask", 0), tag.getIntArray("colors").orElse(new int[0]));
        } else if (tag.contains("colorScheme")) {
            // aelterer Stand: ein Schema-Index -> passende Vorgabe uebernehmen
            var presets = dr4kun3.cosmiccore.color.CoreColors.PRESETS;
            colors = presets.get(Math.floorMod(tag.getIntOr("colorScheme", 0), presets.size())).config();
        }
        withFunnel = tag.getBooleanOr("withFunnel", false);
        withLasers = tag.getBooleanOr("withLasers", false);
        overdrive = Math.max(0, tag.getIntOr("overdrive", 0));
        if (size != null) overdrive = Math.min(overdrive, size.maxOverdriveTotal());
        formed = tag.getBooleanOr("formed", false);
        energy = tag.getLongOr("energy", 0L);
        burnTime = tag.getIntOr("burnTime", 0);
        burnTotal = tag.getIntOr("burnTotal", 0);
        burnPower = tag.getIntOr("burnPower", 0);
        burnHeat = tag.contains("burnHeat") ? tag.getFloatOr("burnHeat", 0.0F) : 1.0f;
        field = tag.getFloatOr("field", 0.0F);
        fieldMax = tag.getFloatOr("fieldMax", 0.0F);
        instability = tag.getFloatOr("instability", 0.0F);
        resonance = tag.getIntOr("resonance", 0);
        collapseCooldown = tag.getIntOr("collapseCooldown", 0);
        meltdown = tag.getIntOr("meltdown", 0);
        awakened = tag.getBooleanOr("awakened", false);
        phase = CorePhase.byName(tag.getStringOr("phase", ""));
        if (tag.contains("inventory")) {
            inventory.deserialize(input.childOrEmpty("inventory"));
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
        if (!inventory.getStackInSlot(SLOT_FUEL).isEmpty()) tag.put("fuel", writeStack(registries, inventory.getStackInSlot(SLOT_FUEL)));
        if (!inventory.getStackInSlot(SLOT_STAB).isEmpty()) tag.put("stab", writeStack(registries, inventory.getStackInSlot(SLOT_STAB)));
        tag.putInt("loot", lootCount());
        tag.putInt("fuelSec", fuelSeconds);
        tag.putInt("stabSec", stabSeconds);
        tag.putBoolean("energyPort", energyPort);
        net.minecraft.nbt.ListTag lootList = new net.minecraft.nbt.ListTag();
        for (int i = SLOT_LOOT_FIRST; i < LOOT_END; i++) {
            ItemStack st = inventory.getStackInSlot(i);
            if (!st.isEmpty()) lootList.add(writeStack(registries, st));
        }
        tag.put("lootItems", lootList);
        return tag;
    }

    private int lootSignature() {
        int h = 0;
        for (int i = SLOT_LOOT_FIRST; i < LOOT_END; i++) h = h * 31 + inventory.getStackInSlot(i).getItem().hashCode();
        return h;
    }

    /** Erster Item-Port der Struktur (fuer den Lager-Knopf im Menue). */
    @Nullable
    private ItemPortBlockEntity firstItemPort(ServerLevel level) {
        CorePattern p = pattern();
        if (p == null) return null;
        ItemPortBlockEntity[] found = {null};
        p.forEach(center(level), (pos, cell) -> {
            if (found[0] == null && (cell == CorePattern.Cell.HULL || cell == CorePattern.Cell.SLOT) && level.hasChunkAt(pos)
                    && level.getBlockEntity(pos) instanceof ItemPortBlockEntity ip) {
                found[0] = ip;
            }
        });
        return found[0];
    }

    /** Legt Beute in die Lager der Item-Ports der Struktur; zurueck kommt, was nirgends passt. */
    private ItemStack storeInPorts(ServerLevel level, ItemStack stack) {
        CorePattern p = pattern();
        if (p == null || !formed) return stack;
        ItemStack[] rest = {stack};
        p.forEach(center(level), (pos, cell) -> {
            if (rest[0].isEmpty() || (cell != CorePattern.Cell.HULL && cell != CorePattern.Cell.SLOT) || !level.hasChunkAt(pos)) return;
            if (level.getBlockEntity(pos) instanceof ItemPortBlockEntity port) rest[0] = port.store(rest[0]);
        });
        return rest[0];
    }

    /** Verdeckten Beute-Puffer (aeltere Spielstaende, volle Ports) in die Item-Ports nachschieben. */
    private void flushLoot(ServerLevel level) {
        for (int i = SLOT_LOOT_FIRST; i < LOOT_END; i++) {
            ItemStack st = inventory.getStackInSlot(i);
            if (st.isEmpty()) continue;
            ItemStack rest = storeInPorts(level, st.copy());
            if (rest.getCount() != st.getCount()) inventory.setStackInSlot(i, rest);
        }
    }

    private boolean scanEnergyPort(ServerLevel level) {
        CorePattern p = pattern();
        if (p == null) return false;
        boolean[] found = {false};
        p.forEach(center(level), (pos, cell) -> {
            if (!found[0] && (cell == CorePattern.Cell.HULL || cell == CorePattern.Cell.SLOT) && level.hasChunkAt(pos)
                    && level.getBlockState(pos).is(ModBlocks.ENERGY_PORT.get())) {
                found[0] = true;
            }
        });
        return found[0];
    }

    /**
     * Wie lange reichen Brennstoff und Stabilisator bei der aktuellen Stufe, Overdrive, Modulen und Umgebung?
     * Brennstoff: Restbrenndauer / Verbrauch pro Tick. Stabilisator: Feldpunkte / Hitze pro Tick (im Gleichgewicht
     * verbraucht das Feld genau so viel, wie die Reaktion an Instabilitaet erzeugt).
     */
    private void updateForecast(ServerLevel level) {
        if (!formed || !enabled || collapseCooldown > 0 || size == null) {
            fuelSeconds = stabSeconds = TIME_OFF;
            return;
        }
        ItemStack fuelStack = inventory.getStackInSlot(SLOT_FUEL);
        CoreFuel slotFuel = ModDataMaps.fuel(fuelStack);
        long units = Math.max(0, burnTime) + (slotFuel == null ? 0L : (long) fuelStack.getCount() * slotFuel.burnTime());
        float perTick = scale * fuelUseFactor();
        if (energy >= getCapacity()) {
            fuelSeconds = stabSeconds = TIME_PAUSED;
            return;
        }
        fuelSeconds = units <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.ceil(units / Math.max(1e-6, perTick) / 20.0));
        // Hitze der laufenden (sonst der naechsten) Ladung
        int power = burnTime > 0 ? burnPower : slotFuel != null ? slotFuel.power() : 0;
        float heat = burnTime > 0 ? burnHeat : slotFuel != null ? slotFuel.heat() : 0f;
        double gain = CoreConfig.INSTABILITY_RATE.get() * (power / 1000.0) * heat * instabilityScale() * cosmicInstability(level)
                * stabilityFactor();
        if (instability > UNSTABLE_ON) gain *= 0.4;
        ItemStack stabStack = inventory.getStackInSlot(SLOT_STAB);
        CoreStabilizer stab = ModDataMaps.stabilizer(stabStack);
        double points = Math.max(0f, field) + (stab == null ? 0.0 : stabStack.getCount() * stabilizerPoints(stab) * size.stabilizerFactor());
        if (units <= 0 || gain <= 0) {
            stabSeconds = points <= 0 ? 0 : TIME_NONE;
        } else {
            stabSeconds = points <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.ceil(points / gain / 20.0));
        }
    }

    public int getFuelSeconds() {
        return fuelSeconds;
    }

    public int getStabSeconds() {
        return stabSeconds;
    }

    public boolean hasEnergyPort() {
        return energyPort;
    }

    public java.util.List<ItemStack> getClientLootItems() {
        return clientLootItems;
    }

    private int lootCount() {
        int n = 0;
        for (int i = SLOT_LOOT_FIRST; i < LOOT_END; i++) n += inventory.getStackInSlot(i).getCount();
        return n;
    }

    /** Schreibt einen Item-Stapel als NBT. */
    private static net.minecraft.nbt.Tag writeStack(HolderLookup.Provider registries, ItemStack stack) {
        return ItemStack.CODEC.encodeStart(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), stack)
                .result().orElseGet(CompoundTag::new);
    }

    /** Liest einen Item-Stapel aus NBT (leer, wenn ungueltig). */
    private static ItemStack readStack(HolderLookup.Provider registries, net.minecraft.nbt.Tag tag) {
        return ItemStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag)
                .result().orElse(ItemStack.EMPTY);
    }

    public ItemStack getClientFuel() {
        return clientFuel;
    }

    public ItemStack getClientStab() {
        return clientStab;
    }

    public int getClientLoot() {
        return clientLoot;
    }

    @Override
    public void handleUpdateTag(net.minecraft.world.level.storage.ValueInput input) {
        CompoundTag tag = dr4kun3.cosmiccore.data.NbtBridge.read(input);
        HolderLookup.Provider registries = input.lookup();
        size = ReactorSize.bySize(tag.getIntOr("size", 0));
        preview = tag.getBooleanOr("preview", false);
        withFunnel = tag.getBooleanOr("withFunnel", false);
        withLasers = tag.getBooleanOr("withLasers", false);
        formed = tag.getBooleanOr("formed", false);
        clientFuel = tag.contains("fuel") ? readStack(registries, tag.getCompoundOrEmpty("fuel")) : ItemStack.EMPTY;
        clientStab = tag.contains("stab") ? readStack(registries, tag.getCompoundOrEmpty("stab")) : ItemStack.EMPTY;
        clientLoot = tag.getIntOr("loot", 0);
        fuelSeconds = tag.contains("fuelSec") ? tag.getIntOr("fuelSec", 0) : TIME_NONE;
        stabSeconds = tag.contains("stabSec") ? tag.getIntOr("stabSec", 0) : TIME_NONE;
        energyPort = tag.getBooleanOr("energyPort", false);
        clientLootItems.clear();
        for (net.minecraft.nbt.Tag t : tag.getListOrEmpty("lootItems")) {
            ItemStack st = readStack(registries, t);
            if (!st.isEmpty()) clientLootItems.add(st);
        }
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.world.level.storage.ValueInput input) {
        handleUpdateTag(input);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------------ innere Klassen

    /**
     * Brennstoff, Stabilisator, zwei Modul-Slots und die Beute-Slots. Auch clientseitig im Menue benutzt
     * (dort ohne Block-Entity).
     */
    public static class CoreInventory extends StackHandler {
        private final Runnable onChange;

        public CoreInventory(Runnable onChange) {
            super(INVENTORY_SIZE);
            this.onChange = onChange;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_FUEL -> ModDataMaps.fuel(stack) != null;
                case SLOT_STAB -> ModDataMaps.stabilizer(stack) != null;
                case SLOT_EFFICIENCY -> stack.is(ModItems.EFFICIENCY_MODULE.get());
                case SLOT_STABILITY -> stack.is(ModItems.STABILITY_MODULE.get());
                case SLOT_ANTI -> stack.is(ModItems.ANTI_ASTEROID_MODULE.get());
                case SLOT_COLOR -> stack.is(ModItems.COLOR_MODULE.get());
                default -> true; // Beute wird nur intern eingefuegt; GUI und Rohre sperren das Einfuegen
            };
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot == SLOT_ANTI || slot == SLOT_COLOR) return 1;
            return slot == SLOT_EFFICIENCY || slot == SLOT_STABILITY ? MAX_MODULES : super.getSlotLimit(slot);
        }

        /** Aeltere Speicherstaende hatten nur 2 Slots - die Groesse bleibt immer INVENTORY_SIZE. */
        @Override
        public void deserialize(net.minecraft.world.level.storage.ValueInput input) {
            setSize(INVENTORY_SIZE);
            input.listOrEmpty("Items", net.minecraft.world.ItemStackWithSlot.CODEC).forEach(slot -> {
                if (slot.isValidInContainer(INVENTORY_SIZE)) stacks.set(slot.slot(), slot.stack());
            });
            onLoad();
        }

        @Override
        protected void onContentsChanged(int slot) {
            onChange.run();
        }
    }

    /** Scheitert die Struktur nur an einem fehlenden Pflicht-Port (Uebersetzungsschluessel der Meldung)? */
    private boolean missing(String key) {
        return lastReport != null && lastReport.expected() != null
                && lastReport.expected().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                && tc.getKey().equals(key);
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
                case ControllerMenu.D_UNLOCKED -> unlockedScale;
                case ControllerMenu.D_COLOR_MASK -> colors.mask();
                case ControllerMenu.D_COLOR_FIRST, ControllerMenu.D_COLOR_FIRST + 1, ControllerMenu.D_COLOR_FIRST + 2,
                     ControllerMenu.D_COLOR_FIRST + 3, ControllerMenu.D_COLOR_FIRST + 4, ControllerMenu.D_COLOR_FIRST + 5,
                     ControllerMenu.D_COLOR_FIRST + 6, ControllerMenu.D_COLOR_FIRST + 7 -> colors.colors()[index - ControllerMenu.D_COLOR_FIRST];
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
                        | (burnTime > 0 || !inventory.getStackInSlot(SLOT_FUEL).isEmpty() ? ControllerMenu.F_HAS_FUEL : 0)
                        | (hasAntiAsteroidModule() ? ControllerMenu.F_ANTI_INSTALLED : 0)
                        | (antiAsteroid && hasAntiAsteroidModule() ? ControllerMenu.F_ANTI_ON : 0)
                        | (hasColorModule() ? ControllerMenu.F_COLOR_INSTALLED : 0)
                        | (!formed && missing("message.cosmiccore.missing_energy_port") ? ControllerMenu.F_NO_ENERGY_PORT : 0)
                        | (!formed && missing("message.cosmiccore.missing_item_port") ? ControllerMenu.F_NO_ITEM_PORT : 0);
                case ControllerMenu.D_COSMIC -> (int) Math.round(lastCosmic * 100.0);
                case ControllerMenu.D_BURN -> burnTotal <= 0 ? 0 : Math.round(1000f * burnTime / burnTotal);
                case ControllerMenu.D_STAB -> fieldMax <= 0 ? 0 : Math.round(1000f * Math.min(1f, field / fieldMax));
                case ControllerMenu.D_COLLAPSE -> collapseCooldown / 20;
                case ControllerMenu.D_OVERDRIVE -> overdrive;
                case ControllerMenu.D_MELTDOWN -> meltdown <= 0 ? 0 : (MELTDOWN_TICKS - meltdown) / 20 + 1;
                case ControllerMenu.D_ERRORS -> lastReport == null || formed ? 0 : Math.min(32767, lastReport.mismatches());
                case ControllerMenu.D_FUEL_TIME_LO -> fuelSeconds & 0xFFFF;
                case ControllerMenu.D_FUEL_TIME_HI -> fuelSeconds >>> 16;
                case ControllerMenu.D_STAB_TIME_LO -> stabSeconds & 0xFFFF;
                case ControllerMenu.D_STAB_TIME_HI -> stabSeconds >>> 16;
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

    /** Fuer Trichter/Rohre: Brennstoff, Stabilisator und Module einfuegen, Asteroiden-Beute entnehmen. */
    private final class InsertOnlyView implements ItemHandler {
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
            return slot < SLOT_LOOT_FIRST || slot >= SLOT_ANTI ? inventory.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= SLOT_LOOT_FIRST && slot < LOOT_END ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return (slot < SLOT_LOOT_FIRST || slot >= SLOT_ANTI) && inventory.isItemValid(slot, stack);
        }
    }

    private final class EnergyView implements EnergyStore {
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
