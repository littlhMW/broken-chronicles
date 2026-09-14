package littlh.broken_chronicles.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import littlh.broken_chronicles.data.CollectionData;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 运行时门槛：比 requires（只认"收录过哪些条目"）更宽的条件，用来决定
 * "这条内容现在能不能刷出来"。和 requires 的分工：
 * <ul>
 *   <li>{@code requires} 管"存不存在"：前置没收齐，收集册里连 ??? 都不显示；</li>
 *   <li>{@code gates} 管"刷不刷得出来"：条目照常显示成 ???，但战利品表 / 生物掉落 / 自然生成不给。</li>
 * </ul>
 * JSON（全部满足才放行）：
 * <pre>
 * "gates": [
 *   { "type": "entry",       "id": "你的数据包:前置条目" },
 *   { "type": "advancement", "id": "minecraft:story/enter_the_end" },
 *   { "type": "dimension",   "id": "minecraft:the_end" },
 *   { "type": "item",        "id": "minecraft:ender_pearl", "count": 1 },
 *   { "type": "scoreboard",  "objective": "quest_stage", "min": 3 },
 *   { "type": "any",         "values": [ { "type": "dimension", "id": "minecraft:the_nether" } ] },
 *   { "type": "not",         "value": { "type": "dimension", "id": "minecraft:overworld" } }
 * ]
 * </pre>
 * 判定用的是"此刻相关的那个玩家"：开箱的人、钓上来的玩家、打死生物的人、交易的人。
 * 没有人（例如方块自己掉的战利品）时一律判失败，也就是不给。
 * <p>
 * 客户端只拿得到维度、背包、收录状态：进度与计分板在客户端一律按"满足"处理，
 * 所以门槛不会影响收集册的显示，只影响服务端的生成。作者调试时可以用配置 enforceGates 一键关掉。
 */
public interface EntryGate {

    /** @param player 相关玩家，可能为 null（没人在场）；返回 false 表示不放行 */
    boolean test(Player player);

    /** 中文简述，/broken_chronicles graph 与线索界面用。 */
    String describe();

    /** 客户端收录状态查询的注册点（接口里的字段不能改，所以放这个壳里）。 */
    final class ClientHooks {
        /** 未注册时按"已收录"处理，免得把显示拦掉。 */
        public static volatile Predicate<String> COLLECTED = id -> true;

        private ClientHooks() {
        }
    }

    /** 全部门槛都过（空列表 = 放行）。 */
    static boolean allPass(List<EntryGate> gates, Player player) {
        if (gates == null || gates.isEmpty()) return true;
        if (!littlh.broken_chronicles.ModConfig.ENFORCE_GATES.get()) return true;
        for (EntryGate gate : gates) {
            if (gate == null) continue;
            boolean pass;
            try {
                pass = gate.test(player);
            } catch (Exception e) {
                pass = false;
            }
            if (!pass) return false;
        }
        return true;
    }

    /** 某个玩家是否收录了某条目（服务端看存档，客户端看同步下来的集合）。 */
    static boolean collected(Player player, ResourceLocation id) {
        if (id == null) return false;
        if (player instanceof ServerPlayer serverPlayer) {
            return CollectionData.collectedIds(serverPlayer).contains(id.toString());
        }
        return ClientHooks.COLLECTED.test(id.toString());
    }

