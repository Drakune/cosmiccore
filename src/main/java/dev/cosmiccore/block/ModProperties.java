package dev.cosmiccore.block;

import net.minecraft.world.level.block.state.properties.EnumProperty;

public final class ModProperties {
    public static final EnumProperty<CorePhase> PHASE = EnumProperty.create("phase", CorePhase.class);

    private ModProperties() {}
}
