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
            // 空白碎片纸/手记书：右键也打开阅读界面，显示空白提示，不收录。
            EntryType blankType = stack.is(ModItems.SHARD_BOOK.get()) ? EntryType.BOOK : EntryType.PAGE;
            return Optional.of(new ResolvedContent("blank:" + blankType.name().toLowerCase(), blankType,
                    Localized.of(""), List.of(Localized.of("")), List.of(), false));
        }

        if (stack.is(Items.PAPER)) {
            // 只有命名过的纸可以阅读/收录；普通纸不解析。
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
                return Optional.of(new ResolvedContent("vanilla:" + hash, EntryType.BOOK,
                        Localized.of(title), pages, List.of(), false));
            }
        }
        return Optional.empty();
    }

    private static ResolvedContent resolveInline(ItemStack stack, CompoundTag shard) {
        String typeString = shard.getString("type");
        EntryType type = EntryType.fromString(typeString);
        String title = shard.getString("title");

        // 注册表条目引用优先
        String entryReference = shard.getString("entry");
        if (!entryReference.isEmpty()) {
            Optional<ShardEntry> referenced = ShardEntries.get(entryReference);
            if (referenced.isPresent()) {
                return ResolvedContent.fromEntry(referenced.get());
            }
        }

        List<String> rawPages = new ArrayList<>();
        String text = shard.getString("text");
        if (!text.isEmpty()) {
            rawPages.add(text);
        } else if (shard.contains("pages", Tag.TAG_COMPOUND)) {
            CompoundTag pagesTag = shard.getCompound("pages");
            for (String key : pagesTag.getAllKeys()) rawPages.add(pagesTag.getString(key));
        }

        List<ResourceLocation> textures = new ArrayList<>();
        List<ResourceLocation> pageTextures = new ArrayList<>();
        if (shard.contains("textures", Tag.TAG_LIST)) {
            ListTag list = shard.getList("textures", Tag.TAG_STRING);
            if (type != EntryType.BOOK) {
                // page/tag：单个材质直接作为正文背景
                for (int i = 0; i < list.size(); i++) {
                    String t = list.getString(i);
                    if (t != null && !t.isEmpty()) {
                        try {
                            textures.add(ResourceLocation.parse(t));
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            // book：每页材质与页一一对应，空串表示用默认材质
            if (type == EntryType.BOOK) {
                for (int i = 0; i < rawPages.size(); i++) {
                    String t = i < list.size() ? list.getString(i) : "";
                    pageTextures.add(t == null || t.isEmpty() ? null : ResourceLocation.parse(t));
                }
            }
        }

        String key = typeString + "\u0000" + title + "\u0000" + String.join("\u0000", rawPages);
        String id = "inline:" + sha256(key).substring(0, 24);

        List<Localized> pages = rawPages.stream().map(Localized::of).toList();
        return new ResolvedContent(id, type, Localized.of(title), pages, textures, pageTextures, false);
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
