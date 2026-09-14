package littlh.broken_chronicles.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 数据包加载：data/&lt;namespace&gt;/shards/&lt;page|book|tag&gt;/&lt;id&gt;.json
 */
public final class ShardEntryLoader implements PreparableReloadListener {
    public static final ShardEntryLoader INSTANCE = new ShardEntryLoader();
    private static final Logger LOGGER = LoggerFactory.getLogger(ShardEntryLoader.class);
    private static final String ROOT = "shards";

    private ShardEntryLoader() {
    }

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                          ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                          Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> loadAll(resourceManager), backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(data -> {
                    ShardEntries.setData(data);
                    // 翻译覆盖层：config/broken_chronicles/lang/<语言>.json
                    // 放在 setData 之后，这样数据包条目、自带残片、其他 MOD 注册的条目都能被覆盖
                    ShardEntries.applyLangOverrides(LangOverrides.loadAll());
                    LootLibrary.injectAll();
                }, gameExecutor);
    }

    private Map<ResourceLocation, ShardEntry> loadAll(ResourceManager manager) {
        // 每次重载重新收集诊断信息，/broken_chronicles validate 只看当前状态
        EntryDiagnostics.clear();
        // 配置此时一定已加载，按开关补齐/移除自带残片
        BuiltinEntries.sync();
        Map<ResourceLocation, ShardEntry> entries = new HashMap<>();
        loadType(manager, "page", EntryType.PAGE, entries);
        loadType(manager, "book", EntryType.BOOK, entries);
        loadType(manager, "tag", EntryType.TAG, entries);
        // 外部文本文件夹：config/broken_chronicles/entries/，覆盖数据包条目
        Map<ResourceLocation, ShardEntry> external = ExternalEntries.loadAll();
        for (ResourceLocation id : external.keySet()) {
            if (entries.containsKey(id)) {
                EntryDiagnostics.warn("外部条目", id.toString(),
                        "与数据包里的同 id 条目重名，这份 config/broken_chronicles/entries/ 里的覆盖了数据包那份");
            }
        }
        entries.putAll(external);
        // 集中的战利品注入配置（数据包 shards_loot + config/broken_chronicles/loot.json）
        LootLibrary.setExtra(LootConfig.load(manager));
        // 自带残片：自动放进原版会生成纸/书/墨囊的箱子表
        if (BuiltinEntries.lootEnabled()) {
            java.util.Set<ResourceLocation> tables = LootLibrary.scanChestTables(manager);
            LootLibrary.setBuiltinLoot(tables, BuiltinEntries.lootWeights(), BuiltinEntries.lootChance());
            LOGGER.info("[破碎编年史] 自带残片可出现的箱子战利品表：{} 个", tables.size());
        } else {
            LootLibrary.setBuiltinLoot(java.util.Set.of(), java.util.Map.of(), 0.0F);
        }
        LOGGER.info("[破碎编年史] 已加载 {} 条数据包 / 外部条目：{}", entries.size(), entries.keySet());
        return entries;
    }

    private void loadType(ResourceManager manager, String typeDir, EntryType type, Map<ResourceLocation, ShardEntry> out) {
        String prefix = ROOT + "/" + typeDir;
        for (Map.Entry<ResourceLocation, Resource> resourceEntry
                : manager.listResources(prefix, rl -> rl.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = resourceEntry.getKey();
            String path = file.getPath();
            String id = path.substring(prefix.length() + 1, path.length() - ".json".length());
            ResourceLocation entryId = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), id);
            try (Reader reader = resourceEntry.getValue().openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                ShardEntry entry = ShardEntry.parse(entryId, type, element);
                if (entry == null) {
                    LOGGER.error("[破碎编年史] 无效的条目文件: {}", file);
                } else {
                    out.put(entryId, entry);
                }
            } catch (Exception e) {
                LOGGER.error("[破碎编年史] 加载条目失败: {}", file, e);
            }
        }
    }
}
