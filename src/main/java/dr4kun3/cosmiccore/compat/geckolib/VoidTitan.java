package dr4kun3.cosmiccore.compat.geckolib;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Der Void-Titan der Erwachen-Cutscene - kein Entity, nur ein animiertes Objekt (keine Registrierung, damit Server
 * und Clients auch ohne GeckoLib zusammenpassen). Die Zeit gibt die Cutscene vor.
 */
public class VoidTitan implements GeoAnimatable {
    private static final RawAnimation CINEMATIC = RawAnimation.begin().thenPlayAndHold("animation.void_titan.cinematic");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Zeit seit dem Erscheinen in Ticks (inkl. Teil-Tick), von der Cutscene gesetzt. */
    double ticks;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "cinematic", 0, state -> state.setAndContinue(CINEMATIC)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return ticks;
    }
}
