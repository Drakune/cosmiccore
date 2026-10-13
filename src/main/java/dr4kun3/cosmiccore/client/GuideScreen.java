package dr4kun3.cosmiccore.client;

import dr4kun3.cosmiccore.block.entity.ControllerBlockEntity;
import dr4kun3.cosmiccore.data.CoreFuel;
import dr4kun3.cosmiccore.data.CoreStabilizer;
import dr4kun3.cosmiccore.data.ModDataMaps;
import dr4kun3.cosmiccore.multiblock.ReactorSize;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Das kosmische Handbuch als eigene Doppelseite: klickbares Inhaltsverzeichnis, Textseiten, drehende 3D-Ansichten
 * jeder Baugroesse mit Blockliste sowie Brennstoff- und Stabilisator-Tabellen direkt aus den Data Maps.
 */
public class GuideScreen extends Screen {
    private static final int PW = 146, PH = 192, GAP = 10;
    private static final int PAPER = 0xFFF3EAD6, PAPER_EDGE = 0xFFE4D6B8, INK = 0xFF2A1A40, GOLD = 0xFFB8903A, COVER = 0xFF2A1450;

    private interface Page {
        void render(GuiGraphics g, int x, int y, int mouseX, int mouseY, float t);
    }

    private record Chapter(String key, int page) {}

    private final List<Page> pages = new ArrayList<>();
    private final List<Chapter> chapters = new ArrayList<>();
    private final List<int[]> tocHits = new ArrayList<>(); // {x0, y0, x1, y1, page}
    private int spread;
    private float dragYaw, dragPitch;
    private PageButton back, forward;

    public GuideScreen() {
        super(Component.translatable("item.cosmiccore.guide_book"));
        pages.add(this::toc);
        chapter("intro", text("guide.cosmiccore.page.1"));
        chapter("building", text("guide.cosmiccore.page.2"), text("guide.cosmiccore.page.3"), text("guide.cosmiccore.page.4"));
        List<Page> structures = new ArrayList<>();
        for (ReactorSize s : ReactorSize.values()) structures.add(structure(s));
        chapter("structures", structures.toArray(new Page[0]));
        chapter("operation", text("guide.cosmiccore.page.5"), text("guide.cosmiccore.page.6"),
                text("guide.cosmiccore.page.7"), text("guide.cosmiccore.page.8"));
        chapter("fuels", this::fuels, this::stabilizers, text("guide.cosmiccore.modules"));
        chapter("cosmos", text("guide.cosmiccore.page.9"), text("guide.cosmiccore.page.11"));
        chapter("asteroids", text("guide.cosmiccore.page.12"), text("guide.cosmiccore.page.13"), text("guide.cosmiccore.loot"));
        chapter("overdrive", text("guide.cosmiccore.page.14"), text("guide.cosmiccore.page.15"));
        chapter("tips", text("guide.cosmiccore.page.10"));
    }

    private void chapter(String key, Page... content) {
        chapters.add(new Chapter(key, pages.size()));
        for (Page p : content) pages.add(p);
    }

    private int left() {
        return (width - (PW * 2 + GAP)) / 2;
    }

    private int top() {
        return (height - PH) / 2;
    }

    private int spreads() {
        return (pages.size() + 1) / 2;
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        back = addRenderableWidget(new PageButton(x + 10, y + PH - 22, false, b -> turn(-1), true));
        forward = addRenderableWidget(new PageButton(x + PW * 2 + GAP - 33, y + PH - 22, true, b -> turn(1), true));
        addRenderableWidget(Button.builder(Component.translatable("guide.cosmiccore.contents"), b -> spread = 0)
                .bounds(x + PW + GAP / 2 - 30, y + PH + 4, 60, 16).build());
        updateButtons();
    }

    private void turn(int dir) {
        spread = Mth.clamp(spread + dir, 0, spreads() - 1);
        updateButtons();
    }

