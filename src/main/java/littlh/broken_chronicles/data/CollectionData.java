package littlh.broken_chronicles.data;

import littlh.broken_chronicles.content.Localized;
import littlh.broken_chronicles.content.ResolvedContent;
import littlh.broken_chronicles.network.GenericEntryDto;
import littlh.broken_chronicles.network.S2CCollectionData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
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
        }
        root.put(content.id(), entry);
        player.getPersistentData().put(KEY, root);
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
                for (String key : pagesTag.getAllKeys()) pages.add(pagesTag.getString(key));
                generic.add(new GenericEntryDto(id, entry.getString("type"), entry.getString("title"), pages));
            }
        }
        return new S2CCollectionData(unlocked, generic);
    }
}
