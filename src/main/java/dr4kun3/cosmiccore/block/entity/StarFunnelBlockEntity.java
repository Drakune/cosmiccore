package dr4kun3.cosmiccore.block.entity;

import dr4kun3.cosmiccore.block.CorePhase;
import dr4kun3.cosmiccore.block.ModProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Traegt die Wirbel-Animation des Sternentrichters und laesst dauerhaft Teilchen aus dem All in ihn saugen. */
public class StarFunnelBlockEntity extends BlockEntity {
    /**
     * Legt den Blockinhalt des Sternentrichters an.
     *
     * @param pos Position des Trichters
     * @param state Blockzustand beim Erzeugen
     */
    public StarFunnelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STAR_FUNNEL.get(), pos, state);
    }

    /**
     * Dauerhafter Sog: Teilchen erscheinen hoch am Himmel in einer Spirale ueber dem Trichter und werden hineingezogen.
     * PORTAL-Partikel wandern von (Position + Geschwindigkeit) zur Position - so landen sie genau im Trichter. Laeuft
     * der Reaktor darunter, ist der Sog staerker. Nur auf dem Client und nur bei freier Sicht zum Himmel.
     *
     * @param level die Client-Welt
     * @param pos Position des Trichters
     */
    public static void clientTick(Level level, BlockPos pos) {
        if (!level.canSeeSky(pos.above())) return;
        BlockState below = level.getBlockState(pos.below());
        boolean running = below.hasProperty(ModProperties.PHASE)
                && (below.getValue(ModProperties.PHASE) == CorePhase.ACTIVE || below.getValue(ModProperties.PHASE) == CorePhase.UNSTABLE);
        RandomSource r = level.random;
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.9, cz = pos.getZ() + 0.5;
        int count = running ? 4 : 2;
        for (int i = 0; i < count; i++) {
            double h = 8.0 + r.nextDouble() * 26.0;
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = 0.3 + h * h * h / 7000.0 * (0.6 + 0.4 * r.nextDouble()); // passt zur Trompetenform
            level.addAlwaysVisibleParticle(ParticleTypes.PORTAL, true, cx, cy, cz, Math.cos(ang) * rad, h, Math.sin(ang) * rad);
        }
        if (r.nextInt(running ? 2 : 5) == 0) {
            double h = 10.0 + r.nextDouble() * 22.0;
            double ang = r.nextDouble() * Math.PI * 2;
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, cx + Math.cos(ang) * h * 0.12, cy + h,
                    cz + Math.sin(ang) * h * 0.12, -Math.cos(ang) * 0.03, -0.35 - r.nextDouble() * 0.2, -Math.sin(ang) * 0.03);
        }
    }
}
