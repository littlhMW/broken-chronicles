package littlh.broken_chronicles.content;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import littlh.broken_chronicles.ModItems;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * 把任意物品解析成可阅读内容：
 * 1. 带 broken_chronicles 数据的物品（page/book/tag，可能引用注册表条目）
 * 2. 原版成书：先匹配注册表 book 条目，否则生成通用条目
 */
public final class ShardContentResolver {
    private ShardContentResolver() {
    }

    public static Optional<ResolvedContent> resolve(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) return Optional.empty();

        CompoundTag shard = ShardContentHelper.getShard(stack);
        if (shard != null) {
            return Optional.of(resolveInline(stack, shard));
        }

        if (stack.is(ModItems.FRAGMENT_PAGE.get()) || stack.is(ModItems.SHARD_BOOK.get())) {
            // 空白破碎残片 / 破碎残册：右键也打开阅读界面，显示空白提示，不收录。
            EntryType blankType = stack.is(ModItems.SHARD_BOOK.get()) ? EntryType.BOOK : EntryType.PAGE;
            return Optional.of(new ResolvedContent("blank:" + blankType.name().toLowerCase(), blankType,
                    Localized.of(""), List.of(Localized.of("")), List.of(), false));
        }

        if (stack.is(Items.PAPER)) {
            // 命名过的纸：内容就是它的名字，读了会收录。
            net.minecraft.network.chat.Component paperName = stack.get(DataComponents.CUSTOM_NAME);
            if (paperName != null) {
                String nameStr = paperName.getString().trim();
                if (!nameStr.isEmpty()) {
                    String hash = sha256("paper\u0000" + nameStr).substring(0, 24);
                    ResourceLocation paperTexture =
                            ResourceLocation.fromNamespaceAndPath("broken_chronicles", "textures/gui/page/oldpaper.png");
                    return Optional.of(new ResolvedContent("named_paper:" + hash, EntryType.PAGE,
                            Localized.of(nameStr), List.of(Localized.of(nameStr)), List.of(paperTexture), false));
                }
            }
            // 纸本体（没改过名的原版纸）：不能阅读，也不收录。
            return Optional.empty();
        }

