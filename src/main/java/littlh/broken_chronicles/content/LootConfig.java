package littlh.broken_chronicles.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 集中的战利品注入配置，让整合包作者不修改条目 JSON 也能把条目塞进战利品表。
 * <p>
 * 数据包：data/&lt;ns&gt;/shards_loot/&lt;任意&gt;.json
 * <pre>
 * { "table": "minecraft:chests/simple_dungeon",
 *   "entries": [ { "id": "broken_chronicles:undead_memo", "weight": 5 }, "your_mod:apple_note" ] }
 * </pre>
 * 外部配置：config/broken_chronicles/loot.json（数组形式，元素同上），优先级高于数据包。
 */
public final class LootConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(LootConfig.class);
    private static final String ROOT = "shards_loot";

    private LootConfig() {
    }

    /** 返回 表名 -> (条目id -> 权重)。 */
    public static Map<ResourceLocation, Map<String, Integer>> load(ResourceManager manager) {
        Map<ResourceLocation, Map<String, Integer>> out = new HashMap<>();
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> resourceEntry
                : manager.listResources(ROOT, rl -> rl.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = resourceEntry.getValue().openAsReader()) {
                parseInto(reader, out);
            } catch (Exception e) {
                LOGGER.error("[破碎编年史] 加载战利品配置失败: {}", resourceEntry.getKey(), e);
            }
        }
        // 外部配置覆盖数据包
        Path cfg = FMLPaths.CONFIGDIR.get().resolve("broken_chronicles").resolve("loot.json");
        if (Files.isRegularFile(cfg)) {
            try (Reader reader = Files.newBufferedReader(cfg, StandardCharsets.UTF_8)) {
                parseInto(reader, out);
            } catch (Exception e) {
                LOGGER.error("[破碎编年史] 加载外部战利品配置失败: {}", cfg, e);
            }
        }
        return out;
    }

    private static void parseInto(Reader reader, Map<ResourceLocation, Map<String, Integer>> out) {
        JsonElement root = JsonParser.parseReader(reader);
        if (root.isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray()) parseOne(element, out);
        } else {
            parseOne(root, out);
        }
    }

    private static void parseOne(JsonElement element, Map<ResourceLocation, Map<String, Integer>> out) {
        if (element == null || !element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        if (!object.has("table") || !object.get("table").isJsonPrimitive()) return;
        ResourceLocation table = ResourceLocation.tryParse(object.get("table").getAsString());
        if (table == null) return;
        JsonElement entries = object.get("entries");
        if (entries == null || !entries.isJsonArray()) return;
        Map<String, Integer> map = out.computeIfAbsent(table, k -> new HashMap<>());
        for (JsonElement item : entries.getAsJsonArray()) {
            if (item.isJsonPrimitive()) {
                map.put(item.getAsString(), 1);
            } else if (item.isJsonObject()) {
                JsonObject entryObject = item.getAsJsonObject();
                if (!entryObject.has("id")) continue;
                int weight = entryObject.has("weight") ? entryObject.get("weight").getAsInt() : 1;
                map.put(entryObject.get("id").getAsString(), weight);
            }
        }
    }
}
