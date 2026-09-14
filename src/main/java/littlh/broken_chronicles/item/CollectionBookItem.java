package littlh.broken_chronicles.item;

import littlh.broken_chronicles.client.ClientUi;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CollectionBookItem extends Item {
    public CollectionBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 关掉「破碎编年史」本体时右键当作没发生：不打开收集册，也不给任何提示
        if (!enabled(level)) return InteractionResultHolder.pass(stack);
        if (level.isClientSide()) {
            ClientUi.openCollection();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** 「破碎编年史」本体开关。 */
    private static boolean enabled(Level level) {
        return level.isClientSide()
                ? littlh.broken_chronicles.client.ClientCollectionState.collectionBookEnabled()
                : littlh.broken_chronicles.ModFeatures.collectionBookEnabled();
    }
}
