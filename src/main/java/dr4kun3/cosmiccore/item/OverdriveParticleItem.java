package dr4kun3.cosmiccore.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Overdrive-Partikel: wird über die GUI des Controllers im Reaktor installiert und dabei verbraucht.
 * Jede Stufe verdoppelt die Leistung und verdreifacht die Instabilität.
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

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /**
     * Tooltip: Wirkung und Grenze des Overdrive.
     *
     * @param stack der Stapel
     * @param ctx Kontext des Tooltips
     * @param tooltip Zeilen, an die angehängt wird
     * @param flag erweiterte Anzeige (F3+H)
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.cosmiccore.overdrive.effect").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.cosmiccore.overdrive.limit").withStyle(ChatFormatting.GRAY));
    }
}
