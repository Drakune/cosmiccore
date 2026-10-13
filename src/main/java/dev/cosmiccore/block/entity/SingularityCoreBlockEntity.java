package dev.cosmiccore.block.entity;

import dev.cosmiccore.block.CorePhase;
import dev.cosmiccore.block.ModProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Traeger der Animation: Der Renderer und die Partikel haengen hier dran, die Logik sitzt im Controller.
 * Der Controller schiebt die fuer die Darstellung noetigen Werte (Groesse, Stufe, Instabilitaet,
 * Himmelsverbindung) hierher; sie werden an die Clients synchronisiert.
 */
public class SingularityCoreBlockEntity extends BlockEntity {
    private int structureSize = 7;
    private int scale = 1;
    private int maxScale = 1;
    private int instability;      // 0..100 (gerundet, damit nicht jeder Tick ein Paket ausloest)
    private boolean skyLink;
    private int cosmic = 100;     // kosmischer Faktor in Prozent

    public SingularityCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CORE.get(), pos, state);
    }

    // ------------------------------------------------------------------ vom Controller gesetzt

    public void setVisuals(int structureSize, int scale, int maxScale, float instability, boolean skyLink, double cosmic) {
        int inst = Math.round(instability);
        int cos = (int) Math.round(cosmic * 100.0);
        if (structureSize == this.structureSize && scale == this.scale && maxScale == this.maxScale
                && inst == this.instability && skyLink == this.skyLink && cos == this.cosmic) {
            return;
        }
        this.structureSize = structureSize;
        this.scale = scale;
        this.maxScale = maxScale;
        this.instability = inst;
        this.skyLink = skyLink;
        this.cosmic = cos;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public int getStructureSize() {
        return structureSize;
    }

    /** Abstand vom Kern zur Innenflaeche der Achsen-Spulen/Kristalle. */
    public float getReach() {
        int half = structureSize / 2;
        int focus = structureSize <= 5 ? half : half - 1;
        return focus - 0.5F;
    }

    /** 0 bei Stufe 1, 1 bei der hoechsten Stufe der Baugroesse. */
    public float getGrowth() {
        return maxScale <= 1 ? 0.0F : (scale - 1) / (float) (maxScale - 1);
    }

    public int getScale() {
        return scale;
    }

    public float getInstability() {
        return instability;
    }

    public boolean hasSkyLink() {
        return skyLink;
    }

    public float getCosmic() {
        return cosmic / 100.0F;
    }

    // ------------------------------------------------------------------ Partikel (Client)

    public static void clientTick(Level level, BlockPos pos, BlockState state, SingularityCoreBlockEntity be) {
        CorePhase phase = state.getValue(ModProperties.PHASE);
        if (phase == CorePhase.DORMANT) return;
        RandomSource r = level.random;
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        float growth = be.getGrowth();
        float reach = be.getReach();
        boolean running = phase == CorePhase.ACTIVE || phase == CorePhase.UNSTABLE;

        // Partikel die in die Singularitaet gesogen werden (PORTAL startet bei pos+vel und endet bei pos)
        int pulls = switch (phase) {
            case FORMED -> r.nextInt(4) == 0 ? 1 : 0;
            case ACTIVE -> 2 + Math.round(growth * 3);
            default -> 4 + Math.round(growth * 3);
        };
        for (int i = 0; i < pulls; i++) {
            double dx = r.nextGaussian(), dy = r.nextGaussian(), dz = r.nextGaussian();
            double len = Math.max(0.001, Math.sqrt(dx * dx + dy * dy + dz * dz));
            double rad = reach + 0.1 + r.nextDouble() * 0.6;
            level.addParticle(ParticleTypes.PORTAL, cx, cy, cz, dx / len * rad, dy / len * rad, dz / len * rad);
        }

        if (running && r.nextInt(12) == 0) {
            level.addParticle(ParticleTypes.SCULK_SOUL, cx + (r.nextDouble() - 0.5) * 2.0, cy - 0.8,
                    cz + (r.nextDouble() - 0.5) * 2.0, 0.0, 0.03, 0.0);
        }
        if (phase == CorePhase.UNSTABLE && r.nextInt(2) == 0) {
            double s = reach * 1.6;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, cx + (r.nextDouble() - 0.5) * s, cy + (r.nextDouble() - 0.5) * s,
                    cz + (r.nextDouble() - 0.5) * s, (r.nextDouble() - 0.5) * 0.2, (r.nextDouble() - 0.5) * 0.2, (r.nextDouble() - 0.5) * 0.2);
        }
        // kurz vor dem Kollaps: Flammenzungen schiessen aus dem Kern
        if (running && be.instability >= 85 && r.nextInt(3) == 0) {
            double dx = r.nextGaussian(), dy = r.nextGaussian(), dz = r.nextGaussian();
            double len = Math.max(0.001, Math.sqrt(dx * dx + dy * dy + dz * dz));
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, cx, cy, cz, dx / len * 0.25, dy / len * 0.25, dz / len * 0.25);
        }

        // Energiefluss von den Spulen in den Kern
        if (running && r.nextInt(3) == 0) {
            int side = r.nextInt(4);
            double fx = side == 0 ? reach : side == 1 ? -reach : 0;
            double fz = side == 2 ? reach : side == 3 ? -reach : 0;
            level.addParticle(ParticleTypes.END_ROD, cx + fx, cy + (r.nextDouble() - 0.5) * 0.3, cz + fz,
                    -fx * 0.04, 0.0, -fz * 0.04);
        }

        // Verbindung zum Kosmos: Sternenlicht faellt vom Himmel durch den Strahl in den Reaktor
        if (running && be.skyLink) {
            int falls = 1 + Math.round(be.getCosmic() * 1.5F);
            for (int i = 0; i < falls; i++) {
                if (r.nextInt(2) != 0) continue;
                double h = 12.0 + r.nextDouble() * 28.0;
                double a = r.nextDouble() * Math.PI * 2;
                double d = 0.3 + r.nextDouble() * 1.4;
                level.addParticle(ParticleTypes.END_ROD, cx + Math.cos(a) * d, cy + h, cz + Math.sin(a) * d,
                        -Math.cos(a) * d * 0.01, -0.45 - r.nextDouble() * 0.35, -Math.sin(a) * d * 0.01);
            }
            // kreisender Sternenstaub ueber der Decke
            if (r.nextInt(2) == 0) {
                int half = be.structureSize / 2;
                double a = r.nextDouble() * Math.PI * 2;
                double rad = half + 0.5;
                level.addParticle(ParticleTypes.GLOW, cx + Math.cos(a) * rad, cy + half + 1.2, cz + Math.sin(a) * rad,
                        -Math.sin(a) * 0.08, -0.01, Math.cos(a) * 0.08);
            }
        }
    }

    // ------------------------------------------------------------------ Persistenz / Sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeVisuals(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("structureSize")) structureSize = tag.getInt("structureSize");
        scale = Math.max(1, tag.getInt("scale"));
        maxScale = Math.max(1, tag.getInt("maxScale"));
        instability = tag.getInt("instability");
        skyLink = tag.getBoolean("skyLink");
        cosmic = tag.contains("cosmic") ? tag.getInt("cosmic") : 100;
    }

    private void writeVisuals(CompoundTag tag) {
        tag.putInt("structureSize", structureSize);
        tag.putInt("scale", scale);
        tag.putInt("maxScale", maxScale);
        tag.putInt("instability", instability);
        tag.putBoolean("skyLink", skyLink);
        tag.putInt("cosmic", cosmic);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeVisuals(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
