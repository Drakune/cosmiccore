package dev.cosmiccore.item;

import dev.cosmiccore.block.entity.ControllerBlockEntity;
import dev.cosmiccore.block.entity.PortBlockEntity;
import dev.cosmiccore.entity.CosmicAsteroid;
import java.text.NumberFormat;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Kosmos-Laser: Rechtsklick gedrueckt halten und auf einen Asteroiden zielen, um ihn abzubauen. Die gewonnene
 * Energie wird im Laser gespeichert und per Rechtsklick auf den Controller oder einen Energie-Port in den Puffer
 * des Reaktors uebertragen.
 */
public class CosmicLaserItem extends Item {
    public static final int CAPACITY = 5_000_000;
    public static final double RANGE = 160.0;

    public CosmicLaserItem(Properties props) {
        super(props);
    }

    // ------------------------------------------------------------------ gespeicherte Energie

    public static int getStored(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("energy");
    }

    public static void setStored(ItemStack stack, int energy) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("energy", Mth.clamp(energy, 0, CAPACITY)));
    }

    // ------------------------------------------------------------------ Benutzung

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    /** Ziel des Laserstrahls (Asteroid) oder null. Wird auch clientseitig fuer den Strahl benutzt. */
    @Nullable
    public static CosmicAsteroid findTarget(LivingEntity user) {
        Vec3 start = user.getEyePosition();
        Vec3 end = start.add(user.getLookAngle().scale(RANGE));
        AABB box = user.getBoundingBox().expandTowards(user.getLookAngle().scale(RANGE)).inflate(2.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(user.level(), user, start, end, box,
                e -> e instanceof CosmicAsteroid && e.isAlive(), 1.0F);
        return hit != null && hit.getEntity() instanceof CosmicAsteroid a ? a : null;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level.isClientSide || !(user instanceof Player player)) return;
        if (user.tickCount % 12 == 0) {
            level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.6F, 1.8F);
        }
        CosmicAsteroid target = findTarget(user);
        if (target == null) return;
        if (getStored(stack) >= CAPACITY) {
            if (user.tickCount % 20 == 0) {
                player.displayClientMessage(Component.translatable("message.cosmiccore.laser.full").withStyle(ChatFormatting.YELLOW), true);
            }
            return;
        }
        if (level instanceof ServerLevel sl && user.tickCount % 3 == 0) {
            Vec3 c = target.position().add(0, target.getBbHeight() / 2, 0);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 4,
                    target.getBbWidth() * 0.3, target.getBbHeight() * 0.3, target.getBbWidth() * 0.3, 0.1);
        }
        int gained = target.harvestTick();
        if (gained > 0) {
            int stored = Math.min(CAPACITY, getStored(stack) + gained);
            setStored(stack, stored);
            player.displayClientMessage(Component.translatable("message.cosmiccore.laser.collected", fmt(gained), fmt(stored))
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    /** Rechtsklick auf einen Energie-Port: Energie an den angeschlossenen Reaktor abgeben. */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        BlockEntity be = ctx.getLevel().getBlockEntity(ctx.getClickedPos());
        ControllerBlockEntity controller = be instanceof ControllerBlockEntity c ? c
                : be instanceof PortBlockEntity port ? port.linkedController() : null;
        if (controller == null) return InteractionResult.PASS;
        if (!ctx.getLevel().isClientSide && ctx.getPlayer() != null) {
            discharge(ctx.getItemInHand(), controller, ctx.getPlayer());
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    /** Uebertraegt die gespeicherte Energie in den Puffer des Reaktors (Server). */
    public static void discharge(ItemStack stack, ControllerBlockEntity controller, Player player) {
        int stored = getStored(stack);
        if (stored <= 0) {
            player.displayClientMessage(Component.translatable("message.cosmiccore.laser.empty").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int moved = controller.receiveCosmicEnergy(stored);
        if (moved <= 0) {
            player.displayClientMessage(Component.translatable("message.cosmiccore.laser.no_room").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        setStored(stack, stored - moved);
        player.level().playSound(null, controller.getBlockPos(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.4F);
        player.displayClientMessage(Component.translatable("message.cosmiccore.laser.transferred", fmt(moved)).withStyle(ChatFormatting.AQUA), true);
    }

    // ------------------------------------------------------------------ Anzeige

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getStored(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getStored(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x7AE8FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.cosmiccore.laser.energy", fmt(getStored(stack)), fmt(CAPACITY))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.cosmiccore.laser.hint1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.cosmiccore.laser.hint2").withStyle(ChatFormatting.GRAY));
    }

    private static String fmt(long n) {
        return NumberFormat.getIntegerInstance().format(n);
    }
}
