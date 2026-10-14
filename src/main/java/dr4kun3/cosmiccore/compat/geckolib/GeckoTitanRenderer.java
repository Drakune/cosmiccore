package dr4kun3.cosmiccore.compat.geckolib;

import com.mojang.blaze3d.vertex.PoseStack;
import dr4kun3.cosmiccore.client.TitanCutscene;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Zeichnet den Void-Titanen mit GeckoLib. Wird von der Cutscene nur geladen, wenn GeckoLib installiert ist. */
public class GeckoTitanRenderer implements TitanCutscene.TitanRenderer {
    private final GeoObjectRenderer<VoidTitan> renderer;
    private VoidTitan titan = new VoidTitan();

    public GeckoTitanRenderer() {
        renderer = new GeoObjectRenderer<>(new VoidTitanModel()).withScale(TitanCutscene.TITAN_SCALE);
        renderer.addRenderLayer(new AutoGlowingGeoLayer<>(renderer));
    }

    @Override
    public void reset() {
        titan = new VoidTitan(); // frischer Animationszustand fuer jede Cutscene
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, double titanTicks, float partialTick) {
        titan.ticks = titanTicks;
        renderer.render(pose, titan, buffers, null, null, LightTexture.FULL_BRIGHT, partialTick);
    }
}
