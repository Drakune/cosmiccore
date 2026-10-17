package dr4kun3.cosmiccore.client;

import dr4kun3.cosmiccore.CoreConfig;
import dr4kun3.cosmiccore.ModItems;
import dr4kun3.cosmiccore.data.CoreFuel;
import dr4kun3.cosmiccore.data.ModDataMaps;
import net.minecraft.world.item.ItemStack;
import dr4kun3.cosmiccore.block.entity.ControllerBlockEntity;
import dr4kun3.cosmiccore.menu.ControllerMenu;
import dr4kun3.cosmiccore.multiblock.ReactorSize;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dr4kun3.cosmiccore.block.ControllerBlock;
import dr4kun3.cosmiccore.block.ModBlocks;
import dr4kun3.cosmiccore.multiblock.CorePattern;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * GUI des Kerngenerators. Alles wird prozedural gezeichnet (keine Texturdatei).
 * Zwei Modi: Groessenwahl (einmalig nach dem Platzieren) und Betrieb.
 */
public class ControllerScreen extends AbstractContainerScreen<ControllerMenu> {
    // Einheitliches Raster: 5 px Aussenabstand (Rand und zwischen Karten), 5 px Innenabstand in jeder Karte
    /** H = Hoehe der Groessenwahl; H_MAIN = Hoehe der Steuerung (ohne Beute-Reihe, Module im Seiten-Tab). */
    /** Modul-Tab ausgeklappt? Standard eingeklappt - spart Platz. */
    private static boolean tabOpen;
    private static final int W = 316, H = 325, H_MAIN = ControllerMenu.INV_Y + 76 + 6;
    // Modul-Tab rechts am Fenster
    private static final int MTAB_X = W - 1, TAB_Y0 = ControllerMenu.TAB_TOP, TAB_CLOSED = 24, TAB_OPEN_W = 60,
            TAB_OPEN_H = 22 + 2 * ControllerMenu.MOD_STEP_Y + 8;
    private static final int M = 5, PAD = 5;
    private static final int PANEL_BORDER = 0xFF3A2468, PANEL_FILL = 0xCC0E0820, PANEL_ACCENT = 0xFF6A44B8;
    private static final int HEAD = 0xFFB8A8D8;

    // Betrieb: Kern-Karte (Slots), Anzeige-Karte (Balken), Status-Karte, Steuerung, Beute/Module
    private static final int TOP_Y = 18, TOP_H = 84;
    private static final int CORE_X = M, CORE_W = 118;
    private static final int BARS_X = CORE_X + CORE_W + M, BARS_W = 56;
    private static final int STATUS_X = BARS_X + BARS_W + M;
    private static final int BAR_Y = 25, BAR_H = 60, BAR_W = 10;
    private static final int BAR_ENERGY_X = BARS_X + 7, BAR_INST_X = BAR_ENERGY_X + 16, BAR_RES_X = BAR_INST_X + 16;
    private static final int INFO_X = STATUS_X + 1 + PAD;
    private static final int CTRL_Y = TOP_Y + TOP_H + M, CTRL_H = 61;
    private static final int ROW_Y = CTRL_Y + 18;
    private static final int OD_Y = ROW_Y + 21;
    private static final int COL_A = M + 1 + PAD + 1, COL_B = 106, COL_B_END = W - M - 1 - PAD;
    private static final int BTN_W = (COL_B_END - COL_B - 2 * M) / 3;
    private static final int SCALE_MINUS_X = COL_A, SCALE_PLUS_X = 85;
    private static final int SLOTS_Y = CTRL_Y + CTRL_H + M, SLOTS_H = 52;

    // Groessenwahl: Reiter links, darunter Info-Karte, 3D-Vorschau rechts, Optionen, Blockliste, Bestaetigung
    // linke Spalte: Karten "Groesse" (Reiter), "Kennwerte", "Optionen"; rechte Spalte: 3D-Vorschau und Blockliste
    private static final int LEFT_W = 96, HEAD_H = 8 + 4; // Ueberschrift + Abstand darunter
    private static final int SIZE_CARD_Y = M, TAB_X = M + 1 + PAD, TAB_W = LEFT_W - 2 - 2 * PAD;
    private static final int TAB_Y = SIZE_CARD_Y + 1 + PAD + HEAD_H, TAB_H = 20, TAB_STEP = TAB_H + M;
    private static final int SIZE_CARD_H = TAB_Y + 3 * TAB_STEP + TAB_H + PAD + 1 - SIZE_CARD_Y;
    private static final int SIZE_INFO_Y = SIZE_CARD_Y + SIZE_CARD_H + M, SIZE_INFO_H = 93;
    private static final int OPT_Y = SIZE_INFO_Y + SIZE_INFO_H + M, OPT_H = 1 + PAD + HEAD_H + 17 + M + 17 + PAD + 1;
    private static final int VIEW_X = M + LEFT_W + M, VIEW_Y = M, VIEW_W = W - M - VIEW_X;
    private static final int SCROLL_H = 3 + 3; // Abstand + Scrollbalken unter den Icons
    private static final int BLOCKS_H = 1 + PAD + HEAD_H + 18 + SCROLL_H + PAD + 1, BLOCKS_Y = OPT_Y + OPT_H - BLOCKS_H;
    private static final int VIEW_H = BLOCKS_Y - M - VIEW_Y;
    private static final int ICON_Y = BLOCKS_Y + 1 + PAD + HEAD_H + 1;

    private final List<Button> sizeButtons = new ArrayList<>();
    private Button scaleDown, scaleUp, toggle, preview, build, confirm;
    private int selected = 0; // kleinster Kern (5x5x5) vorausgewaehlt
    private boolean withFunnel = true;
    private boolean withLasers = true;
    private Checkbox funnelBox, laserBox;
    private Button overdriveUp, overdriveDown;
    private float dragYaw, dragPitch;

