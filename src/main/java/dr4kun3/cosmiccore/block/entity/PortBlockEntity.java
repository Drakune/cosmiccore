package dr4kun3.cosmiccore.block.entity;

import dr4kun3.cosmiccore.block.CorePhase;
import dr4kun3.cosmiccore.block.ModProperties;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Gemeinsame Basis der Ports: kennt den Controller, zu dessen Struktur sie gehören. */
public abstract class PortBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos controllerPos;
    /** Nur Client: Zeitpunkt, an dem die Struktur als gebildet gesehen wurde (für die Einblend-Animation). */
    public long clientAppear = Long.MIN_VALUE;
    /** Nur Client: Seiten, an denen etwas angedockt ist (Bitmaske), und wann zuletzt geprüft. */
    public int clientConnMask;
    public long clientConnTick = Long.MIN_VALUE;


    /**
     * Gemeinsame Grundlage von Energie- und Item-Port.
     *
     * @param type Typ des Blockinhalts
     * @param pos Position
     * @param state Blockzustand
     */
    protected PortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Client: merkt sich den Wechsel ruhend -> gebildet für die Einblend-Animation (beim Laden einer fertigen Struktur
     * gibt es keinen Wechsel, dann ist die Station sofort da).
     *
     * @param state neuer Blockzustand
     */
    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        BlockState old = getBlockState();
        super.setBlockState(state);
        if (level == null || !level.isClientSide || !old.hasProperty(ModProperties.PHASE)
                || !state.hasProperty(ModProperties.PHASE)) {
            return;
        }
        CorePhase before = old.getValue(ModProperties.PHASE);
        CorePhase now = state.getValue(ModProperties.PHASE);
        if (before == CorePhase.DORMANT && now != CorePhase.DORMANT) {
            clientAppear = level.getGameTime();
        } else if (now == CorePhase.DORMANT) {
            clientAppear = Long.MIN_VALUE;
        }
    }

    /**
     * Verbindet den Port mit einem Controller oder löst die Verbindung; der Client erfährt es sofort.
     *
     * @param pos Position des Controllers oder {@code null} zum Lösen
     */
    public void link(@Nullable BlockPos pos) {
        if (!Objects.equals(controllerPos, pos)) {
            controllerPos = pos;
            setChanged();
            // der Client braucht den Controller für das Timing der Einblend-Animation
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Nullable
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    /**
     * Daten für den Client: nur die Position des Controllers.
     *
     * @param registries Registry-Zugriff
     * @return die zu sendenden Daten
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (controllerPos != null) tag.putLong("controller", controllerPos.asLong());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Öffentlicher Zugriff auf den angeschlossenen Controller (nur bei gebildeter Struktur).
     *
     * @return der Controller oder {@code null}
     */
    @Nullable
    public ControllerBlockEntity linkedController() {
        return controller();
    }

    /**
     * Liefert den Controller nur, wenn die Struktur gerade vollständig gebildet und geladen ist.
     *
     * @return der Controller oder {@code null}
     */
    @Nullable
    protected ControllerBlockEntity controller() {
        if (controllerPos == null || level == null || !level.hasChunkAt(controllerPos)) return null;
        if (level.getBlockEntity(controllerPos) instanceof ControllerBlockEntity c && c.isFormed()) return c;
        return null;
    }

    /**
     * Speichert die Position des Controllers.
     *
     * @param tag Ziel der Daten
     * @param registries Registry-Zugriff
     */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) tag.putLong("controller", controllerPos.asLong());
    }

    /**
     * Lädt die Position des Controllers.
     *
     * @param tag gespeicherte Daten
     * @param registries Registry-Zugriff
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPos = tag.contains("controller") ? BlockPos.of(tag.getLong("controller")) : null;
    }
}
