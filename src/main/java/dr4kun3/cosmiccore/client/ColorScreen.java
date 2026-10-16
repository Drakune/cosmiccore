package dr4kun3.cosmiccore.client;

import dr4kun3.cosmiccore.color.CoreColors;
import dr4kun3.cosmiccore.network.SetCoreColorsPayload;
import java.awt.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Farbmenue des Farb-Moduls: Vorgaben, je Effekt eine eigene Farbe (Farbton/Saettigung/Helligkeit) und ein teilbarer
 * Code. Aenderungen werden sofort an den Controller geschickt - der Kern faerbt sich live mit.
 */
public class ColorScreen extends Screen {
    private static final int W = 320, H = 296, M = 5, PAD = 5;
    private static final int PANEL_BORDER = 0xFF3A2468, PANEL_FILL = 0xEE0E0820, PANEL_ACCENT = 0xFF6A44B8, HEAD = 0xFFB8A8D8;
    // Karten
    private static final int PRESET_Y = M, PRESET_H = 1 + PAD + 12 + 16 + PAD + 1;
    private static final int LIST_X = M, LIST_Y = PRESET_Y + PRESET_H + M, LIST_W = 150, ROW_H = 18;
    private static final int LIST_H = 1 + PAD + 12 + CoreColors.CHANNELS * ROW_H + PAD;
    private static final int EDIT_X = LIST_X + LIST_W + M, EDIT_W = W - M - EDIT_X;
    private static final int SLIDER_X = EDIT_X + 1 + PAD + 12, SLIDER_W = EDIT_W - 2 - 2 * PAD - 12, SLIDER_H = 8;
    private static final int CODE_Y = LIST_Y + LIST_H + M, CODE_H = 1 + PAD + 12 + 18 + PAD + 1;

    private final Screen parent;
    private final BlockPos pos;
    private CoreColors.Config config;
    private int selected;
    private float hue, sat, val;
    private int dragging = -1; // 0 = Farbton, 1 = Saettigung, 2 = Helligkeit
    private EditBox codeBox;
    private Component codeMessage = Component.empty();
    private int left, top;

