package dr4kun3.cosmiccore.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
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
    /** Nur statische Render-Typen - keine Instanzen. */
    private CosmicRenderTypes() {}

    // ------------------------------------------------------------------ Pipelines (ab Minecraft 1.21.5)

    /** Additives Leuchten wie "lightning", ohne Tiefe zu schreiben und ohne Culling. */
    private static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(CosmicCore.id("pipeline/glow"))
            .withVertexShader("core/rendertype_lightning").withFragmentShader("core/rendertype_lightning")
            .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL, false)).withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS).build();

    /**
     * Schreibt nur Tiefe, nur Rueckseiten. Ab 26.1 zeichnet Minecraft erst alles Undurchsichtige, dann alles
     * Durchscheinende - mit einer (wirkungslosen) Mischfunktion laeuft der Abschluss im zweiten Durchgang und damit wie
     * frueher nach den Leuchteffekten.
     */
    private static final RenderPipeline DEPTH_BACK_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(CosmicCore.id("pipeline/depth_back"))
            .withVertexShader("core/position").withFragmentShader("core/position")
            .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(java.util.Optional.of(BlendFunction.TRANSLUCENT), com.mojang.blaze3d.pipeline.ColorTargetState.WRITE_NONE))
            .withDepthStencilState(com.mojang.blaze3d.pipeline.DepthStencilState.DEFAULT).withCull(true)
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS).build();

    /** End-Sternenfeld ohne Tiefentest. */
    private static final RenderPipeline SPACE_DOME_PIPELINE = RenderPipeline.builder(RenderPipelines.END_PORTAL_SNIPPET)
            .withLocation(CosmicCore.id("pipeline/space_dome")).withShaderDefine("PORTAL_LAYERS", 16)
            .withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS, false)).withCull(false).build();

    /** Deckende Farbe ohne Tiefentest. */
    private static final RenderPipeline SPACE_SOLID_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(CosmicCore.id("pipeline/space_solid"))
            .withVertexShader("core/position_color").withFragmentShader("core/position_color")
            .withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS, false)).withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS).build();

    /** Deckende Farbe mit Tiefentest, die Tiefe schreibt (wie frueher debugQuads, das seit 1.21.11 keine Tiefe mehr schreibt). */
    private static final RenderPipeline SOLID_COLOR_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(CosmicCore.id("pipeline/solid_color"))
            .withVertexShader("core/position_color").withFragmentShader("core/position_color")
            .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(com.mojang.blaze3d.pipeline.DepthStencilState.DEFAULT).withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS).build();

    /** Halbtransparente Farbe ohne Tiefentest. */
    private static final RenderPipeline SPACE_TINT_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(CosmicCore.id("pipeline/space_tint"))
            .withVertexShader("core/position_color").withFragmentShader("core/position_color")
            .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS, false)).withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS).build();

    /** Additives Licht ohne Tiefentest. */
    private static final RenderPipeline SPACE_GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(CosmicCore.id("pipeline/space_glow"))
            .withVertexShader("core/rendertype_lightning").withFragmentShader("core/rendertype_lightning")
            .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS, false)).withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS).build();

    /**
     * Registriert die eigenen Render-Pipelines.
     *
     * @param event Registrierungs-Ereignis
     */
    @SubscribeEvent
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        for (RenderPipeline p : new RenderPipeline[]{SOLID_COLOR_PIPELINE, GLOW_PIPELINE, DEPTH_BACK_PIPELINE, SPACE_DOME_PIPELINE, SPACE_SOLID_PIPELINE,
                SPACE_TINT_PIPELINE, SPACE_GLOW_PIPELINE}) {
            event.registerPipeline(p);
        }
    }

    // ------------------------------------------------------------------ Render-Typen

    private static final RenderType GLOW = RenderType.create("cosmiccore_glow",
            net.minecraft.client.renderer.rendertype.RenderSetup.builder(GLOW_PIPELINE).setOutputTarget(net.minecraft.client.renderer.rendertype.OutputTarget.WEATHER_TARGET).bufferSize(1536).createRenderSetup());

    /**
     * Additives Leuchten wie bei Blitzen, ohne Tiefe zu schreiben und ohne Culling - fuer fast alle Effekte.
     *
     * @return der Render-Typ
     */
    public static RenderType glow() {
        return GLOW;
    }

    /**
     * Unsichtbarer Tiefen-Abschluss: schreibt nur Tiefe, nur Rueckseiten (Culling). Die leuchtenden Effekte schreiben
     * keine Tiefe - ohne diesen Abschluss wuerden spaeter gezeichnete Wolken/Regen hinter der Kammer davor erscheinen.
     */
    private static final RenderType DEPTH_BACK = RenderType.create("cosmiccore_depth_back", net.minecraft.client.renderer.rendertype.RenderSetup.builder(DEPTH_BACK_PIPELINE).bufferSize(1536).createRenderSetup());

    /**
     * Unsichtbarer Tiefen-Abschluss: schreibt nur Tiefe, nur Rueckseiten. Ohne ihn wuerden spaeter gezeichnete
     * Wolken oder Regen hinter der Kammer davor erscheinen.
     *
     * @return der Render-Typ
     */
    public static RenderType depthBack() {
        return DEPTH_BACK;
    }

    /** Sternenfeld-Kuppel eines fremden Universums: End-Sternenfeld ohne Tiefentest (liegt hinter allem, Himmels-Phase). */
    private static final RenderType SPACE_DOME = RenderType.create("cosmiccore_space_dome",
            net.minecraft.client.renderer.rendertype.RenderSetup.builder(SPACE_DOME_PIPELINE)
                    .withTexture("Sampler0", net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer.END_SKY_LOCATION)
                    .withTexture("Sampler1", net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer.END_PORTAL_LOCATION)
                    .bufferSize(1536).createRenderSetup());

    /**
     * Sternenfeld-Kuppel eines fremden Universums: End-Sternenfeld ohne Tiefentest (liegt hinter allem).
     *
     * @return der Render-Typ
     */
    public static RenderType spaceDome() {
        return SPACE_DOME;
    }

    /** Deckende Farbkuppel ohne Tiefentest (schwarzes All fuer den Warp-Flug, verdeckt Welt und Himmel). */
    private static final RenderType SPACE_SOLID = RenderType.create("cosmiccore_space_solid", net.minecraft.client.renderer.rendertype.RenderSetup.builder(SPACE_SOLID_PIPELINE).bufferSize(1536).createRenderSetup());

    /**
     * Deckende Farbflaechen ohne Tiefentest (schwarzes All fuer den Warp-Flug, verdeckt Welt und Himmel).
     *
     * @return der Render-Typ
     */
    public static RenderType spaceSolid() {
        return SPACE_SOLID;
    }

    /** Deckende Farbflaechen mit Tiefe (Bildschirme, Asteroidenfels, Kerne der Items). */
    private static final RenderType SOLID_COLOR = RenderType.create("cosmiccore_solid_color", net.minecraft.client.renderer.rendertype.RenderSetup.builder(SOLID_COLOR_PIPELINE).bufferSize(1536).createRenderSetup());

    /**
     * Deckende Farbflaechen, die Tiefe schreiben: Bildschirme verdecken so die Leuchtstrahlen dahinter. Ersetzt
     * debugQuads, das seit Minecraft 1.21.11 keine Tiefe mehr schreibt.
     *
     * @return der Render-Typ
     */
    public static RenderType solidColor() {
        return SOLID_COLOR;
    }

    /** Halbtransparente Farbflaechen ohne Tiefentest (Planetenringe, Wolkendecken, Abblenden im All). */
    private static final RenderType SPACE_TINT = RenderType.create("cosmiccore_space_tint", net.minecraft.client.renderer.rendertype.RenderSetup.builder(SPACE_TINT_PIPELINE).bufferSize(1536).createRenderSetup());

    /**
     * Halbtransparente Farbflaechen ohne Tiefentest (Planetenringe, Wolkendecken, Abblenden im All).
     *
     * @return der Render-Typ
     */
    public static RenderType spaceTint() {
        return SPACE_TINT;
    }

    /** Additives Licht ohne Tiefentest (Sterne, Nebel und Lichtreflexe der Kulissen - liegen immer vorn). */
    private static final RenderType SPACE_GLOW = RenderType.create("cosmiccore_space_glow", net.minecraft.client.renderer.rendertype.RenderSetup.builder(SPACE_GLOW_PIPELINE).bufferSize(1536).createRenderSetup());

    /**
     * Additives Licht ohne Tiefentest (Sterne, Nebel und Lichtreflexe der Kulissen - liegen immer vorn).
     *
     * @return der Render-Typ
     */
    public static RenderType spaceGlow() {
        return SPACE_GLOW;
    }
}
