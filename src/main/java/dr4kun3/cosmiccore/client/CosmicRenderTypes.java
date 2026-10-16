package dr4kun3.cosmiccore.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * Eigene RenderTypes des Mods.
 * <p>
 * {@link #glow()} ist wie Minecrafts "lightning" (additives Leuchten), schreibt aber NICHT in den Tiefenpuffer und
 * wird nicht sortiert. Beim Vanilla-Typ verdecken sich ueberlappende Leuchteffekte je nach Blickwinkel gegenseitig -
 * das fuehrte zu Flackern. Additives Licht ist reihenfolgeunabhaengig, daher ist kein Sortieren noetig.
 */
@EventBusSubscriber(modid = CosmicCore.MODID, value = Dist.CLIENT)
public final class CosmicRenderTypes {
    private CosmicRenderTypes() {}

    // ------------------------------------------------------------------ Pipelines (ab Minecraft 1.21.5)

    /** Additives Leuchten wie "lightning", ohne Tiefe zu schreiben und ohne Culling. */
    private static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_COLOR_FOG_SNIPPET)
            .withLocation(CosmicCore.id("pipeline/glow"))
            .withVertexShader("core/rendertype_lightning").withFragmentShader("core/rendertype_lightning")
            .withBlend(BlendFunction.LIGHTNING).withDepthWrite(false).withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS).build();

    /** Schreibt nur Tiefe, nur Rueckseiten. */
    private static final RenderPipeline DEPTH_BACK_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_SNIPPET)
            .withLocation(CosmicCore.id("pipeline/depth_back"))
            .withVertexShader("core/position").withFragmentShader("core/position")
            .withColorWrite(false).withDepthWrite(true).withCull(true)
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS).build();

    /** End-Sternenfeld ohne Tiefentest. */
    private static final RenderPipeline SPACE_DOME_PIPELINE = RenderPipeline.builder(RenderPipelines.END_PORTAL_SNIPPET)
            .withLocation(CosmicCore.id("pipeline/space_dome")).withShaderDefine("PORTAL_LAYERS", 16)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).build();

    /** Deckende Farbe ohne Tiefentest. */
    private static final RenderPipeline SPACE_SOLID_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_COLOR_SNIPPET)
            .withLocation(CosmicCore.id("pipeline/space_solid"))
            .withVertexShader("core/position_color").withFragmentShader("core/position_color")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS).build();

    /** Halbtransparente Farbe ohne Tiefentest. */
    private static final RenderPipeline SPACE_TINT_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_COLOR_SNIPPET)
            .withLocation(CosmicCore.id("pipeline/space_tint"))
            .withVertexShader("core/position_color").withFragmentShader("core/position_color")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS).build();

    /** Additives Licht ohne Tiefentest. */
    private static final RenderPipeline SPACE_GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_COLOR_FOG_SNIPPET)
            .withLocation(CosmicCore.id("pipeline/space_glow"))
            .withVertexShader("core/rendertype_lightning").withFragmentShader("core/rendertype_lightning")
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS).build();

    /** NeoForge muss eigene Pipelines vorab kennen (Shader werden dabei vorbereitet). */
    @SubscribeEvent
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        for (RenderPipeline p : new RenderPipeline[]{GLOW_PIPELINE, DEPTH_BACK_PIPELINE, SPACE_DOME_PIPELINE, SPACE_SOLID_PIPELINE,
                SPACE_TINT_PIPELINE, SPACE_GLOW_PIPELINE}) {
            event.registerPipeline(p);
        }
    }

    // ------------------------------------------------------------------ Render-Typen

    private static final RenderType GLOW = RenderType.create("cosmiccore_glow", 1536, GLOW_PIPELINE,
            RenderType.CompositeState.builder().setOutputState(RenderStateShard.WEATHER_TARGET).createCompositeState(false));

    public static RenderType glow() {
        return GLOW;
    }

    /**
     * Unsichtbarer Tiefen-Abschluss: schreibt nur Tiefe, nur Rueckseiten (Culling). Die leuchtenden Effekte schreiben
     * keine Tiefe - ohne diesen Abschluss wuerden spaeter gezeichnete Wolken/Regen hinter der Kammer davor erscheinen.
     */
    private static final RenderType DEPTH_BACK = RenderType.create("cosmiccore_depth_back", 1536, DEPTH_BACK_PIPELINE,
            RenderType.CompositeState.builder().createCompositeState(false));

    public static RenderType depthBack() {
        return DEPTH_BACK;
    }

    /** Sternenfeld-Kuppel eines fremden Universums: End-Sternenfeld ohne Tiefentest (liegt hinter allem, Himmels-Phase). */
    private static final RenderType SPACE_DOME = RenderType.create("cosmiccore_space_dome", 1536, SPACE_DOME_PIPELINE,
            RenderType.CompositeState.builder()
                    .setTextureState(RenderStateShard.MultiTextureStateShard.builder()
                            .add(net.minecraft.client.renderer.blockentity.TheEndPortalRenderer.END_SKY_LOCATION, false, false)
                            .add(net.minecraft.client.renderer.blockentity.TheEndPortalRenderer.END_PORTAL_LOCATION, false, false)
                            .build())
                    .createCompositeState(false));

    public static RenderType spaceDome() {
        return SPACE_DOME;
    }

    /** Deckende Farbkuppel ohne Tiefentest (schwarzes All fuer den Warp-Flug, verdeckt Welt und Himmel). */
    private static final RenderType SPACE_SOLID = RenderType.create("cosmiccore_space_solid", 1536, SPACE_SOLID_PIPELINE,
            RenderType.CompositeState.builder().createCompositeState(false));

    public static RenderType spaceSolid() {
        return SPACE_SOLID;
    }

    /** Halbtransparente Farbflaechen ohne Tiefentest (Planetenringe, Wolkendecken, Abblenden im All). */
    private static final RenderType SPACE_TINT = RenderType.create("cosmiccore_space_tint", 1536, SPACE_TINT_PIPELINE,
            RenderType.CompositeState.builder().createCompositeState(false));

    public static RenderType spaceTint() {
        return SPACE_TINT;
    }

    /** Additives Licht ohne Tiefentest (Sterne, Nebel und Lichtreflexe der Kulissen - liegen immer vorn). */
    private static final RenderType SPACE_GLOW = RenderType.create("cosmiccore_space_glow", 1536, SPACE_GLOW_PIPELINE,
            RenderType.CompositeState.builder().createCompositeState(false));

    public static RenderType spaceGlow() {
        return SPACE_GLOW;
    }
}
