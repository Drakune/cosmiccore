package dr4kun3.cosmiccore.item;

import dr4kun3.cosmiccore.client.GuideBook;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Kosmisches Handbuch: erklaert Aufbau und Betrieb des Kerngenerators. Oeffnet sich nur clientseitig. */
public class GuideBookItem extends Item {
    public GuideBookItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            GuideBook.open(); // wird auf dem dedizierten Server nie erreicht
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
