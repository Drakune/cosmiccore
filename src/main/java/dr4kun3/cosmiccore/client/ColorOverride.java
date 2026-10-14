package dr4kun3.cosmiccore.client;

import dr4kun3.cosmiccore.color.CoreColors;

/**
 * Farbsatz des gerade gezeichneten Kerns (nur Render-Thread). Effekte fragen ihre Kanalfarbe ab und behalten ihre
 * eigene Farbe, solange der Kanal auf "Standard" steht.
 */
public final class ColorOverride {
    private static final float[][] CURRENT = new float[CoreColors.CHANNELS][];
    private static int mask;

    public static void begin(CoreColors.Config c) {
        mask = c.mask();
        for (int i = 0; i < CoreColors.CHANNELS; i++) CURRENT[i] = c.custom(i) ? CoreColors.toFloat(c.rgb(i)) : null;
    }

    public static void end() {
        mask = 0;
    }

    public static boolean custom(int channel) {
        return (mask >> channel & 1) != 0;
    }

    /** Eigene Farbe des Kanals oder def. */
    public static float[] get(int channel, float[] def) {
        return custom(channel) ? CURRENT[channel] : def;
    }

    /** Aufgehellte Variante der Kanalfarbe (fuer Kerne/Spitzen von Effekten). */
    public static float[] light(int channel, float[] def, float amount) {
        return custom(channel) ? RenderUtil.lerp(CURRENT[channel], new float[]{1, 1, 1}, amount) : def;
    }

    /** Abgedunkelte Variante der Kanalfarbe (fuer Raender/Schatten von Effekten). */
    public static float[] dark(int channel, float[] def, float amount) {
        if (!custom(channel)) return def;
        float[] c = CURRENT[channel];
        return new float[]{c[0] * (1 - amount), c[1] * (1 - amount), c[2] * (1 - amount)};
    }

    private ColorOverride() {}
}
