package dev.cosmiccore;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CoreConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue ENERGY_CAPACITY = B
            .comment("Groesse des RF-Puffers im Controller (7x7x7; 5x5x5 hat die Haelfte, 9x9x9 das 2,5-fache)")
            .defineInRange("energyCapacity", 100_000_000, 1_000, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue PORT_TRANSFER = B
            .comment("Maximale RF pro Tick, die ein Energie-Port abgibt (pro Port)")
            .defineInRange("portTransferPerTick", 50_000, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue OUTPUT_MULTIPLIER = B
            .comment("Globaler Faktor auf die RF-Erzeugung (Balancing-Hebel fuer Modpacks)")
            .defineInRange("outputMultiplier", 1.0, 0.0, 1000.0);
    public static final ModConfigSpec.IntValue RAMP_UP_TICKS = B
            .comment("Ticks, bis die Resonanz voll aufgebaut ist (Leistung steigt von 20% auf 100%)")
            .defineInRange("rampUpTicks", 600, 1, 72_000);
    public static final ModConfigSpec.DoubleValue INSTABILITY_RATE = B
            .comment("Instabilitaet pro Tick bei 1000 RF/t Brennstoffleistung (0-100 Skala)")
            .defineInRange("instabilityRate", 0.015, 0.0, 10.0);
    public static final ModConfigSpec.BooleanValue COSMIC_MODIFIERS = B
            .comment("Kosmische Boni: Nacht/Mondphase im Overworld, The End mit Dauerbonus")
            .define("cosmicModifiers", true);
    public static final ModConfigSpec.DoubleValue SKY_LINK_BONUS = B
            .comment("Leistungsbonus, wenn der Reaktor freie Sicht zum Himmel hat (Sternenlicht-Verbindung). 0.25 = +25%")
            .defineInRange("skyLinkBonus", 0.25, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue SCALE_OUTPUT_EXPONENT = B
            .comment("Leistung waechst mit Stufe^x. Der Brennstoffverbrauch waechst linear mit der Stufe.")
            .defineInRange("scaleOutputExponent", 1.3, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue SCALE_INSTABILITY_EXPONENT = B
            .comment("Instabilitaet waechst mit Stufe^x (vor dem Stabilitaetsfaktor der Baugroesse)")
            .defineInRange("scaleInstabilityExponent", 1.35, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue COLLAPSE_DAMAGE = B
            .comment("Schaden an Lebewesen bei einem Kollaps (0 = aus). Der Radius waechst mit der Baugroesse")
            .defineInRange("collapseDamage", 6.0, 0.0, 1000.0);
    public static final ModConfigSpec.BooleanValue COLLAPSE_DESTROYS_BLOCKS = B
            .comment("Wenn true, zerstoert ein Kollaps zusaetzlich Bloecke (Explosion). Standard: aus")
            .define("collapseDestroysBlocks", false);
    public static final ModConfigSpec.BooleanValue COLLAPSE_DESTROYS_CORE = B
            .comment("Wenn true, wird der Singularitaetskern bei einem Kollaps zerstoert (ohne Drop)")
            .define("collapseDestroysCore", true);
    public static final ModConfigSpec.IntValue COLLAPSE_COOLDOWN = B
            .comment("Ticks, die der Kern nach einem Kollaps ausfaellt")
            .defineInRange("collapseCooldownTicks", 600, 0, 72_000);
    public static final ModConfigSpec.IntValue VALIDATION_INTERVAL = B
            .comment("Alle wie viele Ticks die Struktur neu geprueft wird")
            .defineInRange("validationIntervalTicks", 20, 1, 200);

    public static final ModConfigSpec SPEC = B.build();

    private CoreConfig() {}
}
