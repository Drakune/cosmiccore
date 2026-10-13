package dev.cosmiccore.block.entity;

import dev.cosmiccore.block.CorePhase;
import dev.cosmiccore.block.ModProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Traeger der Animation: Der Renderer und die Partikel haengen hier dran, die Logik sitzt im Controller. */
public class SingularityCoreBlockEntity extends BlockEntity {
    public SingularityCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CORE.get(), pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SingularityCoreBlockEntity be) {
        CorePhase phase = state.getValue(ModProperties.PHASE);
        if (phase == CorePhase.DORMANT) return;
        RandomSource r = level.random;
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;

        // Partikel die in die Singularitaet gesogen werden (PORTAL startet bei pos+vel und endet bei pos)
        int pulls = switch (phase) {
            case FORMED -> r.nextInt(4) == 0 ? 1 : 0;
            case ACTIVE -> 2;
            default -> 3;
        };
        for (int i = 0; i < pulls; i++) {
            double dx = r.nextGaussian(), dy = r.nextGaussian(), dz = r.nextGaussian();
            double len = Math.max(0.001, Math.sqrt(dx * dx + dy * dy + dz * dz));
            double rad = 1.6 + r.nextDouble() * 0.6;
            level.addParticle(ParticleTypes.PORTAL, cx, cy, cz, dx / len * rad, dy / len * rad, dz / len * rad);
        }

        if (phase != CorePhase.FORMED && r.nextInt(12) == 0) {
            level.addParticle(ParticleTypes.SCULK_SOUL, cx + (r.nextDouble() - 0.5) * 2.0, cy - 0.8,
                    cz + (r.nextDouble() - 0.5) * 2.0, 0.0, 0.03, 0.0);
        }
        if (phase == CorePhase.UNSTABLE && r.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, cx + (r.nextDouble() - 0.5) * 2.4, cy + (r.nextDouble() - 0.5) * 2.4,
                    cz + (r.nextDouble() - 0.5) * 2.4, (r.nextDouble() - 0.5) * 0.2, (r.nextDouble() - 0.5) * 0.2, (r.nextDouble() - 0.5) * 0.2);
        }
    }
}
