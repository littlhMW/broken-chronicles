package littlh.broken_chronicles.content;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 书库：把声明了 loot_tables 的条目注入到对应战利品表。
 * <p>
 * 1.21 里战利品表在 ReloadableServerRegistries 阶段加载，早于本模组的条目
 * reload listener，所以 LootTableLoadEvent 触发时条目可能还没就绪。
 * 这里先记录战利品表对象，等条目加载完成后再统一注入。
 * <p>
 * 两种来源：
 * <ul>
 *   <li>条目自带的 loot_tables 字段、数据包 shards_loot、config/broken_chronicles/loot.json
 *       —— 每格必掷（老行为），权重只看 loot_weight；</li>
 *   <li>模组自带残片，自动塞进原版含纸/书/墨囊的箱子表 —— 受 builtinLootChance 控制概率。</li>
 * </ul>
 */
public final class LootLibrary {
    private static final Logger LOGGER = LoggerFactory.getLogger(LootLibrary.class);

    /** 判定"生成纸/书/墨囊"的箱子表时查找的物品。 */
    private static final List<String> TARGET_ITEMS = List.of(
            "minecraft:paper", "minecraft:book", "minecraft:ink_sac");

    /** 战利品表名 -> 当前会话的表对象（每次 reload 会被覆盖）。 */
    private static final Map<ResourceLocation, LootTable> TABLES = new HashMap<>();

    /** 已注入的 (表名, 条目id)，防止同一 reload 内重复加池。 */
    private static final Set<String> INJECTED = new HashSet<>();

    /** 集中配置：表名 -> (条目id -> 权重)，来自数据包 shards_loot 与 config loot.json。 */
    private static Map<ResourceLocation, Map<String, Integer>> EXTRA = new HashMap<>();

    /** 自带残片：表名 -> 条目权重（每次 reload 重新计算）。 */
    private static Map<ResourceLocation, Map<String, Integer>> BUILTIN = new HashMap<>();

    /** 自带残片的掉落概率（0~1）。 */
    private static float BUILTIN_CHANCE = 0.3F;

    /** 上次注入的结果，给 /broken_chronicles loot 用。 */
    private static final Map<ResourceLocation, String> REPORT = new java.util.LinkedHashMap<>();

    private LootLibrary() {
    }

    /** 战利品表加载时记录，不直接注入（此时条目可能还没加载）。 */
    /** 已经加载过的战利品表名（/broken_chronicles validate 用来检查 loot_tables 写得对不对）。 */
    public static java.util.Set<ResourceLocation> knownTables() {
        return java.util.Collections.unmodifiableSet(TABLES.keySet());
    }

    public static void record(ResourceLocation name, LootTable table) {
        TABLES.put(name, table);
    }

    /** 设置集中的战利品注入配置。 */
    public static void setExtra(Map<ResourceLocation, Map<String, Integer>> extra) {
        EXTRA = extra == null ? new HashMap<>() : extra;
    }

    /** 设置自带残片的注入目标与概率。 */
    public static void setBuiltinLoot(Set<ResourceLocation> tables, Map<String, Integer> weights, float chance) {
        Map<ResourceLocation, Map<String, Integer>> map = new HashMap<>();
        if (tables != null && weights != null && !weights.isEmpty()) {
            for (ResourceLocation table : tables) map.put(table, weights);
        }
        BUILTIN = map;
        BUILTIN_CHANCE = Math.max(0.0F, Math.min(1.0F, chance));
    }

