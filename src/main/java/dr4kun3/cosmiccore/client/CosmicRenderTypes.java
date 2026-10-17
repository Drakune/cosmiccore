package dr4kun3.cosmiccore.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;

/**
 * Eigene RenderTypes des Mods.
 * <p>
 * {@link #glow()} ist wie Minecrafts "lightning" (additives Leuchten), schreibt aber NICHT in den Tiefenpuffer und
 * wird nicht sortiert. Beim Vanilla-Typ verdecken sich überlappende Leuchteffekte je nach Blickwinkel gegenseitig -
 * das führte zu Flackern. Additives Licht ist reihenfolgeunabhängig, daher ist kein Sortieren nötig.
 */
public final class CosmicRenderTypes {
    /** Nur statische Render-Typen - keine Instanzen. */
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

    /**
     * Additives Leuchten wie bei Blitzen, ohne Tiefe zu schreiben und ohne Culling - für fast alle Effekte.
     *
     * @return der Render-Typ
     */
    public static RenderType glow() {
        return GLOW;
    }

    /**
     * Unsichtbarer Tiefen-Abschluss: schreibt nur Tiefe, nur Rückseiten (Culling). Die leuchtenden Effekte schreiben
     * keine Tiefe - ohne diesen Abschluss würden später gezeichnete Wolken/Regen hinter der Kammer davor erscheinen.
     */
    private static final RenderType DEPTH_BACK = RenderType.create("cosmiccore_depth_back", DefaultVertexFormat.POSITION,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_SHADER)
                    .setWriteMaskState(RenderStateShard.DEPTH_WRITE)
                    .setCullState(RenderStateShard.CULL)
                    .createCompositeState(false));

    /**
     * Unsichtbarer Tiefen-Abschluss: schreibt nur Tiefe, nur Rückseiten. Ohne ihn würden später gezeichnete
     * Wolken oder Regen hinter der Kammer davor erscheinen.
     *
     * @return der Render-Typ
     */
    public static RenderType depthBack() {
        return DEPTH_BACK;
    }

    /** Sternenfeld-Kuppel eines fremden Universums: End-Sternenfeld ohne Tiefentest (liegt hinter allem, Himmels-Phase). */
    private static final RenderType SPACE_DOME = RenderType.create("cosmiccore_space_dome", DefaultVertexFormat.POSITION,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_END_GATEWAY_SHADER)
                    .setTextureState(RenderStateShard.MultiTextureStateShard.builder()
                            .add(TheEndPortalRenderer.END_SKY_LOCATION, false, false)
                            .add(TheEndPortalRenderer.END_PORTAL_LOCATION, false, false)
                            .build())
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /**
     * Sternenfeld-Kuppel eines fremden Universums: End-Sternenfeld ohne Tiefentest (liegt hinter allem).
     *
     * @return der Render-Typ
     */
    public static RenderType spaceDome() {
        return SPACE_DOME;
    }

    /** Deckende Farbkuppel ohne Tiefentest (schwarzes All für den Warp-Flug, verdeckt Welt und Himmel). */
    private static final RenderType SPACE_SOLID = RenderType.create("cosmiccore_space_solid", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /**
     * Deckende Farbflächen ohne Tiefentest (schwarzes All für den Warp-Flug, verdeckt Welt und Himmel).
     *
     * @return der Render-Typ
     */
    public static RenderType spaceSolid() {
        return SPACE_SOLID;
    }

    /** Halbtransparente Farbflächen ohne Tiefentest (Planetenringe, Wolkendecken, Abblenden im All). */
    private static final RenderType SPACE_TINT = RenderType.create("cosmiccore_space_tint", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /**
     * Halbtransparente Farbflächen ohne Tiefentest (Planetenringe, Wolkendecken, Abblenden im All).
     *
     * @return der Render-Typ
     */
    public static RenderType spaceTint() {
        return SPACE_TINT;
    }

    /** Additives Licht ohne Tiefentest (Sterne, Nebel und Lichtreflexe der Kulissen - liegen immer vorn). */
    private static final RenderType SPACE_GLOW = RenderType.create("cosmiccore_space_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /**
     * Additives Licht ohne Tiefentest (Sterne, Nebel und Lichtreflexe der Kulissen - liegen immer vorn).
     *
     * @return der Render-Typ
     */
    public static RenderType spaceGlow() {
        return SPACE_GLOW;
    }
}
