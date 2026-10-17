package dr4kun3.cosmiccore.client;

import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Nur fuer die Entwicklung (System-Property cosmiccore.cutsceneTest): spielt die Titan-Cutscene nach dem Betreten
 * der Welt automatisch ab, speichert Screenshots in festen Abstaenden und beendet danach das Spiel.
 * Im normalen Spiel ist diese Klasse inaktiv.
 */
@EventBusSubscriber(modid = CosmicCore.MODID, value = Dist.CLIENT)
public final class CutsceneTestHarness {
    private static final boolean ENABLED = System.getProperty("cosmiccore.cutsceneTest") != null;
    private static final int FROM = Integer.getInteger("cosmiccore.cutsceneFrom", 0);
    private static final int TO = Integer.getInteger("cosmiccore.cutsceneTo", 99999);
    private static final int EVERY = Math.max(1, Integer.getInteger("cosmiccore.cutsceneEvery", 15));
    private static int worldTicks;
    private static boolean started, finished;
    private static int lastShot = -1;
    private static volatile net.minecraft.core.BlockPos testCore;

    private static net.minecraft.world.entity.Marker cam;
    private static String guidePhase;
    private static long guideMs, guideLastShot;
    private static int guideShot;
    private static net.minecraft.core.BlockPos consoleAt;
    private static final boolean GUIDE = System.getProperty("cosmiccore.guideTest") != null;
    private static final boolean BOOK = System.getProperty("cosmiccore.bookTest") != null;
    private static final boolean CONSOLE = System.getProperty("cosmiccore.consoleTest") != null;
    /** Eigene Blickwinkel fuer den Konsolen-Test: "x,y,z,Richtung,Neigung;..." relativ zum Controller. */
    private static final int[][] VIEWS = parseViews(System.getProperty("cosmiccore.views", ""));