    /**
     * 扫描数据包与模组里的箱子战利品表，找出会生成纸 / 书 / 墨囊的表。
     * 只扫 loot_table/chests/ 下的表，避免方块掉落（如书架）也被注入。
     */
    public static Set<ResourceLocation> scanChestTables(ResourceManager manager) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        String root = "loot_table";
        for (Map.Entry<ResourceLocation, Resource> resourceEntry
                : manager.listResources(root, rl -> rl.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = resourceEntry.getKey();
            String path = file.getPath();
            String sub = path.substring(root.length() + 1, path.length() - ".json".length());
            if (!sub.startsWith("chests/")) continue;
            try (Reader reader = resourceEntry.getValue().openAsReader()) {
                StringBuilder text = new StringBuilder();
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) text.append(buffer, 0, read);
                String content = text.toString();
                for (String item : TARGET_ITEMS) {
                    if (content.contains("\"" + item + "\"")) {
                        out.add(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), sub));
                        break;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[破碎编年史] 读取战利品表失败: {}", file, e);
            }
        }
        return out;
    }

    /** 条目加载完成后调用：把所有条目注入到记录过的战利品表。 */
    public static void injectAll() {
        INJECTED.clear();
        REPORT.clear();
        for (Map.Entry<ResourceLocation, LootTable> entry : TABLES.entrySet()) {
            inject(entry.getKey(), entry.getValue());
        }
    }

    private static void inject(ResourceLocation tableName, LootTable table) {
        List<ShardEntry> declared = new ArrayList<>();
        List<ShardEntry> builtin = new ArrayList<>();
        Map<String, Integer> extraWeights = EXTRA.get(tableName);
        Map<String, Integer> builtinWeights = BUILTIN.get(tableName);
        for (ShardEntry candidate : ShardEntries.all()) {
            // 载体物品被关掉的条目不再进战利品表（关掉残片 / 残册后箱子里就不会摸出它们）
            if (!itemEnabled(candidate)) continue;
            String id = candidate.id().toString();
            boolean own = candidate.lootTables().contains(tableName);
            boolean extra = extraWeights != null && extraWeights.containsKey(id);
            boolean built = builtinWeights != null && builtinWeights.containsKey(id);
            if (!own && !extra && !built) continue;
            String key = tableName + "|" + id;
            if (!INJECTED.add(key)) continue;
            if (own || extra) {
                declared.add(candidate);
            } else {
                builtin.add(candidate);
            }
        }
        if (declared.isEmpty() && builtin.isEmpty()) return;

        if (!declared.isEmpty()) {
            addPool(table, declared, extraWeights, -1.0F);
        }
        if (!builtin.isEmpty()) {
            addPool(table, builtin, builtinWeights, BUILTIN_CHANCE);
        }
        REPORT.put(tableName, declared.size() + " 个声明条目 + " + builtin.size() + " 个自带残片（概率 "
                + String.format(java.util.Locale.ROOT, "%.0f%%", BUILTIN_CHANCE * 100) + "）");
        LOGGER.info("[破碎编年史] 注入战利品表 {}：{} 个条目（其中自带残片 {}）",
                tableName, declared.size() + builtin.size(), builtin.size());
    }

    /** 注入情况报告（/broken_chronicles loot）。 */
    public static List<String> report() {
        List<String> lines = new ArrayList<>();
        lines.add("已扫描战利品表 " + TABLES.size() + " 张，其中被注入 " + REPORT.size() + " 张。");
        REPORT.forEach((table, detail) -> lines.add("  " + table + " ← " + detail));
        if (REPORT.isEmpty()) {
            lines.add("没有任何条目进入战利品表。可选方式：");
            lines.add("  1) 条目 JSON 里写 \"loot_tables\": [\"minecraft:chests/simple_dungeon\"]（必出）");
            lines.add("  2) 数据包 data/<ns>/shards_loot/<name>.json 集中声明");
            lines.add("  3) config/broken_chronicles/loot.json 配置");
            lines.add("  4) 其他模组用全局战利品修饰符 broken_chronicles:add_entry");
        }
        return lines;
    }

    /** 这条内容的载体物品还开着没有。残片 / 残册可以被单独关掉，其余类型不受影响。 */
    private static boolean itemEnabled(ShardEntry entry) {
        return switch (entry.type()) {
            case PAGE -> littlh.broken_chronicles.ModFeatures.fragmentPageEnabled();
            case BOOK -> littlh.broken_chronicles.ModFeatures.shardBookEnabled();
            default -> true;
        };
    }

    /** chance 小于 0 表示每格必掷（数据包/配置声明的条目）。 */
    private static void addPool(LootTable table, List<ShardEntry> entries,
                                Map<String, Integer> weights, float chance) {
        LootPool.Builder pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F));
        if (chance >= 0.0F) {
            // 池级概率：这次掷骰没过就整池什么都不掉
            pool.when(LootItemRandomChanceCondition.randomChance(chance));
        }
        int added = 0;
        for (ShardEntry entry : entries) {
            ItemStack stack = ShardContentHelper.itemFor(entry);
            if (stack.isEmpty()) continue;
            int weight = weights != null
                    ? weights.getOrDefault(entry.id().toString(), entry.lootWeight())
                    : entry.lootWeight();
            LootItem.Builder<?> lootItem = LootItem.lootTableItem(stack.getItem())
                    .setWeight(Math.max(1, weight));
            lootItem.apply(SetComponentsFunction.setComponent(DataComponents.CUSTOM_DATA,
                    CustomData.of(ShardContentHelper.getRoot(stack))));
            // 掉落出来的带字物品同样带金色附魔光效
            lootItem.apply(SetComponentsFunction.setComponent(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
            // 故事链条：前置没收录时不掉这条（判定开箱的玩家）
            if (!entry.requires().isEmpty()) {
                lootItem.when(() -> new littlh.broken_chronicles.content.loot.HasEntryCondition(entry.requires()));
            }
            pool.add(lootItem);
            added++;
        }
        if (added == 0) return;
        table.addPool(pool.build());
    }
}