    public ColorScreen(Screen parent, BlockPos pos, CoreColors.Config config) {
        super(Component.translatable("gui.cosmiccore.colors.title"));
        this.parent = parent;
        this.pos = pos;
        this.config = config;
        loadHsv();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int sy = sliderY(2) + SLIDER_H + 8;
        addRenderableWidget(Button.builder(Component.translatable("gui.cosmiccore.colors.reset"), b -> {
            setConfig(config.reset(selected));
            loadHsv();
        }).bounds(left + EDIT_X + 1 + PAD, top + sy, EDIT_W - 2 - 2 * PAD, 18).build());
        int cy = top + CODE_Y + 1 + PAD + 12;
        codeBox = addRenderableWidget(new EditBox(font, left + M + 1 + PAD, cy, W - 2 * M - 2 - 2 * PAD - 2 * 64 - 2 * M, 18,
                Component.translatable("gui.cosmiccore.colors.code")));
        codeBox.setMaxLength(40);
        codeBox.setValue(CoreColors.encode(config));
        int bx = left + W - M - 1 - PAD - 64;
        addRenderableWidget(Button.builder(Component.translatable("gui.cosmiccore.colors.load"), b -> loadCode())
                .bounds(bx, cy, 64, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cosmiccore.colors.copy"), b -> {
            if (minecraft != null) minecraft.keyboardHandler.setClipboard(CoreColors.encode(config));
            codeMessage = Component.translatable("gui.cosmiccore.colors.copied").withStyle(ChatFormatting.GREEN);
        }).bounds(bx - M - 64, cy, 64, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + W / 2 - 50, top + H - M - 20, 100, 20).build());
    }

    private void loadCode() {
        var decoded = CoreColors.decode(codeBox.getValue());
        if (decoded.isPresent()) {
            setConfig(decoded.get());
            loadHsv();
            codeMessage = Component.translatable("gui.cosmiccore.colors.loaded").withStyle(ChatFormatting.GREEN);
        } else {
            codeMessage = Component.translatable("gui.cosmiccore.colors.invalid").withStyle(ChatFormatting.RED);
        }
    }

    /** Neuen Farbsatz uebernehmen, Code aktualisieren und an den Server schicken (Kern faerbt sich live). */
    private void setConfig(CoreColors.Config c) {
        config = c;
        if (codeBox != null && !codeBox.isFocused()) codeBox.setValue(CoreColors.encode(config));
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(SetCoreColorsPayload.of(pos, config));
    }

    private void loadHsv() {
        int rgb = config.rgb(selected);
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        hue = hsb[0];
        sat = hsb[1];
        val = hsb[2];
    }

    private int currentRgb() {
        return Color.HSBtoRGB(hue, sat, val) & 0xFFFFFF;
    }

    private static int sliderY(int i) {
        return LIST_Y + 1 + PAD + 12 + 36 + i * 16;
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = left, y = top;
        // Vorgaben
        g.drawString(font, Component.translatable("gui.cosmiccore.colors.presets"), x + M + 1 + PAD, y + PRESET_Y + 1 + PAD, HEAD, false);
        Component hovered = null;
        for (int i = 0; i < CoreColors.PRESETS.size(); i++) {
            int px = x + M + 1 + PAD + i * 21, py = y + PRESET_Y + 1 + PAD + 12;
            presetTile(g, px, py, CoreColors.PRESETS.get(i).config());
            if (mouseX >= px && mouseX < px + 16 && mouseY >= py && mouseY < py + 16) {
                hovered = Component.translatable("gui.cosmiccore.color." + i);
            }
        }
        // Effekt-Liste
        g.drawString(font, Component.translatable("gui.cosmiccore.colors.effects"), x + LIST_X + 1 + PAD, y + LIST_Y + 1 + PAD, HEAD, false);
        for (int i = 0; i < CoreColors.CHANNELS; i++) {
            int rx = x + LIST_X + 1 + PAD, ry = y + LIST_Y + 1 + PAD + 12 + i * ROW_H;
            if (i == selected) g.fill(rx - 2, ry - 1, x + LIST_X + LIST_W - 1 - PAD + 2, ry + ROW_H - 2, 0x553A2468);
            g.fill(rx, ry + 1, rx + 14, ry + 15, 0xFF120A20);
            g.fill(rx + 1, ry + 2, rx + 13, ry + 14, 0xFF000000 | config.rgb(i));
            g.drawString(font, Component.translatable("gui.cosmiccore.channel." + CoreColors.KEYS[i]), rx + 19, ry + 4,
                    i == selected ? 0xFFFFFFFF : 0xFFC8C0E0, false);
            if (!config.custom(i)) {
                Component std = Component.translatable("gui.cosmiccore.colors.std");
                g.drawString(font, std, x + LIST_X + LIST_W - 1 - PAD - font.width(std), ry + 4, 0xFF6A6488, false);
            }
        }
        // Farbwaehler fuer den gewaehlten Effekt
        int ex = x + EDIT_X + 1 + PAD, ey = y + LIST_Y + 1 + PAD;
        g.drawString(font, Component.translatable("gui.cosmiccore.channel." + CoreColors.KEYS[selected]), ex, ey, HEAD, false);
        int rgb = config.rgb(selected);
        g.fill(ex, ey + 12, ex + 30, ey + 42, 0xFF120A20);
        g.fill(ex + 1, ey + 13, ex + 29, ey + 41, 0xFF000000 | rgb);
        g.drawString(font, String.format("#%06X", rgb), ex + 36, ey + 17, 0xFFE0E0F0, false);
        g.drawString(font, Component.translatable(config.custom(selected) ? "gui.cosmiccore.colors.own" : "gui.cosmiccore.colors.std_long"),
                ex + 36, ey + 29, 0xFF908AA8, false);
        String[] labels = {"H", "S", "V"};
        for (int i = 0; i < 3; i++) {
            int sy = y + sliderY(i);
            g.drawString(font, labels[i], ex, sy, 0xFF908AA8, false);
            drawSlider(g, x + SLIDER_X, sy, i);
        }
        // Vorschau: Scheibe von innen nach aussen, darueber der Jet
        int vy = y + sliderY(2) + SLIDER_H + 8 + 18 + 6;
        int vx0 = x + EDIT_X + 1 + PAD, vw = EDIT_W - 2 - 2 * PAD;
        g.drawString(font, Component.translatable("gui.cosmiccore.colors.preview"), vx0, vy, HEAD, false);
        int barY = vy + 11;
        for (int i = 0; i < vw; i++) {
            float f = i / (float) (vw - 1);
            int c = f < 0.5F ? mix(config.rgb(CoreColors.DISK_INNER), config.rgb(CoreColors.DISK_MID), f * 2)
                    : mix(config.rgb(CoreColors.DISK_MID), config.rgb(CoreColors.DISK_OUTER), (f - 0.5F) * 2);
            g.fill(vx0 + i, barY, vx0 + i + 1, barY + 8, 0xFF000000 | c);
        }
        g.fill(vx0, barY + 10, vx0 + vw, barY + 12, 0xFF000000 | config.rgb(CoreColors.JETS));
        g.fill(vx0, barY + 14, vx0 + vw, barY + 15, 0xFF000000 | config.rgb(CoreColors.SEAMS));
        // Code
        g.drawString(font, Component.translatable("gui.cosmiccore.colors.code"), x + M + 1 + PAD, y + CODE_Y + 1 + PAD, HEAD, false);
        g.drawString(font, codeMessage, x + W - M - 1 - PAD - font.width(codeMessage), y + CODE_Y + 1 + PAD, 0xFFFFFFFF, false);
        if (hovered != null) g.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int x = left, y = top;
        g.fill(x, y, x + W, y + H, 0xFF3A2468);
        g.fill(x + 1, y + 1, x + W - 1, y + H - 1, 0xFF0A0614);
        panel(g, x + M, y + PRESET_Y, W - 2 * M, PRESET_H);
        panel(g, x + LIST_X, y + LIST_Y, LIST_W, LIST_H);
        panel(g, x + EDIT_X, y + LIST_Y, EDIT_W, LIST_H);
        panel(g, x + M, y + CODE_Y, W - 2 * M, CODE_H);
    }

    private static void panel(GuiGraphics g, int px, int py, int pw, int ph) {
        g.fill(px, py, px + pw, py + ph, PANEL_BORDER);
        g.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, PANEL_FILL);
        g.fill(px + 3, py + 1, px + pw - 3, py + 2, PANEL_ACCENT);
    }

    /** Vorgabe als Kachel: vier Streifen (Scheibe innen/mitte/aussen, Jet). */
    private static void presetTile(GuiGraphics g, int px, int py, CoreColors.Config c) {
        g.fill(px - 1, py - 1, px + 17, py + 17, 0xFF120A20);
        int[] ch = {CoreColors.DISK_INNER, CoreColors.DISK_MID, CoreColors.DISK_OUTER, CoreColors.JETS};
        for (int i = 0; i < 4; i++) g.fill(px + i * 4, py, px + i * 4 + 4, py + 16, 0xFF000000 | c.rgb(ch[i]));
    }

    private void drawSlider(GuiGraphics g, int sx, int sy, int which) {
        g.fill(sx - 1, sy - 1, sx + SLIDER_W + 1, sy + SLIDER_H + 1, 0xFF120A20);
        for (int i = 0; i < SLIDER_W; i++) {
            float f = i / (float) (SLIDER_W - 1);
            int c = switch (which) {
                case 0 -> Color.HSBtoRGB(f, 1.0F, 1.0F);
                case 1 -> Color.HSBtoRGB(hue, f, Math.max(val, 0.25F));
                default -> Color.HSBtoRGB(hue, sat, f);
            };
            g.fill(sx + i, sy, sx + i + 1, sy + SLIDER_H, 0xFF000000 | (c & 0xFFFFFF));
        }
        float v = which == 0 ? hue : which == 1 ? sat : val;
        int mx = sx + Math.round(v * (SLIDER_W - 1));
        g.fill(mx - 1, sy - 2, mx + 2, sy + SLIDER_H + 2, 0xFF000000);
        g.fill(mx, sy - 1, mx + 1, sy + SLIDER_H + 1, 0xFFFFFFFF);
    }

    private static int mix(int a, int b, float f) {
        int r = (int) Mth.lerp(f, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int gg = (int) Mth.lerp(f, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bb = (int) Mth.lerp(f, a & 0xFF, b & 0xFF);
        return (r << 16) | (gg << 8) | bb;
    }

    // ------------------------------------------------------------------ Eingabe

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX - left, my = (int) mouseY - top;
        for (int i = 0; i < CoreColors.PRESETS.size(); i++) {
            int px = M + 1 + PAD + i * 21, py = PRESET_Y + 1 + PAD + 12;
            if (mx >= px && mx < px + 16 && my >= py && my < py + 16) {
                setConfig(CoreColors.PRESETS.get(i).config());
                loadHsv();
                return true;
            }
        }
        for (int i = 0; i < CoreColors.CHANNELS; i++) {
            int ry = LIST_Y + 1 + PAD + 12 + i * ROW_H;
            if (mx >= LIST_X + 1 && mx < LIST_X + LIST_W - 1 && my >= ry - 1 && my < ry + ROW_H - 2) {
                selected = i;
                loadHsv();
                return true;
            }
        }
        for (int i = 0; i < 3; i++) {
            int sy = sliderY(i);
            if (mx >= SLIDER_X - 2 && mx < SLIDER_X + SLIDER_W + 2 && my >= sy - 3 && my < sy + SLIDER_H + 3) {
                dragging = i;
                dragTo(mouseX, false);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging >= 0) {
            dragTo(mouseX, false);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging >= 0) {
            dragTo(mouseX, true); // erst beim Loslassen an den Server
            dragging = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void dragTo(double mouseX, boolean send) {
        float f = Mth.clamp((float) (mouseX - left - SLIDER_X) / (SLIDER_W - 1), 0.0F, 1.0F);
        if (dragging == 0) hue = f;
        else if (dragging == 1) sat = f;
        else val = f;
        CoreColors.Config next = config.with(selected, currentRgb());
        if (send) {
            setConfig(next);
        } else {
            config = next;
            if (codeBox != null && !codeBox.isFocused()) codeBox.setValue(CoreColors.encode(config));
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
