package dr4kun3.cosmiccore;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Eigene, synthetisch erzeugte Klaenge (tools/generate_sounds.py). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CosmicCore.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> CORE_HUM = register("core_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORE_IGNITE = register("core_ignite");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORE_FORM = register("core_form");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORE_ALARM = register("core_alarm");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORE_COLLAPSE = register("core_collapse");
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_LOCK = register("laser_lock");
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_CHARGE = register("laser_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> ASTEROID_SHATTER = register("asteroid_shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLECT_PING = register("collect_ping");
    public static final DeferredHolder<SoundEvent, SoundEvent> ORB_FORMED = register("orb_formed");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORE_ABSORB = register("core_absorb");
    public static final DeferredHolder<SoundEvent, SoundEvent> AWAKENING = register("awakening");

    /** Alle Namen (fuer sounds.json und Untertitel). */
    public static final String[] NAMES = {"core_hum", "core_ignite", "core_form", "core_alarm", "core_collapse", "laser_lock",
            "laser_charge", "asteroid_shatter", "collect_ping", "orb_formed", "core_absorb", "awakening"};

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CosmicCore.id(name)));
    }

    private ModSounds() {}
}
