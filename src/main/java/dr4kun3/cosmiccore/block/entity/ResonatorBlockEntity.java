package dr4kun3.cosmiccore.block.entity;

import dr4kun3.cosmiccore.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Traegt nur die Animation von Spule und Kristall; der Zustand steckt in der PHASE des Blockstates. */
public class ResonatorBlockEntity extends BlockEntity {
    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 0}, {0, -1, 0}};

    // nur clientseitig: Richtung zum Kern (fuer die Ausrichtung der Animation)
    @Nullable
    private int[] coreOffset;
    private long lastLookup = Long.MIN_VALUE;

    public ResonatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESONATOR.get(), pos, state);
    }

    /** Versatz zum Singularitaetskern (sucht entlang der Achsen und Diagonalen), null wenn keiner in Reichweite. */
    @Nullable
    public int[] findCoreOffset(long gameTime) {
        if (level == null) return null;
        if (gameTime - lastLookup < 40 && lastLookup != Long.MIN_VALUE) return coreOffset;
        lastLookup = gameTime;
        coreOffset = null;
        for (int dist = 1; dist <= 4 && coreOffset == null; dist++) {
            for (int[] d : DIRS) {
                BlockPos p = worldPosition.offset(d[0] * dist, d[1] * dist, d[2] * dist);
                if (level.getBlockState(p).is(ModBlocks.SINGULARITY_CORE.get())) {
                    coreOffset = new int[]{d[0] * dist, d[1] * dist, d[2] * dist};
                    break;
                }
            }
        }
        return coreOffset;
    }
}
