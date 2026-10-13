package dr4kun3.cosmiccore.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;

/** Zeigt das Handbuch im Vanilla-Buchbildschirm. Die Seiten stehen in den Sprachdateien (guide.cosmiccore.page.N). */
public final class GuideBook {
    public static final int PAGES = 15;

    public static void open() {
        List<Component> pages = new ArrayList<>();
        for (int i = 1; i <= PAGES; i++) {
            pages.add(Component.translatable("guide.cosmiccore.page." + i));
        }
        Minecraft.getInstance().setScreen(new BookViewScreen(new BookViewScreen.BookAccess(pages)));
    }

    private GuideBook() {}
}
