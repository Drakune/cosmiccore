package dr4kun3.cosmiccore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/** Schrift in der Welt: ab 26.2 nur noch ueber den SubmitNodeCollector (ersetzt Font.drawInBatch). */
public final class WorldText {
    /** Nur statische Hilfsfunktionen - keine Instanzen. */
    private WorldText() {}

    /**
     * Zeichnet Text in der Welt (ueber den Submit-Sammler der Puffer).
     *
     * @param font Schrift
     * @param text Text
     * @param x Position x in Schriftpixeln
     * @param y Position y in Schriftpixeln
     * @param color Farbe 0xAARRGGBB
     * @param shadow mit Schatten
     * @param matrix Matrix des Textraums
     * @param buffers Puffer (muessen DeferredBuffers sein)
     * @param mode Darstellungsart
     * @param background Hintergrundfarbe
     * @param light Lichtwert
     */
    public static void draw(Font font, String text, float x, float y, int color, boolean shadow, Matrix4f matrix, MultiBufferSource buffers,
                            Font.DisplayMode mode, int background, int light) {
        if (!(buffers instanceof DeferredBuffers d)) return;
        PoseStack pose = new PoseStack();
        pose.last().pose().set(matrix);
        d.collector().submitText(pose, x, y, FormattedCharSequence.forward(text, Style.EMPTY), shadow, mode, light, color, background, 0);
    }
}
