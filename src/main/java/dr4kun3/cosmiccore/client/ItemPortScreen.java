package dr4kun3.cosmiccore.client;

import dr4kun3.cosmiccore.block.entity.ItemPortBlockEntity;
import dr4kun3.cosmiccore.menu.ItemPortMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Beutelager des Item-Ports - gleicher Look wie die Steuerung des Controllers. */
public class ItemPortScreen extends AbstractContainerScreen<ItemPortMenu> {
    private static final int BORDER = 0xFF3A2468, FILL = 0xCC0E0820, ACCENT = 0xFF6A44B8, HEAD = 0xFFB8A8D8;

    public ItemPortScreen(ItemPortMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = ItemPortMenu.W;
        imageHeight = ItemPortMenu.H;
        titleLabelY = 6;
        inventoryLabelY = ItemPortMenu.INV_Y - 11;
    }

    /** Vom Controller aus geoeffnet: Schliessen fuehrt zurueck zur Steuerung. */
    @Override
    public void onClose() {
        if (menu.getReturnTo() != null && minecraft != null && minecraft.gameMode != null && minecraft.player != null
                && minecraft.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(menu.getReturnTo())) <= 64.0) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            return; // der Server oeffnet die Steuerung, die dieses Menue ersetzt
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        float t = (minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0) + partialTick;
        g.fill(x, y, x + imageWidth, y + imageHeight, BORDER);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFF0A0614);
        g.fillGradient(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFF1A0F33, 0xFF070410);
        for (int i = 0; i < 24; i++) { // Sterne
            int h = i * 0x9E3779B1;
            int sx = 3 + Math.floorMod(h >>> 7, imageWidth - 6), sy = 3 + Math.floorMod(h >>> 17, imageHeight - 6);
            int a = (int) (40 + 150 * (0.5f + 0.5f * Mth.sin(t * 0.08f + i * 1.7f)));
            g.fill(x + sx, y + sy, x + sx + 1, y + sy + 1, (a << 24) | (i % 2 == 0 ? 0xFFB070 : 0xE0B0FF));
        }
        // Karte um das Lager
        int px = x + ItemPortMenu.STORE_X - 4, py = y + ItemPortMenu.STORE_Y - 4;
        g.fill(px, py, px + 9 * 18 + 6, py + 3 * 18 + 6, BORDER);
        g.fill(px + 1, py + 1, px + 9 * 18 + 5, py + 3 * 18 + 5, FILL);
        g.fill(px + 3, py + 1, px + 9 * 18 + 3, py + 2, ACCENT);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            int border = i < ItemPortBlockEntity.STORAGE_SLOTS ? 0xFFB06A20 : 0xFF2E2050;
            g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, border);
            g.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, 0xFF0C0818);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFC070, false);
        int used = 0;
        for (int i = 0; i < ItemPortBlockEntity.STORAGE_SLOTS; i++) if (menu.slots.get(i).hasItem()) used++;
        String slots = used + " / " + ItemPortBlockEntity.STORAGE_SLOTS;
        g.drawString(font, slots, imageWidth - 8 - font.width(slots), titleLabelY, used >= ItemPortBlockEntity.STORAGE_SLOTS ? 0xFFFF6060 : HEAD, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, HEAD, false);
    }
}
