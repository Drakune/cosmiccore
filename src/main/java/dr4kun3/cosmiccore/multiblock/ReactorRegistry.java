package dr4kun3.cosmiccore.multiblock;

import dr4kun3.cosmiccore.CosmicCore;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Server-seitige Liste der geladenen Reaktor-Controller je Dimension - damit der Asteroiden-Spawner schnell weiss,
 * wo Reaktoren stehen und wo ein Anti-Asteroiden-Modul aktiv ist.
 */
@EventBusSubscriber(modid = CosmicCore.MODID)
public final class ReactorRegistry {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> CONTROLLERS = new ConcurrentHashMap<>();

    /**
     * Merkt sich einen Controller (fuer Asteroiden und den Zwischensequenz-Befehl).
     *
     * @param level Welt des Controllers
     * @param pos Position des Controllers
     */
    public static void add(Level level, BlockPos pos) {
        CONTROLLERS.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
    }

    /**
     * Vergisst einen Controller (abgebaut oder entladen).
     *
     * @param level Welt des Controllers
     * @param pos Position des Controllers
     */
    public static void remove(Level level, BlockPos pos) {
        Set<BlockPos> set = CONTROLLERS.get(level.dimension());
        if (set != null) set.remove(pos);
    }

    /**
     * Alle bekannten Controller einer Dimension.
     *
     * @param level die Welt
     * @return Kopie der Positionen (darf veraendert werden)
     */
    public static List<BlockPos> controllers(Level level) {
        Set<BlockPos> set = CONTROLLERS.get(level.dimension());
        return set == null ? List.of() : List.copyOf(set);
    }

    /**
     * Beim Beenden des Servers alles vergessen (die naechste Welt beginnt leer).
     *
     * @param event Server wurde beendet
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CONTROLLERS.clear();
    }

    /** Nur statischer Zustand - keine Instanzen. */
    private ReactorRegistry() {}
}
