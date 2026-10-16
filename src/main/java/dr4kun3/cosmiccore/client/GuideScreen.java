package dr4kun3.cosmiccore.client;

import dr4kun3.cosmiccore.block.entity.ControllerBlockEntity;
import dr4kun3.cosmiccore.data.CoreFuel;
import dr4kun3.cosmiccore.data.CoreStabilizer;
import dr4kun3.cosmiccore.data.ModDataMaps;
import dr4kun3.cosmiccore.multiblock.ReactorSize;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
        void render(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY, float t);
    }

    private record Chapter(String key, int page) {}

    private final List<Page> pages = new ArrayList<>();
    private final List<Chapter> chapters = new ArrayList<>();
    private final List<int[]> tocHits = new ArrayList<>(); // {x0, y0, x1, y1, page}
    private int spread;
    private float dragYaw, dragPitch;
    private PageButton back, forward;
    private Button contents;

    public GuideScreen() {
        super(Component.translatable("item.cosmiccore.guide_book"));
    }

    /**
     * Seiten aufbauen (braucht die Schrift, deshalb erst in init()). Lange Texte werden automatisch auf Folgeseiten
     * verteilt, damit nichts ueber den Seitenrand laeuft - in jeder Sprache.
     */
    private void buildPages() {
        pages.clear();
        chapters.clear();
        pages.add(this::toc);
        chapter("intro", text("guide.cosmiccore.page.1"));
        chapter("building", text("guide.cosmiccore.page.2"), text("guide.cosmiccore.page.3"), text("guide.cosmiccore.page.4"));
        List<Page> structures = new ArrayList<>();
        for (ReactorSize s : ReactorSize.values()) structures.add(structure(s));
        chapter("structures", structures.toArray(new Page[0]));
        chapter("operation", text("guide.cosmiccore.page.5"), text("guide.cosmiccore.page.6"),
                text("guide.cosmiccore.page.7"), text("guide.cosmiccore.page.8"));
        chapter("fuels", new Page[]{this::fuels}, new Page[]{this::stabilizers}, text("guide.cosmiccore.modules"));
        chapter("cosmos", text("guide.cosmiccore.page.9"), text("guide.cosmiccore.page.11"));
        chapter("asteroids", text("guide.cosmiccore.page.12"), text("guide.cosmiccore.page.13"), text("guide.cosmiccore.loot"));
        chapter("overdrive", text("guide.cosmiccore.page.14"), text("guide.cosmiccore.page.15"));
        chapter("tips", text("guide.cosmiccore.page.10"));
    }

    private void chapter(String key, Page... content) {
        chapters.add(new Chapter(key, pages.size()));
        for (Page p : content) pages.add(p);
    }

    private void chapter(String key, Page[]... content) {
        chapters.add(new Chapter(key, pages.size()));
        for (Page[] group : content) for (Page p : group) pages.add(p);
    }

    private int left() {
        return (width - (PW * 2 + GAP)) / 2;
    }

    private int top() {
        return (height - PH) / 2;
    }

    int spreads() {
        return (pages.size() + 1) / 2;
    }

    @Override
    protected void init() {
        buildPages();
        spread = Mth.clamp(spread, 0, spreads() - 1);
        int x = left(), y = top();
        back = addRenderableWidget(new PageButton(x + 10, y + PH - 22, false, b -> turn(-1), true));
        forward = addRenderableWidget(new PageButton(x + PW * 2 + GAP - 33, y + PH - 22, true, b -> turn(1), true));
        contents = addRenderableWidget(Button.builder(Component.translatable("guide.cosmiccore.contents"), b -> goTo(0))
                .bounds(x + PW + GAP / 2 - 30, y + PH + 4, 60, 16).build());
        updateButtons();
    }

    /** Auch fuer den Entwicklungs-Test: eine Doppelseite weiter/zurueck. */
    void turn(int dir) {
        goTo(spread + dir);
    }

    /** Zu einer Doppelseite blaettern - die Seite dreht sich dabei sichtbar um den Buchruecken. */
    private void goTo(int target) {
        target = Mth.clamp(target, 0, spreads() - 1);
        if (target == spread || opening() < 1.0F || closeStart >= 0) return;
        turnFrom = spread;
        spread = target;
        turnStart = Util.getMillis();
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.9F + 0.2F * (float) Math.random()));
        }
        updateButtons();
    }

    private void updateButtons() {
        boolean ready = opening() >= 1.0F && closeStart < 0;
        if (back != null) back.visible = ready && spread > 0;
        if (forward != null) forward.visible = ready && spread < spreads() - 1;
        if (contents != null) contents.visible = ready;
    }

    // ------------------------------------------------------------------ Animation

    private static final long OPEN_MS = 1100, TURN_MS = 520, CLOSE_MS = 650;
    private long openStart = -1, turnStart = -1, closeStart = -1;
    private int turnFrom;
    /** Waehrend eine Seite umklappt, wird die 3D-Vorschau auf ihr nicht gezeichnet (die Schere waere sonst falsch). */
    private boolean flat;

    /** Aufklappen beim Oeffnen 0..1 (beim Schliessen rueckwaerts). */
    private float opening() {
        long now = Util.getMillis();
        if (closeStart >= 0) return 1.0F - Mth.clamp((now - closeStart) / (float) CLOSE_MS, 0.0F, 1.0F);
        if (openStart < 0) return 0.0F;
        return Mth.clamp((now - openStart) / (float) OPEN_MS, 0.0F, 1.0F);
    }

    private float turning() {
        if (turnStart < 0) return 1.0F;
        float p = Mth.clamp((Util.getMillis() - turnStart) / (float) TURN_MS, 0.0F, 1.0F);
        if (p >= 1.0F) turnStart = -1;
        return p;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    @Override
    public void tick() {
        super.tick();
        updateButtons();
        if (closeStart >= 0 && Util.getMillis() - closeStart >= CLOSE_MS && minecraft != null) minecraft.gui.setScreen(null);
    }

    /** Schliessen: das Buch klappt erst zu. */
    @Override
    public void onClose() {
        if (closeStart < 0 && opening() >= 1.0F) {
            closeStart = Util.getMillis();
            if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PUT, 1.0F));
            updateButtons();
            return;
        }
        if (closeStart < 0) super.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (openStart < 0) {
            openStart = Util.getMillis();
            if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.7F));
        }
        float t = (minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0) + partialTick;
        int x = left(), y = top();
        float spine = x + PW + GAP / 2.0F;
        tocHits.clear();
        float o = opening();
        if (o < 1.0F) {
            renderOpening(g, x, y, spine, o, mouseX, mouseY, t);
        } else {
            float p = turning();
            if (p < 1.0F) renderTurn(g, x, y, spine, p, mouseX, mouseY, t);
            else {
                frame(g, x, x + PW * 2 + GAP, y);
                page(g, spread * 2, x, y, mouseX, mouseY, t);
                page(g, spread * 2 + 1, x + PW + GAP, y, mouseX, mouseY, t);
                spineLine(g, spine, y);
            }
        }
        // Knoepfe erst bei aufgeschlagenem Buch (ueber dem Buch gezeichnet)
        for (var w : renderables) if (w instanceof net.minecraft.client.gui.components.AbstractWidget aw && aw.visible) aw.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    /** Oeffnen: geschlossen aus der Tiefe in die Mitte, dann klappt der Einband um den Buchruecken auf. */
    private void renderOpening(GuiGraphicsExtractor g, int x, int y, float spine, float o, int mouseX, int mouseY, float t) {
        float fly = smooth(o / 0.4F);              // 0..0.4: heranfliegen
        float open = smooth((o - 0.4F) / 0.6F);    // 0.4..1: aufklappen
        float scale = Mth.lerp(fly, 0.35F, 1.0F);
        // geschlossen liegt das Buch mittig; beim Aufklappen rueckt es zur Doppelseite
        float shift = Mth.lerp(open, -(PW + GAP) / 2.0F, 0.0F);
        g.pose().pushMatrix();
        float cx = width / 2.0F, cy = height / 2.0F;
        g.pose().translate(cx, cy + (1.0F - fly) * 40.0F);
        g.pose().scale(scale, scale);
        g.pose().translate(-cx + shift, -cy);
        // rechte Haelfte: Seitenblock mit Seite 2
        frame(g, (int) spine, x + PW * 2 + GAP, y);
        page(g, 1, x + PW + GAP, y, -1, -1, t);
        float angle = open * Mth.PI;
        float c = Mth.cos(angle);
        if (c > 0) {
            // Einband liegt noch rechts und klappt hoch
            squeezed(g, spine, c, () -> cover(g, x + PW + GAP, y));
            shade(g, (int) spine, (int) (spine + PW * c), y, 1.0F - c);
        } else {
            // Einband ist ueber den Ruecken - seine Innenseite wird zur linken Seite (Inhaltsverzeichnis)
            float k = -c;
            frameLeft(g, spine, k, y);
            flat = true;
            squeezed(g, spine, k, () -> page(g, 0, x, y, -1, -1, t));
            flat = false;
            shade(g, (int) (spine - PW * k), (int) spine, y, 1.0F - k);
        }
        spineLine(g, spine, y);
        g.pose().popMatrix();
    }

    /** Blaettern: das Blatt dreht sich um den Buchruecken (vorwaerts von rechts nach links, rueckwaerts umgekehrt). */
    private void renderTurn(GuiGraphicsExtractor g, int x, int y, float spine, float p, int mouseX, int mouseY, float t) {
        boolean fwd = spread > turnFrom;
        float e = smooth(p);
        float c = Mth.cos(e * Mth.PI);
        int from = turnFrom * 2, to = spread * 2;
        frame(g, x, x + PW * 2 + GAP, y);
        // darunter: was schon bzw. noch zu sehen ist
        if (fwd) {
            page(g, from, x, y, -1, -1, t);
            page(g, to + 1, x + PW + GAP, y, -1, -1, t);
        } else {
            page(g, to, x, y, -1, -1, t);
            page(g, from + 1, x + PW + GAP, y, -1, -1, t);
        }
        spineLine(g, spine, y);
        flat = true;
        if (fwd) {
            if (c > 0) squeezed(g, spine, c, () -> page(g, from + 1, x + PW + GAP, y, -1, -1, t));
            else squeezed(g, spine, -c, () -> page(g, to, x, y, -1, -1, t));
        } else {
            if (c > 0) squeezed(g, spine, c, () -> page(g, from, x, y, -1, -1, t));
            else squeezed(g, spine, -c, () -> page(g, to + 1, x + PW + GAP, y, -1, -1, t));
        }
        flat = false;
        // Schatten auf dem drehenden Blatt und ein Schlagschatten auf der Seite darunter
        float k = Math.abs(c);
        boolean right = fwd == (c > 0);
        int x0 = right ? (int) spine : (int) (spine - PW * k), x1 = right ? (int) (spine + PW * k) : (int) spine;
        shade(g, x0, x1, y, 1.0F - k);
        int sw = (int) (18 * (1.0F - k));
        if (sw > 0) {
            int a = (int) (90 * (1.0F - k));
            g.pose().pushMatrix();
            if (right) g.fillGradient(x1, y, x1 + sw, y + PH, a << 24, 0); // Kante wirft Schatten nach aussen
            else g.fillGradient(x0 - sw, y, x0, y + PH, 0, a << 24);
            g.pose().popMatrix();
        }
    }

    /** Zeichnet etwas zur Mitte hin gestaucht (Blatt im Drehen; factor 1 = flach, 0 = senkrecht). */
    private void squeezed(GuiGraphicsExtractor g, float spine, float factor, Runnable draw) {
        if (factor <= 0.01F) return;
        g.nextStratum(); // Blatt liegt ueber allem, was darunter auf den Seiten ist (auch Items und 3D-Vorschau)
        g.pose().pushMatrix();
        g.pose().translate(spine, 0);
        g.pose().scale(factor, 1.0F);
        g.pose().translate(-spine, 0);
        draw.run();
        g.pose().popMatrix();
    }

    private void shade(GuiGraphicsExtractor g, int x0, int x1, int y, float amount) {
        int a = (int) (110 * Mth.clamp(amount, 0, 1));
        if (a <= 2 || x1 <= x0) return;
        g.pose().pushMatrix();
        g.fill(x0, y, x1, y + PH, a << 24);
        g.pose().popMatrix();
    }

    /** Einband um einen Bereich (Buchdeckel mit Goldkante). */
    private void frame(GuiGraphicsExtractor g, int x0, int x1, int y) {
        g.fill(x0 - 8, y - 8, x1 + 8, y + PH + 8, COVER);
        g.fill(x0 - 6, y - 6, x1 + 6, y + PH + 6, GOLD);
        g.fill(x0 - 5, y - 5, x1 + 5, y + PH + 5, COVER);
    }

    /** Linker Deckel, der beim Aufklappen mitwaechst. */
    private void frameLeft(GuiGraphicsExtractor g, float spine, float k, int y) {
        int x0 = (int) (spine - (PW + GAP / 2.0F) * k);
        g.fill(x0 - 8, y - 8, (int) spine, y + PH + 8, COVER);
        g.fill(x0 - 6, y - 6, (int) spine, y + PH + 6, GOLD);
        g.fill(x0 - 5, y - 5, (int) spine, y + PH + 5, COVER);
    }

    private void spineLine(GuiGraphicsExtractor g, float spine, int y) {
        g.fill((int) spine - 1, y, (int) spine + 1, y + PH, GOLD);
    }

    /** Vorderer Buchdeckel: Titel, Goldrahmen, Sternemblem. */
    private void cover(GuiGraphicsExtractor g, int px, int y) {
        g.fill(px - GAP / 2, y - 8, px + PW + 8, y + PH + 8, COVER);
        g.fill(px + 2, y + 2, px + PW - 2, y + PH - 2, GOLD);
        g.fill(px + 3, y + 3, px + PW - 3, y + PH - 3, COVER);
        int cx = px + PW / 2, cy = y + 78;
        for (int r = 0; r < 22; r++) { // Stern-Emblem als Raute
            int w = 22 - r;
            g.fill(cx - w, cy - r, cx + w, cy - r + 1, r % 4 == 0 ? GOLD : 0xFF4A2A80);
            g.fill(cx - w, cy + r, cx + w, cy + r + 1, r % 4 == 0 ? GOLD : 0xFF4A2A80);
        }
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFFFFF0C0);
        Component title = getTitle();
        List<FormattedCharSequence> lines = font.split(title, PW - 24);
        int ly = y + 118;
        for (FormattedCharSequence l : lines) {
            g.text(font, l, cx - font.width(l) / 2, ly, GOLD, false);
            ly += 11;
        }
    }

    /** Eine Seite: Papier, Ecksterne, Inhalt und Seitenzahl. */
    private void page(GuiGraphicsExtractor g, int index, int px, int y, int mouseX, int mouseY, float t) {
        g.fillGradient(px, y, px + PW, y + PH, PAPER, PAPER_EDGE);
        for (int k = 0; k < 4; k++) {
            int sx = px + (k % 2 == 0 ? 6 : PW - 8), sy = y + (k < 2 ? 6 : PH - 8);
            int a = (int) (120 + 100 * Mth.sin(t * 0.08F + k + index * 2));
            g.fill(sx, sy, sx + 2, sy + 2, (a << 24) | 0x7A4AC0);
        }
        if (index >= pages.size()) return;
        pages.get(index).render(g, px, y, mouseX, mouseY, t);
        g.text(font, String.valueOf(index + 1), px + PW / 2 - 3, y + PH - 12, GOLD, false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(g);
    }

    // ------------------------------------------------------------------ Seitentypen

    private void title(GuiGraphicsExtractor g, Component text, int x, int y) {
        g.text(font, text, x + (PW - font.width(text)) / 2, y + 10, INK, false);
        g.fill(x + 16, y + 21, x + PW - 16, y + 22, GOLD);
    }

    private void toc(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY, float t) {
        title(g, Component.translatable("guide.cosmiccore.contents").withStyle(ChatFormatting.BOLD), x, y);
        int ly = y + 30;
        for (Chapter c : chapters) {
            Component name = Component.translatable("guide.cosmiccore.chapter." + c.key());
            String num = String.valueOf(c.page() + 1);
            boolean hover = mouseX >= x + 10 && mouseX < x + PW - 10 && mouseY >= ly - 1 && mouseY < ly + 10;
            int col = hover ? 0xFF7A4AC0 : INK;
            g.text(font, name, x + 12, ly, col, false);
            int dotsEnd = x + PW - 14 - font.width(num);
            for (int dx = x + 16 + font.width(name); dx < dotsEnd - 2; dx += 4) g.fill(dx, ly + 7, dx + 1, ly + 8, 0x66000000);
            g.text(font, num, x + PW - 12 - font.width(num), ly, col, false);
            tocHits.add(new int[]{x + 10, ly - 1, x + PW - 10, ly + 10, c.page()});
            ly += 15;
        }
    }

    /** Text fuer das helle Papier: zu blasse Farben (Gelb, Hellblau, Hellgruen) werden dunkler, damit sie lesbar bleiben. */
    private static Component onPaper(String key) {
        String text = Component.translatable(key).getString();
        return Component.literal(text.replace("\u00A7e", "\u00A76").replace("\u00A7b", "\u00A73").replace("\u00A7a", "\u00A72")
                .replace("\u00A7f", "\u00A78"));
    }

    /** Zeilen pro Textseite: unten bleiben Seitenzahl und Blaetterknoepfe frei. */
    private static final int LINES_PER_PAGE = 15;

    /** Textseite(n): ist der Text laenger als eine Seite, geht er auf der naechsten Seite weiter. */
    private Page[] text(String key) {
        List<FormattedCharSequence> lines = font.split(onPaper(key), PW - 20);
        int count = Math.max(1, (lines.size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
        Page[] out = new Page[count];
        for (int i = 0; i < count; i++) {
            List<FormattedCharSequence> part = lines.subList(i * LINES_PER_PAGE, Math.min(lines.size(), (i + 1) * LINES_PER_PAGE));
            out[i] = (g, x, y, mouseX, mouseY, t) -> {
                int ly = y + 10;
                for (FormattedCharSequence line : part) {
                    g.text(font, line, x + 10, ly, INK, false);
                    ly += 10;
                }
            };
        }
        return out;
    }

    private Page structure(ReactorSize size) {
        return (g, x, y, mouseX, mouseY, t) -> {
            Component head = Component.literal(size.size() + "x" + size.size() + "x" + size.size())
                    .withStyle(size.isUltimate() ? ChatFormatting.GOLD : ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
            title(g, head, x, y);
            int bx0 = x + 8, by0 = y + 26, bx1 = x + PW - 8, by1 = y + 122;
            g.fill(bx0, by0, bx1, by1, 0xFF140A28);
            if (!flat) {
                StructurePreview.render(g, size, (bx0 + bx1) / 2, (by0 + by1) / 2 + 4, new int[]{bx0, by0, bx1, by1}, 82.0F,
                        t * 0.8F + 30.0F + dragYaw, dragPitch, false, false);
            }
            g.text(font, Component.translatable("guide.cosmiccore.levels", size.maxScale()), x + 10, y + 126, INK, false);
            List<ItemStack> icons = StructurePreview.requiredBlocks(size, false, false);
            for (int i = 0; i < icons.size(); i++) {
                int ix = x + 10 + (i % 7) * 18, iy = y + 138 + (i / 7) * 18;
                g.item(icons.get(i), ix, iy);
                g.itemDecorations(font, icons.get(i), ix, iy);
                if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                    g.setTooltipForNextFrame(font, icons.get(i).getHoverName(), mouseX, mouseY);
                }
            }
        };
    }

    private void fuels(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY, float t) {
        title(g, Component.translatable("guide.cosmiccore.chapter.fuels").withStyle(ChatFormatting.BOLD), x, y);
        int ly = y + 28;
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack st = new ItemStack(item);
            CoreFuel f = ModDataMaps.fuel(st);
            if (f == null) continue;
            if (ly > y + PH - 30) break;
            g.item(st, x + 8, ly - 4);
            String line = f.power() + " RF/t · " + f.burnTime() / 20 + " s" + (f.heat() != 1.0F ? " · ♨" + String.format("%.1f", f.heat()) : "");
            // zu breite Zeilen (z. B. mit Hitze-Angabe) werden passend verkleinert statt ueber den Rand zu laufen
            float room = PW - 28 - 8, w = font.width(line), sc = Math.min(1.0F, room / w);
            g.pose().pushMatrix();
            g.pose().translate(x + 28, ly + (1.0F - sc) * 4.0F);
            g.pose().scale(sc, sc);
            g.text(font, line, 0, 0, f.heat() > 1.0F ? 0xFF9A2A2A : INK, false);
            g.pose().popMatrix();
            if (mouseX >= x + 8 && mouseX < x + PW - 8 && mouseY >= ly - 4 && mouseY < ly + 12) {
                g.setTooltipForNextFrame(font, st.getHoverName(), mouseX, mouseY);
            }
            ly += 17;
        }
    }

    private void stabilizers(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY, float t) {
        title(g, Component.translatable("guide.cosmiccore.stabilizers").withStyle(ChatFormatting.BOLD), x, y);
        int ly = y + 28;
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack st = new ItemStack(item);
            CoreStabilizer s = ModDataMaps.stabilizer(st);
            if (s == null) continue;
            if (ly > y + 110) break;
            g.item(st, x + 8, ly - 4);
            g.text(font, Component.translatable("guide.cosmiccore.cooling",
                    String.format("%.0f", ControllerBlockEntity.stabilizerPoints(s))), x + 28, ly, INK, false);
            if (mouseX >= x + 8 && mouseX < x + PW - 8 && mouseY >= ly - 4 && mouseY < ly + 12) {
                g.setTooltipForNextFrame(font, st.getHoverName(), mouseX, mouseY);
            }
            ly += 17;
        }
        List<FormattedCharSequence> lines = font.split(Component.translatable("guide.cosmiccore.stabilizer_note"), PW - 20);
        for (FormattedCharSequence line : lines) {
            ly += 10;
            g.text(font, line, x + 10, ly, 0xFF5A4A70, false);
        }
    }

    // ------------------------------------------------------------------ Eingabe

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        for (int[] h : tocHits) {
            if (mouseX >= h[0] && mouseX < h[2] && mouseY >= h[1] && mouseY < h[3]) {
                goTo(h[4] / 2);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
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
