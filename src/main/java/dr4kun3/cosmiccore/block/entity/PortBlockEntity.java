package dr4kun3.cosmiccore.block.entity;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Gemeinsame Basis der Ports: kennt den Controller, zu dessen Struktur sie gehoeren. */
public abstract class PortBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos controllerPos;
    /** Nur Client: Zeitpunkt, an dem die Struktur als gebildet gesehen wurde (fuer die Einblend-Animation). */
    public long clientAppear = Long.MIN_VALUE;
    /** Nur Client: Seiten, an denen etwas angedockt ist (Bitmaske), und wann zuletzt geprueft. */
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
     * Client: merkt sich den Wechsel ruhend -> gebildet fuer die Einblend-Animation (beim Laden einer fertigen Struktur
     * gibt es keinen Wechsel, dann ist die Station sofort da).
     *
     * @param state neuer Blockzustand
     */
    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        BlockState old = getBlockState();
        super.setBlockState(state);
        if (level == null || !level.isClientSide || !old.hasProperty(dr4kun3.cosmiccore.block.ModProperties.PHASE)
                || !state.hasProperty(dr4kun3.cosmiccore.block.ModProperties.PHASE)) {
            return;
        }
        dr4kun3.cosmiccore.block.CorePhase before = old.getValue(dr4kun3.cosmiccore.block.ModProperties.PHASE);
        dr4kun3.cosmiccore.block.CorePhase now = state.getValue(dr4kun3.cosmiccore.block.ModProperties.PHASE);
        if (before == dr4kun3.cosmiccore.block.CorePhase.DORMANT && now != dr4kun3.cosmiccore.block.CorePhase.DORMANT) {
            clientAppear = level.getGameTime();
        } else if (now == dr4kun3.cosmiccore.block.CorePhase.DORMANT) {
            clientAppear = Long.MIN_VALUE;
        }
    }

    /**
     * Verbindet den Port mit einem Controller oder loest die Verbindung; der Client erfaehrt es sofort.
     *
     * @param pos Position des Controllers oder {@code null} zum Loesen
     */
    public void link(@Nullable BlockPos pos) {
        if (!Objects.equals(controllerPos, pos)) {
            controllerPos = pos;
            setChanged();
            // der Client braucht den Controller fuer das Timing der Einblend-Animation
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * Position des Controllers, zu dem der Port gehoert (auch auf dem Client bekannt).
     *
     * @return die Position oder {@code null}
     */
    @Nullable
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    /**
     * Daten fuer den Client: nur die Position des Controllers.
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

    /**
     * Paket, mit dem Aenderungen an den Client gehen.
     *
     * @return das Paket mit den Daten aus {@link #getUpdateTag}
     */
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Oeffentlicher Zugriff auf den angeschlossenen Controller (nur bei gebildeter Struktur).
     *
     * @return der Controller oder {@code null}
     */
    @Nullable
    public ControllerBlockEntity linkedController() {
        return controller();
    }

    /**
     * Liefert den Controller nur, wenn die Struktur gerade vollstaendig gebildet und geladen ist.
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
     * Speichert die Position des verbundenen Controllers.
     *
     * @param tag Ziel der Daten
     */
    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
        super.saveAdditional(tag);
        if (controllerPos != null) tag.putLong("controller", controllerPos.asLong());
    }

    /**
     * Laedt die Position des verbundenen Controllers.
     *
     * @param input gespeicherte Daten
     */
    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        CompoundTag tag = dr4kun3.cosmiccore.data.NbtBridge.read(input);
        controllerPos = tag.contains("controller") ? BlockPos.of(tag.getLongOr("controller", 0L)) : null;
    }
}
