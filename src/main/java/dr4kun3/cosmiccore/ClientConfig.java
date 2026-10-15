package dr4kun3.cosmiccore;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-Einstellungen (config/cosmiccore-client.toml): Detailgrad der Effekte fuer schwaechere Rechner. */
public final class ClientConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public enum Quality { LOW, MEDIUM, HIGH }

    public static final ModConfigSpec.EnumValue<Quality> QUALITY = B
            .comment("Detailgrad der Reaktor-Effekte: LOW, MEDIUM oder HIGH")
            .defineEnum("effectQuality", Quality.HIGH);
    public static final ModConfigSpec.IntValue DETAIL_DISTANCE = B
            .comment("Ab dieser Entfernung (Bloecke) werden Reaktoren vereinfacht gezeichnet")
            .defineInRange("detailDistance", 48, 8, 512);

    public static final ModConfigSpec SPEC = B.build();

    private ClientConfig() {}
}
