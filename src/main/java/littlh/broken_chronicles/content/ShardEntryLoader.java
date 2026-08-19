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
                .thenAcceptAsync(data -> { ShardEntries.setData(data); LootLibrary.injectAll(); }, gameExecutor);
    }

    private Map<ResourceLocation, ShardEntry> loadAll(ResourceManager manager) {
        Map<ResourceLocation, ShardEntry> entries = new HashMap<>();
        loadType(manager, "page", EntryType.PAGE, entries);
        loadType(manager, "book", EntryType.BOOK, entries);
        loadType(manager, "tag", EntryType.TAG, entries);
        // 外部文本文件夹：config/broken_chronicles/entries/，覆盖数据包条目
        entries.putAll(ExternalEntries.loadAll());
        // 集中的战利品注入配置（数据包 shards_loot + config/broken_chronicles/loot.json）
        LootLibrary.setExtra(LootConfig.load(manager));
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
