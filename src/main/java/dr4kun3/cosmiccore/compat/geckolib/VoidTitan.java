package dr4kun3.cosmiccore.compat.geckolib;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Animationszustand des Oberkoerpers einer Void-Titan-Entity (je Entity eine Instanz, nur clientseitig). */
public class VoidTitan implements GeoAnimatable {
    private static final RawAnimation CINEMATIC = RawAnimation.begin().thenPlayAndHold("animation.void_titan.cinematic");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.void_titan.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Zeit in Ticks (inkl. Teil-Tick), von der Entity vorgegeben. */
    double ticks;
    boolean cinematic;

    /**
     * Ein Animations-Controller: im Film die Choreografie (einmal, dann halten), sonst die Ruhe-Animation in Schleife.
     *
     * @param controllers Sammelstelle der Controller
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<VoidTitan>("main", 0, state -> state.setAndContinue(cinematic ? CINEMATIC : IDLE)));
    }

    /**
     * Zwischenspeicher der Animationszustaende dieser Instanz.
     *
     * @return der Zwischenspeicher
     */
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

}
