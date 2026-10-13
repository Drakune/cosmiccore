package dev.cosmiccore.block.entity;

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

    protected PortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void link(@Nullable BlockPos pos) {
        if (!Objects.equals(controllerPos, pos)) {
            controllerPos = pos;
            setChanged();
        }
    }

    /** Liefert den Controller nur, wenn die Struktur gerade vollstaendig gebildet ist. */
    @Nullable
    protected ControllerBlockEntity controller() {
        if (controllerPos == null || level == null || !level.hasChunkAt(controllerPos)) return null;
        if (level.getBlockEntity(controllerPos) instanceof ControllerBlockEntity c && c.isFormed()) return c;
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) tag.putLong("controller", controllerPos.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPos = tag.contains("controller") ? BlockPos.of(tag.getLong("controller")) : null;
    }
}
