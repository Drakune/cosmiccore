package dr4kun3.cosmiccore.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Overdrive-Partikel: wird ueber die GUI des Controllers im Reaktor installiert und dabei verbraucht.
 * Jede Stufe verdoppelt die Leistung und verdreifacht die Instabilitaet.
 */
public class OverdriveParticleItem extends Item {
    /**
     * Erzeugt das Overdrive-Partikel.
     *
     * @param props Item-Eigenschaften aus der Registrierung
     */
    public OverdriveParticleItem(Properties props) {
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
     * Tooltip: Wirkung und Grenze.
     *
     * @param stack der Stapel
     * @param ctx Tooltip-Kontext
     * @param display welche Tooltip-Teile angezeigt werden
     * @param tooltip nimmt die Zeilen auf
     * @param flag erweiterte Anzeige
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.cosmiccore.overdrive.effect").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.cosmiccore.overdrive.limit").withStyle(ChatFormatting.GRAY));
    }
}
