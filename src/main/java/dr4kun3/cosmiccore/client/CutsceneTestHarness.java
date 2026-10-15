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

    private CutsceneTestHarness() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        mc.options.pauseOnLostFocus = false;
        worldTicks++;
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
