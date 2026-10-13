package dev.cosmiccore.block;

import dev.cosmiccore.block.entity.StarFunnelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Sternentrichter: wird auf die Deckenmitte des Reaktors gesetzt. Mit freiem Himmel darueber verdoppelt er den
 * Himmelsbonus und saugt sichtbar kosmische Energie aus dem All (Wirbel siehe StarFunnelRenderer).
 */
public class StarFunnelBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(4, 0, 4, 12, 4, 12),
            Block.box(2, 4, 2, 14, 9, 14),
            Block.box(0, 9, 0, 16, 13, 16));

    public StarFunnelBlock(Properties props) {
        super(props);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StarFunnelBlockEntity(pos, state);
    }
}
