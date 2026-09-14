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
    /** 服务端下发的条目内容（只有客户端会填），本地已有同名条目时以本地为准。 */
    private static final Map<ResourceLocation, ShardEntry> REMOTE_ENTRIES = new LinkedHashMap<>();
    /**
     * all() 的缓存。掉落 / 生成判定里每条掉落都会问一次"有哪些条目"，
     * 每次重新收集 + 排序太浪费，所以缓存一份；三张表任何一次改动都清掉它。
     */
    private static volatile List<ShardEntry> SORTED_CACHE;

    private ShardEntries() {
    }

    /** 模组代码注册条目，数据包重载时不会被清除。 */
    public static void register(ShardEntry entry) {
        CODE_ENTRIES.put(entry.id(), entry);
        SORTED_CACHE = null;
    }

    /** 用构建器注册条目（供其他 MOD 作为前置时使用）。 */
    public static ShardEntry register(ShardEntry.Builder builder) {
        ShardEntry entry = builder.build();
        register(entry);
        return entry;
    }

    /** 注销一个代码注册的条目（配置开关用）。 */
    public static void unregister(ResourceLocation id) {
        CODE_ENTRIES.remove(id);
        SORTED_CACHE = null;
    }

    /** 数据包加载结果。 */
    public static void setData(Map<ResourceLocation, ShardEntry> entries) {
        DATA_ENTRIES.clear();
        DATA_ENTRIES.putAll(entries);
        SORTED_CACHE = null;
    }

    /** 客户端：设置服务端下发的条目内容。 */
    public static void setRemote(Map<ResourceLocation, ShardEntry> entries) {
        REMOTE_ENTRIES.clear();
        if (entries != null) REMOTE_ENTRIES.putAll(entries);
        SORTED_CACHE = null;
    }

    /** 客户端：单条追加/覆盖（预览用），不影响已下发的其它条目。 */
    public static void putRemote(ShardEntry entry) {
        REMOTE_ENTRIES.put(entry.id(), entry);
        SORTED_CACHE = null;
    }

    /** 服务端下发的条目 id 集合。 */
    public static java.util.Set<ResourceLocation> remoteIds() {
        return java.util.Set.copyOf(REMOTE_ENTRIES.keySet());
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
        if (entry == null) entry = REMOTE_ENTRIES.get(location);
        return Optional.ofNullable(entry);
    }

    /** 全部条目，按 order 升序（同 order 按 id 字典序）。返回的是只读快照，不要改。 */
    public static List<ShardEntry> all() {
        List<ShardEntry> cached = SORTED_CACHE;
        if (cached != null) return cached;
        List<ShardEntry> list = new ArrayList<>(DATA_ENTRIES.values());
        for (ShardEntry entry : CODE_ENTRIES.values()) {
            if (!DATA_ENTRIES.containsKey(entry.id())) list.add(entry);
        }
        for (ShardEntry entry : REMOTE_ENTRIES.values()) {
            if (!DATA_ENTRIES.containsKey(entry.id()) && !CODE_ENTRIES.containsKey(entry.id())) list.add(entry);
        }
        list.sort((a, b) -> {
            int cmp = Integer.compare(a.order(), b.order());
            return cmp != 0 ? cmp : a.id().toString().compareTo(b.id().toString());
        });
        List<ShardEntry> snapshot = List.copyOf(list);
        SORTED_CACHE = snapshot;
        return snapshot;
    }

    /**
     * 把 lang 覆盖层（config/broken_chronicles/lang/&lt;语言&gt;.json）并进条目文本。
     * <p>
     * 数据包条目和模组代码注册的条目（本模组自带残片、其他 MOD 注册的）都会被覆盖。
     * 覆盖文件里写了不存在的条目 id 会记一条警告——拼错 id 是最常见的问题。
     */
    public static void applyLangOverrides(Map<String, Map<String, LangOverrides.Override>> overrides) {
        if (overrides == null || overrides.isEmpty()) return;
        int applied = 0;
        for (Map.Entry<String, Map<String, LangOverrides.Override>> language : overrides.entrySet()) {
            String currentLanguage = language.getKey();
            for (Map.Entry<String, LangOverrides.Override> item : language.getValue().entrySet()) {
                String id = item.getKey();
                ResourceLocation key = ResourceLocation.tryParse(id);
                ShardEntry entry = key == null ? null
                        : (DATA_ENTRIES.containsKey(key) ? DATA_ENTRIES.get(key) : CODE_ENTRIES.get(key));
                if (entry == null) {
                    EntryDiagnostics.warn("lang 覆盖", currentLanguage + " / " + id,
                            "找不到这个条目 id（拼错了，或者这条条目被 conditions 停用了）");
                    continue;
                }
                ShardEntry merged = LangOverrides.apply(entry, Map.of(currentLanguage, item.getValue()));
                if (merged != entry) {
                    if (DATA_ENTRIES.containsKey(key)) {
                        DATA_ENTRIES.put(key, merged);
                    } else {
                        CODE_ENTRIES.put(key, merged);
                    }
                    applied++;
                }
            }
        }
        if (applied > 0) SORTED_CACHE = null;
        EntryDiagnostics.info("lang 覆盖：" + overrides.size() + " 种语言，命中 " + applied + " 条条目");
    }

    public static List<ShardEntry> books() {
        return all().stream().filter(entry -> entry.type() == EntryType.BOOK).toList();
    }

    public static boolean anyReveal() {
        return all().stream().anyMatch(ShardEntry::reveal);
    }
}
