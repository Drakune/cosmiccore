package dr4kun3.cosmiccore.client;

import dr4kun3.cosmiccore.CosmicCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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
    private static final boolean CONSOLE = System.getProperty("cosmiccore.consoleTest") != null;

    private CutsceneTestHarness() {}

    /** Konsolen-Test: Kreativ-Bau (gebildet) und eine von Hand gesetzte Konsole (ruhend), Ansichten von vorn und von der Seite. */
    private static void consoleTest(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        net.minecraft.core.BlockPos at = mc.player.blockPosition().offset(0, 0, -18);
        net.minecraft.core.BlockPos manual = at.offset(-16, 0, 0);
        int[][] views = {{0, 1, -5, 0, 12}, {0, 2, -3, 0, 35}, {-5, 1, -3, -60, 10}, {5, 1, -3, 60, 10}};
        if (worldTicks == 20) {
            server.execute(() -> {
                var level = server.getLevel(mc.player.level().dimension());
                if (level == null) return;
                dr4kun3.cosmiccore.block.entity.ControllerBlockEntity.debugSetupUltimate(level, at);
                level.setDayTime(6000);
                // von Hand: erst der Controller, dann die Ports wie beim Platzieren durch einen Spieler
                level.setBlock(manual, dr4kun3.cosmiccore.block.ModBlocks.CONTROLLER.get().defaultBlockState(), 3);
                var right = manual.relative(dr4kun3.cosmiccore.block.Terminal.right(net.minecraft.core.Direction.NORTH));
                var left = manual.relative(dr4kun3.cosmiccore.block.Terminal.left(net.minecraft.core.Direction.NORTH));
                level.setBlock(right, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(
                        dr4kun3.cosmiccore.block.ModBlocks.ENERGY_PORT.get().defaultBlockState(), level, right), 3);
                level.setBlock(left, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(
                        dr4kun3.cosmiccore.block.ModBlocks.ITEM_PORT.get().defaultBlockState(), level, left), 3);
                for (var pos : new net.minecraft.core.BlockPos[]{at, at.west(), at.east(), at.west(2), at.east(2), manual, right, left}) {
                    CosmicCore.LOGGER.info("[ConsoleTest] {} = {}", pos, level.getBlockState(pos));
                }
                CosmicCore.LOGGER.info("[ConsoleTest] Energie-Cap West={} Item-Cap Ost={}",
                        level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, at.west(), net.minecraft.core.Direction.WEST) != null,
                        level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, at.east(), net.minecraft.core.Direction.EAST) != null);
            });
        }
        if (worldTicks == 15 || worldTicks >= 300 && (worldTicks - 300) % 40 == 0) {
            int v = worldTicks == 15 ? 0 : (worldTicks - 300) / 40;
            if (v >= views.length * 2) return;
            int[] view = views[v % views.length];
            net.minecraft.core.BlockPos base = v < views.length ? at : manual;
            if (cam == null) cam = net.minecraft.world.entity.EntityType.MARKER.create(mc.level);
            double x = base.getX() + 0.5 + view[0], y = base.getY() + view[1] + 0.2, z = base.getZ() + 0.5 + view[2];
            cam.setPos(x, y, z);
            cam.xo = cam.xOld = x;
            cam.yo = cam.yOld = y;
            cam.zo = cam.zOld = z;
            cam.setYRot(view[3]);
            cam.yRotO = view[3];
            cam.setXRot(view[4]);
            cam.xRotO = view[4];
            mc.setCameraEntity(cam);
        }
        if (worldTicks >= 24 && worldTicks < 300 && worldTicks % 8 == 0) {
            mc.options.hideGui = true;
            Screenshot.grab(mc.gameDirectory, String.format("cutscene_a%03d.png", worldTicks), mc.getMainRenderTarget(), msg -> {});
        }
        if (worldTicks >= 330 && (worldTicks - 330) % 40 == 0) {
            int v = (worldTicks - 330) / 40;
            mc.options.hideGui = true;
            Screenshot.grab(mc.gameDirectory, String.format("cutscene_%03d.png", v), mc.getMainRenderTarget(), msg -> {});
            if (v >= views.length * 2 - 1) {
                finished = true;
                mc.stop();
            }
        }
    }

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

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        if (!ENABLED || !started || finished || !TitanCutscene.isActive()) return;
        int t = TitanCutscene.currentTick();
        if (t != lastShot && t % EVERY == 0) {
            lastShot = t;
            Minecraft mc = Minecraft.getInstance();
            Screenshot.grab(mc.gameDirectory, String.format("cutscene_%03d.png", t), mc.getMainRenderTarget(), msg -> {});
        }
    }
}
