package dr4kun3.cosmiccore.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Quasar-Overdrive-Partikel: nur craftbar (Overdrive-Partikel umgeben von vier Quasar-Essenzen). Vier davon schalten
 * beim ultimativen Kern nach der dritten Overdrive-Stufe den Ultimate-Modus frei.
 */
public class QuasarOverdriveItem extends Item {
    public QuasarOverdriveItem(Properties props) {
        super(props);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.cosmiccore.quasar_overdrive.effect").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.cosmiccore.quasar_overdrive.limit").withStyle(ChatFormatting.GRAY));
    }
}
