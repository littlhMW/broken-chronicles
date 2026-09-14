package littlh.broken_chronicles.item;

import littlh.broken_chronicles.client.ClientUi;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 失传墨水：主手拿墨水、副手持载体，右键打开书写界面。
 * 载体可以是残片/残册，也可以是任何普通物品（写成 tag 文字）。
 */
public class FragmentInkItem extends Item {
    public FragmentInkItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 关掉「失传墨水」时右键当作没发生：不打开书写界面，也不给任何提示
        if (!enabled(level)) return InteractionResultHolder.pass(stack);
        if (level.isClientSide()) {
            ClientUi.openInkWriting(player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** 「失传墨水」开关。 */
    private static boolean enabled(Level level) {
        return level.isClientSide()
                ? littlh.broken_chronicles.client.ClientCollectionState.fragmentInkEnabled()
                : littlh.broken_chronicles.ModFeatures.fragmentInkEnabled();
    }

    /** 物品说明（官方口吻的两行描述，见语言文件）。 */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.broken_chronicles.fragment_ink.desc1")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("item.broken_chronicles.fragment_ink.desc2")
                .withStyle(ChatFormatting.GRAY));
    }
}