    /**
     * Legt das Controller-Fenster an: vor der Groessenwahl gross (Auswahl), danach kompakt (Steuerung).
     *
     * @param menu das Menue
     * @param inv Inventar des Spielers
     * @param title Fenstertitel
     */
    public ControllerScreen(ControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, menu.sizeChosen() ? H_MAIN : H);
        menu.modulesOpen = tabOpen; // Tab-Zustand bleibt fuer die Sitzung erhalten (wie bei Mekanism)
        inventoryLabelX = ControllerMenu.INV_X;
        inventoryLabelY = ControllerMenu.INV_Y - 11;
        titleLabelX = 8;
        titleLabelY = 6;
    }

    /**
     * Baut die Knoepfe auf: Groessen-Reiter, Trichter/Laser-Haken, Bestaetigen, Start/Stopp, Stufe, Overdrive,
     * Vorschau, Bauen, Entfernen und Farbmenue.
     */
    @Override
    protected void init() {
        super.init();
        sizeButtons.clear();
        ReactorSize[] sizes = ReactorSize.values();
        for (int i = 0; i < sizes.length; i++) {
            final int index = i;
            ReactorSize s = sizes[i];
            Button b = Button.builder(Component.translatable("gui.cosmiccore.size_button", s.size(), s.size(), s.size()),
                    btn -> selected = index).bounds(leftPos + TAB_X, topPos + TAB_Y + i * TAB_STEP, TAB_W, TAB_H).build();
            sizeButtons.add(addRenderableWidget(b));
        }
        int sizeCount = sizes.length;
        confirm = addRenderableWidget(Button.builder(Component.empty(),
                        b -> press(ControllerMenu.BTN_SIZE_FIRST + selected + (withFunnel ? sizeCount : 0) + (withLasers ? 2 * sizeCount : 0)))
                .bounds(leftPos + 40, topPos + H - M - 20, W - 80, 20).build());
        funnelBox = addRenderableWidget(Checkbox.builder(Component.translatable("gui.cosmiccore.with_funnel"), font)
                .pos(leftPos + M + 1 + PAD, topPos + OPT_Y + 1 + PAD + HEAD_H)
                .selected(withFunnel)
                .onValueChange((box, value) -> withFunnel = value)
                .build());
        laserBox = addRenderableWidget(Checkbox.builder(Component.translatable("gui.cosmiccore.with_lasers"), font)
                .pos(leftPos + M + 1 + PAD, topPos + OPT_Y + 1 + PAD + HEAD_H + 17 + M)
                .selected(withLasers)
                .onValueChange((box, value) -> withLasers = value)
                .build());
        overdriveUp = addRenderableWidget(Button.builder(Component.translatable("gui.cosmiccore.overdrive_up"), b -> confirmOverdrive(true))
                .bounds(leftPos + COL_B, topPos + OD_Y, 2 * BTN_W + M, 16).build());
        overdriveDown = addRenderableWidget(Button.builder(Component.translatable("gui.cosmiccore.overdrive_down"), b -> confirmOverdrive(false))
                .bounds(leftPos + COL_B + 2 * (BTN_W + M), topPos + OD_Y, BTN_W, 16).build());
        scaleDown = addRenderableWidget(Button.builder(Component.literal("-"), b -> press(ControllerMenu.BTN_SCALE_DOWN))
                .bounds(leftPos + SCALE_MINUS_X, topPos + ROW_Y, 16, 16).build());
        scaleUp = addRenderableWidget(Button.builder(Component.literal("+"), b -> press(ControllerMenu.BTN_SCALE_UP))
                .bounds(leftPos + SCALE_PLUS_X, topPos + ROW_Y, 16, 16).build());
        toggle = addRenderableWidget(Button.builder(Component.empty(), b -> press(ControllerMenu.BTN_TOGGLE))
                .bounds(leftPos + COL_B, topPos + ROW_Y, BTN_W, 16).build());
        preview = addRenderableWidget(Button.builder(Component.empty(), b -> press(ControllerMenu.BTN_PREVIEW))
                .bounds(leftPos + COL_B + BTN_W + M, topPos + ROW_Y, BTN_W, 16).build());
        build = addRenderableWidget(Button.builder(Component.translatable("gui.cosmiccore.build"), b -> press(ControllerMenu.BTN_BUILD))
                .bounds(leftPos + COL_B + 2 * (BTN_W + M), topPos + ROW_Y, BTN_W, 16).build());
        updateWidgets();
    }

    /**
     * Warnung vor dem Aktivieren bzw. Entfernen einer Overdrive-Stufe (beim Ultimate-Schritt mit eigenem Text).
     *
     * @param up {@code true}: Stufe erhoehen, sonst senken
     */
    private void confirmOverdrive(boolean up) {
        if (minecraft == null) return;
        ReactorSize current = ReactorSize.bySize(menu.size());
        boolean ultimateStep = current != null && (up ? menu.overdrive() >= current.maxOverdrive() : menu.overdrive() > current.maxOverdrive());
        String k = ultimateStep ? "gui.cosmiccore.ultimate_mode" : "gui.cosmiccore.overdrive";
        Component title = Component.translatable(k + (up ? ".confirm_up" : ".confirm_down")).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component message = Component.translatable(k + (up ? ".warning_up" : ".warning_down"), ControllerBlockEntity.ULTIMATE_COST);
        minecraft.setScreen(new ConfirmScreen(ok -> {
            minecraft.setScreen(this);
            if (ok) press(up ? ControllerMenu.BTN_OVERDRIVE_UP : ControllerMenu.BTN_OVERDRIVE_DOWN);
        }, title, message));
    }

    // Schalter unter dem Anti-Asteroiden-Slot
    private static final int ANTI_SW_X = ControllerMenu.MOD_X, ANTI_SW_Y = ControllerMenu.MOD_Y + ControllerMenu.MOD_STEP_Y + 19,
            ANTI_SW_W = 16, ANTI_SW_H = 7;

    // Farbfeld unter dem Farb-Modul-Slot: zeigt den Farbsatz als Streifen, Klick oeffnet das Farbmenue
    private static final int COLOR_SW_X = ControllerMenu.MOD_X + ControllerMenu.MOD_STEP_X;

    /**
     * Lasche bzw. ausgeklappter Modul-Tab.
     *
     * @return {x, y, Breite, Hoehe} relativ zum Fenster
     */
    private int[] tabRect() {
        return menu.modulesOpen ? new int[]{MTAB_X, TAB_Y0, TAB_OPEN_W, TAB_OPEN_H} : new int[]{MTAB_X, TAB_Y0, TAB_CLOSED, TAB_CLOSED};
    }

    /**
     * Lager-Tab direkt unter dem Modul-Tab: oeffnet das Lager des Item-Ports.
     *
     * @return {x, y, Breite, Hoehe} relativ zum Fenster
     */
    private int[] storageTabRect() {
        int[] r = tabRect();
        return new int[]{MTAB_X, r[1] + r[3] + 2, TAB_CLOSED, TAB_CLOSED};
    }

    /**
     * Liegt ein Punkt auf der Kopfzeile des Modul-Tabs (zum Auf- und Zuklappen)?
     *
     * @param mx x relativ zum Fenster
     * @param my y relativ zum Fenster
     * @return {@code true}, wenn ja
     */
    private boolean inTabHeader(int mx, int my) {
        return in(mx, my, MTAB_X, TAB_Y0, menu.modulesOpen ? TAB_OPEN_W : TAB_CLOSED, menu.modulesOpen ? 20 : TAB_CLOSED);
    }

    /**
     * Zeichnet den Modul-Tab: zu als Lasche mit Symbol, offen mit den Modulplaetzen, Anti-Asteroiden-Schalter und
     * Farbfeld.
     *
     * @param g Grafik-Kontext
     * @param x linke Kante des Fensters
     * @param y obere Kante des Fensters
     * @param t Animationszeit
     */
    private void drawModuleTab(GuiGraphicsExtractor g, int x, int y, float t) {
        if (!menu.sizeChosen()) return;
        int[] r = tabRect();
        int tx = x + r[0], ty = y + r[1];
        // Lasche haengt am Fensterrand: links offen, damit sie wie angesetzt wirkt
        g.fill(tx, ty, tx + r[2], ty + r[3], PANEL_BORDER);
        g.fill(tx, ty + 1, tx + r[2] - 1, ty + r[3] - 1, 0xFF120A24);
        g.fill(tx, ty + 1, tx + 1, ty + r[3] - 1, 0xFF1A0F33);
        g.fill(tx + 3, ty + 1, tx + r[2] - 3, ty + 2, PANEL_ACCENT);
        // Lager-Tab darunter (Truhe)
        int[] sr = storageTabRect();
        int sx = x + sr[0], sy = y + sr[1];
        g.fill(sx, sy, sx + sr[2], sy + sr[3], PANEL_BORDER);
        g.fill(sx, sy + 1, sx + sr[2] - 1, sy + sr[3] - 1, 0xFF120A24);
        g.fill(sx, sy + 1, sx + 1, sy + sr[3] - 1, 0xFF1A0F33);
        g.fill(sx + 3, sy + 1, sx + sr[2] - 3, sy + 2, 0xFFB06A20);
        g.item(new ItemStack(dr4kun3.cosmiccore.ModItems.ITEM_PORT.get()), sx + 4, sy + 4);
        // Symbol: Effizienz-Modul, darunter ggf. Anzahl installierter Module
        g.item(new ItemStack(dr4kun3.cosmiccore.ModItems.EFFICIENCY_MODULE.get()), tx + 4, ty + 4);
        int installed = 0;
        for (int i = 2; i < ControllerMenu.MACHINE_SLOTS; i++) if (menu.slots.get(i).hasItem()) installed++;
        if (menu.modulesOpen) {
            g.text(font, Component.translatable("gui.cosmiccore.modules"), tx + 22, ty + 8, 0xFFE0C080, false);
        } else if (installed > 0) {
            g.text(font, String.valueOf(installed), tx + r[2] - 7, ty + r[3] - 9, 0xFFFFE080, true);
        }
    }

    /**
     * Farbfeld zum Oeffnen des Farbmenues (grau ohne Farb-Modul, sonst die Kanalfarben im Wechsel).
     *
     * @param g Grafik-Kontext
     * @param sx linke Kante
     * @param sy obere Kante
     * @param t Animationszeit
     */
    private void drawColorSwatch(GuiGraphicsExtractor g, int sx, int sy, float t) {
        boolean installed = menu.flag(ControllerMenu.F_COLOR_INSTALLED);
        g.fill(sx - 1, sy - 1, sx + ANTI_SW_W + 1, sy + ANTI_SW_H + 1, 0xFF120A20);
        if (!installed) {
            g.fill(sx, sy, sx + ANTI_SW_W, sy + ANTI_SW_H, 0xFF3A3448);
            return;
        }
        dr4kun3.cosmiccore.color.CoreColors.Config cfg = menu.colorConfig();
        for (int i = 0; i < dr4kun3.cosmiccore.color.CoreColors.CHANNELS; i++) {
            g.fill(sx + i * 2, sy, sx + i * 2 + 2, sy + ANTI_SW_H, 0xFF000000 | cfg.rgb(i));
        }
        // feiner Glanz, der ueber das Feld wandert
        int gx = sx + Math.floorMod((int) (t * 0.5F), ANTI_SW_W + 6) - 3;
        if (gx >= sx && gx < sx + ANTI_SW_W) g.fill(gx, sy, Math.min(gx + 2, sx + ANTI_SW_W), sy + ANTI_SW_H, 0x60FFFFFF);
    }

    /**
     * Schalter der Asteroidenabwehr (grau ohne Modul, gruen an, rot aus).
     *
     * @param g Grafik-Kontext
     * @param sx linke Kante
     * @param sy obere Kante
     */
    private void drawAntiSwitch(GuiGraphicsExtractor g, int sx, int sy) {
        boolean installed = menu.flag(ControllerMenu.F_ANTI_INSTALLED), on = menu.flag(ControllerMenu.F_ANTI_ON);
        int track = !installed ? 0xFF3A3448 : on ? 0xFF2FB872 : 0xFF6A2E3A;
        g.fill(sx - 1, sy - 1, sx + ANTI_SW_W + 1, sy + ANTI_SW_H + 1, 0xFF120A20);
        g.fill(sx, sy, sx + ANTI_SW_W, sy + ANTI_SW_H, track);
        int kx = on ? sx + ANTI_SW_W - 7 : sx;
        g.fill(kx, sy, kx + 7, sy + ANTI_SW_H, installed ? 0xFFF0ECF8 : 0xFF8A8498);
    }

    /**
     * Klicks auf die Reiter neben dem Fenster gelten nicht als ausserhalb (sonst wuerden Items fallen gelassen).
     *
     * @param mouseX Mausposition x
     * @param mouseY Mausposition y
     * @param left linke Kante des Fensters
     * @param top obere Kante des Fensters
     * @return {@code true}, wenn wirklich ausserhalb
     */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        int[] r = tabRect(), sr = storageTabRect();
        if (menu.sizeChosen() && (in((int) mouseX - left, (int) mouseY - top, r[0], r[1], r[2], r[3])
                || in((int) mouseX - left, (int) mouseY - top, sr[0], sr[1], sr[2], sr[3]))) return false;
        return super.hasClickedOutside(mouseX, mouseY, left, top);
    }

    /**
     * Bereiche ausserhalb des Fensters (fuer JEI/EMI/REI, damit die Item-Liste die Tabs nicht verdeckt).
     *
     * @return die Bereiche in Bildschirmkoordinaten
     */
    public List<net.minecraft.client.renderer.Rect2i> extraAreas() {
        if (!menu.sizeChosen()) return List.of();
        int[] r = tabRect(), sr = storageTabRect();
        return List.of(new net.minecraft.client.renderer.Rect2i(leftPos + r[0], topPos + r[1], r[2], r[3]),
                new net.minecraft.client.renderer.Rect2i(leftPos + sr[0], topPos + sr[1], sr[2], sr[3]));
    }

    /**
     * Klick auf Reiter, Groessenkarten, Schalter oder Bildlaufleiste.
     *
     * @param event Mausereignis (Position und Taste)
     * @param doubleClick Doppelklick
     * @return {@code true}, wenn verarbeitet
     */
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        int[] sr = storageTabRect();
        if (menu.sizeChosen() && button == 0 && in((int) mouseX - leftPos, (int) mouseY - topPos, sr[0], sr[1], sr[2], sr[3])) {
            press(ControllerMenu.BTN_STORAGE);
            if (minecraft != null) {
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
        if (menu.sizeChosen() && button == 0 && inTabHeader((int) mouseX - leftPos, (int) mouseY - topPos)) {
            menu.modulesOpen = tabOpen = !menu.modulesOpen;
            if (minecraft != null) {
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
        if (menu.sizeChosen() && menu.modulesOpen && button == 0 && menu.flag(ControllerMenu.F_COLOR_INSTALLED)
                && in((int) mouseX - leftPos, (int) mouseY - topPos, COLOR_SW_X - 1, ANTI_SW_Y - 1, ANTI_SW_W + 2, ANTI_SW_H + 2)) {
            if (minecraft != null) {
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                minecraft.setScreen(new ColorScreen(this, menu.getPos(), menu.colorConfig()));
            }
            return true;
        }
        if (!menu.sizeChosen() && button == 0 && in((int) mouseX - leftPos, (int) mouseY - topPos, BLOCKS_IN_X0, SCROLL_Y - 2, BLOCKS_IN_X1 - BLOCKS_IN_X0, 7)
                && blocksMaxScroll(requiredBlocks(ReactorSize.values()[selected], withFunnel, withLasers).size()) > 0) {
            draggingScroll = true;
            scrollTo(mouseX);
            return true;
        }
        if (menu.sizeChosen() && menu.modulesOpen && button == 0 && menu.flag(ControllerMenu.F_ANTI_INSTALLED)
                && in((int) mouseX - leftPos, (int) mouseY - topPos, ANTI_SW_X - 1, ANTI_SW_Y - 1, ANTI_SW_W + 2, ANTI_SW_H + 2)) {
            press(ControllerMenu.BTN_ANTI_ASTEROID);
            if (minecraft != null) {
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Schickt einen Knopfdruck an den Server.
     *
     * @param id Nummer des Knopfs ({@code ControllerMenu.BTN_*})
     */
    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    /** Jeden Tick: Knoepfe anpassen und nach der Groessenwahl auf das kompakte Fenster wechseln. */
    @Override
    protected void containerTick() {
        super.containerTick();
        int wanted = menu.sizeChosen() ? H_MAIN : H;
        if (imageHeight != wanted && minecraft != null && minecraft.player != null) {
            // nach der Groessenwahl wird das Fenster kompakter - die Hoehe ist ab 26.1 fest, also neu oeffnen
            minecraft.setScreen(new ControllerScreen(menu, minecraft.player.getInventory(), title));
            return;
        }
        updateWidgets();
    }

    /**
     * Sichtbarkeit und Freigabe der Knoepfe nach Zustand (Groessenwahl, laeuft, freigeschaltet, Overdrive moeglich).
     */
    private void updateWidgets() {
        boolean chosen = menu.sizeChosen();
        // Leistungsstufe und Overdrive erst bei laufendem Generator
        boolean running = menu.flag(ControllerMenu.F_RUNNING);
        for (Button b : sizeButtons) b.visible = !chosen;
        confirm.visible = !chosen;
        funnelBox.visible = !chosen;
        laserBox.visible = !chosen;
        ReactorSize current = ReactorSize.bySize(menu.size());
        int maxOd = current == null ? 0 : current.maxOverdrive();
        int maxTotal = current == null ? 0 : current.maxOverdriveTotal();
        overdriveUp.visible = overdriveDown.visible = chosen;
        overdriveUp.active = running && menu.overdrive() < maxTotal;
        // nach Overdrive 3 wird der Knopf beim ultimativen Kern zum Ultimate-Modus
        boolean ultimateNext = maxTotal > maxOd && menu.overdrive() >= maxOd;
        overdriveUp.setMessage(Component.translatable(ultimateNext ? "gui.cosmiccore.ultimate_mode_btn" : "gui.cosmiccore.overdrive_up")
                .withStyle(ultimateNext ? ChatFormatting.GOLD : ChatFormatting.RESET));
        overdriveDown.active = running && menu.overdrive() > 0;
        ReactorSize sel = ReactorSize.values()[selected];
        confirm.setMessage(Component.translatable("gui.cosmiccore.confirm", sel.size(), sel.size(), sel.size()));
        scaleDown.visible = scaleUp.visible = toggle.visible = preview.visible = chosen;
        scaleDown.active = running && menu.scale() > 1;
        scaleUp.active = running && menu.scale() < menu.maxScale();
        boolean formed = menu.flag(ControllerMenu.F_FORMED);
        toggle.setMessage(Component.translatable(menu.flag(ControllerMenu.F_ENABLED) ? "gui.cosmiccore.stop" : "gui.cosmiccore.start"));
        preview.setMessage(Component.translatable(menu.flag(ControllerMenu.F_PREVIEW) ? "gui.cosmiccore.preview_on" : "gui.cosmiccore.preview_off"));
        preview.active = !formed;
        build.visible = chosen && minecraft != null && minecraft.player != null && minecraft.player.isCreative();
        build.active = !formed;
    }

    // ------------------------------------------------------------------ Zeichnen

    /**
     * Hintergrund: Rahmen mit Weltraum, Slots, Anzeigen und Knoepfe - bzw. die Karten der Groessenwahl.
     *
     * @param g Grafik-Kontext
     * @param mouseX Mausposition x
     * @param mouseY Mausposition y
     * @param partialTick Teil-Tick
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        float t = (minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0) + partialTick;

        // Rahmen + Weltraum-Hintergrund
        int hh = imageHeight;
        g.fill(x, y, x + W, y + hh, 0xFF3A2468);
        g.fill(x + 1, y + 1, x + W - 1, y + hh - 1, 0xFF0A0614);
        g.fillGradient(x + 2, y + 2, x + W - 2, y + hh - 2, 0xFF1A0F33, 0xFF070410);
        drawStars(g, x, y, t);

        if (!menu.sizeChosen()) {
            renderSizeSelection(g, x, y, t);
            return;
        }

        // Karten: Kern (Brennstoff/Stabilisator), Anzeigen, Status, Steuerung, Beute + Module
        drawPanel(g, x + CORE_X, y + TOP_Y, CORE_W, TOP_H);
        drawPanel(g, x + BARS_X, y + TOP_Y, BARS_W, TOP_H);
        drawPanel(g, x + STATUS_X, y + TOP_Y, W - M - STATUS_X, TOP_H);
        drawPanel(g, x + M, y + CTRL_Y, W - 2 * M, CTRL_H);
        // Slots
        drawSlotBox(g, x + ControllerMenu.FUEL_X, y + ControllerMenu.FUEL_Y, 0xFFB06A20);
        drawSlotBox(g, x + ControllerMenu.STAB_X, y + ControllerMenu.STAB_Y, 0xFF2A8AA8);
        drawHBar(g, x + ControllerMenu.FUEL_X - 1, y + ControllerMenu.FUEL_Y + 19, 18, menu.burnProgress(), 0xFFFFB040, 0xFFFF6A00);
        drawHBar(g, x + ControllerMenu.STAB_X - 1, y + ControllerMenu.STAB_Y + 19, 18, menu.stabProgress(), 0xFF60E0FF, 0xFF2080FF);
        // Modul-Tab (eingeklappt nur die Lasche, ausgeklappt mit Slots, Schalter und Farbfeld)
        drawModuleTab(g, x, y, t);
        // Modul-Slots (Gold), Rest = Spielerinventar
        for (int i = 2; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (!s.isActive()) continue;
            drawSlotBox(g, x + s.x, y + s.y, i < ControllerMenu.MACHINE_SLOTS ? 0xFFB08A30 : 0xFF2E2050);
        }
        if (menu.sizeChosen() && menu.modulesOpen) {
            drawAntiSwitch(g, x + ANTI_SW_X, y + ANTI_SW_Y);
            drawColorSwatch(g, x + COLOR_SW_X, y + ANTI_SW_Y, t);
        }

        // Balken
        float energy = menu.capacity() <= 0 ? 0 : menu.energy() / (float) menu.capacity();
        drawVBar(g, x + BAR_ENERGY_X, y + BAR_Y, energy, 0xFF60F0FF, 0xFF7A40FF);
        float inst = menu.instability() / 100f;
        int instTop = inst < 0.4f ? 0xFF60FF80 : inst < ControllerBlockEntity.UNSTABLE_ON / 100f ? 0xFFFFE040 : 0xFFFF3040;
        boolean blink = inst >= 0.85f && ((int) (t / 4) % 2 == 0);
        drawVBar(g, x + BAR_INST_X, y + BAR_Y, inst, blink ? 0xFFFFFFFF : instTop, 0xFF603070);
        drawVBar(g, x + BAR_RES_X, y + BAR_Y, menu.resonance() / 100f, 0xFFE070FF, 0xFF5020A0);
        g.centeredText(font, "E", x + BAR_ENERGY_X + BAR_W / 2, y + BAR_Y + BAR_H + 4, 0xFF9AE8FF);
        g.centeredText(font, "I", x + BAR_INST_X + BAR_W / 2, y + BAR_Y + BAR_H + 4, 0xFFFF9AA8);
        g.centeredText(font, "R", x + BAR_RES_X + BAR_W / 2, y + BAR_Y + BAR_H + 4, 0xFFE0A8FF);

        // Overdrive-Stufen als Kaestchen
        ReactorSize current = ReactorSize.bySize(menu.size());
        int maxOd = current == null ? 0 : current.maxOverdrive();
        for (int i = 0; i < maxOd; i++) {
            int bx = x + COL_A + 54 + i * 10, by = y + OD_Y + 4;
            boolean on = i < menu.overdrive();
            g.fill(bx, by, bx + 8, by + 8, on ? 0xFFFFB040 : 0xFF3A2468);
            g.fill(bx + 1, by + 1, bx + 7, by + 7, on ? (((int) (t / 3) + i) % 2 == 0 ? 0xFFFFE080 : 0xFFFF8020) : 0xFF0E0820);
        }
        if (current != null && current.maxOverdriveTotal() > maxOd) {
            // Ultimate-Feld: golden umrandet, aktiv in schimmernden Regenbogen-Goldtoenen
            int bx = x + COL_A + 54 + maxOd * 10, by = y + OD_Y + 4;
            boolean on = menu.overdrive() > maxOd;
            int pulse = (int) (150 + 100 * Mth.sin(t * 0.2F));
            g.fill(bx, by, bx + 8, by + 8, on ? 0xFFFFFFFF : (pulse << 24) | 0xFFC040);
            int[] cols = {0xFFFFE080, 0xFF60FFE0, 0xFFFF80E0, 0xFFFFC040};
            g.fill(bx + 1, by + 1, bx + 7, by + 7, on ? cols[(int) (t / 4) % cols.length] : 0xFF0E0820);
        }

        // Stufenanzeige zwischen - und +
        int sx0 = SCALE_MINUS_X + 16 + M, sx1 = SCALE_PLUS_X - M, sw = sx1 - sx0 - 2;
        g.fill(x + sx0, y + ROW_Y, x + sx1, y + ROW_Y + 16, PANEL_BORDER);
        g.fill(x + sx0 + 1, y + ROW_Y + 1, x + sx1 - 1, y + ROW_Y + 15, 0xFF0C0818);
        float growth = menu.maxScale() <= 1 ? 0 : (menu.scale() - 1) / (float) (menu.maxScale() - 1);
        int fillW = Math.round(sw * growth);
        // freigeschalteter Bereich (gedimmt), darueber die aktuelle Stufe
        float unl = menu.maxScale() <= 1 ? 0 : (Math.min(menu.unlocked(), menu.maxScale()) - 1) / (float) (menu.maxScale() - 1);
        int unlW = Math.round(sw * unl);
        if (unlW > 0) g.fill(x + sx0 + 1, y + ROW_Y + 12, x + sx0 + 1 + unlW, y + ROW_Y + 15, 0xFF4A2A70);
        if (fillW > 0) g.fillGradient(x + sx0 + 1, y + ROW_Y + 12, x + sx0 + 1 + fillW, y + ROW_Y + 15, 0xFFFF60C0, 0xFF8030FF);
        g.centeredText(font, menu.scale() + "/" + menu.maxScale(), x + (sx0 + sx1) / 2, y + ROW_Y + 3, 0xFFFFFFFF);
    }

    /**
     * Funkelnde Sterne im Hintergrund.
     *
     * @param g Grafik-Kontext
     * @param x linke Kante
     * @param y obere Kante
     * @param t Animationszeit
     */
    private void drawStars(GuiGraphicsExtractor g, int x, int y, float t) {
        for (int i = 0; i < 46; i++) {
            int h = i * 0x9E3779B1;
            int sx = 3 + Math.floorMod(h >>> 7, W - 6);
            int sy = 3 + Math.floorMod(h >>> 17, ControllerMenu.INV_Y - 20);
            float tw = 0.5f + 0.5f * Mth.sin(t * 0.08f + i * 1.7f);
            int a = (int) (40 + 150 * tw);
            int col = (i % 3 == 0) ? 0x9AE8FF : (i % 3 == 1) ? 0xE0B0FF : 0xFFFFFF;
            g.fill(x + sx, y + sy, x + sx + 1, y + sy + 1, (a << 24) | col);
        }
    }

    /**
     * Einheitliche Karte: dunkle Flaeche, violetter Rahmen, feine Akzentlinie oben.
     *
     * @param g Grafik-Kontext
     * @param px linke Kante
     * @param py obere Kante
     * @param pw Breite
     * @param ph Hoehe
     */
    private static void drawPanel(GuiGraphicsExtractor g, int px, int py, int pw, int ph) {
        g.fill(px, py, px + pw, py + ph, PANEL_BORDER);
        g.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, PANEL_FILL);
        g.fill(px + 3, py + 1, px + pw - 3, py + 2, PANEL_ACCENT);
    }

    /**
     * Rahmen eines Slots.
     *
     * @param g Grafik-Kontext
     * @param sx linke Kante
     * @param sy obere Kante
     * @param border Farbe des Rahmens
     */
    private static void drawSlotBox(GuiGraphicsExtractor g, int sx, int sy, int border) {
        g.fill(sx - 1, sy - 1, sx + 17, sy + 17, border);
        g.fill(sx, sy, sx + 16, sy + 16, 0xFF0C0818);
    }

    /**
     * Senkrechter Fuellbalken mit Farbverlauf.
     *
     * @param g Grafik-Kontext
     * @param bx linke Kante
     * @param by obere Kante
     * @param frac Fuellung 0..1
     * @param top Farbe oben
     * @param bottom Farbe unten
     */
    private static void drawVBar(GuiGraphicsExtractor g, int bx, int by, float frac, int top, int bottom) {
        g.fill(bx - 1, by - 1, bx + BAR_W + 1, by + BAR_H + 1, PANEL_BORDER);
        g.fill(bx, by, bx + BAR_W, by + BAR_H, 0xFF0C0818);
        int h = Math.round(BAR_H * Mth.clamp(frac, 0f, 1f));
        if (h > 0) g.fillGradient(bx, by + BAR_H - h, bx + BAR_W, by + BAR_H, top, bottom);
    }

    /**
     * Waagrechter Fortschrittsbalken mit Farbverlauf.
     *
     * @param g Grafik-Kontext
     * @param bx linke Kante
     * @param by obere Kante
     * @param w Breite
     * @param frac Fuellung 0..1
     * @param left Farbe links
     * @param right Farbe rechts
     */
    private static void drawHBar(GuiGraphicsExtractor g, int bx, int by, int w, float frac, int left, int right) {
        g.fill(bx, by, bx + w, by + 3, 0xFF0C0818);
        int fw = Math.round(w * Mth.clamp(frac, 0f, 1f));
        if (fw > 0) g.fillGradient(bx, by, bx + fw, by + 3, left, right);
    }

    /**
     * Beschriftungen: Titel, Werte und Zustandsmeldungen (in der Groessenwahl die Kartentexte).
     *
     * @param g Grafik-Kontext
     * @param mouseX Mausposition x
     * @param mouseY Mausposition y
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // in der Groessenwahl tragen die Karten eigene Ueberschriften - kein Fenstertitel
        if (menu.sizeChosen()) g.text(font, title, titleLabelX, titleLabelY, 0xFFE0B0FF, false);
        if (!menu.sizeChosen()) {
            ReactorSize sel = ReactorSize.values()[selected];
            // Info-Karte unter den Reitern: verlaessliche Kennwerte der Baugroesse
            g.text(font, Component.translatable("gui.cosmiccore.size_head"), TAB_X, SIZE_CARD_Y + 1 + PAD, HEAD, false);
            g.text(font, Component.translatable("gui.cosmiccore.options_head"), TAB_X, OPT_Y + 1 + PAD, HEAD, false);
            int ix = TAB_X, iy = SIZE_INFO_Y + 1 + PAD;
            g.text(font, Component.translatable("gui.cosmiccore.info_head"), ix, iy, HEAD, false);
            g.fill(ix, iy + 11, TAB_X + TAB_W, iy + 12, 0x663A2468);
            // je Kennwert eine eigene Zeile mit Luft dazwischen: Bezeichnung links, Wert rechtsbuendig
            spec(g, ix, iy + 17, "gui.cosmiccore.info_levels", String.valueOf(sel.maxScale()), 0xFFE0A8FF);
            g.text(font, Component.translatable("gui.cosmiccore.info_unlock"), ix, SIZE_INFO_Y + SPEC_KEY_Y, 0xFF908AA8, false);
            spec(g, ix, iy + 53, "gui.cosmiccore.info_buffer", bufferText(sel.buffer()), 0xFF9AE8FF);
            spec(g, ix, iy + 69, "gui.cosmiccore.info_overdrive",
                    sel.maxOverdrive() + (sel.maxOverdriveTotal() > sel.maxOverdrive() ? " + \u2605" : ""), 0xFFFFB040);
            if (sel.isUltimate()) {
                // goldener, sanft pulsierender Schriftzug mittig ueber der Struktur
                float tt = (System.currentTimeMillis() % 100000L) / 50.0F;
                float glowA = 0.75F + 0.25F * Mth.sin(tt * 0.15F);
                Component head = Component.translatable("gui.cosmiccore.ultimate_title").withStyle(ChatFormatting.BOLD);
                g.pose().pushMatrix();
                g.pose().translate(VIEW_X + VIEW_W / 2.0F, VIEW_Y + 7.0F);
                g.pose().scale(1.5F, 1.5F);
                int col = ((int) (glowA * 255) << 24) | 0xFFC040;
                g.text(font, head, -font.width(head) / 2, 0, col, true);
                g.pose().popMatrix();
                Component sub = Component.translatable("gui.cosmiccore.ultimate_cost");
                g.text(font, sub, VIEW_X + (VIEW_W - font.width(sub)) / 2, VIEW_Y + 22, 0xFFE0C080, true);
            }
            g.text(font, Component.translatable("gui.cosmiccore.blocks_label"), ICON_LEFT - 1, BLOCKS_Y + 1 + PAD, HEAD, false);
            return;
        }
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFB8A8D8, false);

        int ly = TOP_Y + 1 + PAD;
        int statusW = W - M - 1 - PAD - INFO_X;
        g.textWithWordWrap(font, statusLine(), INFO_X, ly, statusW, 0xFFFFFFFF);
        ly += 11 * Math.max(1, font.split(statusLine(), statusW).size()) + 4;

        // Kern-Karte: neben den Slots Vorrat und Restlaufzeit, darunter Leistung und Puffer
        int tx = ControllerMenu.FUEL_X + 20;
        ItemStack fuel = menu.slots.get(ControllerBlockEntity.SLOT_FUEL).getItem();
        ItemStack stab = menu.slots.get(ControllerBlockEntity.SLOT_STAB).getItem();
        g.text(font, Component.translatable("gui.cosmiccore.fuel_label"), tx, ControllerMenu.FUEL_Y, HEAD, false);
        g.text(font, fuel.getCount() + " \u00B7 " + PortStation.duration(menu.fuelSeconds()), tx, ControllerMenu.FUEL_Y + 10, 0xFFFFC070, false);
        g.text(font, Component.translatable("gui.cosmiccore.stab_label"), tx, ControllerMenu.STAB_Y, HEAD, false);
        g.text(font, stab.getCount() + " \u00B7 " + PortStation.duration(menu.stabSeconds()), tx, ControllerMenu.STAB_Y + 10, 0xFF9AD8FF, false);
        int cx0 = CORE_X + 1 + PAD, cx1 = CORE_X + CORE_W - 1 - PAD;
        int oy = ControllerMenu.STAB_Y + 25;
        g.fill(cx0, oy - 3, cx1, oy - 2, 0x663A2468);
        String out = PortStation.compact(menu.output()) + " RF/t";
        g.text(font, Component.translatable("gui.cosmiccore.out_label"), cx0, oy, HEAD, false);
        g.text(font, out, cx1 - font.width(out), oy, 0xFF9AE8FF, false);
        String buf = PortStation.compact(menu.energy()) + "/" + PortStation.compact(menu.capacity());
        g.text(font, Component.translatable("gui.cosmiccore.buf_label"), cx0, oy + 10, HEAD, false);
        g.text(font, buf, cx1 - font.width(buf), oy + 10, 0xFFC8B8F0, false);

        g.text(font, Component.translatable("gui.cosmiccore.instability", String.format("%.1f", menu.instability())), INFO_X, ly, instColor(), false);
        ly += 11;
        g.text(font, Component.translatable("gui.cosmiccore.cosmic", String.format("%.2f", menu.cosmic())), INFO_X, ly, 0xFFE0A8FF, false);
        ly += 11;
        boolean sky = menu.flag(ControllerMenu.F_SKY);
        String skyKey = !sky ? "gui.cosmiccore.sky_off" : menu.flag(ControllerMenu.F_FUNNEL) ? "gui.cosmiccore.funnel_on" : "gui.cosmiccore.sky_on";
        g.text(font, Component.translatable(skyKey), INFO_X, ly,
                sky ? 0xFF80FFD0 : 0xFF807090, false);
        ly += 11;
        if (menu.flag(ControllerMenu.F_LASER)) {
            g.text(font, Component.translatable("gui.cosmiccore.laser_on"), INFO_X, ly, 0xFF80E8FF, false);
        } else {
            g.text(font, Component.translatable("gui.cosmiccore.size", menu.size(), menu.size(), menu.size()), INFO_X, ly, 0xFF908AA8, false);
        }

        g.text(font, Component.translatable("gui.cosmiccore.scale"), COL_A, CTRL_Y + 1 + PAD, HEAD, false);
        g.text(font, Component.translatable("gui.cosmiccore.controls"), COL_B, CTRL_Y + 1 + PAD, HEAD, false);
        g.text(font, Component.translatable("gui.cosmiccore.overdrive"), COL_A, OD_Y + 4, menu.overdrive() > 0 ? 0xFFFFB040 : HEAD, false);
    }

    // ------------------------------------------------------------------ Groessenwahl

    /**
     * Groessenwahl: Reiter je Baugroesse, Kennwerte, 3D-Vorschau und die Liste benoetigter Bloecke.
     *
     * @param g Grafik-Kontext
     * @param x linke Kante des Fensters
     * @param y obere Kante des Fensters
     * @param t Animationszeit
     */
    private void renderSizeSelection(GuiGraphicsExtractor g, int x, int y, float t) {
        // Markierung des gewaehlten Reiters
        int ty = y + TAB_Y + selected * TAB_STEP;
        int glowCol = 0xFF000000 | (0xA060FF);
        g.fill(x + TAB_X - 2, ty - 2, x + TAB_X + TAB_W + 2, ty + TAB_H + 2, glowCol);
        // der ultimative Kern hat einen goldenen, pulsierenden Rahmen
        for (int i = 0; i < ReactorSize.values().length; i++) {
            if (!ReactorSize.values()[i].isUltimate()) continue;
            int uy = y + TAB_Y + i * TAB_STEP;
            int a = (int) (140 + 110 * Mth.sin(t * 0.15F));
            int gold = (a << 24) | 0xFFC040;
            g.fill(x + TAB_X - 1, uy - 1, x + TAB_X + TAB_W + 1, uy, gold);
            g.fill(x + TAB_X - 1, uy + TAB_H, x + TAB_X + TAB_W + 1, uy + TAB_H + 1, gold);
            g.fill(x + TAB_X - 1, uy, x + TAB_X, uy + TAB_H, gold);
            g.fill(x + TAB_X + TAB_W, uy, x + TAB_X + TAB_W + 1, uy + TAB_H, gold);
        }

        // Karten: Info unter den Reitern, Optionen, benoetigte Bloecke
        drawPanel(g, x + M, y + SIZE_CARD_Y, LEFT_W, SIZE_CARD_H);
        drawPanel(g, x + M, y + SIZE_INFO_Y, LEFT_W, SIZE_INFO_H);
        drawPanel(g, x + M, y + OPT_Y, LEFT_W, OPT_H);
        drawPanel(g, x + VIEW_X, y + BLOCKS_Y, VIEW_W, BLOCKS_H);
        // Freischalt-Item der gewaehlten Groesse in der Info-Karte
        ItemStack key = new ItemStack(ReactorSize.values()[selected].unlockItem());
        g.item(key, x + TAB_X + specTab(), y + SIZE_INFO_Y + SPEC_KEY_Y - 4);
        // Vorschaufenster
        g.fill(x + VIEW_X - 1, y + VIEW_Y - 1, x + VIEW_X + VIEW_W + 1, y + VIEW_Y + VIEW_H + 1, PANEL_BORDER);
        g.fillGradient(x + VIEW_X, y + VIEW_Y, x + VIEW_X + VIEW_W, y + VIEW_Y + VIEW_H, 0xFF120A26, 0xFF05030C);
        renderStructure(g, ReactorSize.values()[selected], x + VIEW_X + VIEW_W / 2, y + VIEW_Y + VIEW_H / 2 + 8, t);

        // benoetigte Bloecke
        List<ItemStack> icons = requiredBlocks(ReactorSize.values()[selected], withFunnel, withLasers);
        int maxScroll = blocksMaxScroll(icons.size());
        blocksScroll = Mth.clamp(blocksScroll, 0, maxScroll);
        // Icons innerhalb der Karte beschneiden; laengere Listen lassen sich horizontal scrollen
        g.enableScissor(x + BLOCKS_IN_X0, y + ICON_Y - 1, x + BLOCKS_IN_X1, y + ICON_Y + 17);
        for (int i = 0; i < icons.size(); i++) {
            int ix = x + iconX(i, icons.size()) - blocksScroll, iy = y + ICON_Y;
            drawSlotBox(g, ix, iy, 0xFF2E2050);
            g.item(icons.get(i), ix, iy);
            g.itemDecorations(font, icons.get(i), ix, iy);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int tx0 = x + BLOCKS_IN_X0, tx1 = x + BLOCKS_IN_X1, sy = y + SCROLL_Y;
            int trackW = tx1 - tx0, contentW = blocksContentW(icons.size());
            int thumbW = Math.max(12, trackW * trackW / contentW);
            int thumbX = tx0 + Math.round((trackW - thumbW) * (blocksScroll / (float) maxScroll));
            g.fill(tx0, sy, tx1, sy + 3, 0xFF1A1030);
            g.fill(thumbX, sy, thumbX + thumbW, sy + 3, 0xFF8A5AD8);
        }
    }

    // Blockliste: sichtbarer Innenbereich der Karte und Scrollzustand
    private static final int BLOCKS_IN_X0 = VIEW_X + 1 + PAD, BLOCKS_IN_X1 = VIEW_X + VIEW_W - 1 - PAD;
    private static final int SCROLL_Y = ICON_Y + 17 + 3;
    private int blocksScroll;
    private boolean draggingScroll;

    /**
     * Breite der Liste benoetigter Bloecke.
     *
     * @param count Anzahl Eintraege
     * @return Breite in Pixeln
     */
    private static int blocksContentW(int count) {
        return count * 24 - 6;
    }

    /**
     * Wie weit sich die Liste verschieben laesst.
     *
     * @param count Anzahl Eintraege
     * @return Pixel
     */
    private static int blocksMaxScroll(int count) {
        return Math.max(0, blocksContentW(count) - (BLOCKS_IN_X1 - BLOCKS_IN_X0));
    }

    /**
     * Verschiebt die Liste passend zur Mausposition auf dem Scrollbalken.
     *
     * @param mouseX Mausposition x
     */
    private void scrollTo(double mouseX) {
        int count = requiredBlocks(ReactorSize.values()[selected], withFunnel, withLasers).size();
        int maxScroll = blocksMaxScroll(count);
        float f = (float) ((mouseX - leftPos - BLOCKS_IN_X0) / (BLOCKS_IN_X1 - BLOCKS_IN_X0));
        blocksScroll = Math.round(Mth.clamp(f, 0.0F, 1.0F) * maxScroll);
    }

    /**
     * Mausrad ueber der Blockliste verschiebt sie.
     *
     * @param mouseX Mausposition x
     * @param mouseY Mausposition y
     * @param scrollX waagrechtes Scrollen
     * @param scrollY senkrechtes Scrollen
     * @return {@code true}, wenn verarbeitet
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!menu.sizeChosen() && in((int) mouseX - leftPos, (int) mouseY - topPos, VIEW_X, BLOCKS_Y, VIEW_W, BLOCKS_H)) {
            blocksScroll -= (int) Math.round((scrollY != 0 ? scrollY : scrollX) * 24);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Beendet das Ziehen der Bildlaufleiste.
     *
     * @param event Mausereignis (Position und Taste)
     * @return {@code true}, wenn verarbeitet
     */
    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        draggingScroll = false;
        return super.mouseReleased(event);
    }

    /**
     * Position eines Block-Symbols, buendig unter der Ueberschrift "Benoetigte Bloecke".
     *
     * @param i Nummer des Eintrags
     * @param count Anzahl Eintraege
     * @return x relativ zum Fenster
     */
    private static int iconX(int i, int count) {
        return ICON_LEFT + i * 24;
    }

    private static final int ICON_LEFT = VIEW_X + 1 + PAD + 1;

    /**
     * Benoetigte Bloecke einer Baugroesse.
     *
     * @param size Baugroesse
     * @param funnel Trichter mitzaehlen
     * @param lasers Laser mitzaehlen
     * @return Stapel mit Anzahl
     */
    private static List<ItemStack> requiredBlocks(ReactorSize size, boolean funnel, boolean lasers) {
        return StructurePreview.requiredBlocks(size, funnel, lasers);
    }

    /**
     * Dreht die echte Struktur als 3D-Modell im Vorschaufenster (mit der Maus ziehbar).
     *
     * @param g Grafik-Kontext
     * @param size Baugroesse
     * @param cx Mitte x
     * @param cy Mitte y
     * @param t Animationszeit
     */
    private void renderStructure(GuiGraphicsExtractor g, ReactorSize size, int cx, int cy, float t) {
        StructurePreview.render(g, size, cx, cy,
                new int[]{leftPos + VIEW_X, topPos + VIEW_Y, leftPos + VIEW_X + VIEW_W, topPos + VIEW_Y + VIEW_H},
                128.0F, t * 0.8F + 30.0F + dragYaw, dragPitch, withFunnel, withLasers);
    }

    /**
     * Ziehen der Bildlaufleiste.
     *
     * @param event Mausereignis (Position und Taste)
     * @param dragX Bewegung x
     * @param dragY Bewegung y
     * @return {@code true}, wenn verarbeitet
     */
    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        if (draggingScroll) {
            scrollTo(mouseX);
            return true;
        }
        if (!menu.sizeChosen() && in((int) mouseX - leftPos, (int) mouseY - topPos, VIEW_X, VIEW_Y, VIEW_W, VIEW_H)) {
            dragYaw += (float) dragX * 1.5F;
            dragPitch = Mth.clamp(dragPitch + (float) dragY, -40.0F, 50.0F);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    /**
     * Puffer kurz: ganze Gigawerte ohne Nachkommastellen.
     *
     * @param rf Menge in RF
     * @return z. B. "5G RF"
     */
    private static String bufferText(long rf) {
        if (rf >= 1_000_000_000L && rf % 1_000_000_000L == 0) return rf / 1_000_000_000L + "G RF";
        return compact(rf) + " RF";
    }

    /** Freischalt-Zeile der Kennwerte-Karte (Text, Item rechts daneben). */
    private static final int SPEC_KEY_Y = 1 + PAD + 17 + 16 + 4;

    private static final String[] SPEC_KEYS = {"gui.cosmiccore.info_levels", "gui.cosmiccore.info_buffer",
            "gui.cosmiccore.info_overdrive", "gui.cosmiccore.info_unlock"};

    /**
     * Tabulatorkante der Kennwerte: hinter der laengsten Bezeichnung, damit alle Werte genau untereinander stehen.
     *
     * @return Abstand in Pixeln
     */
    private int specTab() {
        int w = 0;
        for (String k : SPEC_KEYS) w = Math.max(w, font.width(Component.translatable(k)));
        return w + 8;
    }

    /**
     * Kennwert-Zeile: Bezeichnung gedimmt links, Wert an der Tabulatorkante.
     *
     * @param g Grafik-Kontext
     * @param ix linke Kante
     * @param iy obere Kante
     * @param key Uebersetzungsschluessel der Bezeichnung
     * @param value Wert
     * @param color Farbe des Werts
     */
    private void spec(GuiGraphicsExtractor g, int ix, int iy, String key, String value, int color) {
        g.text(font, Component.translatable(key), ix, iy, 0xFF908AA8, false);
        g.text(font, value, ix + specTab(), iy, color, false);
    }

    /**
     * Statuszeile: fehlender Pflicht-Port, Baufehler, Countdown, laeuft, kein Brennstoff ...
     *
     * @return farbiger Text
     */
    private Component statusLine() {
        if (!menu.flag(ControllerMenu.F_FORMED)) {
            // Pflicht-Ports: klar sagen, welcher fehlt
            if (menu.flag(ControllerMenu.F_NO_ENERGY_PORT)) return Component.translatable("gui.cosmiccore.status.no_energy_port").withStyle(ChatFormatting.RED);
            if (menu.flag(ControllerMenu.F_NO_ITEM_PORT)) return Component.translatable("gui.cosmiccore.status.no_item_port").withStyle(ChatFormatting.RED);
            return Component.translatable("gui.cosmiccore.status.incomplete", menu.errors()).withStyle(ChatFormatting.RED);
        }
        if (menu.meltdown() > 0) {
            boolean blink = minecraft != null && minecraft.level != null && minecraft.level.getGameTime() / 5 % 2 == 0;
            return Component.translatable("gui.cosmiccore.status.meltdown", menu.meltdown())
                    .withStyle(blink ? ChatFormatting.RED : ChatFormatting.WHITE, ChatFormatting.BOLD);
        }
        if (menu.collapseSeconds() > 0) {
            return Component.translatable("gui.cosmiccore.status.collapsed", menu.collapseSeconds()).withStyle(ChatFormatting.DARK_RED);
        }
        if (!menu.flag(ControllerMenu.F_ENABLED)) {
            return Component.translatable("gui.cosmiccore.status.off").withStyle(ChatFormatting.GRAY);
        }
        if (menu.flag(ControllerMenu.F_FULL)) {
            // Puffer voll: der Reaktor pausiert, bis Energie abfliesst - Brennstoff bleibt erhalten
            return Component.translatable("gui.cosmiccore.status.full").withStyle(ChatFormatting.GOLD);
        }
        if (!menu.flag(ControllerMenu.F_BURNING)) {
            return Component.translatable(menu.flag(ControllerMenu.F_HAS_FUEL) ? "gui.cosmiccore.status.starting"
                    : "gui.cosmiccore.status.idle").withStyle(ChatFormatting.YELLOW);
        }
        if (menu.instability() >= ControllerBlockEntity.UNSTABLE_ON) {
            return Component.translatable("gui.cosmiccore.status.unstable").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        }
        return Component.translatable("gui.cosmiccore.status.active").withStyle(ChatFormatting.GREEN);
    }

    /**
     * Steckt ein Modul im Platz?
     *
     * @param inventoryIndex Nummer im Controller-Inventar
     * @return {@code true}, wenn belegt
     */
    private boolean hasModule(int inventoryIndex) {
        Slot slot = menu.machineSlot(inventoryIndex);
        return slot != null && slot.hasItem();
    }

    /**
     * Farbe der Instabilitaet (gruen, gelb, rot).
     *
     * @return Farbe 0xAARRGGBB
     */
    private int instColor() {
        float i = menu.instability();
        return i < 40f ? 0xFF80FF90 : i < ControllerBlockEntity.UNSTABLE_ON ? 0xFFFFE060 : 0xFFFF5060;
    }

    // ------------------------------------------------------------------ Tooltips

    /**
     * Zeichnet das Fenster samt Bauteil-Liste der Groessenwahl und Tooltips.
     *
     * @param g Grafik-Kontext
     * @param mouseX Mausposition x
     * @param mouseY Mausposition y
     * @param partialTick Teil-Tick
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (!menu.sizeChosen()) {
            List<ItemStack> icons = requiredBlocks(ReactorSize.values()[selected], withFunnel, withLasers);
            for (int i = 0; i < icons.size(); i++) {
                int ix = leftPos + iconX(i, icons.size()) - blocksScroll, iy = topPos + ICON_Y;
                if (mouseX < leftPos + BLOCKS_IN_X0 || mouseX >= leftPos + BLOCKS_IN_X1) break; // ausserhalb des sichtbaren Bereichs
                if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                    g.setTooltipForNextFrame(font, icons.get(i).getHoverName(), mouseX, mouseY);
                }
            }
            int kx = leftPos + TAB_X + specTab(), ky = topPos + SIZE_INFO_Y + SPEC_KEY_Y - 4;
            if (mouseX >= kx && mouseX < kx + 16 && mouseY >= ky && mouseY < ky + 16) {
                ItemStack key = new ItemStack(ReactorSize.values()[selected].unlockItem());
                g.setComponentTooltipForNextFrame(font, List.of(key.getHoverName(),
                        Component.translatable("gui.cosmiccore.info_unlock_tip").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            }
            return;
        }

        int mx = mouseX - leftPos, my = mouseY - topPos;
        List<Component> tip = new ArrayList<>();
        int mx0 = ControllerMenu.MOD_X, mx1 = mx0 + ControllerMenu.MOD_STEP_X, my0 = ControllerMenu.MOD_Y, my1 = my0 + ControllerMenu.MOD_STEP_Y;
        boolean open = menu.modulesOpen;
        int[] str = storageTabRect();
        if (in(mx, my, str[0], str[1], str[2], str[3])) {
            tip.add(Component.translatable("gui.cosmiccore.storage"));
            tip.add(Component.translatable("gui.cosmiccore.storage.hint").withStyle(ChatFormatting.GRAY));
        } else if (inTabHeader(mx, my)) {
            tip.add(Component.translatable("gui.cosmiccore.modules"));
            tip.add(Component.translatable(open ? "gui.cosmiccore.tab.close" : "gui.cosmiccore.tab.open").withStyle(ChatFormatting.GRAY));
        } else if (open && in(mx, my, mx0, my0, 16, 16) && !hasModule(ControllerBlockEntity.SLOT_EFFICIENCY)) {
            tip.add(Component.translatable("item.cosmiccore.efficiency_module"));
            tip.add(Component.translatable("tooltip.cosmiccore.efficiency_module").withStyle(ChatFormatting.GRAY));
        } else if (open && in(mx, my, mx1, my0, 16, 16) && !hasModule(ControllerBlockEntity.SLOT_STABILITY)) {
            tip.add(Component.translatable("item.cosmiccore.stability_module"));
            tip.add(Component.translatable("tooltip.cosmiccore.stability_module").withStyle(ChatFormatting.GRAY));
        } else if (open && in(mx, my, mx0, my1, 16, 16) && !hasModule(ControllerBlockEntity.SLOT_ANTI)) {
            tip.add(Component.translatable("item.cosmiccore.anti_asteroid_module"));
            tip.add(Component.translatable("tooltip.cosmiccore.anti_asteroid_module", dr4kun3.cosmiccore.CoreConfig.ANTI_ASTEROID_RADIUS.get())
                    .withStyle(ChatFormatting.GRAY));
        } else if (open && in(mx, my, mx1, my1, 16, 16) && !hasModule(ControllerBlockEntity.SLOT_COLOR)) {
            tip.add(Component.translatable("item.cosmiccore.color_module"));
            tip.add(Component.translatable("tooltip.cosmiccore.color_module").withStyle(ChatFormatting.GRAY));
        } else if (open && in(mx, my, COLOR_SW_X - 1, ANTI_SW_Y - 1, ANTI_SW_W + 2, ANTI_SW_H + 2)) {
            boolean installed = menu.flag(ControllerMenu.F_COLOR_INSTALLED);
            tip.add(Component.translatable("gui.cosmiccore.color.title"));
            if (installed) tip.add(Component.literal(dr4kun3.cosmiccore.color.CoreColors.encode(menu.colorConfig())).withStyle(ChatFormatting.DARK_GRAY));
            tip.add(Component.translatable(installed ? "gui.cosmiccore.color.hint" : "gui.cosmiccore.color.missing").withStyle(ChatFormatting.GRAY));
        } else if (open && in(mx, my, ANTI_SW_X - 1, ANTI_SW_Y - 1, ANTI_SW_W + 2, ANTI_SW_H + 2)) {
            boolean installed = menu.flag(ControllerMenu.F_ANTI_INSTALLED), on = menu.flag(ControllerMenu.F_ANTI_ON);
            tip.add(Component.translatable("gui.cosmiccore.anti.title"));
            tip.add(Component.translatable(on ? "gui.cosmiccore.anti.on" : "gui.cosmiccore.anti.off")
                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
            if (!installed) tip.add(Component.translatable("gui.cosmiccore.anti.missing").withStyle(ChatFormatting.GRAY));
        } else if (in(mx, my, SCALE_PLUS_X, ROW_Y, 16, 16) && menu.sizeChosen() && menu.scale() < menu.maxScale()) {
            ReactorSize rs = ReactorSize.bySize(menu.size());
            int next = menu.scale() + 1;
            if (rs != null && next > menu.unlocked()) {
                tip.add(Component.translatable("gui.cosmiccore.unlock.title", next).withStyle(ChatFormatting.LIGHT_PURPLE));
                tip.add(Component.translatable("gui.cosmiccore.unlock.cost", new ItemStack(rs.unlockItem()).getHoverName())
                        .withStyle(ChatFormatting.GRAY));
            } else {
                tip.add(Component.translatable("gui.cosmiccore.unlock.free", next).withStyle(ChatFormatting.GRAY));
            }
            tip.add(Component.translatable("gui.cosmiccore.unlock.status", menu.unlocked(), menu.maxScale()).withStyle(ChatFormatting.DARK_GRAY));
        } else if (in(mx, my, BAR_ENERGY_X, BAR_Y, BAR_W, BAR_H)) {
            tip.add(Component.translatable("gui.cosmiccore.tip.energy"));
            tip.add(Component.literal(fmt(menu.energy()) + " / " + fmt(menu.capacity()) + " RF").withStyle(ChatFormatting.GRAY));
        } else if (in(mx, my, BAR_INST_X, BAR_Y, BAR_W, BAR_H)) {
            tip.add(Component.translatable("gui.cosmiccore.tip.instability"));
            tip.add(Component.literal(String.format("%.1f %%", menu.instability())).withStyle(ChatFormatting.GRAY));
        } else if (in(mx, my, BAR_RES_X, BAR_Y, BAR_W, BAR_H)) {
            tip.add(Component.translatable("gui.cosmiccore.tip.resonance"));
            tip.add(Component.literal(menu.resonance() + " %").withStyle(ChatFormatting.GRAY));
        }
        if (!tip.isEmpty()) g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
    }

    /**
     * Liegt ein Punkt in einem Rechteck?
     *
     * @param mx Punkt x
     * @param my Punkt y
     * @param x linke Kante
     * @param y obere Kante
     * @param w Breite
     * @param h Hoehe
     * @return {@code true}, wenn ja
     */
    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /**
     * Zahl mit Tausenderpunkten.
     *
     * @param n die Zahl
     * @return der Text
     */
    private static String fmt(long n) {
        return NumberFormat.getIntegerInstance().format(n);
    }

    /**
     * Grosse Zahl kurz: 1.25G, 3.4M, 12.5k.
     *
     * @param n die Zahl
     * @return der Text
     */
    private static String compact(long n) {
        if (n >= 1_000_000_000L) return String.format("%.2fG", n / 1e9);
        if (n >= 1_000_000L) return String.format("%.1fM", n / 1e6);
        if (n >= 10_000L) return String.format("%.1fk", n / 1e3);
        return Long.toString(n);
    }
}
