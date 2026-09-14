package littlh.broken_chronicles.item;

import littlh.broken_chronicles.ModBlocks;
import littlh.broken_chronicles.block.entity.LostInscriptionBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/** 失传铭刻（物品形态）：可以放到地上，也可以直接用失传墨水在上面写字。 */
public class LostInscriptionBlockItem extends BlockItem {
    public LostInscriptionBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("block.broken_chronicles.lost_inscription.desc1")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("block.broken_chronicles.lost_inscription.desc2")
                .withStyle(ChatFormatting.GRAY));
        BlockState mimic = mimicOf(stack, context);
        if (mimic != null) {
            // 方块形态的外观变了以后，物品图标还是铭刻本体（没法画出拟态），至少在这儿说一声
            tooltipComponents.add(Component.translatable("block.broken_chronicles.lost_inscription.mimic",
                    mimic.getBlock().getName()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** 这一块铭刻拟态成了什么方块；没拟态、或者拿不到注册表就返回 null。 */
    @Nullable
    private static BlockState mimicOf(ItemStack stack, Item.TooltipContext context) {
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) return null;
        HolderLookup.Provider registries = context.registries();
        if (registries == null) return null;
        BlockState mimic = LostInscriptionBlockEntity.readMimic(registries, data.copyTag());
        return mimic == null || mimic.isAir() ? null : mimic;
    }
}
