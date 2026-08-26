package littlh.broken_chronicles.data;

import littlh.broken_chronicles.advancements.CollectedEntryTrigger;
import littlh.broken_chronicles.content.Localized;
import littlh.broken_chronicles.content.ResolvedContent;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.network.GenericEntryDto;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import littlh.broken_chronicles.network.S2CCollectionData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 玩家收录数据，存进玩家持久 NBT（死亡、重进、物品销毁后都保留）。
 * 结构：broken_chronicles_shards -> &lt;条目id&gt; -> { type, title, pages }（注册表条目只存空标记）
 */
public final class CollectionData {
    private static final String KEY = "broken_chronicles_shards";

    private CollectionData() {
    }

    public static void unlock(ServerPlayer player, ResolvedContent content) {
        CompoundTag root = player.getPersistentData().getCompound(KEY);
        CompoundTag entry = root.getCompound(content.id());
        boolean generic = content.id().startsWith("vanilla:") || content.id().startsWith("inline:")
                || content.id().startsWith("named_paper:");
        if (generic) {
            entry.putString("type", content.type().id());
            entry.putString("title", content.title().resolve(""));
            CompoundTag pages = new CompoundTag();
            List<Localized> list = content.pages();
            for (int i = 0; i < list.size(); i++) {
                pages.putString(String.valueOf(i), list.get(i).resolve(""));
            }
            entry.put("pages", pages);
            // 材质：book 存每页材质（空串=默认），page/tag 存正文材质；旧数据没有该字段则为空
            ListTag textures = new ListTag();
            if (content.type() == EntryType.BOOK) {
                for (ResourceLocation t : content.pageTextures()) {
                    textures.add(StringTag.valueOf(t == null ? "" : t.toString()));
                }
            } else {
                for (ResourceLocation t : content.textures()) {
                    textures.add(StringTag.valueOf(t == null ? "" : t.toString()));
                }
            }
            entry.put("textures", textures);
        }
        root.put(content.id(), entry);
        player.getPersistentData().put(KEY, root);

        // 成就：默认点亮条目属于系统行为，不触发成就；其余按本次类型与主动收录集合触发
        boolean autoUnlocked = ShardEntries.get(content.id()).map(ShardEntry::startUnlocked).orElse(false);
        if (!autoUnlocked) {
            Set<String> categories = collectedCategories(player, autoUnlockedIds());
            CollectedEntryTrigger.INSTANCE.trigger(player, category(content.id(), entry), categories);
        }
    }

    /** 默认点亮条目 id 集合：不参与成就结算。 */
    public static Set<String> autoUnlockedIds() {
        Set<String> result = new HashSet<>();
        for (ShardEntry entry : ShardEntries.all()) {
            if (entry.startUnlocked()) result.add(entry.id().toString());
        }
        return result;
    }

    /** 条目所属成就类型：vanilla/named_paper 看 id 前缀，其余看条目自身类型。 */
    private static String category(String id, CompoundTag entry) {
        if (id.startsWith("vanilla:")) return "vanilla";
        if (id.startsWith("named_paper:")) return "named_paper";
        if (entry.contains("type")) return entry.getString("type");
        return ShardEntries.get(id).map(e -> e.type().id()).orElse("page");
    }

    /** 玩家当前已收录的类型集合（不含默认点亮条目）。 */
    public static Set<String> collectedCategories(ServerPlayer player) {
        return collectedCategories(player, autoUnlockedIds());
    }

    /** 玩家当前已收录的类型集合，可排除指定条目 id。 */
    public static Set<String> collectedCategories(ServerPlayer player, Set<String> excludeIds) {
        Set<String> result = new HashSet<>();
        CompoundTag root = player.getPersistentData().getCompound(KEY);
        for (String id : root.getAllKeys()) {
            if (excludeIds.contains(id)) continue;
            result.add(category(id, root.getCompound(id)));
        }
        return result;
    }

    public static S2CCollectionData snapshot(ServerPlayer player) {
        CompoundTag root = player.getPersistentData().getCompound(KEY);
        Set<String> unlocked = new HashSet<>(root.getAllKeys());
        List<GenericEntryDto> generic = new ArrayList<>();
        for (String id : root.getAllKeys()) {
            CompoundTag entry = root.getCompound(id);
            if (entry.contains("type")) {
                List<String> pages = new ArrayList<>();
                CompoundTag pagesTag = entry.getCompound("pages");
                List<String> pageKeys = new ArrayList<>(pagesTag.getAllKeys());
                pageKeys.sort(Comparator.comparingInt(k -> {
                    try {
                        return Integer.parseInt(k);
                    } catch (NumberFormatException e) {
                        return Integer.MAX_VALUE;
                    }
                }));
                for (String key : pageKeys) pages.add(pagesTag.getString(key));
                List<String> textures = new ArrayList<>();
                if (entry.contains("textures", net.minecraft.nbt.Tag.TAG_LIST)) {
                    ListTag texturesTag = entry.getList("textures", net.minecraft.nbt.Tag.TAG_STRING);
                    for (int i = 0; i < texturesTag.size(); i++) textures.add(texturesTag.getString(i));
                }
                generic.add(new GenericEntryDto(id, entry.getString("type"), entry.getString("title"), pages, textures));
            }
        }
        return new S2CCollectionData(unlocked, generic);
    }
}
