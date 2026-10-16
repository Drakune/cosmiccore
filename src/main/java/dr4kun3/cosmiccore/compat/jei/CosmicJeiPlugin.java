package dr4kun3.cosmiccore.compat.jei;

import dr4kun3.cosmiccore.CosmicCore;
import dr4kun3.cosmiccore.compat.ViewerEntries;
import dr4kun3.cosmiccore.compat.ViewerEntries.Entry;
import dr4kun3.cosmiccore.compat.ViewerEntries.Kind;
import java.util.EnumMap;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** JEI: Kategorien fuer Brennstoffe, Stabilisatoren und Asteroiden-Beute plus Info-Seiten. */
@JeiPlugin
public class CosmicJeiPlugin implements IModPlugin {
    private static final Map<Kind, RecipeType<Entry>> TYPES = new EnumMap<>(Kind.class);

    static {
        for (Kind k : Kind.values()) TYPES.put(k, RecipeType.create(CosmicCore.MODID, k.name().toLowerCase(), Entry.class));
    }

    @Override
    public Identifier getPluginUid() {
        return CosmicCore.id("jei_plugin");
    }

    /** Der Modul-Tab ragt rechts aus dem Controller-Fenster - JEI soll ihn nicht verdecken. */
    @Override
    public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(dr4kun3.cosmiccore.client.ControllerScreen.class,
                new mezz.jei.api.gui.handlers.IGuiContainerHandler<>() {
                    @Override
                    public java.util.List<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas(dr4kun3.cosmiccore.client.ControllerScreen screen) {
                        return screen.extraAreas();
                    }
                });
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        for (Kind k : Kind.values()) registration.addRecipeCategories(new Category(k, gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (Kind k : Kind.values()) registration.addRecipes(TYPES.get(k), ViewerEntries.of(k));
        for (ViewerEntries.Info info : ViewerEntries.infos()) registration.addItemStackInfo(info.stack(), info.text());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (Kind k : Kind.values()) {
            for (ItemStack st : ViewerEntries.catalysts(k)) registration.addRecipeCatalyst(st, TYPES.get(k));
        }
    }

    private static final class Category implements IRecipeCategory<Entry> {
        private final Kind kind;
        private final IDrawable icon;

        Category(Kind kind, IGuiHelper gui) {
            this.kind = kind;
            this.icon = gui.createDrawableItemStack(ViewerEntries.icon(kind));
        }

        @Override
        public RecipeType<Entry> getRecipeType() {
            return TYPES.get(kind);
        }

        @Override
        public Component getTitle() {
            return ViewerEntries.title(kind);
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public int getWidth() {
            return ViewerEntries.WIDTH;
        }

        @Override
        public int getHeight() {
            return kind == Kind.LOOT ? 44 : 30;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Entry entry, IFocusGroup focuses) {
            if (entry.kind() == Kind.LOOT) {
                for (int i = 0; i < entry.outputs().size(); i++) {
                    builder.addSlot(RecipeIngredientRole.OUTPUT, 1 + i * 22, 13)
                            .addItemStack(entry.outputs().get(i).stack())
                            .setStandardSlotBackground();
                }
            } else {
                builder.addSlot(RecipeIngredientRole.INPUT, 1, 7).addItemStack(entry.input()).setStandardSlotBackground();
            }
        }

        @Override
        public void draw(Entry entry, IRecipeSlotsView slots, GuiGraphicsExtractor g, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            if (entry.kind() == Kind.LOOT) {
                g.text(font, entry.lines().get(0), 0, 1, ViewerEntries.TEXT_COLOR, false);
                for (int i = 0; i < entry.outputs().size(); i++) {
                    Component c = entry.outputs().get(i).chanceText();
                    g.text(font, c, 9 + i * 22 - font.width(c) / 2, 33, 0xFF606060, false);
                }
            } else {
                int y = entry.lines().size() > 2 ? 1 : 6;
                for (Component line : entry.lines()) {
                    g.text(font, line, 24, y, ViewerEntries.TEXT_COLOR, false);
                    y += 10;
                }
            }
        }
    }
}
