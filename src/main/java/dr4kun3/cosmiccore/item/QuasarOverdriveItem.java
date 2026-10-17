package dr4kun3.cosmiccore.item;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Quasar-Overdrive-Partikel: nur craftbar (Overdrive-Partikel umgeben von vier Quasar-Essenzen). Vier davon schalten
 * beim ultimativen Kern nach der dritten Overdrive-Stufe den Ultimate-Modus frei.
 */
public class QuasarOverdriveItem extends Item {
    /**
     * Erzeugt das Quasar-Overdrive-Partikel.
     *
     * @param props Item-Eigenschaften aus der Registrierung
     */
    public QuasarOverdriveItem(Properties props) {
        super(props);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /**
     * Tooltip: Wirkung und Grenze.
     *
     * @param stack der Stapel
     * @param ctx Tooltip-Kontext
     * @param display welche Tooltip-Teile angezeigt werden
     * @param tooltip nimmt die Zeilen auf
     * @param flag erweiterte Anzeige
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.cosmiccore.quasar_overdrive.effect").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.cosmiccore.quasar_overdrive.limit").withStyle(ChatFormatting.GRAY));
    }
}
