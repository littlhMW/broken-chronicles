package littlh.broken_chronicles.content;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 条目注册表：数据包条目 + 模组代码条目。
 */
public final class ShardEntries {
    private static final Map<ResourceLocation, ShardEntry> CODE_ENTRIES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ShardEntry> DATA_ENTRIES = new LinkedHashMap<>();

    private ShardEntries() {
    }

    /** 模组代码注册条目，数据包重载时不会被清除。 */
    public static void register(ShardEntry entry) {
        CODE_ENTRIES.put(entry.id(), entry);
    }

    /** 用构建器注册条目（供其他 MOD 作为前置时使用）。 */
    public static ShardEntry register(ShardEntry.Builder builder) {
        ShardEntry entry = builder.build();
        register(entry);
        return entry;
    }

    /** 数据包加载结果。 */
    public static void setData(Map<ResourceLocation, ShardEntry> entries) {
        DATA_ENTRIES.clear();
        DATA_ENTRIES.putAll(entries);
    }

    public static Optional<ShardEntry> get(String id) {
        if (id == null || id.isEmpty()) return Optional.empty();
        ResourceLocation location;
        try {
            location = ResourceLocation.parse(id);
        } catch (Exception e) {
            return Optional.empty();
        }
        ShardEntry entry = DATA_ENTRIES.get(location);
        if (entry == null) entry = CODE_ENTRIES.get(location);
        return Optional.ofNullable(entry);
    }

    /** 全部条目，按 order 升序（同 order 按 id 字典序）。 */
    public static List<ShardEntry> all() {
        List<ShardEntry> list = new ArrayList<>(DATA_ENTRIES.values());
        for (ShardEntry entry : CODE_ENTRIES.values()) {
            if (!DATA_ENTRIES.containsKey(entry.id())) list.add(entry);
        }
        list.sort((a, b) -> {
            int cmp = Integer.compare(a.order(), b.order());
            return cmp != 0 ? cmp : a.id().toString().compareTo(b.id().toString());
        });
        return list;
    }

    public static List<ShardEntry> books() {
        return all().stream().filter(entry -> entry.type() == EntryType.BOOK).toList();
    }

    public static boolean anyReveal() {
        return all().stream().anyMatch(ShardEntry::reveal);
    }
}
