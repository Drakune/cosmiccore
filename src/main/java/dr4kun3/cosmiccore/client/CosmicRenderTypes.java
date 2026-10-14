package dr4kun3.cosmiccore.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * Eigene RenderTypes des Mods.
 * <p>
 * {@link #glow()} ist wie Minecrafts "lightning" (additives Leuchten), schreibt aber NICHT in den Tiefenpuffer und
 * wird nicht sortiert. Beim Vanilla-Typ verdecken sich ueberlappende Leuchteffekte je nach Blickwinkel gegenseitig -
 * das fuehrte zu Flackern. Additives Licht ist reihenfolgeunabhaengig, daher ist kein Sortieren noetig.
 */
public final class CosmicRenderTypes {
    private CosmicRenderTypes() {}

    private static final RenderType GLOW = RenderType.create("cosmiccore_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setOutputState(RenderStateShard.WEATHER_TARGET)
                    .createCompositeState(false));

    public static RenderType glow() {
        return GLOW;
    }

    /**
     * Unsichtbarer Tiefen-Abschluss: schreibt nur Tiefe, nur Rueckseiten (Culling). Die leuchtenden Effekte schreiben
     * keine Tiefe - ohne diesen Abschluss wuerden spaeter gezeichnete Wolken/Regen hinter der Kammer davor erscheinen.
     */
    private static final RenderType DEPTH_BACK = RenderType.create("cosmiccore_depth_back", DefaultVertexFormat.POSITION,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_SHADER)
                    .setWriteMaskState(RenderStateShard.DEPTH_WRITE)
                    .setCullState(RenderStateShard.CULL)
                    .createCompositeState(false));

    public static RenderType depthBack() {
        return DEPTH_BACK;
    }

    /** Sternenfeld-Kuppel eines fremden Universums: End-Sternenfeld ohne Tiefentest (liegt hinter allem, Himmels-Phase). */
    private static final RenderType SPACE_DOME = RenderType.create("cosmiccore_space_dome", DefaultVertexFormat.POSITION,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_END_GATEWAY_SHADER)
                    .setTextureState(RenderStateShard.MultiTextureStateShard.builder()
                            .add(net.minecraft.client.renderer.blockentity.TheEndPortalRenderer.END_SKY_LOCATION, false, false)
                            .add(net.minecraft.client.renderer.blockentity.TheEndPortalRenderer.END_PORTAL_LOCATION, false, false)
                            .build())
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    public static RenderType spaceDome() {
        return SPACE_DOME;
    }

    /** Deckende Farbkuppel ohne Tiefentest (schwarzes All fuer den Warp-Flug, verdeckt Welt und Himmel). */
    private static final RenderType SPACE_SOLID = RenderType.create("cosmiccore_space_solid", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    public static RenderType spaceSolid() {
        return SPACE_SOLID;
    }
}
