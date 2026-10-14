package dr4kun3.cosmiccore.compat;

import dr4kun3.cosmiccore.ModItems;
import dr4kun3.cosmiccore.block.entity.ControllerBlockEntity;
import dr4kun3.cosmiccore.data.CoreFuel;
import dr4kun3.cosmiccore.data.CoreStabilizer;
import dr4kun3.cosmiccore.data.ModDataMaps;
import dr4kun3.cosmiccore.entity.AsteroidLoot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Gemeinsame Daten fuer JEI, EMI und REI: Brennstoffe und Stabilisatoren (aus den Data Maps), Asteroiden-Beute
 * und Info-Seiten. Jeder Viewer zeichnet daraus nur noch Slots und Textzeilen.
 */
public final class ViewerEntries {
    public static final int WIDTH = 160;
    public static final int TEXT_COLOR = 0xFF404040;

    public enum Kind { FUEL, STABILIZER, LOOT }

    /** Ein Ausgabe-Slot mit Wahrscheinlichkeit (1 = sicher) fuer die Beute-Anzeige. */
    public record Output(ItemStack stack, float chance) {
        public Component chanceText() {
            return Component.literal(chance >= 1.0F ? "100%" : Math.round(chance * 100) + "%");
        }
    }

    public record Entry(Kind kind, String id, ItemStack input, List<Output> outputs, List<Component> lines) {
        public int height() {
            return kind == Kind.LOOT ? 44 : 30;
        }
    }

    public static List<Entry> fuels() {
        List<Entry> list = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack st = new ItemStack(item);
            CoreFuel f = ModDataMaps.fuel(st);
            if (f == null) continue;
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("jei.cosmiccore.fuel.power", f.power()).withStyle(ChatFormatting.BOLD));
            lines.add(Component.translatable("jei.cosmiccore.fuel.time", String.format("%.1f", f.burnTime() / 20.0F)));
            if (f.heat() != 1.0F) {
                lines.add(Component.translatable("jei.cosmiccore.fuel.heat", String.format("%.1f", f.heat()))
                        .withStyle(f.heat() > 1.0F ? ChatFormatting.DARK_RED : ChatFormatting.DARK_AQUA));
            }
            list.add(new Entry(Kind.FUEL, "fuel/" + BuiltInRegistries.ITEM.getKey(item).getPath(), st, List.of(), lines));
        }
        return list;
    }

    public static List<Entry> stabilizers() {
        List<Entry> list = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack st = new ItemStack(item);
            CoreStabilizer s = ModDataMaps.stabilizer(st);
            if (s == null) continue;
            list.add(new Entry(Kind.STABILIZER, "stabilizer/" + BuiltInRegistries.ITEM.getKey(item).getPath(), st, List.of(), List.of(
                    Component.translatable("jei.cosmiccore.stabilizer.points",
                            String.format("%.0f", ControllerBlockEntity.stabilizerPoints(s))).withStyle(ChatFormatting.BOLD),
                    Component.translatable("jei.cosmiccore.stabilizer.time", String.format("%.1f", s.duration() / 20.0F)))));
        }
        return list;
    }

    public static List<Entry> loot() {
        List<Entry> list = new ArrayList<>();
        for (AsteroidLoot.Table t : AsteroidLoot.TABLES) {
            List<Output> outs = new ArrayList<>();
            for (AsteroidLoot.Drop d : t.drops()) outs.add(new Output(new ItemStack(d.item().get(), d.max()), d.chance()));
            list.add(new Entry(Kind.LOOT, "loot/" + t.name(), new ItemStack(ModItems.COSMIC_LASER.get()), outs,
                    List.of(Component.translatable("jei.cosmiccore.loot." + t.name()).withStyle(ChatFormatting.BOLD))));
        }
        return list;
    }

    public static List<Entry> of(Kind kind) {
        return switch (kind) {
            case FUEL -> fuels();
            case STABILIZER -> stabilizers();
            case LOOT -> loot();
        };
    }

    public static ItemStack icon(Kind kind) {
        return new ItemStack(switch (kind) {
            case FUEL -> ModItems.STAR_DUST.get();
            case STABILIZER -> ModItems.STABILITY_MODULE.get();
            case LOOT -> ModItems.COSMIC_LASER.get();
        });
    }

    public static Component title(Kind kind) {
        return Component.translatable("jei.cosmiccore.category." + kind.name().toLowerCase());
    }

    /** Werkbank-Bloecke, die die Kategorie "benutzen". */
    public static List<ItemStack> catalysts(Kind kind) {
        return kind == Kind.LOOT
                ? List.of(new ItemStack(ModItems.COSMIC_LASER.get()), new ItemStack(ModItems.STAR_FUNNEL.get()))
                : List.of(new ItemStack(ModItems.CONTROLLER.get()), new ItemStack(ModItems.SINGULARITY_CORE.get()));
    }

    /** Info-Seiten (Beschreibung) fuer die wichtigsten Bloecke und Items. */
    public static List<Info> infos() {
        return List.of(
                new Info(new ItemStack(ModItems.CONTROLLER.get()), "controller"),
                new Info(new ItemStack(ModItems.SINGULARITY_CORE.get()), "core"),
                new Info(new ItemStack(ModItems.STAR_FUNNEL.get()), "funnel"),
                new Info(new ItemStack(ModItems.COSMIC_LASER.get()), "laser"),
                new Info(new ItemStack(ModItems.EFFICIENCY_MODULE.get()), "efficiency_module"),
                new Info(new ItemStack(ModItems.STABILITY_MODULE.get()), "stability_module"),
                new Info(new ItemStack(ModItems.ANTI_ASTEROID_MODULE.get()), "anti_asteroid_module"),
                new Info(new ItemStack(ModItems.OVERDRIVE_PARTICLE.get()), "overdrive_particle"),
                new Info(new ItemStack(ModItems.QUASAR_OVERDRIVE_PARTICLE.get()), "quasar_overdrive_particle"),
                new Info(new ItemStack(ModItems.ENERGY_PORT.get()), "energy_port"),
                new Info(new ItemStack(ModItems.ITEM_PORT.get()), "item_port"));
    }

    public record Info(ItemStack stack, String key) {
        public Component text() {
            return Component.translatable("jei.cosmiccore.info." + key);
        }
    }

    private ViewerEntries() {}
}
