package littlh.broken_chronicles.item;

import littlh.broken_chronicles.client.ClientUi;
import littlh.broken_chronicles.content.ShardContentHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ShardBookItem extends Item {
    public ShardBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            ClientUi.openReading(player.getItemInHand(hand));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    /**
     * 物品名 = 这段文字的标题，就像用铁砧命名一样。
     * 物品上写了 title 就用它；引用注册表条目时用条目的标题（按当前语言解析）。
     * 两者都没有（空白残册）时退回默认名称。
     */
    @Override
    public Component getName(ItemStack stack) {
        String title = ShardContentHelper.displayTitle(stack);
        return title.isEmpty() ? super.getName(stack) : Component.literal(title);
    }
}