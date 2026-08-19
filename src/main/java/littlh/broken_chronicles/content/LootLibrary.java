package littlh.broken_chronicles.content;

import littlh.broken_chronicles.ModEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 书库：把声明了 loot_tables 的条目注入到对应战利品表。
 * <p>
 * 1.21 里战利品表在 ReloadableServerRegistries 阶段加载，早于本模组的条目
 * reload listener，所以 LootTableLoadEvent 触发时条目可能还没就绪。
 * 这里先记录战利品表对象，等条目加载完成后再统一注入。
 */
public final class LootLibrary {
    private static final Logger LOGGER = LoggerFactory.getLogger(LootLibrary.class);

    /** 战利品表名 -> 当前会话的表对象（每次 reload 会被覆盖）。 */
    private static final Map<ResourceLocation, LootTable> TABLES = new HashMap<>();

    /** 已注入的 (表名, 条目id)，防止同一 reload 内重复加池。 */
    private static final Set<String> INJECTED = new HashSet<>();

    /** 集中配置：表名 -> (条目id -> 权重)，来自数据包 shards_loot 与 config loot.json。 */
    private static Map<ResourceLocation, Map<String, Integer>> EXTRA = new HashMap<>();

    private LootLibrary() {
    }

    /** 战利品表加载时记录，不直接注入（此时条目可能还没加载）。 */
    public static void record(ResourceLocation name, LootTable table) {
        TABLES.put(name, table);
    }

    /** 设置集中的战利品注入配置。 */
    public static void setExtra(Map<ResourceLocation, Map<String, Integer>> extra) {
        EXTRA = extra == null ? new HashMap<>() : extra;
    }

    /** 条目加载完成后调用：把所有条目注入到记录过的战利品表。 */
    public static void injectAll() {
        INJECTED.clear();
        for (Map.Entry<ResourceLocation, LootTable> entry : TABLES.entrySet()) {
            inject(entry.getKey(), entry.getValue());
        }
    }

    private static void inject(ResourceLocation tableName, LootTable table) {
        List<ShardEntry> matched = new ArrayList<>();
        Map<String, Integer> extraWeights = EXTRA.get(tableName);
        for (ShardEntry candidate : ShardEntries.all()) {
            boolean own = candidate.lootTables().contains(tableName);
            boolean extra = extraWeights != null && extraWeights.containsKey(candidate.id().toString());
            if (!own && !extra) continue;
            String key = tableName + "|" + candidate.id();
            if (!INJECTED.add(key)) continue;
            matched.add(candidate);
        }
        if (matched.isEmpty()) return;

        LootPool.Builder pool = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F));
        for (ShardEntry entry : matched) {
            ItemStack stack = ShardContentHelper.itemFor(entry);
            if (stack.isEmpty()) continue;
            int weight = extraWeights != null
                    ? extraWeights.getOrDefault(entry.id().toString(), entry.lootWeight())
                    : entry.lootWeight();
            LootItem.Builder<?> lootItem = LootItem.lootTableItem(stack.getItem())
                    .setWeight(Math.max(1, weight))
                    .apply(SetComponentsFunction.setComponent(DataComponents.CUSTOM_DATA,
                            CustomData.of(ShardContentHelper.getRoot(stack))));
            // 掉落出来的带字物品同样带金色附魔光效
            lootItem.apply(SetComponentsFunction.setComponent(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
            pool.add(lootItem);
        }
        table.addPool(pool.build());
        LOGGER.info("[破碎编年史] 注入战利品表 {}：{} 个条目", tableName, matched.size());
    }
}
