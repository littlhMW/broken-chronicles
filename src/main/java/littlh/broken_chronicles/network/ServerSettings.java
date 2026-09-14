package littlh.broken_chronicles.network;

import io.netty.buffer.ByteBuf;
import littlh.broken_chronicles.ModConfig;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端的"功能开关"，随收录数据一起同步给客户端。
 * <p>
 * 客户端自己那份 config 文件在多人游戏里管不到服务端，所以这些值以服务端下发的为准；
 * 玩家在书写界面的「设置」里改动时通过 C2SConfigEdit 请求服务端改（需要 OP）。
 * <p>
 * 键名和 config/broken_chronicles-common.toml 里的一致（见 {@link #KEYS}）。
 * 用 Map 存是为了后面再加开关时不用改一大串构造参数：加一项只要在 {@link #DEFAULTS} 与
 * {@link #current()} 里各补一行。
 */
public final class ServerSettings {

    /** 每项的默认值：服务端没下发这一项、或者客户端根本没收到同步时用它。 */
    private static final Map<String, Boolean> DEFAULTS;

    /** 全部开关的键名，顺序固定（也就是网络编解码的顺序）。 */
    public static final List<String> KEYS;

    static {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put("writingEnabled", false);
        defaults.put("authorExportEnabled", false);
        defaults.put("autoCollectOnRead", true);
        defaults.put("showUnknownEntries", false);
        defaults.put("showCollectionProgress", false);
        defaults.put("allowSurvivalInscriptionMimic", false);
        defaults.put("enableBuiltinEntries", true);
        defaults.put("allowCraftingModItems", true);
        defaults.put("enforceStoryChain", true);
        defaults.put("enforceGates", true);
        defaults.put("syncEntryContentToClients", true);
        defaults.put("readingEnabled", true);
        defaults.put("readOnRightClick", true);
        defaults.put("readWhileHolding", true);
        defaults.put("readInContainerScreens", true);
        defaults.put("readTaggedItems", true);
        defaults.put("readVanillaBooks", true);
        defaults.put("readInscriptions", true);
        defaults.put("readingOnly", false);
        defaults.put("collectionBookEnabled", true);
        defaults.put("fragmentPageEnabled", true);
        defaults.put("shardBookEnabled", true);
        defaults.put("fragmentInkEnabled", true);
        defaults.put("lostInscriptionEnabled", true);
        defaults.put("transcribeEnabled", true);
        DEFAULTS = Collections.unmodifiableMap(defaults);
        KEYS = List.copyOf(defaults.keySet());
    }

    /** 一份"全是默认值"的设置。 */
    public static final ServerSettings DEFAULT = new ServerSettings(DEFAULTS);

    private final Map<String, Boolean> values;

    public ServerSettings(Map<String, Boolean> values) {
        Map<String, Boolean> merged = new LinkedHashMap<>();
        Map<String, Boolean> source = values == null ? Map.of() : values;
        // 认识的键按顺序补齐（缺的用默认值），不认识的键也留着：服务端可能比客户端新
        for (String key : KEYS) {
            merged.put(key, source.getOrDefault(key, DEFAULTS.get(key)));
        }
        for (Map.Entry<String, Boolean> entry : source.entrySet()) {
            merged.putIfAbsent(entry.getKey(), entry.getValue());
        }
        this.values = Collections.unmodifiableMap(merged);
    }

    /** 所有开关（键名 -> 值），顺序和 {@link #KEYS} 一致（可能多出客户端不认识的键）。 */
    public Map<String, Boolean> values() {
        return values;
    }

    /** 读一项开关；不认识的键按 true 处理（和后加的开关默认值一致）。 */
    public boolean get(String key) {
        return values.getOrDefault(key == null ? "" : key, true);
    }

    /** 字段不少了，干脆手写编解码：先写项数，再写「键名 + 值」。 */
    public static final StreamCodec<ByteBuf, ServerSettings> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                Map<String, Boolean> map = value.values();
                ByteBufCodecs.VAR_INT.encode(buf, map.size());
                for (Map.Entry<String, Boolean> entry : map.entrySet()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, entry.getKey());
                    ByteBufCodecs.BOOL.encode(buf, entry.getValue());
                }
            },
            buf -> {
                int size = ByteBufCodecs.VAR_INT.decode(buf);
                Map<String, Boolean> map = new LinkedHashMap<>();
                for (int i = 0; i < size; i++) {
                    map.put(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.BOOL.decode(buf));
                }
                return new ServerSettings(map);
            });

    /**
     * 按配置键改一项，返回改过的那份（书写界面点开关时先用它做即时反馈）。
     * 不认识的键原样返回——客户端乱写配置不会污染服务端的设置。
     */
    public ServerSettings with(String key, boolean value) {
        if (key == null || !DEFAULTS.containsKey(key)) return this;
        Map<String, Boolean> copy = new LinkedHashMap<>(values);
        copy.put(key, value);
        return new ServerSettings(copy);
    }

    /** 服务端当前配置。 */
    public static ServerSettings current() {
        try {
            return new ServerSettings(new LinkedHashMap<>(Map.ofEntries(
                    Map.entry("writingEnabled", ModConfig.WRITING_ENABLED.get()),
                    Map.entry("authorExportEnabled", ModConfig.AUTHOR_EXPORT_ENABLED.get()),
                    Map.entry("autoCollectOnRead", ModConfig.AUTO_COLLECT_ON_READ.get()),
                    Map.entry("showUnknownEntries", ModConfig.SHOW_UNKNOWN.get()),
                    Map.entry("showCollectionProgress", ModConfig.SHOW_PROGRESS.get()),
                    Map.entry("allowSurvivalInscriptionMimic", ModConfig.ALLOW_SURVIVAL_INSCRIPTION_MIMIC.get()),
                    Map.entry("enableBuiltinEntries", ModConfig.ENABLE_BUILTIN_ENTRIES.get()),
                    Map.entry("allowCraftingModItems", ModConfig.ALLOW_CRAFTING_MOD_ITEMS.get()),
                    Map.entry("enforceStoryChain", ModConfig.ENFORCE_STORY_CHAIN.get()),
                    Map.entry("enforceGates", ModConfig.ENFORCE_GATES.get()),
                    Map.entry("syncEntryContentToClients", ModConfig.SYNC_ENTRY_CONTENT.get()),
                    Map.entry("readingEnabled", ModConfig.READING_ENABLED.get()),
                    Map.entry("readOnRightClick", ModConfig.READ_ON_RIGHT_CLICK.get()),
                    Map.entry("readWhileHolding", ModConfig.READ_WHILE_HOLDING.get()),
                    Map.entry("readInContainerScreens", ModConfig.READ_IN_CONTAINER_SCREENS.get()),
                    Map.entry("readTaggedItems", ModConfig.READ_TAGGED_ITEMS.get()),
                    Map.entry("readVanillaBooks", ModConfig.READ_VANILLA_BOOKS.get()),
                    Map.entry("readInscriptions", ModConfig.READ_INSCRIPTIONS.get()),
                    Map.entry("readingOnly", ModConfig.READING_ONLY.get()),
                    Map.entry("collectionBookEnabled", ModConfig.COLLECTION_BOOK_ENABLED.get()),
                    Map.entry("fragmentPageEnabled", ModConfig.FRAGMENT_PAGE_ENABLED.get()),
                    Map.entry("shardBookEnabled", ModConfig.SHARD_BOOK_ENABLED.get()),
                    Map.entry("fragmentInkEnabled", ModConfig.FRAGMENT_INK_ENABLED.get()),
                    Map.entry("lostInscriptionEnabled", ModConfig.LOST_INSCRIPTION_ENABLED.get()),
                    Map.entry("transcribeEnabled", ModConfig.TRANSCRIBE_ENABLED.get()))));
        } catch (Exception e) {
            // 配置还没加载：别让同步流程崩掉，按默认值来
            return DEFAULT;
        }
    }

    // ==================== 书写 / 收录 ====================

    public boolean writingEnabled() {
        return get("writingEnabled");
    }

    public boolean authorExportEnabled() {
        return get("authorExportEnabled");
    }

    public boolean autoCollect() {
        return get("autoCollectOnRead");
    }

    public boolean showUnknown() {
        return get("showUnknownEntries");
    }

    public boolean showProgress() {
        return get("showCollectionProgress");
    }

    public boolean allowSurvivalInscriptionMimic() {
        return get("allowSurvivalInscriptionMimic");
    }

    /** 自带攻略残片（我们写的那 24 张）开关。 */
    public boolean enableBuiltinEntries() {
        return get("enableBuiltinEntries");
    }

    public boolean allowCraftingModItems() {
        return get("allowCraftingModItems");
    }

    public boolean enforceStoryChain() {
        return get("enforceStoryChain");
    }

    public boolean enforceGates() {
        return get("enforceGates");
    }

    public boolean syncEntryContent() {
        return get("syncEntryContentToClients");
    }

    // ==================== 阅读 ====================

    public boolean readingEnabled() {
        return get("readingEnabled");
    }

    public boolean readOnRightClick() {
        return readingEnabled() && get("readOnRightClick");
    }

    public boolean readWhileHolding() {
        return readingEnabled() && get("readWhileHolding");
    }

    public boolean readInContainerScreens() {
        return readingEnabled() && get("readInContainerScreens");
    }

    public boolean readTaggedItems() {
        return readingEnabled() && get("readTaggedItems");
    }

    public boolean readVanillaBooks() {
        return readingEnabled() && get("readVanillaBooks");
    }

    public boolean readInscriptions() {
        return readingEnabled() && get("readInscriptions");
    }

    // ==================== 物品功能 ====================

    /** 「只保留阅读」：开启时下面六件物品一律当作关闭（和 ModFeatures#readingOnly 一致）。 */
    public boolean readingOnly() {
        return get("readingOnly");
    }

    /** 这一件物品的功能还开不开：总开关关掉时全部当作没开。 */
    private boolean itemEnabled(String key) {
        return !readingOnly() && get(key);
    }

    public boolean collectionBookEnabled() {
        return itemEnabled("collectionBookEnabled");
    }

    public boolean fragmentPageEnabled() {
        return itemEnabled("fragmentPageEnabled");
    }

    public boolean shardBookEnabled() {
        return itemEnabled("shardBookEnabled");
    }

    public boolean fragmentInkEnabled() {
        return itemEnabled("fragmentInkEnabled");
    }

    public boolean lostInscriptionEnabled() {
        return itemEnabled("lostInscriptionEnabled");
    }

    public boolean transcribeEnabled() {
        return itemEnabled("transcribeEnabled");
    }
}