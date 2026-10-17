package dr4kun3.cosmiccore.compat.emi;

import dr4kun3.cosmiccore.CosmicCore;
import dr4kun3.cosmiccore.compat.ViewerEntries;
import dr4kun3.cosmiccore.compat.ViewerEntries.Entry;
import dr4kun3.cosmiccore.compat.ViewerEntries.Kind;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** EMI: Kategorien fuer Brennstoffe, Stabilisatoren und Asteroiden-Beute plus Info-Seiten. */
@EmiEntrypoint
public class CosmicEmiPlugin implements EmiPlugin {
    private static final Map<Kind, EmiRecipeCategory> CATEGORIES = new EnumMap<>(Kind.class);

    /**
     * Meldet bei EMI die Kategorien (Brennstoffe, Stabilisatoren, Asteroiden-Beute) und ihre Eintraege an und haelt
     * den Modul-Tab rechts am Controller-Fenster frei.
     *
     * @param registry Registrierung von EMI
     */
    @Override
    public void register(EmiRegistry registry) {
        // Modul-Tab rechts am Controller-Fenster freihalten
        registry.addExclusionArea(dr4kun3.cosmiccore.client.ControllerScreen.class, (screen, out) -> {
            for (var r : screen.extraAreas()) out.accept(new dev.emi.emi.api.widget.Bounds(r.getX(), r.getY(), r.getWidth(), r.getHeight()));
        });
        for (Kind k : Kind.values()) {
            EmiRecipeCategory cat = new EmiRecipeCategory(CosmicCore.id(k.name().toLowerCase()), EmiStack.of(ViewerEntries.icon(k))) {
                @Override
                public Component getName() {
                    return ViewerEntries.title(k);
                }
            };
            CATEGORIES.put(k, cat);
            registry.addCategory(cat);
            for (ItemStack st : ViewerEntries.catalysts(k)) registry.addWorkstation(cat, EmiStack.of(st));
            for (Entry e : ViewerEntries.of(k)) registry.addRecipe(new Recipe(e));
        }
        for (ViewerEntries.Info info : ViewerEntries.infos()) {
            registry.addRecipe(new EmiInfoRecipe(List.of(EmiStack.of(info.stack())), List.of(info.text()),
                    CosmicCore.id("/info/" + info.key())));
        }
    }

    private record Recipe(Entry entry) implements EmiRecipe {
        /**
         * Kategorie dieses Eintrags.
         *
         * @return Brennstoff-, Stabilisator- oder Beute-Kategorie
         */
        @Override
        public EmiRecipeCategory getCategory() {
            return CATEGORIES.get(entry.kind());
        }

        /**
         * Eindeutige Kennung des Eintrags.
         *
         * @return Kennung im Namensraum der Mod
         */
        @Override
        public Identifier getId() {
            return CosmicCore.id("/" + entry.id());
        }

        /**
         * Eingabe: das Item (bei Asteroiden-Beute keine).
         *
         * @return Liste der Eingaben
         */
        @Override
        public List<EmiIngredient> getInputs() {
            return entry.kind() == Kind.LOOT ? List.of() : List.of(EmiStack.of(entry.input()));
        }

        /**
         * Ausgaben, bei Beute mit Wahrscheinlichkeit.
         *
         * @return Liste der Ausgaben
         */
        @Override
        public List<EmiStack> getOutputs() {
            return entry.outputs().stream().map(o -> EmiStack.of(o.stack()).setChance(o.chance())).toList();
        }

        /**
         * Breite der Anzeige.
         *
         * @return Breite in Pixeln
         */
        @Override
        public int getDisplayWidth() {
            return ViewerEntries.WIDTH;
        }

        /**
         * Hoehe der Anzeige (je nach Zeilenzahl).
         *
         * @return Hoehe in Pixeln
         */
        @Override
        public int getDisplayHeight() {
            return entry.height();
        }

        /**
         * Diese Eintraege sind keine Rezepte zum Herstellen und gehoeren nicht in den Rezeptbaum.
         *
         * @return immer {@code false}
         */
        @Override
        public boolean supportsRecipeTree() {
            return false;
        }

        /**
         * Baut die Anzeige: Item-Slot und Textzeilen bzw. bei Beute die moeglichen Drops mit Chance.
         *
         * @param widgets Sammelstelle der Anzeigeelemente
         */
        @Override
        public void addWidgets(WidgetHolder widgets) {
            if (entry.kind() == Kind.LOOT) {
                widgets.addText(entry.lines().get(0), 0, 1, ViewerEntries.TEXT_COLOR, false);
                List<EmiStack> outs = getOutputs();
                for (int i = 0; i < outs.size(); i++) {
                    widgets.addSlot(outs.get(i), i * 22, 12).recipeContext(this);
                    Component c = entry.outputs().get(i).chanceText();
                    widgets.addText(c, 9 + i * 22 - net.minecraft.client.Minecraft.getInstance().font.width(c) / 2, 33, 0xFF606060, false);
                }
            } else {
                widgets.addSlot(EmiStack.of(entry.input()), 0, 6);
                int y = entry.lines().size() > 2 ? 1 : 6;
                for (Component line : entry.lines()) {
                    widgets.addText(line, 24, y, ViewerEntries.TEXT_COLOR, false);
                    y += 10;
                }
            }
        }
    }
}
