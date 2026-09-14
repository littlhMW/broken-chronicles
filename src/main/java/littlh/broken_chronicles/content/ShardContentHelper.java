package littlh.broken_chronicles.content;

import littlh.broken_chronicles.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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

    /** 生成玩家书写出的碎片纸 / 手记书（没有描述）。 */
    public static ItemStack make(String type, String title, List<String> pages, List<String> textures) {
        return make(type, title, "", pages, textures);
    }

    /** 生成玩家书写出的碎片纸 / 手记书。 */
    public static ItemStack make(String type, String title, String description, List<String> pages,
                                 List<String> textures) {
        return make(type, title, description, null, pages, textures);
    }

    /** 生成玩家书写出的碎片纸 / 手记书（带作者）。 */
    public static ItemStack make(String type, String title, String description, String author,
                                 List<String> pages, List<String> textures) {
        ItemStack stack = "book".equals(type)
                ? new ItemStack(ModItems.SHARD_BOOK.get())
                : new ItemStack(ModItems.FRAGMENT_PAGE.get());
        CompoundTag data = new CompoundTag();
        data.putString("type", type);
        if (title != null && !title.isEmpty()) data.putString("title", title);
        if (description != null && !description.isBlank()) data.putString("description", description.trim());
        if (author != null && !author.isBlank()) data.putString("author", author.trim());
        if ("book".equals(type) || "page".equals(type)) {
            // 用列表存页：复合标签的键是无序的，多页会串页。
            // 残页也能写好几页（每一页都是独立的一张纸，阅读时翻页看），所以和残册一样按列表存。
            ListTag pagesTag = new ListTag();
            for (String page : pages) {
                pagesTag.add(net.minecraft.nbt.StringTag.valueOf(page == null ? "" : page));
            }
            data.put("pages", pagesTag);
        } else {
            // 铭刻 / 标签挂在物品上，只显示一页：多页合并成一段，阅读时滚动看
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

    /** 给某个物品实例打上 tag 文字（没有描述）。 */
    public static void applyTag(ItemStack stack, String title, List<String> pages, List<String> textures) {
        applyTag(stack, title, "", pages, textures);
    }

    /** 给某个物品实例打上 tag 文字（多页内容合并为一段，阅读时滚动展示）。 */
    public static void applyTag(ItemStack stack, String title, String description, List<String> pages,
                                List<String> textures) {
        applyTag(stack, title, description, null, pages, textures);
    }

    /** 给某个物品实例打上 tag 文字（带作者）。 */
    public static void applyTag(ItemStack stack, String title, String description, String author,
                                List<String> pages, List<String> textures) {
        CompoundTag data = new CompoundTag();
        data.putString("type", "tag");
        if (title != null && !title.isEmpty()) data.putString("title", title);
        if (description != null && !description.isBlank()) data.putString("description", description.trim());
        if (author != null && !author.isBlank()) data.putString("author", author.trim());
        data.putString("text", String.join("\n", pages));
        putTextures(data, textures);
        setShard(stack, data);
    }

    /** 当前界面语言。服务端没有"语言"这个概念，返回空串（解析时退回 en_us、再退回第一个键）。 */
    public static String displayLanguage() {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return "";
        }
        return net.minecraft.client.Minecraft.getInstance().options.languageCode;
    }

    /** 物品引用的注册表条目（"entry" 标签指向的那条），没有则空。 */
    public static java.util.Optional<ShardEntry> referencedEntry(ItemStack stack) {
        CompoundTag shard = getShard(stack);
        if (shard == null) return java.util.Optional.empty();
        String entry = shard.getString("entry");
        return entry.isEmpty() ? java.util.Optional.empty() : ShardEntries.get(entry);
    }

    /**
     * 这个物品该显示的标题：先看物品上写的 title，再看它引用的条目的标题。
     * 残片/残册用它当物品名（就像铁砧命名），读取界面和 tooltip 也用它。没有就空串。
     */
    public static String displayTitle(ItemStack stack) {
        String written = titleOf(stack);
        if (!written.isEmpty()) return written;
        return referencedEntry(stack)
                .map(entry -> entry.title() == null ? "" : entry.title().resolve(displayLanguage()))
                .orElse("");
    }

    /** 这个物品该显示的描述：先看物品上写的 description，再看它引用的条目的描述。没有就空串。 */
    public static String displayDescription(ItemStack stack) {
        String written = descriptionOf(stack);
        if (!written.isEmpty()) return written;
        return referencedEntry(stack)
                .map(entry -> entry.extras().descriptionText(displayLanguage()))
                .orElse("");
    }

    /** 物品上写的标题（没有就返回空串）。 */
    public static String titleOf(ItemStack stack) {
        CompoundTag shard = getShard(stack);
        return shard == null ? "" : shard.getString("title");
    }

    /** 物品上写的描述（没有就返回空串）。 */
    public static String descriptionOf(ItemStack stack) {
        CompoundTag shard = getShard(stack);
        return shard == null ? "" : shard.getString("description");
    }

    /** 这个物品该显示的作者：先看物品上写的 author，再看它引用的条目的叙述者。没有就空串。 */
    public static String displayAuthor(ItemStack stack) {
        String written = authorOf(stack);
        if (!written.isEmpty()) return written;
        return referencedEntry(stack)
                .map(entry -> entry.narratorFor(displayLanguage()))
                .orElse("");
    }

    /** 物品上写的作者（没有就返回空串）。 */
    public static String authorOf(ItemStack stack) {
        CompoundTag shard = getShard(stack);
        return shard == null ? "" : shard.getString("author");
    }

    /** 物品上有没有一段可以阅读的文字（残页 / 残册 / 被打上文字的物品）。 */
    public static boolean hasText(ItemStack stack) {
        CompoundTag shard = getShard(stack);
        if (shard == null) return false;
        if (!shard.getString("text").isEmpty()) return true;
        if (shard.contains("pages", Tag.TAG_COMPOUND) || shard.contains("pages", Tag.TAG_LIST)) return true;
        String entry = shard.getString("entry");
        return !entry.isEmpty();
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
