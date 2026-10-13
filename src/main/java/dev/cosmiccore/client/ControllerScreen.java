package dev.cosmiccore.client;

import dev.cosmiccore.CoreConfig;
import dev.cosmiccore.ModItems;
import dev.cosmiccore.data.CoreFuel;
import dev.cosmiccore.data.ModDataMaps;
import net.minecraft.world.item.ItemStack;
import dev.cosmiccore.block.entity.ControllerBlockEntity;
import dev.cosmiccore.menu.ControllerMenu;
import dev.cosmiccore.multiblock.ReactorSize;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.cosmiccore.block.ControllerBlock;
import dev.cosmiccore.block.ModBlocks;
import dev.cosmiccore.multiblock.CorePattern;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
    private static final int W = 234, H = 226;
    private static final int BAR_Y = 18, BAR_H = 60, BAR_W = 10;
    private static final int BAR_ENERGY_X = 42, BAR_INST_X = 58, BAR_RES_X = 74;
    private static final int INFO_X = 92;
    private static final int ROW_Y = 104;

    // Groessenwahl: Reiter links, 3D-Vorschau rechts, Blockliste und Bestaetigung unten
    private static final int TAB_X = 8, TAB_Y = 22, TAB_W = 58, TAB_H = 20, TAB_STEP = 26;
    private static final int VIEW_X = 72, VIEW_Y = 18, VIEW_W = 154, VIEW_H = 150;
    private static final int ICON_Y = 176;

    private final List<Button> sizeButtons = new ArrayList<>();
    private Button scaleDown, scaleUp, toggle, preview, build, confirm;
    private int selected = 1; // 7x7x7 vorausgewaehlt
    private float dragYaw, dragPitch;

    public ControllerScreen(ControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = W;
        imageHeight = H;
        inventoryLabelX = ControllerMenu.INV_X;
        inventoryLabelY = ControllerMenu.INV_Y - 11;
        titleLabelX = 8;
        titleLabelY = 6;
    }

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
        confirm = addRenderableWidget(Button.builder(Component.empty(), b -> press(ControllerMenu.BTN_SIZE_FIRST + selected))
                .bounds(leftPos + 42, topPos + H - 26, W - 84, 18).build());
        scaleDown = addRenderableWidget(Button.builder(Component.literal("-"), b -> press(ControllerMenu.BTN_SCALE_DOWN))
                .bounds(leftPos + 8, topPos + ROW_Y, 16, 16).build());
        scaleUp = addRenderableWidget(Button.builder(Component.literal("+"), b -> press(ControllerMenu.BTN_SCALE_UP))
                .bounds(leftPos + 68, topPos + ROW_Y, 16, 16).build());
        toggle = addRenderableWidget(Button.builder(Component.empty(), b -> press(ControllerMenu.BTN_TOGGLE))
                .bounds(leftPos + INFO_X, topPos + ROW_Y, 44, 16).build());
        preview = addRenderableWidget(Button.builder(Component.empty(), b -> press(ControllerMenu.BTN_PREVIEW))
                .bounds(leftPos + INFO_X + 46, topPos + ROW_Y, 46, 16).build());
        build = addRenderableWidget(Button.builder(Component.translatable("gui.cosmiccore.build"), b -> press(ControllerMenu.BTN_BUILD))
                .bounds(leftPos + INFO_X + 94, topPos + ROW_Y, 42, 16).build());
        updateWidgets();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateWidgets();
    }

    private void updateWidgets() {
        boolean chosen = menu.sizeChosen();
        for (Button b : sizeButtons) b.visible = !chosen;
        confirm.visible = !chosen;
        ReactorSize sel = ReactorSize.values()[selected];
        confirm.setMessage(Component.translatable("gui.cosmiccore.confirm", sel.size(), sel.size(), sel.size()));
        scaleDown.visible = scaleUp.visible = toggle.visible = preview.visible = chosen;
        scaleDown.active = menu.scale() > 1;
        scaleUp.active = menu.scale() < menu.maxScale();
        boolean formed = menu.flag(ControllerMenu.F_FORMED);
        toggle.setMessage(Component.translatable(menu.flag(ControllerMenu.F_ENABLED) ? "gui.cosmiccore.stop" : "gui.cosmiccore.start"));
        preview.setMessage(Component.translatable(menu.flag(ControllerMenu.F_PREVIEW) ? "gui.cosmiccore.preview_on" : "gui.cosmiccore.preview_off"));
        preview.active = !formed;
        build.visible = chosen && minecraft != null && minecraft.player != null && minecraft.player.isCreative();
        build.active = !formed;
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        float t = (minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0) + partialTick;

        // Rahmen + Weltraum-Hintergrund
        g.fill(x, y, x + W, y + H, 0xFF3A2468);
        g.fill(x + 1, y + 1, x + W - 1, y + H - 1, 0xFF0A0614);
        g.fillGradient(x + 2, y + 2, x + W - 2, y + H - 2, 0xFF1A0F33, 0xFF070410);
        drawStars(g, x, y, t);

        if (!menu.sizeChosen()) {
            renderSizeSelection(g, x, y, t);
            return;
        }

        // Slots
        drawSlotBox(g, x + ControllerMenu.FUEL_X, y + ControllerMenu.FUEL_Y, 0xFFB06A20);
        drawSlotBox(g, x + ControllerMenu.STAB_X, y + ControllerMenu.STAB_Y, 0xFF2A8AA8);
        drawHBar(g, x + ControllerMenu.FUEL_X - 1, y + ControllerMenu.FUEL_Y + 19, 18, menu.burnProgress(), 0xFFFFB040, 0xFFFF6A00);
        drawHBar(g, x + ControllerMenu.STAB_X - 1, y + ControllerMenu.STAB_Y + 19, 18, menu.stabProgress(), 0xFF60E0FF, 0xFF2080FF);
        for (Slot s : menu.slots) {
            if (s.index >= 2 && s.isActive()) drawSlotBox(g, x + s.x, y + s.y, 0xFF2E2050);
        }

        // Balken
        float energy = menu.capacity() <= 0 ? 0 : menu.energy() / (float) menu.capacity();
        drawVBar(g, x + BAR_ENERGY_X, y + BAR_Y, energy, 0xFF60F0FF, 0xFF7A40FF);
        float inst = menu.instability() / 100f;
        int instTop = inst < 0.4f ? 0xFF60FF80 : inst < ControllerBlockEntity.UNSTABLE_ON / 100f ? 0xFFFFE040 : 0xFFFF3040;
        boolean blink = inst >= 0.85f && ((int) (t / 4) % 2 == 0);
        drawVBar(g, x + BAR_INST_X, y + BAR_Y, inst, blink ? 0xFFFFFFFF : instTop, 0xFF603070);
        drawVBar(g, x + BAR_RES_X, y + BAR_Y, menu.resonance() / 100f, 0xFFE070FF, 0xFF5020A0);
        g.drawCenteredString(font, "E", x + BAR_ENERGY_X + BAR_W / 2, y + BAR_Y + BAR_H + 3, 0xFF9AE8FF);
        g.drawCenteredString(font, "I", x + BAR_INST_X + BAR_W / 2, y + BAR_Y + BAR_H + 3, 0xFFFF9AA8);
        g.drawCenteredString(font, "R", x + BAR_RES_X + BAR_W / 2, y + BAR_Y + BAR_H + 3, 0xFFE0A8FF);

        // Stufenanzeige zwischen - und +
        g.fill(x + 25, y + ROW_Y, x + 67, y + ROW_Y + 16, 0xFF2A1A4A);
        g.fill(x + 26, y + ROW_Y + 1, x + 66, y + ROW_Y + 15, 0xFF0E0820);
        float growth = menu.maxScale() <= 1 ? 0 : (menu.scale() - 1) / (float) (menu.maxScale() - 1);
        int fillW = Math.round(40 * growth);
        if (fillW > 0) g.fillGradient(x + 26, y + ROW_Y + 12, x + 26 + fillW, y + ROW_Y + 15, 0xFFFF60C0, 0xFF8030FF);
        g.drawCenteredString(font, menu.scale() + "/" + menu.maxScale(), x + 46, y + ROW_Y + 3, 0xFFFFFFFF);
    }

    private void drawStars(GuiGraphics g, int x, int y, float t) {
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

    private static void drawSlotBox(GuiGraphics g, int sx, int sy, int border) {
        g.fill(sx - 1, sy - 1, sx + 17, sy + 17, border);
        g.fill(sx, sy, sx + 16, sy + 16, 0xFF0C0818);
    }

    private static void drawVBar(GuiGraphics g, int bx, int by, float frac, int top, int bottom) {
        g.fill(bx - 1, by - 1, bx + BAR_W + 1, by + BAR_H + 1, 0xFF3A2468);
        g.fill(bx, by, bx + BAR_W, by + BAR_H, 0xFF0C0818);
        int h = Math.round(BAR_H * Mth.clamp(frac, 0f, 1f));
        if (h > 0) g.fillGradient(bx, by + BAR_H - h, bx + BAR_W, by + BAR_H, top, bottom);
    }

    private static void drawHBar(GuiGraphics g, int bx, int by, int w, float frac, int left, int right) {
        g.fill(bx, by, bx + w, by + 3, 0xFF0C0818);
        int fw = Math.round(w * Mth.clamp(frac, 0f, 1f));
        if (fw > 0) g.fillGradient(bx, by, bx + fw, by + 3, left, right);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, menu.sizeChosen() ? title : Component.translatable("gui.cosmiccore.choose_size"),
                titleLabelX, titleLabelY, 0xFFE0B0FF, false);
        if (!menu.sizeChosen()) {
            ReactorSize sel = ReactorSize.values()[selected];
            int base = referencePower();
            long min = Math.round(base * CoreConfig.OUTPUT_MULTIPLIER.get());
            long max = Math.round(base * Math.pow(sel.maxScale(), CoreConfig.SCALE_OUTPUT_EXPONENT.get()) * CoreConfig.OUTPUT_MULTIPLIER.get());
            // Infoblock unter den Reitern - endet oberhalb von "Benoetigte Bloecke" (ICON_Y - 9)
            int ix = TAB_X, iy = TAB_Y + 3 * TAB_STEP;
            g.drawString(font, Component.translatable("gui.cosmiccore.output_label"), ix, iy, 0xFFB8A8D8, false);
            g.drawString(font, fmt(min) + " -", ix, iy + 10, 0xFF9AE8FF, false);
            g.drawString(font, fmt(max) + " RF/t", ix, iy + 19, 0xFF9AE8FF, false);
            g.drawString(font, Component.translatable("gui.cosmiccore.max_level"), ix, iy + 32, 0xFFB8A8D8, false);
            g.drawString(font, String.valueOf(sel.maxScale()), ix, iy + 42, 0xFFE0A8FF, false);
            g.drawString(font, Component.translatable("gui.cosmiccore.blocks_label"), 8, ICON_Y - 9 + 0, 0xFFB8A8D8, false);
            return;
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFB8A8D8, false);

        int ly = BAR_Y;
        g.drawString(font, statusLine(), INFO_X, ly, 0xFFFFFFFF, false);
        ly += 11;
        g.drawString(font, Component.translatable("gui.cosmiccore.output", fmt(menu.output())), INFO_X, ly, 0xFF9AE8FF, false);
        ly += 11;
        g.drawString(font, Component.translatable("gui.cosmiccore.energy", compact(menu.energy()), compact(menu.capacity())), INFO_X, ly, 0xFFC8B8F0, false);
        ly += 11;
        g.drawString(font, Component.translatable("gui.cosmiccore.instability", String.format("%.1f", menu.instability())), INFO_X, ly, instColor(), false);
        ly += 11;
        g.drawString(font, Component.translatable("gui.cosmiccore.cosmic", String.format("%.2f", menu.cosmic())), INFO_X, ly, 0xFFE0A8FF, false);
        ly += 11;
        boolean sky = menu.flag(ControllerMenu.F_SKY);
        String skyKey = !sky ? "gui.cosmiccore.sky_off" : menu.flag(ControllerMenu.F_FUNNEL) ? "gui.cosmiccore.funnel_on" : "gui.cosmiccore.sky_on";
        g.drawString(font, Component.translatable(skyKey), INFO_X, ly,
                sky ? 0xFF80FFD0 : 0xFF807090, false);
        ly += 11;
        g.drawString(font, Component.translatable("gui.cosmiccore.size", menu.size(), menu.size(), menu.size()), INFO_X, ly, 0xFF908AA8, false);

        g.drawString(font, Component.translatable("gui.cosmiccore.scale"), 8, ROW_Y - 12, 0xFFB8A8D8, false);
    }

    // ------------------------------------------------------------------ Groessenwahl

    private void renderSizeSelection(GuiGraphics g, int x, int y, float t) {
        // Markierung des gewaehlten Reiters
        int ty = y + TAB_Y + selected * TAB_STEP;
        int glowCol = 0xFF000000 | (0xA060FF);
        g.fill(x + TAB_X - 2, ty - 2, x + TAB_X + TAB_W + 2, ty + TAB_H + 2, glowCol);

        // Vorschaufenster
        g.fill(x + VIEW_X - 1, y + VIEW_Y - 1, x + VIEW_X + VIEW_W + 1, y + VIEW_Y + VIEW_H + 1, 0xFF3A2468);
        g.fillGradient(x + VIEW_X, y + VIEW_Y, x + VIEW_X + VIEW_W, y + VIEW_Y + VIEW_H, 0xFF120A26, 0xFF05030C);
        renderStructure(g, ReactorSize.values()[selected], x + VIEW_X + VIEW_W / 2, y + VIEW_Y + VIEW_H / 2 + 6, t);

        // benoetigte Bloecke
        List<ItemStack> icons = requiredBlocks(ReactorSize.values()[selected]);
        for (int i = 0; i < icons.size(); i++) {
            int ix = x + iconX(i, icons.size()), iy = y + ICON_Y;
            drawSlotBox(g, ix, iy, 0xFF2E2050);
            g.renderItem(icons.get(i), ix, iy);
            g.renderItemDecorations(font, icons.get(i), ix, iy);
        }
    }

    private static int iconX(int i, int count) {
        int step = 26;
        return W / 2 - (count * step - 10) / 2 + i * step;
    }

    /** Die fuer eine Baugroesse benoetigten Bloecke als Stapel (Anzahl = Stapelgroesse, darf ueber 64 liegen). */
    private static List<ItemStack> requiredBlocks(ReactorSize size) {
        CorePattern p = size.pattern();
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(ModItems.CONTROLLER.get(), 1));
        out.add(new ItemStack(ModItems.VOID_CASING.get(), p.count(CorePattern.Cell.HULL) + p.count(CorePattern.Cell.SLOT) - 1));
        out.add(new ItemStack(ModItems.RUNE_FRAME.get(), p.count(CorePattern.Cell.FRAME)));
        out.add(new ItemStack(ModItems.FOCUS_GLASS.get(), p.count(CorePattern.Cell.GLASS)));
        out.add(new ItemStack(ModItems.RESONANCE_COIL.get(), p.count(CorePattern.Cell.COIL)));
        out.add(new ItemStack(ModItems.FOCUS_CRYSTAL.get(), p.count(CorePattern.Cell.CRYSTAL)));
        out.add(new ItemStack(ModItems.SINGULARITY_CORE.get(), p.count(CorePattern.Cell.CORE)));
        return out;
    }

    /** Dreht die echte Struktur als 3D-Modell im Vorschaufenster (mit der Maus ziehbar). */
    private void renderStructure(GuiGraphics g, ReactorSize size, int cx, int cy, float t) {
        if (minecraft == null) return;
        CorePattern p = size.pattern();
        int half = p.half();
        float scale = 118.0F / (size.size() * 1.75F);
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        BlockPos controller = new BlockPos(0, -half, half);

        g.enableScissor(leftPos + VIEW_X, topPos + VIEW_Y, leftPos + VIEW_X + VIEW_W, topPos + VIEW_Y + VIEW_H);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 400);
        ps.scale(scale, -scale, scale);
        ps.mulPose(Axis.XP.rotationDegrees(22.0F + dragPitch));
        ps.mulPose(Axis.YP.rotationDegrees(t * 0.8F + 30.0F + dragYaw));
        Lighting.setupFor3DItems();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        p.forEach(BlockPos.ZERO, (pos, cell) -> {
            BlockState state = CorePattern.blockFor(cell);
            if (state == null) return;
            if (pos.equals(controller)) {
                state = ModBlocks.CONTROLLER.get().defaultBlockState().setValue(ControllerBlock.FACING, Direction.SOUTH);
            }
            ps.pushPose();
            ps.translate(pos.getX() - 0.5F, pos.getY() - 0.5F, pos.getZ() - 0.5F);
            dispatcher.renderSingleBlock(state, ps, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY, null);
            ps.popPose();
        });
        g.flush();
        ps.popPose();
        g.disableScissor();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!menu.sizeChosen() && in((int) mouseX - leftPos, (int) mouseY - topPos, VIEW_X, VIEW_Y, VIEW_W, VIEW_H)) {
            dragYaw += (float) dragX * 1.5F;
            dragPitch = Mth.clamp(dragPitch + (float) dragY, -40.0F, 50.0F);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** RF/t des Sternenstaubs als Referenz fuer die Leistungsspanne in der Groessenwahl. */
    private static int referencePower() {
        CoreFuel fuel = ModDataMaps.fuel(new ItemStack(ModItems.STAR_DUST.get()));
        return fuel == null ? 1200 : fuel.power();
    }

    private Component statusLine() {
        if (!menu.flag(ControllerMenu.F_FORMED)) {
            return Component.translatable("gui.cosmiccore.status.incomplete", menu.errors()).withStyle(ChatFormatting.RED);
        }
        if (menu.collapseSeconds() > 0) {
            return Component.translatable("gui.cosmiccore.status.collapsed", menu.collapseSeconds()).withStyle(ChatFormatting.DARK_RED);
        }
        if (!menu.flag(ControllerMenu.F_ENABLED)) {
            return Component.translatable("gui.cosmiccore.status.off").withStyle(ChatFormatting.GRAY);
        }
        if (!menu.flag(ControllerMenu.F_BURNING)) {
            return Component.translatable("gui.cosmiccore.status.idle").withStyle(ChatFormatting.YELLOW);
        }
        if (menu.instability() >= ControllerBlockEntity.UNSTABLE_ON) {
            return Component.translatable("gui.cosmiccore.status.unstable").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        }
        return Component.translatable("gui.cosmiccore.status.active").withStyle(ChatFormatting.GREEN);
    }

    private int instColor() {
        float i = menu.instability();
        return i < 40f ? 0xFF80FF90 : i < ControllerBlockEntity.UNSTABLE_ON ? 0xFFFFE060 : 0xFFFF5060;
    }

    // ------------------------------------------------------------------ Tooltips

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (!menu.sizeChosen()) {
            List<ItemStack> icons = requiredBlocks(ReactorSize.values()[selected]);
            for (int i = 0; i < icons.size(); i++) {
                int ix = leftPos + iconX(i, icons.size()), iy = topPos + ICON_Y;
                if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                    g.renderTooltip(font, icons.get(i).getHoverName(), mouseX, mouseY);
                }
            }
            return;
        }

        int mx = mouseX - leftPos, my = mouseY - topPos;
        List<Component> tip = new ArrayList<>();
        if (in(mx, my, BAR_ENERGY_X, BAR_Y, BAR_W, BAR_H)) {
            tip.add(Component.translatable("gui.cosmiccore.tip.energy"));
            tip.add(Component.literal(fmt(menu.energy()) + " / " + fmt(menu.capacity()) + " RF").withStyle(ChatFormatting.GRAY));
        } else if (in(mx, my, BAR_INST_X, BAR_Y, BAR_W, BAR_H)) {
            tip.add(Component.translatable("gui.cosmiccore.tip.instability"));
            tip.add(Component.literal(String.format("%.1f %%", menu.instability())).withStyle(ChatFormatting.GRAY));
        } else if (in(mx, my, BAR_RES_X, BAR_Y, BAR_W, BAR_H)) {
            tip.add(Component.translatable("gui.cosmiccore.tip.resonance"));
            tip.add(Component.literal(menu.resonance() + " %").withStyle(ChatFormatting.GRAY));
        }
        if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mouseX, mouseY);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String fmt(long n) {
        return NumberFormat.getIntegerInstance().format(n);
    }

    private static String compact(long n) {
        if (n >= 1_000_000_000L) return String.format("%.2fG", n / 1e9);
        if (n >= 1_000_000L) return String.format("%.1fM", n / 1e6);
        if (n >= 10_000L) return String.format("%.1fk", n / 1e3);
        return Long.toString(n);
    }
}
