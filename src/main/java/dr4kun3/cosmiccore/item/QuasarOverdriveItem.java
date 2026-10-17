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
    /**
     * Erzeugt das Quasar-Overdrive-Partikel.
     *
     * @param props Item-Eigenschaften aus der Registrierung
     */
    public QuasarOverdriveItem(Properties props) {
        super(props);
    }

    /**
     * Das Partikel schimmert immer.
     *
     * @param stack der Stapel
     * @return immer {@code true}
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /**
     * Tooltip: Wirkung (Overdrive-Stufe 4) und Grenze.
     *
     * @param stack der Stapel
     * @param ctx Kontext des Tooltips
     * @param tooltip Zeilen, an die angehaengt wird
     * @param flag erweiterte Anzeige (F3+H)
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.cosmiccore.quasar_overdrive.effect").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.cosmiccore.quasar_overdrive.limit").withStyle(ChatFormatting.GRAY));
    }
}