    private void updateButtons() {
        if (back != null) back.visible = spread > 0;
        if (forward != null) forward.visible = spread < spreads() - 1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        float t = (minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0) + partialTick;
        int x = left(), y = top();
        // Einband und Seiten
        g.fill(x - 8, y - 8, x + PW * 2 + GAP + 8, y + PH + 8, COVER);
        g.fill(x - 6, y - 6, x + PW * 2 + GAP + 6, y + PH + 6, GOLD);
        g.fill(x - 5, y - 5, x + PW * 2 + GAP + 5, y + PH + 5, COVER);
        for (int side = 0; side < 2; side++) {
            int px = x + side * (PW + GAP);
            g.fillGradient(px, y, px + PW, y + PH, PAPER, PAPER_EDGE);
            // Sternenornamente in den Ecken
            for (int k = 0; k < 4; k++) {
                int sx = px + (k % 2 == 0 ? 6 : PW - 8), sy = y + (k < 2 ? 6 : PH - 8);
                int a = (int) (120 + 100 * Mth.sin(t * 0.08F + k + side * 2));
                g.fill(sx, sy, sx + 2, sy + 2, (a << 24) | 0x7A4AC0);
            }
        }
        g.fill(x + PW + GAP / 2 - 1, y, x + PW + GAP / 2 + 1, y + PH, GOLD);

        tocHits.clear();
        int l = spread * 2;
        if (l < pages.size()) pages.get(l).render(g, x, y, mouseX, mouseY, t);
        if (l + 1 < pages.size()) pages.get(l + 1).render(g, x + PW + GAP, y, mouseX, mouseY, t);
        // Seitenzahlen
        g.drawString(font, String.valueOf(l + 1), x + PW / 2 - 3, y + PH - 12, GOLD, false);
        if (l + 1 < pages.size()) g.drawString(font, String.valueOf(l + 2), x + PW + GAP + PW / 2 - 3, y + PH - 12, GOLD, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
    }

    // ------------------------------------------------------------------ Seitentypen

    private void title(GuiGraphics g, Component text, int x, int y) {
        g.drawString(font, text, x + (PW - font.width(text)) / 2, y + 10, INK, false);
        g.fill(x + 16, y + 21, x + PW - 16, y + 22, GOLD);
    }

    private void toc(GuiGraphics g, int x, int y, int mouseX, int mouseY, float t) {
        title(g, Component.translatable("guide.cosmiccore.contents").withStyle(ChatFormatting.BOLD), x, y);
        int ly = y + 30;
        for (Chapter c : chapters) {
            Component name = Component.translatable("guide.cosmiccore.chapter." + c.key());
            String num = String.valueOf(c.page() + 1);
            boolean hover = mouseX >= x + 10 && mouseX < x + PW - 10 && mouseY >= ly - 1 && mouseY < ly + 10;
            int col = hover ? 0xFF7A4AC0 : INK;
            g.drawString(font, name, x + 12, ly, col, false);
            int dotsEnd = x + PW - 14 - font.width(num);
            for (int dx = x + 16 + font.width(name); dx < dotsEnd - 2; dx += 4) g.fill(dx, ly + 7, dx + 1, ly + 8, 0x66000000);
            g.drawString(font, num, x + PW - 12 - font.width(num), ly, col, false);
            tocHits.add(new int[]{x + 10, ly - 1, x + PW - 10, ly + 10, c.page()});
            ly += 15;
        }
    }

    private Page text(String key) {
        return (g, x, y, mouseX, mouseY, t) -> {
            List<FormattedCharSequence> lines = font.split(Component.translatable(key), PW - 20);
            int ly = y + 10;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, x + 10, ly, INK, false);
                ly += 10;
            }
        };
    }

    private Page structure(ReactorSize size) {
        return (g, x, y, mouseX, mouseY, t) -> {
            Component head = Component.literal(size.size() + "x" + size.size() + "x" + size.size())
                    .withStyle(size.isUltimate() ? ChatFormatting.GOLD : ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
            title(g, head, x, y);
            int bx0 = x + 8, by0 = y + 26, bx1 = x + PW - 8, by1 = y + 122;
            g.fill(bx0, by0, bx1, by1, 0xFF140A28);
            StructurePreview.render(g, size, (bx0 + bx1) / 2, (by0 + by1) / 2 + 4, new int[]{bx0, by0, bx1, by1}, 82.0F,
                    t * 0.8F + 30.0F + dragYaw, dragPitch, false, false);
            g.drawString(font, Component.translatable("guide.cosmiccore.levels", size.maxScale()), x + 10, y + 126, INK, false);
            List<ItemStack> icons = StructurePreview.requiredBlocks(size, false, false);
            for (int i = 0; i < icons.size(); i++) {
                int ix = x + 10 + (i % 7) * 18, iy = y + 138 + (i / 7) * 18;
                g.renderItem(icons.get(i), ix, iy);
                g.renderItemDecorations(font, icons.get(i), ix, iy);
                if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                    g.renderTooltip(font, icons.get(i).getHoverName(), mouseX, mouseY);
                }
            }
        };
    }

    private void fuels(GuiGraphics g, int x, int y, int mouseX, int mouseY, float t) {
        title(g, Component.translatable("guide.cosmiccore.chapter.fuels").withStyle(ChatFormatting.BOLD), x, y);
        int ly = y + 28;
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack st = new ItemStack(item);
            CoreFuel f = ModDataMaps.fuel(st);
            if (f == null) continue;
            if (ly > y + PH - 30) break;
            g.renderItem(st, x + 8, ly - 4);
            String line = f.power() + " RF/t · " + f.burnTime() / 20 + " s" + (f.heat() != 1.0F ? " · ♨" + String.format("%.1f", f.heat()) : "");
            g.drawString(font, line, x + 28, ly, f.heat() > 1.0F ? 0xFF9A2A2A : INK, false);
            if (mouseX >= x + 8 && mouseX < x + PW - 8 && mouseY >= ly - 4 && mouseY < ly + 12) {
                g.renderTooltip(font, st.getHoverName(), mouseX, mouseY);
            }
            ly += 17;
        }
    }

    private void stabilizers(GuiGraphics g, int x, int y, int mouseX, int mouseY, float t) {
        title(g, Component.translatable("guide.cosmiccore.stabilizers").withStyle(ChatFormatting.BOLD), x, y);
        int ly = y + 28;
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack st = new ItemStack(item);
            CoreStabilizer s = ModDataMaps.stabilizer(st);
            if (s == null) continue;
            if (ly > y + 110) break;
            g.renderItem(st, x + 8, ly - 4);
            g.drawString(font, Component.translatable("guide.cosmiccore.cooling",
                    String.format("%.0f", ControllerBlockEntity.stabilizerPoints(s))), x + 28, ly, INK, false);
            if (mouseX >= x + 8 && mouseX < x + PW - 8 && mouseY >= ly - 4 && mouseY < ly + 12) {
                g.renderTooltip(font, st.getHoverName(), mouseX, mouseY);
            }
            ly += 17;
        }
        List<FormattedCharSequence> lines = font.split(Component.translatable("guide.cosmiccore.stabilizer_note"), PW - 20);
        for (FormattedCharSequence line : lines) {
            ly += 10;
            g.drawString(font, line, x + 10, ly, 0xFF5A4A70, false);
        }
    }

    // ------------------------------------------------------------------ Eingabe

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int[] h : tocHits) {
            if (mouseX >= h[0] && mouseX < h[2] && mouseY >= h[1] && mouseY < h[3]) {
                spread = h[4] / 2;
                updateButtons();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        dragYaw += (float) dragX * 1.5F;
        dragPitch = Mth.clamp(dragPitch + (float) dragY, -40.0F, 50.0F);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        turn(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
