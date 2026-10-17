package dr4kun3.cosmiccore.compat.rei;

import dr4kun3.cosmiccore.CosmicCore;
import dr4kun3.cosmiccore.compat.ViewerEntries;
import dr4kun3.cosmiccore.compat.ViewerEntries.Entry;
import dr4kun3.cosmiccore.compat.ViewerEntries.Kind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** REI: Kategorien fuer Brennstoffe, Stabilisatoren und Asteroiden-Beute plus Info-Seiten. */
@REIPluginClient
public class CosmicReiPlugin implements REIClientPlugin {
    private static final Map<Kind, CategoryIdentifier<CosmicDisplay>> IDS = new EnumMap<>(Kind.class);

    static {
        for (Kind k : Kind.values()) IDS.put(k, CategoryIdentifier.of(CosmicCore.MODID, k.name().toLowerCase()));
    }

    /**
     * Haelt den Modul- und Lager-Tab rechts am Controller-Fenster frei (REI legt dort sonst seine Liste darueber).
     *
     * @param zones Registrierung der freizuhaltenden Bereiche
     */
    @Override
    public void registerExclusionZones(me.shedaniel.rei.api.client.registry.screen.ExclusionZones zones) {
        zones.register(dr4kun3.cosmiccore.client.ControllerScreen.class, screen -> {
            List<Rectangle> out = new ArrayList<>();
            for (var r : screen.extraAreas()) out.add(new Rectangle(r.getX(), r.getY(), r.getWidth(), r.getHeight()));
            return out;
        });
    }

    /**
     * Meldet die Kategorien Brennstoffe, Stabilisatoren und Asteroiden-Beute samt ihrer Werkbank-Bloecke an.
     *
     * @param registry Registrierung der Kategorien
     */
    @Override
    public void registerCategories(CategoryRegistry registry) {
        for (Kind k : Kind.values()) {
            registry.add(new Category(k));
            for (ItemStack st : ViewerEntries.catalysts(k)) registry.addWorkstations(IDS.get(k), EntryStacks.of(st));
        }
    }

    /**
     * Meldet alle Eintraege der Kategorien und die Info-Seiten der wichtigsten Bloecke an.
     *
     * @param registry Registrierung der Anzeigen
     */
    @Override
    public void registerDisplays(DisplayRegistry registry) {
        for (Kind k : Kind.values()) {
            for (Entry e : ViewerEntries.of(k)) registry.add(new CosmicDisplay(e));
        }
        for (ViewerEntries.Info info : ViewerEntries.infos()) {
            registry.add(DefaultInformationDisplay.createFromEntry(EntryStacks.of(info.stack()), info.stack().getHoverName())
                    .line(info.text()));
        }
    }

    public record CosmicDisplay(Entry entry) implements Display {
        /**
         * Keine Rezept-ID - die Eintraege stammen nicht aus Rezepten.
         *
         * @return leer
         */
        @Override
        public java.util.Optional<net.minecraft.resources.ResourceLocation> getDisplayLocation() {
            return java.util.Optional.empty();
        }

        /**
         * Nur clientseitig registriert - muss nicht zwischen Server und Client uebertragen werden.
         *
         * @return {@code null}
         */
        @Override
        public me.shedaniel.rei.api.common.display.DisplaySerializer<? extends Display> getSerializer() {
            return null;
        }

        /**
         * Eingabe: das Item (bei Asteroiden-Beute keine).
         *
         * @return Liste der Eingaben
         */
        @Override
        public List<EntryIngredient> getInputEntries() {
            return entry.kind() == Kind.LOOT ? List.of() : List.of(EntryIngredients.of(entry.input()));
        }

        /**
         * Ausgaben dieses Eintrags.
         *
         * @return Liste der Ausgaben
         */
        @Override
        public List<EntryIngredient> getOutputEntries() {
            return entry.outputs().stream().map(o -> EntryIngredients.of(o.stack())).toList();
        }

        /**
         * Kategorie, zu der der Eintrag gehoert.
         *
         * @return Kennung der Kategorie
         */
        @Override
        public CategoryIdentifier<?> getCategoryIdentifier() {
            return IDS.get(entry.kind());
        }
    }

    private record Category(Kind kind) implements DisplayCategory<CosmicDisplay> {
        /**
         * Kennung dieser Kategorie.
         *
         * @return die Kennung
         */
        @Override
        public CategoryIdentifier<? extends CosmicDisplay> getCategoryIdentifier() {
            return IDS.get(kind);
        }

        /**
         * Titel der Kategorie.
         *
         * @return uebersetzter Titel
         */
        @Override
        public Component getTitle() {
            return ViewerEntries.title(kind);
        }

        /**
         * Symbol der Kategorie.
         *
         * @return Item-Symbol
         */
        @Override
        public Renderer getIcon() {
            return EntryStacks.of(ViewerEntries.icon(kind));
        }

        /**
         * Hoehe einer Anzeige (Beute braucht eine Zeile mehr).
         *
         * @return Hoehe in Pixeln
         */
        @Override
        public int getDisplayHeight() {
            return (kind == Kind.LOOT ? 44 : 30) + 10;
        }

        /**
         * Breite einer Anzeige.
         *
         * @param display die Anzeige
         * @return Breite in Pixeln
         */
        @Override
        public int getDisplayWidth(CosmicDisplay display) {
            return ViewerEntries.WIDTH + 10;
        }

        /**
         * Baut die Anzeige: Item-Slot und Textzeilen bzw. bei Beute die moeglichen Drops mit Chance.
         *
         * @param display die Anzeige
         * @param bounds verfuegbarer Bereich
         * @return die Anzeigeelemente
         */
        @Override
        public List<Widget> setupDisplay(CosmicDisplay display, Rectangle bounds) {
            Entry entry = display.entry();
            List<Widget> w = new ArrayList<>();
            w.add(Widgets.createRecipeBase(bounds));
            int x = bounds.x + 5, y = bounds.y + 5;
            if (entry.kind() == Kind.LOOT) {
                w.add(Widgets.createLabel(new Point(x, y + 1), entry.lines().get(0)).leftAligned().noShadow().color(0xFF404040, 0xFFBBBBBB));
                for (int i = 0; i < entry.outputs().size(); i++) {
                    w.add(Widgets.createSlot(new Point(x + 1 + i * 22, y + 13)).entries(EntryIngredients.of(entry.outputs().get(i).stack())).markOutput());
                    w.add(Widgets.createLabel(new Point(x + 9 + i * 22, y + 33), entry.outputs().get(i).chanceText()).noShadow().color(0xFF606060, 0xFFAAAAAA));
                }
            } else {
                w.add(Widgets.createSlot(new Point(x + 1, y + 7)).entries(EntryIngredients.of(entry.input())).markInput());
                int ly = y + (entry.lines().size() > 2 ? 1 : 6);
                for (Component line : entry.lines()) {
                    w.add(Widgets.createLabel(new Point(x + 24, ly), line).leftAligned().noShadow().color(0xFF404040, 0xFFBBBBBB));
                    ly += 10;
                }
            }
            return w;
        }
    }
}
