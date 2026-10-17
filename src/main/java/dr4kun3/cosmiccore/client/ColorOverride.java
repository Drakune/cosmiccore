package dr4kun3.cosmiccore.client;

import dr4kun3.cosmiccore.color.CoreColors;

/**
 * Farbsatz des gerade gezeichneten Kerns (nur Render-Thread). Effekte fragen ihre Kanalfarbe ab und behalten ihre
 * eigene Farbe, solange der Kanal auf "Standard" steht.
 */
public final class ColorOverride {
    private static final float[][] CURRENT = new float[CoreColors.CHANNELS][];
    private static int mask;

    /**
     * Uebernimmt den Farbsatz eines Reaktors fuer die folgenden Zeichenaufrufe.
     *
     * @param c Farbsatz (Maske eigener Kanaele und Farben)
     */
    public static void begin(CoreColors.Config c) {
        mask = c.mask();
        for (int i = 0; i < CoreColors.CHANNELS; i++) CURRENT[i] = c.custom(i) ? CoreColors.toFloat(c.rgb(i)) : null;
    }

    /** Beendet den Farbsatz: danach gelten wieder die Standardfarben. */
    public static void end() {
        mask = 0;
    }

    /**
     * Hat der Kanal eine eigene Farbe?
     *
     * @param channel Nummer des Farbkanals (siehe CoreColors)
     * @return {@code true}, wenn der Spieler eine Farbe gewaehlt hat
     */
    public static boolean custom(int channel) {
        return (mask >> channel & 1) != 0;
    }

    /**
     * Eigene Farbe des Kanals oder die Standardfarbe.
     *
     * @param channel Nummer des Farbkanals
     * @param def Standardfarbe als RGB 0..1
     * @return die zu verwendende Farbe
     */
    public static float[] get(int channel, float[] def) {
        return custom(channel) ? CURRENT[channel] : def;
    }

    /**
     * Aufgehellte Variante der Kanalfarbe (fuer Kerne und Spitzen von Effekten).
     *
     * @param channel Nummer des Farbkanals
     * @param def Standardfarbe (wird ohne eigene Farbe unveraendert genommen)
     * @param amount Anteil Weiss 0..1
     * @return die Farbe
     */
    public static float[] light(int channel, float[] def, float amount) {
        return custom(channel) ? RenderUtil.lerp(CURRENT[channel], new float[]{1, 1, 1}, amount) : def;
    }

    /**
     * Abgedunkelte Variante der Kanalfarbe (fuer Raender und Schatten von Effekten).
     *
     * @param channel Nummer des Farbkanals
     * @param def Standardfarbe (wird ohne eigene Farbe unveraendert genommen)
     * @param amount Abdunklung 0..1
     * @return die Farbe
     */
    public static float[] dark(int channel, float[] def, float amount) {
        if (!custom(channel)) return def;
        float[] c = CURRENT[channel];
        return new float[]{c[0] * (1 - amount), c[1] * (1 - amount), c[2] * (1 - amount)};
    }

    /** Nur statischer Zustand - keine Instanzen. */
    private ColorOverride() {}
}