        if (stack.is(Items.WRITTEN_BOOK)) {
            WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (content != null) {
                for (ShardEntry entry : ShardEntries.books()) {
                    if (entry.bookMatch() != null && entry.bookMatch().matches(stack)) {
                        return Optional.of(ResolvedContent.fromEntry(entry));
                    }
                }
                String title = content.title().raw();
                String author = content.author();
                List<String> rawPages = new ArrayList<>();
                for (var page : content.pages()) {
                    rawPages.add(Component.Serializer.toJson(page.raw(), registries));
                }
                String joined = title + "\u0000" + author + "\u0000" + String.join("\u0000", rawPages);
                String hash = sha256(joined).substring(0, 24);
                List<Localized> pages = rawPages.stream().map(Localized::of).toList();
                // 原版成书直接用它自己的作者署名，收集册里就能按叙述者筛选
                Localized narrator = author == null || author.isBlank() ? null : Localized.of(author);
                return Optional.of(new ResolvedContent("vanilla:" + hash, EntryType.BOOK,
                        Localized.of(title), pages, List.of(), false, narrator));
            }
        }
        return Optional.empty();
    }

    private static ResolvedContent resolveInline(ItemStack stack, CompoundTag shard) {
        String typeString = shard.getString("type");
        EntryType type = EntryType.fromString(typeString);
        String title = shard.getString("title");
        List<String> rawPages = readRawPages(shard);
        List<String> rawTextures = readRawTextures(shard);

        // 注册表条目引用优先
        String entryReference = shard.getString("entry");
        if (!entryReference.isEmpty()) {
            Optional<ShardEntry> referenced = ShardEntries.get(entryReference);
            if (referenced.isPresent()) {
                return ResolvedContent.fromEntry(referenced.get());
            }
            // 客户端还没拿到这条条目（数据包只装在服务端，或同步关掉了）时，
            // 不能退化成 inline 哈希——那样两边算出来的 id 对不上，收录状态会显示错误。
            // 直接用条目 id 当内容 id，服务端与客户端就一致了。
            return fromRaw(entryReference, type, title, rawPages, rawTextures, shard.getString("description"),
                    shard.getString("author"));
        }

        // 键里不要带 author / description：老物品（没写这两项）算出来的 id 必须和以前一样，
        // 否则同一个物品会在收集册里变成两条。
        String key = typeString + "\u0000" + title + "\u0000" + String.join("\u0000", rawPages);
        String id = "inline:" + sha256(key).substring(0, 24);
        return fromRaw(id, type, title, rawPages, rawTextures, shard.getString("description"),
                shard.getString("author"));
    }

    /** 物品 shard 数据里的正文：列表形式的 pages（按顺序）优先，其次 text，最后是老格式的复合标签。 */
    private static List<String> readRawPages(CompoundTag shard) {
        List<String> rawPages = new ArrayList<>();
        String text = shard.getString("text");
        if (!text.isEmpty()) {
            rawPages.add(text);
        } else if (shard.contains("pages", Tag.TAG_LIST)) {
            ListTag list = shard.getList("pages", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) rawPages.add(list.getString(i));
        } else if (shard.contains("pages", Tag.TAG_COMPOUND)) {
            CompoundTag pagesTag = shard.getCompound("pages");
            for (String key : pagesTag.getAllKeys()) rawPages.add(pagesTag.getString(key));
        }
        return rawPages;
    }

    /** 物品 shard 数据里的材质（字符串形式）。 */
    private static List<String> readRawTextures(CompoundTag shard) {
        List<String> rawTextures = new ArrayList<>();
        if (shard.contains("textures", Tag.TAG_LIST)) {
            ListTag list = shard.getList("textures", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) rawTextures.add(list.getString(i));
        }
        return rawTextures;
    }

    /**
     * 用原始字符串拼出可阅读内容。id 传 null 时按 类型+标题+正文 算内联 id。
     * <p>
     * 物品上的文字、失传铭刻方块上的文字都走这里，保证同一段文字在任何地方算出的 id 都一样。
     */
    public static ResolvedContent fromRaw(EntryType type, String title, List<String> rawPages,
                                          List<String> rawTextures) {
        return fromRaw(null, type, title, rawPages, rawTextures);
    }

    /** 同上，另带描述与作者（失传铭刻方块上的文字用）。 */
    public static ResolvedContent fromRaw(EntryType type, String title, List<String> rawPages,
                                          List<String> rawTextures, String description, String author) {
        return fromRaw(null, type, title, rawPages, rawTextures, description, author);
    }

    /** 同上，但指定内容 id（例如引用了一条客户端还没有的注册表条目）。 */
    public static ResolvedContent fromRaw(String forcedId, EntryType type, String title,
                                          List<String> rawPages, List<String> rawTextures) {
        return fromRaw(forcedId, type, title, rawPages, rawTextures, null);
    }

    /** 同上，另带一段可选的描述（收集册悬浮提示用）。 */
    public static ResolvedContent fromRaw(String forcedId, EntryType type, String title,
                                          List<String> rawPages, List<String> rawTextures, String description) {
        return fromRaw(forcedId, type, title, rawPages, rawTextures, description, null);
    }

    /** 同上，另带作者（书写界面「作者」栏写的名字）。 */
    public static ResolvedContent fromRaw(String forcedId, EntryType type, String title,
                                          List<String> rawPages, List<String> rawTextures, String description,
                                          String author) {
        List<ResourceLocation> textures = new ArrayList<>();
        List<ResourceLocation> pageTextures = new ArrayList<>();
        List<String> textureList = rawTextures == null ? List.of() : rawTextures;
        if (type == EntryType.BOOK) {
            for (int i = 0; i < rawPages.size(); i++) {
                String t = i < textureList.size() ? textureList.get(i) : "";
                pageTextures.add(t == null || t.isEmpty() ? null : tryParse(t));
            }
        } else {
            for (String t : textureList) {
                ResourceLocation parsed = tryParse(t);
                if (parsed != null) textures.add(parsed);
            }
        }
        String id = forcedId;
        if (id == null || id.isEmpty()) {
            String key = type.id() + "\u0000" + title + "\u0000" + String.join("\u0000", rawPages);
            id = "inline:" + sha256(key).substring(0, 24);
        }
        List<Localized> pages = rawPages.stream().map(Localized::of).toList();
        return new ResolvedContent(id, type, Localized.of(title), pages, textures, pageTextures, false, false,
                description == null || description.isEmpty() ? null : Localized.of(description),
                author == null || author.isEmpty() ? null : Localized.of(author));
    }

    private static ResourceLocation tryParse(String texture) {
        if (texture == null || texture.isEmpty()) return null;
        try {
            return ResourceLocation.parse(texture);
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