    /** 解析 gates 数组；写错的条件会记一条错误并忽略（不影响条目其余部分）。 */
    static List<EntryGate> parse(JsonElement element, String source, ResourceLocation entryId) {
        List<EntryGate> out = new ArrayList<>();
        if (element == null || element.isJsonNull()) return out;
        String id = entryId == null ? "" : entryId.toString();
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                EntryGate gate = parseOne(item, source, id);
                if (gate != null) out.add(gate);
            }
        } else {
            EntryGate gate = parseOne(element, source, id);
            if (gate != null) out.add(gate);
        }
        return out;
    }

    private static EntryGate parseOne(JsonElement element, String source, String entryId) {
        if (element == null || !element.isJsonObject()) {
            EntryDiagnostics.error(source, entryId, "gates 里的每一项必须是对象");
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        String type = string(object, "type");
        if (type == null || type.isEmpty()) {
            EntryDiagnostics.error(source, entryId, "gates 里有一项没写 type");
            return null;
        }
        switch (type) {
            case "entry": {
                ResourceLocation target = id(object, "id", source, entryId, "entry");
                return target == null ? null : new Collected(target);
            }
            case "advancement": {
                ResourceLocation target = id(object, "id", source, entryId, "advancement");
                return target == null ? null : new Advancement(target);
            }
            case "dimension": {
                ResourceLocation target = id(object, "id", source, entryId, "dimension");
                return target == null ? null : new Dimension(target);
            }
            case "item": {
                ResourceLocation target = id(object, "id", source, entryId, "item");
                int count = object.has("count") && object.get("count").isJsonPrimitive()
                        ? Math.max(1, object.get("count").getAsInt()) : 1;
                return target == null ? null : new HasItem(target, count);
            }
            case "scoreboard": {
                String objective = string(object, "objective");
                if (objective == null || objective.isEmpty()) {
                    EntryDiagnostics.error(source, entryId, "scoreboard 门槛缺 objective");
                    return null;
                }
                Integer min = number(object, "min");
                Integer max = number(object, "max");
                if (min == null && max == null) {
                    EntryDiagnostics.warn(source, entryId,
                            "scoreboard 门槛既没写 min 也没写 max，永远成立");
                }
                return new Score(objective, min, max);
            }
            case "any", "or": {
                List<EntryGate> values = parse(object.get("values"), source,
                        ResourceLocation.tryParse(entryId));
                if (values.isEmpty()) {
                    EntryDiagnostics.warn(source, entryId, "any 门槛里没有条件，永远不成立");
                    return new Any(List.of());
                }
                return new Any(values);
            }
            case "all", "and": {
                List<EntryGate> values = parse(object.get("values"), source,
                        ResourceLocation.tryParse(entryId));
                if (values.isEmpty()) {
                    EntryDiagnostics.warn(source, entryId, "all 门槛里没有条件，永远成立");
                    return new Always();
                }
                return new All(values);
            }
            case "not": {
                EntryGate value = parseOne(object.get("value"), source, entryId);
                return value == null ? null : new Not(value);
            }
            default: {
                java.util.function.Function<JsonObject, EntryGate> custom = CUSTOM_TYPES.get(type);
                if (custom != null) {
                    try {
                        EntryGate gate = custom.apply(object);
                        if (gate != null) return gate;
                    } catch (Exception e) {
                        EntryDiagnostics.error(source, entryId, "自定义门槛 " + type + " 解析失败：" + e);
                    }
                    return null;
                }
                EntryDiagnostics.error(source, entryId, "不认识的 gates 类型：" + type
                        + "（可用：entry / advancement / dimension / item / scoreboard / any / all / not）");
                return null;
            }
        }
    }

    private static ResourceLocation id(JsonObject object, String key, String source, String entryId, String type) {
        String raw = string(object, key);
        if (raw == null || raw.isEmpty()) {
            EntryDiagnostics.error(source, entryId, type + " 门槛缺 " + key);
            return null;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed == null) {
            EntryDiagnostics.error(source, entryId, type + " 门槛的 " + key + " 不是合法 id：" + raw);
        }
        return parsed;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static Integer number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : null;
    }

    /** 已收录某条目。 */
    record Collected(ResourceLocation id) implements EntryGate {
        @Override
        public boolean test(Player player) {
            return player != null && EntryGate.collected(player, id);
        }

        @Override
        public String describe() {
            return "已收录 " + id;
        }
    }

    /** 已取得某进度。客户端拿不到进度，一律放行。 */
    record Advancement(ResourceLocation id) implements EntryGate {
        @Override
        public boolean test(Player player) {
            if (!(player instanceof ServerPlayer serverPlayer)) return true;
            AdvancementHolder holder = serverPlayer.server.getAdvancements().get(id);
            if (holder == null) return false;
            AdvancementProgress progress = serverPlayer.getAdvancements().getOrStartProgress(holder);
            return progress != null && progress.isDone();
        }

        @Override
        public String describe() {
            return "已获得进度 " + id;
        }
    }

    /** 玩家此刻在某维度。 */
    record Dimension(ResourceLocation id) implements EntryGate {
        @Override
        public boolean test(Player player) {
            if (player == null) return false;
            ResourceKey<Level> dimension = player.level().dimension();
            return dimension != null && dimension.location().equals(id);
        }

        @Override
        public String describe() {
            return "处于维度 " + id;
        }
    }

    /** 背包里有 enough 个某物品。 */
    record HasItem(ResourceLocation id, int count) implements EntryGate {
        @Override
        public boolean test(Player player) {
            if (player == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(id);
            final int[] found = {0};
            player.getInventory().contains(stack -> {
                if (!stack.is(item)) return false;
                found[0] += stack.getCount();
                return found[0] >= count;
            });
            return found[0] >= count;
        }

        @Override
        public String describe() {
            return "背包里有 " + id + " x" + count;
        }
    }

    /** 计分板数值在区间内。客户端拿不到，一律放行。 */
    record Score(String objective, Integer min, Integer max) implements EntryGate {
        @Override
        public boolean test(Player player) {
            if (!(player instanceof ServerPlayer serverPlayer)) return true;
            Objective target = serverPlayer.getScoreboard().getObjective(objective);
            if (target == null) return false;
            ReadOnlyScoreInfo info = serverPlayer.getScoreboard().getPlayerScoreInfo(serverPlayer, target);
            int value = info == null ? 0 : info.value();
            if (min != null && value < min) return false;
            return max == null || value <= max;
        }

        @Override
        public String describe() {
            StringBuilder sb = new StringBuilder("计分板 ").append(objective);
            if (min != null && max != null) sb.append(" 在 ").append(min).append("~").append(max);
            else if (min != null) sb.append(" ≥ ").append(min);
            else if (max != null) sb.append(" ≤ ").append(max);
            return sb.toString();
        }
    }

    /** 任意一项满足。 */
    record Any(List<EntryGate> values) implements EntryGate {
        @Override
        public boolean test(Player player) {
            for (EntryGate value : values) {
                if (value != null && value.test(player)) return true;
            }
            return false;
        }

        @Override
        public String describe() {
            return "满足任意一项：" + describeAll(values);
        }
    }

    /** 全部满足。 */
    record All(List<EntryGate> values) implements EntryGate {
        @Override
        public boolean test(Player player) {
            for (EntryGate value : values) {
                if (value != null && !value.test(player)) return false;
            }
            return true;
        }

        @Override
        public String describe() {
            return "全部满足：" + describeAll(values);
        }
    }

    /** 取反。 */
    record Not(EntryGate value) implements EntryGate {
        @Override
        public boolean test(Player player) {
            return !value.test(player);
        }

        @Override
        public String describe() {
            return "不满足：" + value.describe();
        }
    }

    /** 恒成立（写空的 all 用）。 */
    record Always() implements EntryGate {
        @Override
        public boolean test(Player player) {
            return true;
        }

        @Override
        public String describe() {
            return "恒成立";
        }
    }

    private static String describeAll(List<EntryGate> values) {
        StringBuilder sb = new StringBuilder();
        for (EntryGate value : values) {
            if (value == null) continue;
            if (sb.length() > 0) sb.append("；");
            sb.append(value.describe());
        }
        return sb.toString();
    }

    /** 供其他 MOD 注册自定义门槛类型（名字里的命名空间会被去掉）。 */
    static void registerType(String name, java.util.function.Function<JsonObject, EntryGate> factory) {
        if (name == null || factory == null) return;
        String bare = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        if (bare.isEmpty()) return;
        CUSTOM_TYPES.put(bare, factory);
    }

    java.util.Map<String, java.util.function.Function<JsonObject, EntryGate>> CUSTOM_TYPES =
            new java.util.concurrent.ConcurrentHashMap<>();
}