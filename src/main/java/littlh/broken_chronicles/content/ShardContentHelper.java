package littlh.broken_chronicles.content;

import littlh.broken_chronicles.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * 物品上的自定义数据读写。数据存在 minecraft:custom_data 的 "broken_chronicles" 子标签里，
 * 这样战利品表 / 命令 / 其他模组都可以直接读写。
 */
public final class ShardContentHelper {
    private static final String KEY = "broken_chronicles";

    private ShardContentHelper() {
    }

    public static CompoundTag getRoot(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /** 返回 "broken_chronicles" 子标签；没有则返回 null。 */
    public static CompoundTag getShard(ItemStack stack) {
        CompoundTag root = getRoot(stack);
        return root.contains(KEY) ? root.getCompound(KEY) : null;
    }

    public static void setShard(ItemStack stack, CompoundTag data) {
        CompoundTag root = getRoot(stack);
        root.put(KEY, data);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        // 被写了字的物品带金色附魔光效
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    /** 生成玩家书写出的碎片纸 / 手记书。 */
    public static ItemStack make(String type, String title, List<String> pages, List<String> textures) {
        ItemStack stack = "book".equals(type)
                ? new ItemStack(ModItems.SHARD_BOOK.get())
                : new ItemStack(ModItems.FRAGMENT_PAGE.get());
        CompoundTag data = new CompoundTag();
        data.putString("type", type);
        if (title != null && !title.isEmpty()) data.putString("title", title);
        if ("book".equals(type)) {
            CompoundTag pagesTag = new CompoundTag();
            for (int i = 0; i < pages.size(); i++) {
                pagesTag.putString(String.valueOf(i), pages.get(i));
            }
            data.put("pages", pagesTag);
        } else {
            data.putString("text", String.join("\n", pages));
        }
        putTextures(data, textures);
        setShard(stack, data);
        return stack;
    }

    /** 把书写界面选中的材质写入数据：book 每页一个，page/tag 一个；空串表示用默认材质。 */
    private static void putTextures(CompoundTag data, List<String> textures) {
        if (textures == null || textures.isEmpty()) return;
        ListTag texturesTag = new ListTag();
        for (String t : textures) {
            texturesTag.add(net.minecraft.nbt.StringTag.valueOf(t == null ? "" : t));
        }
        data.put("textures", texturesTag);
    }

    /** 生成条目对应的可阅读物品：page→碎片纸、book→手记书、tag→绑定物品。 */
    public static ItemStack itemFor(ShardEntry entry) {
        ItemStack stack;
        switch (entry.type()) {
            case PAGE -> stack = new ItemStack(ModItems.FRAGMENT_PAGE.get());
            case BOOK -> stack = new ItemStack(ModItems.SHARD_BOOK.get());
            case TAG -> {
                if (entry.item() == null || !BuiltInRegistries.ITEM.containsKey(entry.item())) return ItemStack.EMPTY;
                stack = new ItemStack(BuiltInRegistries.ITEM.get(entry.item()));
            }
            default -> {
                return ItemStack.EMPTY;
            }
        }
        applyEntry(stack, entry);
        return stack;
    }

    /** 给物品实例打上「引用条目」的文字（tag 自然生成/创造模式条目物品）。 */
    public static void applyEntry(ItemStack stack, ShardEntry entry) {
        CompoundTag data = new CompoundTag();
        data.putString("type", entry.type().id());
        data.putString("entry", entry.id().toString());
        setShard(stack, data);
    }

    /** 给某个物品实例打上 tag 文字（多页内容合并为一段，阅读时滚动展示）。 */
    public static void applyTag(ItemStack stack, String title, List<String> pages, List<String> textures) {
        CompoundTag data = new CompoundTag();
        data.putString("type", "tag");
        if (title != null && !title.isEmpty()) data.putString("title", title);
        data.putString("text", String.join("\n", pages));
        putTextures(data, textures);
        setShard(stack, data);
    }

    /** 这些物品不能被墨水打 tag（它们有自己的书写/阅读流程）。 */
    public static boolean isSpecial(ItemStack stack) {
        return stack.is(Items.PAPER)
                || stack.is(Items.WRITABLE_BOOK)
                || stack.is(ModItems.FRAGMENT_PAGE.get())
                || stack.is(ModItems.SHARD_BOOK.get())
                || stack.is(ModItems.COLLECTION_BOOK.get())
                || stack.is(ModItems.FRAGMENT_INK.get());
    }
}