    /**
     * Liest die Blickwinkel aus der Systemeigenschaft.
     *
     * @param spec "x,y,z,Richtung,Neigung" je Blickwinkel, durch Semikolon getrennt
     * @return die Blickwinkel oder {@code null}, wenn keine angegeben sind
     */
    private static int[][] parseViews(String spec) {
        if (spec.isBlank()) return null;
        String[] parts = spec.split(";");
        int[][] out = new int[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            out[i] = java.util.Arrays.stream(parts[i].split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
        }
        return out;
    }

    /** Nur statische Testroutinen - keine Instanzen. */
    private CutsceneTestHarness() {}

    /**
     * Buch-Test: Handbuch in der Hand (Aufklappen, Blaettern), von aussen und im Inventar - mit Screenshots.
     *
     * @param mc der Client
     */
    private static void bookTest(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        int t = worldTicks;
        if (t == 20) {
            mc.options.hideGui = false;
            mc.player.getInventory().selected = 0;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                sp.getInventory().selected = 0;
                sp.getInventory().setItem(0, ItemStack.EMPTY);
                sp.getInventory().setItem(1, new ItemStack(dr4kun3.cosmiccore.ModItems.GUIDE_BOOK.get()));
                sp.setXRot(10);
            });
        }
        if (t == 40) mc.player.getInventory().selected = 1; // Buch nehmen -> klappt auf
        if (t == 40) server.execute(() -> server.getPlayerList().getPlayers().get(0).getInventory().selected = 1);
        int[] shots = {41, 43, 45, 47, 50, 60, 70, 72, 74, 76, 78};
        for (int k = 0; k < shots.length; k++) {
            if (t == shots[k]) Screenshot.grab(mc.gameDirectory, String.format("cutscene_b%02d.png", k), mc.getMainRenderTarget(), msg -> {});
        }
        if (t == 100) mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        if (t == 120) Screenshot.grab(mc.gameDirectory, "cutscene_b20.png", mc.getMainRenderTarget(), msg -> {});
        if (t == 125) {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        }
        if (t == 140) Screenshot.grab(mc.gameDirectory, "cutscene_b21.png", mc.getMainRenderTarget(), msg -> {});
        if (t == 150) {
            finished = true;
            mc.stop();
        }
    }

    /**
     * Konsolen-Test: Kreativ-Bau (gebildet, mit Trichter am Item-Port), einzelne Ports, eine ruhende Konsole und das
     * Menue - aus festen Blickwinkeln fotografiert.
     *
     * @param mc der Client
     */
    private static void consoleTest(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        if (consoleAt == null) consoleAt = mc.player.blockPosition().offset(0, 0, -18);
        net.minecraft.core.BlockPos at = consoleAt;
        net.minecraft.core.BlockPos manual = at.offset(-16, 0, 0);
        // Kamera relativ zum Controller: x, y, z, Blickrichtung, Neigung
        int[][] views = VIEWS != null ? VIEWS : new int[][]{{1, 2, -7, 0, 14}, {0, 3, 4, 180, 30}, {5, 1, 15, 200, 14}, {-16, 1, -5, 0, 12}};
        if (worldTicks == 20) {
            server.execute(() -> {
                var level = server.getLevel(mc.player.level().dimension());
                if (level == null) return;
                dr4kun3.cosmiccore.block.entity.ControllerBlockEntity.debugSetup(level, at, dr4kun3.cosmiccore.multiblock.ReactorSize.bySize(
                        Integer.getInteger("cosmiccore.size", 11)));
                level.setDayTime(6000);
                // einzelne Ports in der Mitte der gegenueberliegenden Bodenkante, ein Trichter dockt am Item-Port der Konsole an
                net.minecraft.core.BlockPos south = at.south(10);
                level.setBlock(south.west(), dr4kun3.cosmiccore.block.ModBlocks.ENERGY_PORT.get().defaultBlockState(), 3);
                level.setBlock(south.east(), dr4kun3.cosmiccore.block.ModBlocks.ITEM_PORT.get().defaultBlockState(), 3);
                level.setBlock(south.east().south(), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
                // Konsole mit Abstand: Item-Port zwei Felder weiter (Luecke dazwischen ist Gehaeuse), Trichter oben drauf
                // direkt daneben bleibt der Item-Port der Konsole; ein zweiter steht drei Felder weiter (eigenstaendig)
                if (Integer.getInteger("cosmiccore.size", 11) == 11) {
                    level.setBlock(at.east(3), dr4kun3.cosmiccore.block.ModBlocks.ITEM_PORT.get().defaultBlockState(), 3);
                    level.setBlock(at.east(3).above(), net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState(), 3);
                }
                // von Hand: erst der Controller, dann die Ports wie beim Platzieren durch einen Spieler
                level.setBlock(manual, dr4kun3.cosmiccore.block.ModBlocks.CONTROLLER.get().defaultBlockState(), 3);
                var right = manual.relative(dr4kun3.cosmiccore.block.Terminal.right(net.minecraft.core.Direction.NORTH));
                var left = manual.relative(dr4kun3.cosmiccore.block.Terminal.left(net.minecraft.core.Direction.NORTH));
                level.setBlock(right, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(
                        dr4kun3.cosmiccore.block.ModBlocks.ENERGY_PORT.get().defaultBlockState(), level, right), 3);
                level.setBlock(left, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(
                        dr4kun3.cosmiccore.block.ModBlocks.ITEM_PORT.get().defaultBlockState(), level, left), 3);
                level.setBlock(left.east(), net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState(), 3);
            });
        }
        if (worldTicks == 200) {
            server.execute(() -> {
                var level = server.getLevel(mc.player.level().dimension());
                for (var pos : new net.minecraft.core.BlockPos[]{at, at.west(), at.east(), at.east(2), manual, manual.west(), manual.east()}) {
                    CosmicCore.LOGGER.info("[ConsoleTest] {} = {}", pos, level.getBlockState(pos));
                }
                if (level.getBlockEntity(at) instanceof dr4kun3.cosmiccore.block.entity.ControllerBlockEntity c) {
                    CosmicCore.LOGGER.info("[ConsoleTest] fuel={}s stab={}s energyPort={}", c.getFuelSeconds(), c.getStabSeconds(), c.hasEnergyPort());
                }
                // etwas Beute in die Lager, damit die Stationen ihr Raster zeigen
                net.minecraft.world.item.Item[] loot = {net.minecraft.world.item.Items.DIAMOND, dr4kun3.cosmiccore.ModItems.STAR_DUST.get(),
                        net.minecraft.world.item.Items.IRON_INGOT, net.minecraft.world.item.Items.GOLD_INGOT, net.minecraft.world.item.Items.EMERALD};
                for (var pos : new net.minecraft.core.BlockPos[]{at.east(), at.south(10).east()}) {
                    if (level.getBlockEntity(pos) instanceof dr4kun3.cosmiccore.block.entity.ItemPortBlockEntity ip) {
                        for (int i = 0; i < 11; i++) ip.store(new net.minecraft.world.item.ItemStack(loot[i % loot.length], 1 + (i * 17) % 64));
                    }
                }
            });
        }
        if (worldTicks == 15 || worldTicks >= 300 && (worldTicks - 300) % 40 == 0) {
            int v = worldTicks == 15 ? 0 : (worldTicks - 300) / 40;
            if (v < views.length) {
                int[] view = views[v];
                if (cam == null) cam = net.minecraft.world.entity.EntityType.MARKER.create(mc.level);
                double x = at.getX() + 0.5 + view[0], y = at.getY() + view[1] + 0.2, z = at.getZ() + 0.5 + view[2];
                cam.setPos(x, y, z);
                cam.xo = cam.xOld = x;
                cam.yo = cam.yOld = y;
                cam.zo = cam.zOld = z;
                cam.setYRot(view[3]);
                cam.yRotO = view[3];
                cam.setXRot(view[4]);
                cam.xRotO = view[4];
                mc.setCameraEntity(cam);
            } else {
                int step = v - views.length;
                // Lager-Knopf im Controller-Menue druecken bzw. das Lager schliessen (Rueckweg pruefen)
                if (step == 2 && mc.screen instanceof ControllerScreen cs && mc.gameMode != null) {
                    mc.gameMode.handleInventoryButtonClick(cs.getMenu().containerId, dr4kun3.cosmiccore.menu.ControllerMenu.BTN_STORAGE);
                    return;
                }
                if (step == 3 && mc.screen instanceof ItemPortScreen ips) {
                    ips.onClose();
                    return;
                }
                mc.setCameraEntity(mc.player);
                mc.options.guiScale().set(1);
                mc.resizeDisplay();
                server.execute(() -> {
                    var sp = server.getPlayerList().getPlayers().get(0);
                    var level = sp.serverLevel();
                    sp.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() - 2.5, 0, 20);
                    switch (step) {
                        case 0, 1 -> { if (level.getBlockEntity(at) instanceof dr4kun3.cosmiccore.block.entity.ControllerBlockEntity c) sp.openMenu(c, at); }
                        default -> {}
                    }
                });
            }
        }
        if (worldTicks == 300 + 40 * (views.length + 1) + 10 && mc.screen instanceof ControllerScreen) {
            // zweites Bild: Modul-Tab ausgeklappt
            ((dr4kun3.cosmiccore.menu.ControllerMenu) ((ControllerScreen) mc.screen).getMenu()).modulesOpen = true;
        }
        if (worldTicks >= 24 && worldTicks < 300 && worldTicks % 8 == 0) {
            mc.options.hideGui = true;
            Screenshot.grab(mc.gameDirectory, String.format("cutscene_a%03d.png", worldTicks), mc.getMainRenderTarget(), msg -> {});
        }
        if (Boolean.getBoolean("cosmiccore.burst") && worldTicks >= 310 && worldTicks < 330) {
            // Bildserie (ein Bild je Tick) aus dem ersten Blickwinkel - zeigt, wie schnell sich Effekte bewegen
            mc.options.hideGui = true;
            Screenshot.grab(mc.gameDirectory, String.format("cutscene_b%02d.png", worldTicks - 310), mc.getMainRenderTarget(), msg -> {});
        }
        if (worldTicks >= 330 && (worldTicks - 330) % 40 == 0) {
            int v = (worldTicks - 330) / 40;
            mc.options.hideGui = v < views.length;
            Screenshot.grab(mc.gameDirectory, String.format("cutscene_%03d.png", v), mc.getMainRenderTarget(), msg -> {});
            CosmicCore.LOGGER.info("[ConsoleTest] Bild {}: Bildschirm = {}", v, mc.screen == null ? "-" : mc.screen.getClass().getSimpleName());
            if (v >= views.length + 3) {
                finished = true;
                mc.stop();
            }
        }
    }

    /**
     * Steuert den gewaehlten Test Tick fuer Tick (nur mit {@code -Dcosmiccore.cutsceneTest=true}).
     *
     * @param event Ende eines Client-Ticks
     */
    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        mc.options.pauseOnLostFocus = false;
        worldTicks++;
        if (CONSOLE) {
            consoleTest(mc);
            return;
        }
        if (BOOK) {
            bookTest(mc);
            return;
        }
        if (GUIDE) {
            // Handbuch: Aufklappen, einmal Umblaettern und Zuklappen als Bildfolge
            if (worldTicks == 30) {
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                mc.setScreen(new GuideScreen());
                guidePhase = "o";
                guideMs = net.minecraft.Util.getMillis();
            }
            if (worldTicks == 60 && mc.screen instanceof GuideScreen gs0) {
                gs0.turn(1); // zur Doppelseite vor den Multiblock-Seiten
            }
            if (worldTicks == 70 && mc.screen instanceof GuideScreen gs1) {
                gs1.turn(1);
            }
            if (worldTicks == 80 && mc.screen instanceof GuideScreen gs) {
                gs.turn(1); // umblaettern auf die Seiten mit 3D-Vorschau und Item-Symbolen
                guidePhase = "t";
                guideMs = net.minecraft.Util.getMillis();
            }
            if (worldTicks == 110 && mc.screen instanceof GuideScreen gs) {
                gs.onClose();
                guidePhase = "c";
                guideMs = net.minecraft.Util.getMillis();
            }
            if (worldTicks == 140) {
                finished = true;
                mc.stop();
            }
            return;
        }
        if (worldTicks == 20 && mc.getSingleplayerServer() != null) {
            // Buehne: ein fertiger ultimativer Kern 24 Bloecke vor dem Spieler (laeuft im Ultimate-Modus)
            net.minecraft.core.BlockPos at = mc.player.blockPosition().offset(0, 0, -18);
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.getLevel(mc.player.level().dimension());
                if (level != null) {
                    testCore = dr4kun3.cosmiccore.block.entity.ControllerBlockEntity.debugSetupUltimate(level, at);
                    level.setDayTime(18000); // Nacht - der Kosmos wirkt besser
                }
            });
        }
        if (!started && worldTicks == 260 && testCore != null) {
            started = true;
            TitanCutscene.start(testCore);
            TitanCutscene.debugJumpTo(FROM);
            CosmicCore.LOGGER.info("[CutsceneTest] gestartet ab Tick {}", FROM);
        }
        if (started && TitanCutscene.isActive() && TitanCutscene.currentTick() % 40 == 0 && TitanCutscene.titan() != null) {
            CosmicCore.LOGGER.info("[CutsceneTest] t={} bones={}", TitanCutscene.currentTick(), TitanCutscene.titan().bones);
        }
        if (started && (!TitanCutscene.isActive() || TitanCutscene.currentTick() > TO)) {
            finished = true;
            CosmicCore.LOGGER.info("[CutsceneTest] fertig - beende Spiel");
            mc.stop();
        }
    }

    /**
     * Handbuch-Test: fotografiert Aufklappen, Blaettern und Zuklappen bildgenau in festen Abstaenden.
     *
     * @param event Ende eines gezeichneten Bildes
     */
    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        if (GUIDE && guidePhase != null && !finished) {
            long now = net.minecraft.Util.getMillis(), since = now - guideMs;
            long window = guidePhase.equals("o") ? 1300 : 700, step = guidePhase.equals("o") ? 110 : 70;
            if (since <= window && now - guideLastShot >= step) {
                guideLastShot = now;
                Minecraft mc = Minecraft.getInstance();
                Screenshot.grab(mc.gameDirectory, String.format("cutscene_%s%02d.png", guidePhase, guideShot++ % 100), mc.getMainRenderTarget(), msg -> {});
            }
            if (since > window) guideShot = 0;
            return;
        }
        if (!ENABLED || !started || finished || !TitanCutscene.isActive()) return;
        int t = TitanCutscene.currentTick();
        if (t != lastShot && t % EVERY == 0) {
            lastShot = t;
            Minecraft mc = Minecraft.getInstance();
            Screenshot.grab(mc.gameDirectory, String.format("cutscene_%03d.png", t), mc.getMainRenderTarget(), msg -> {});
        }
    }
}
