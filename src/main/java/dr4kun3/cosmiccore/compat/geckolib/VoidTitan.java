package dr4kun3.cosmiccore.compat.geckolib;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

/** Animationszustand des Oberkörpers einer Void-Titan-Entity (je Entity eine Instanz, nur clientseitig). */
public class VoidTitan implements GeoAnimatable {
    private static final RawAnimation CINEMATIC = RawAnimation.begin().thenPlayAndHold("animation.void_titan.cinematic");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.void_titan.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Zeit in Ticks (inkl. Teil-Tick), von der Entity vorgegeben. */
    double ticks;
    boolean cinematic;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<VoidTitan>("main", 0, state -> state.setAndContinue(cinematic ? CINEMATIC : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

}
