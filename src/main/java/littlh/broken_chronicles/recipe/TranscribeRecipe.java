package littlh.broken_chronicles.recipe;

import littlh.broken_chronicles.content.ShardContentHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 抄写：墨水 + 纸 + 一个写了字的载体（残页 / 残册 / 打了铭刻的物品 / 原版成书）。
 * <p>
 * 本质就是"复制一份"：一份墨水加一张纸换来第二个一模一样的载体。原件留在合成格里不消耗，
 * 所以玩家手上最终是 2 个（本体 + 抄写本）。抄出来的那一份连着原件的一切：附魔、署名、
 * 自定义名称、本模组写在物品上的文字与背景材质，全部照搬。
 * <p>
 * 纸放几张就抄几份（最多 8 份）；原件本身不可堆叠时（例如一把剑）一次只抄 1 份。
 */
public class TranscribeRecipe extends CustomRecipe {
    /** 一次最多抄几份（防止拿 64 张纸一次刷一大堆）。 */
    private static final int MAX_COPIES = 8;

    public TranscribeRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** 解析出的配方意图：抄哪个载体、一共出几份。 */
    private record Plan(ItemStack carrier, int copies) {
    }

    /** 配方网格是不是「1 份墨水 + N 张纸 + 1 个写了字的载体」。不是就返回 null。 */
    private static Plan plan(CraftingInput input) {
        ItemStack carrier = ItemStack.EMPTY;
        int papers = 0;
        boolean ink = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(Items.INK_SAC)) {
                if (ink) return null;
                ink = true;
            } else if (stack.is(Items.PAPER)) {
                papers++;
            } else if (isCarrier(stack)) {
                if (!carrier.isEmpty()) return null;
                carrier = stack;
            } else {
                // 混进了别的材料：这配方不管
                return null;
            }
        }
        if (!ink || carrier.isEmpty() || papers < 1) return null;
        int copies = carrier.getMaxStackSize() <= 1 ? 1 : Math.min(papers, MAX_COPIES);
        return new Plan(carrier, copies);
    }

    /** 写了字的载体：本模组的残页 / 残册、被失传墨水打上文字的物品、原版成书。 */
    private static boolean isCarrier(ItemStack stack) {
        if (stack.is(Items.WRITTEN_BOOK)) {
            return stack.has(DataComponents.WRITTEN_BOOK_CONTENT);
        }
        return ShardContentHelper.hasText(stack);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return plan(input) != null;
    }

    /** 产出 = 原件的一份完整副本（所有组件照搬），原件本身另外留在合成格里。 */
    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Plan plan = plan(input);
        if (plan == null) return ItemStack.EMPTY;
        return plan.carrier().copyWithCount(plan.copies());
    }

    /** 原件不消耗（跟原版"复制成书"一个道理）：玩家最终拿到的是 本体 + 抄写本 两个。 */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < remaining.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (isCarrier(stack)) {
                // 合成时每个格子会先扣掉 1 个，这里补回 1 个 = 原件一点没少（堆叠的载体也只当 1 个用）
                remaining.set(i, stack.copyWithCount(1));
                break;
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return littlh.broken_chronicles.ModRecipes.TRANSCRIBE.get();
    }
}