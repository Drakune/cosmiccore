package dr4kun3.cosmiccore.item;

import dr4kun3.cosmiccore.client.GuideBook;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Kosmisches Handbuch: erklärt Aufbau und Betrieb des Kerngenerators. Öffnet sich nur clientseitig. */
public class GuideBookItem extends Item {
    /**
     * Erzeugt das Handbuch-Item.
     *
     * @param props Item-Eigenschaften aus der Registrierung
     */
    public GuideBookItem(Properties props) {
        super(props);
    }

    /**
     * Rechtsklick öffnet das Kosmische Handbuch (nur auf dem Client).
     *
     * @param level Welt, in der benutzt wird
     * @param player Spieler, der das Buch benutzt
     * @param hand Hand mit dem Buch
     * @return Erfolg mit dem unveränderten Stapel
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            GuideBook.open(); // wird auf dem dedizierten Server nie erreicht
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
