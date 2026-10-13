package dr4kun3.cosmiccore.block;

import dr4kun3.cosmiccore.block.entity.ResonatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Resonanzspule und Fokuskristall: Strukturbloecke mit eigener Animation (siehe ResonatorRenderer). */
public class ResonatorBlock extends StructureBlock implements EntityBlock {
    public ResonatorBlock(Properties props) {
        super(props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResonatorBlockEntity(pos, state);
    }
}
