package dr4kun3.cosmiccore.color;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Farbsatz des Kerns (Farb-Modul): acht Kanaele - je Effekt eine Farbe. Ein Kanal ist entweder "Standard" (die
 * normale Farbwelt inkl. Overdrive-Farben) oder eine eigene Farbe. Farben werden als RGB555 (je 5 Bit) gespeichert;
 * so passt jeder Kanal in ein Menue-Datenfeld und der ganze Satz in einen kurzen, teilbaren Code ("CC-...").
 */
public final class CoreColors {
    public static final int CHANNELS = 8;
    public static final int DISK_INNER = 0, DISK_MID = 1, DISK_OUTER = 2, JETS = 3, SEAMS = 4, FIELD = 5, LASER = 6, PARTICLES = 7;
    /** Sprachschluessel-Endungen: gui.cosmiccore.channel.<key>. */
    public static final String[] KEYS = {"disk_inner", "disk_mid", "disk_outer", "jets", "seams", "field", "laser", "particles"};
    /** Standardfarben je Kanal (fuer Vorschau und als Startwert im Farbwaehler), RGB 0xRRGGBB. */
    public static final int[] DEFAULT_RGB = {0xFFF2D9, 0xFF8C33, 0x9E47F2, 0x99CCFF, 0x9E66FF, 0x59BFFF, 0x66D9FF, 0xFF9933};

    /** mask: Bit i gesetzt = Kanal i hat eine eigene Farbe; colors: RGB555 je Kanal. */
    public record Config(int mask, int[] colors) {
        public Config {
            colors = Arrays.copyOf(colors, CHANNELS);
            mask &= 0xFF;
            for (int i = 0; i < CHANNELS; i++) colors[i] &= 0x7FFF;
        }

        public boolean custom(int channel) {
            return (mask >> channel & 1) != 0;
        }

        /** Farbe des Kanals als 0xRRGGBB (Standardfarbe, wenn nicht eigen gesetzt). */
        public int rgb(int channel) {
            return custom(channel) ? to24(colors[channel]) : DEFAULT_RGB[channel];
        }

        public Config with(int channel, int rgb24) {
            int[] c = colors.clone();
            c[channel] = to555(rgb24);
            return new Config(mask | (1 << channel), c);
        }

        public Config reset(int channel) {
            return new Config(mask & ~(1 << channel), colors);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Config c && c.mask == mask && Arrays.equals(c.colors, colors);
        }

        @Override
        public int hashCode() {
            return mask * 31 + Arrays.hashCode(colors);
        }
    }

    public static final Config DEFAULT = new Config(0, new int[CHANNELS]);

    public record Preset(String key, Config config) {}

    /** Vorgaben: Standard + sieben stimmige Farbwelten (alle acht Kanaele gesetzt). */
    public static final List<Preset> PRESETS = List.of(
            new Preset("standard", DEFAULT),
            preset("crimson", 0xFFE6D9, 0xFF4726, 0x990D33, 0xFF664D, 0xC72A3A, 0xFF5A40, 0xFF7A5C, 0xFF6040),
            preset("emerald", 0xE6FFE6, 0x4DFF80, 0x0D8059, 0x80FFB3, 0x2EBF73, 0x40E0A0, 0x66FFC0, 0x7AFF9A),
            preset("azure", 0xE6F7FF, 0x4DB3FF, 0x1A33CC, 0x80D9FF, 0x3A70E0, 0x50A8FF, 0x70D0FF, 0x60B0FF),
            preset("gold", 0xFFF7CC, 0xFFBF33, 0xB3590D, 0xFFD973, 0xD9922A, 0xFFC040, 0xFFE080, 0xFFB040),
            preset("rose", 0xFFEBF7, 0xFF73BF, 0x9933BF, 0xFF99D9, 0xD055B0, 0xFF80C8, 0xFFA0E0, 0xFF70B8),
            preset("frost", 0xFFFFFF, 0xBFF2FF, 0x7399D9, 0xD9F2FF, 0xA0C8F0, 0xC8F0FF, 0xE0F8FF, 0xD0F0FF),
            preset("void", 0xD9B3FF, 0x7333CC, 0x260D59, 0x9966FF, 0x5A2AA0, 0x7A50E0, 0xA080FF, 0x8A60F0));

    private static Preset preset(String key, int... rgb) {
        int[] c = new int[CHANNELS];
        for (int i = 0; i < CHANNELS; i++) c[i] = to555(rgb[i]);
        return new Preset(key, new Config(0xFF, c));
    }

    // ------------------------------------------------------------------ Umrechnung

    public static int to555(int rgb24) {
        int r = (rgb24 >> 16) & 0xFF, g = (rgb24 >> 8) & 0xFF, b = rgb24 & 0xFF;
        return ((r >> 3) << 10) | ((g >> 3) << 5) | (b >> 3);
    }

    public static int to24(int c555) {
        int r = (c555 >> 10) & 31, g = (c555 >> 5) & 31, b = c555 & 31;
        return (expand(r) << 16) | (expand(g) << 8) | expand(b);
    }

    private static int expand(int v5) {
        return (v5 << 3) | (v5 >> 2);
    }

    public static float[] toFloat(int rgb24) {
        return new float[]{((rgb24 >> 16) & 0xFF) / 255.0F, ((rgb24 >> 8) & 0xFF) / 255.0F, (rgb24 & 0xFF) / 255.0F};
    }

    // ------------------------------------------------------------------ Teilbarer Code

    private static final String PREFIX = "CC-";

    /** Kodiert den Farbsatz als "CC-" + 24 Zeichen (Maske, 8 Farben, Pruefsumme). */
    public static String encode(Config c) {
        byte[] b = new byte[18];
        b[0] = (byte) c.mask();
        for (int i = 0; i < CHANNELS; i++) {
            b[1 + i * 2] = (byte) (c.colors()[i] >> 8);
            b[2 + i * 2] = (byte) c.colors()[i];
        }
        b[17] = checksum(b);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Liest einen Code; leer, wenn er ungueltig ist (falsches Format oder Pruefsumme). */
    public static Optional<Config> decode(String code) {
        if (code == null) return Optional.empty();
        String s = code.trim();
        if (s.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) s = s.substring(PREFIX.length());
        try {
            byte[] b = Base64.getUrlDecoder().decode(s);
            if (b.length != 18 || b[17] != checksum(b)) return Optional.empty();
            int[] colors = new int[CHANNELS];
            for (int i = 0; i < CHANNELS; i++) colors[i] = ((b[1 + i * 2] & 0x7F) << 8) | (b[2 + i * 2] & 0xFF);
            return Optional.of(new Config(b[0] & 0xFF, colors));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static byte checksum(byte[] b) {
        int sum = 0x5A;
        for (int i = 0; i < 17; i++) sum = (sum * 31 + (b[i] & 0xFF)) & 0xFF;
        return (byte) sum;
    }

    private CoreColors() {}
}
