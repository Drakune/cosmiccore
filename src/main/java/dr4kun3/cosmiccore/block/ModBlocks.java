package dr4kun3.cosmiccore.block;

import dr4kun3.cosmiccore.CosmicCore;
import java.util.function.ToIntFunction;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmicCore.MODID);

    // Alle Hüllenblöcke sind noOcclusion: bei gebildeter Struktur ist ihr Modell ausgeblendet (die runde Kammer
    // wird gerendert). Würden sie Nachbarflächen verdecken, sähe man durch die Welt.
    public static final DeferredBlock<StructureBlock> VOID_CASING = BLOCKS.registerBlock("void_casing",
            StructureBlock::new, metal(MapColor.COLOR_BLACK, SoundType.NETHERITE_BLOCK, 2, 5, 5).noOcclusion());

    public static final DeferredBlock<StructureBlock> RUNE_FRAME = BLOCKS.registerBlock("rune_frame",
            StructureBlock::new, metal(MapColor.COLOR_PURPLE, SoundType.NETHERITE_BLOCK, 4, 8, 8).noOcclusion());

    public static final DeferredBlock<ResonatorBlock> RESONANCE_COIL = BLOCKS.registerBlock("resonance_coil",
            ResonatorBlock::new, metal(MapColor.COLOR_CYAN, SoundType.COPPER, 5, 10, 10).noOcclusion());

    public static final DeferredBlock<ResonatorBlock> FOCUS_CRYSTAL = BLOCKS.registerBlock("focus_crystal",
            ResonatorBlock::new, metal(MapColor.COLOR_MAGENTA, SoundType.AMETHYST, 8, 15, 15).noOcclusion());

    public static final DeferredBlock<FocusGlassBlock> FOCUS_GLASS = BLOCKS.registerBlock("focus_glass",
            FocusGlassBlock::new, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(3.0F, 600.0F).sound(SoundType.GLASS)
                    .requiresCorrectToolForDrops().noOcclusion()
                    .isValidSpawn((s, l, p, e) -> false)
                    .isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)
                    .lightLevel(light(3, 6, 6)));

    public static final DeferredBlock<SingularityCoreBlock> SINGULARITY_CORE = BLOCKS.registerBlock("singularity_core",
            SingularityCoreBlock::new, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).strength(5.0F, 1200.0F).sound(SoundType.AMETHYST)
                    .requiresCorrectToolForDrops().noOcclusion()
                    .isRedstoneConductor((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)
                    .lightLevel(light(10, 15, 15)));

    public static final DeferredBlock<ControllerBlock> CONTROLLER = BLOCKS.registerBlock("controller",
            ControllerBlock::new, metal(MapColor.COLOR_PURPLE, SoundType.NETHERITE_BLOCK, 6, 10, 10).noOcclusion());

    public static final DeferredBlock<EnergyPortBlock> ENERGY_PORT = BLOCKS.registerBlock("energy_port",
            EnergyPortBlock::new, metal(MapColor.COLOR_CYAN, SoundType.NETHERITE_BLOCK, 4, 8, 8).noOcclusion());

    public static final DeferredBlock<ItemPortBlock> ITEM_PORT = BLOCKS.registerBlock("item_port",
            ItemPortBlock::new, metal(MapColor.COLOR_MAGENTA, SoundType.NETHERITE_BLOCK, 4, 8, 8).noOcclusion());

    public static final DeferredBlock<StarFunnelBlock> STAR_FUNNEL = BLOCKS.registerBlock("star_funnel",
            StarFunnelBlock::new, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 600.0F).sound(SoundType.AMETHYST)
                    .requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> 7));

    public static final DeferredBlock<CosmicLaserBlock> COSMIC_LASER = BLOCKS.registerBlock("cosmic_laser",
            CosmicLaserBlock::new, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN).strength(5.0F, 600.0F).sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> 5));

    /**
     * Gemeinsame Eigenschaften der Strukturblöcke: hart wie Metall, nur mit passendem Werkzeug abbaubar und je nach
     * Reaktorphase leuchtend.
     *
     * @param color Kartenfarbe
     * @param sound Geräusche des Blocks
     * @param formed Lichtstärke 0..15, wenn die Struktur gebildet ist
     * @param active Lichtstärke, wenn der Reaktor läuft
     * @param unstable Lichtstärke, wenn der Kern instabil ist
     * @return die fertigen Blockeigenschaften
     */
    private static BlockBehaviour.Properties metal(MapColor color, SoundType sound, int formed, int active, int unstable) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(5.0F, 600.0F)
                .sound(sound)
                .requiresCorrectToolForDrops()
                .lightLevel(light(formed, active, unstable));
    }

    /**
     * Lichtstärke abhängig von der Reaktorphase (ruhend = dunkel).
     *
     * @param formed Lichtstärke in der Phase "gebildet"
     * @param active Lichtstärke in der Phase "aktiv"
     * @param unstable Lichtstärke in der Phase "instabil"
     * @return Funktion Blockzustand -> Lichtstärke
     */
    private static ToIntFunction<BlockState> light(int formed, int active, int unstable) {
        return state -> switch (state.getValue(ModProperties.PHASE)) {
            case DORMANT -> 0;
            case FORMED -> formed;
            case ACTIVE -> active;
            case UNSTABLE -> unstable;
        };
    }

    /** Nur statische Registrierungen - keine Instanzen. */
    private ModBlocks() {}
}
