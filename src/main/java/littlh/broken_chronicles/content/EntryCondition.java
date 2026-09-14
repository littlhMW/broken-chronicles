package littlh.broken_chronicles.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 条目加载条件：条件不满足的条目不会被注册（不出现在编年史、创造栏、战利品表）。
 * <p>
 * JSON 写法（conditions 数组，全部满足才注册）：
 * <pre>
 * "conditions": [
 *   { "type": "mod_loaded", "mod": "create" },
 *   { "type": "item_exists", "item": "minecraft:diamond" },
 *   { "type": "not", "value": { "type": "mod_loaded", "mod": "sodium" } },
 *   { "type": "any", "values": [ { "type": "mod_loaded", "mod": "a" }, { "type": "mod_loaded", "mod": "b" } ] }
 * ]
 * </pre>
 * 支持：mod_loaded / mod_not_loaded、item_exists / item_missing、all（and）/ any（or）/ not，
 * 以及游戏自带的条件（minecraft:random_chance 之类的概率条件不适用，会被忽略并报错）。
 */
public interface EntryCondition {

    boolean test();

    /** 日志里用的说明文字。 */
    String describe();

    /**
     * 供其他 MOD 注册自定义条件类型（也可以走 {@code BrokenChroniclesApi.registerCondition}）。
     * 名字里的命名空间会被去掉：注册 "your_mod:flag" 和 "flag" 等价。
     */
    static void registerType(String name, Function<Context, EntryCondition> factory) {
        if (name == null || factory == null) return;
        String bare = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        if (bare.isEmpty()) return;
        CUSTOM_TYPES.put(bare, factory);
    }

    /** 自定义条件已经注册的名字（不含内置类型）。 */
    static java.util.Set<String> customTypes() {
        return java.util.Collections.unmodifiableSet(CUSTOM_TYPES.keySet());
    }

    /** 自定义条件工厂拿到的上下文：原始 JSON + 出错时用来定位的来源信息。 */
    record Context(JsonObject json, String source, String entryId) {

        /** 取一个字符串字段（没有就是 null）。 */
        public String string(String key) {
            JsonElement value = json.get(key);
            return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
        }

        /** 取一个布尔字段（没有就用默认值）。 */
        public boolean bool(String key, boolean fallback) {
            JsonElement value = json.get(key);
            return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
        }
    }

    /** 其他 MOD 注册的自定义条件类型。 */
    Map<String, Function<Context, EntryCondition>> CUSTOM_TYPES = new ConcurrentHashMap<>();

    /** 解析单个条件；无法识别时返回 null 并记录问题。 */
    static EntryCondition parse(JsonElement element, String source, String entryId) {
        if (element == null || !element.isJsonObject()) {
            EntryDiagnostics.error(source, entryId, "conditions 里有不是对象的元素，已忽略该条件");
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement typeElement = object.get("type");
        if (typeElement == null || !typeElement.isJsonPrimitive()) {
            EntryDiagnostics.error(source, entryId, "condition 缺少 type 字段，已忽略该条件");
            return null;
        }
        String type = typeElement.getAsString();
        String bare = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        switch (bare) {
            case "mod_loaded":
            case "mod_not_loaded": {
                String mod = string(object, "mod");
                if (mod == null || mod.isEmpty()) {
                    EntryDiagnostics.error(source, entryId, type + " 缺少 mod 字段，已忽略该条件");
                    return null;
                }
                boolean expected = bare.equals("mod_loaded");
                return new ModLoaded(mod, expected);
            }
            case "item_exists":
            case "item_missing": {
                String item = string(object, "item");
                ResourceLocation id = item == null ? null : ResourceLocation.tryParse(item);
                if (id == null) {
                    EntryDiagnostics.error(source, entryId, type + " 的 item 不是合法物品 id：" + item);
                    return null;
                }
                return new ItemPresent(id, bare.equals("item_exists"));
            }
            case "all":
            case "and": {
                List<EntryCondition> values = parseList(object.get("values"), source, entryId);
                return new All(values);
            }
            case "any":
            case "or": {
                List<EntryCondition> values = parseList(object.get("values"), source, entryId);
                return new Any(values);
            }
            case "not": {
                JsonElement value = object.has("value") ? object.get("value") : object.get("values");
                EntryCondition inner = parse(value, source, entryId);
                return new Not(inner == null ? ALWAYS : inner);
            }
            default: {
                Function<Context, EntryCondition> custom = CUSTOM_TYPES.get(bare);
                if (custom != null) {
                    try {
                        EntryCondition parsed = custom.apply(new Context(object, source, entryId));
                        if (parsed != null) return parsed;
                        EntryDiagnostics.error(source, entryId, "自定义条件 " + type + " 返回了 null，已停用该条目");
                    } catch (Exception e) {
                        EntryDiagnostics.error(source, entryId, "自定义条件 " + type + " 解析失败：" + e + "（条目将被停用）");
                    }
                    return NEVER;
                }
                EntryDiagnostics.error(source, entryId, "不认识的 condition 类型：" + type + "（条目将被停用）");
                return NEVER;
            }
        }
    }

    /** 解析 conditions 数组（也可以是单个对象）。 */
    static List<EntryCondition> parseList(JsonElement element, String source, String entryId) {
        List<EntryCondition> out = new ArrayList<>();
        if (element == null || element.isJsonNull()) return out;
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                EntryCondition condition = parse(item, source, entryId);
                if (condition != null) out.add(condition);
            }
        } else {
            EntryCondition condition = parse(element, source, entryId);
            if (condition != null) out.add(condition);
        }
        return out;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    /** 全部满足。 */
    record All(List<EntryCondition> values) implements EntryCondition {
        @Override
        public boolean test() {
            for (EntryCondition value : values) if (!value.test()) return false;
            return true;
        }

        @Override
        public String describe() {
            return "all" + values;
        }
    }

    /** 满足任意一个（空列表视为满足）。 */
    record Any(List<EntryCondition> values) implements EntryCondition {
        @Override
        public boolean test() {
            if (values.isEmpty()) return true;
            for (EntryCondition value : values) if (value.test()) return true;
            return false;
        }

        @Override
        public String describe() {
            return "any" + values;
        }
    }

    /** 取反。 */
    record Not(EntryCondition value) implements EntryCondition {
        @Override
        public boolean test() {
            return !value.test();
        }

        @Override
        public String describe() {
            return "not(" + value.describe() + ")";
        }
    }

    /** 某个 mod 是否加载。 */
    record ModLoaded(String mod, boolean expected) implements EntryCondition {
        @Override
        public boolean test() {
            try {
                return ModList.get().isLoaded(mod) == expected;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public String describe() {
            return (expected ? "mod_loaded " : "mod_not_loaded ") + mod;
        }
    }

    /** 某个物品是否注册。 */
    record ItemPresent(ResourceLocation item, boolean expected) implements EntryCondition {
        @Override
        public boolean test() {
            return BuiltInRegistries.ITEM.containsKey(item) == expected;
        }

        @Override
        public String describe() {
            return (expected ? "item_exists " : "item_missing ") + item;
        }
    }

    /** 固定结果的条件。 */
    record Constant(boolean value, String description) implements EntryCondition {
        @Override
        public boolean test() {
            return value;
        }

        @Override
        public String describe() {
            return description;
        }
    }

    /** 恒不满足：用于把写错条件的条目停用，而不是让它照常出现。 */
    EntryCondition NEVER = new Constant(false, "never");

    /** 恒满足。 */
    EntryCondition ALWAYS = new Constant(true, "always");

    /** 便捷：把布尔判断包成条件（给其他 MOD 从代码注册时用）。 */
    public static EntryCondition of(Supplier<Boolean> supplier, String description) {
        return new EntryCondition() {
            @Override
            public boolean test() {
                return Boolean.TRUE.equals(supplier.get());
            }

            @Override
            public String describe() {
                return description;
            }
        };
    }

    /** 便捷：mod 加载条件。 */
    public static EntryCondition modLoaded(String mod) {
        return new ModLoaded(mod, true);
    }

    /** 便捷：mod 未加载条件。 */
    public static EntryCondition modNotLoaded(String mod) {
        return new ModLoaded(mod, false);
    }

    /** 便捷：物品存在条件。 */
    public static EntryCondition itemExists(String item) {
        ResourceLocation id = ResourceLocation.tryParse(item);
        return id == null ? NEVER : new ItemPresent(id, true);
    }

    /** 便捷：物品不存在条件。 */
    public static EntryCondition itemMissing(String item) {
        ResourceLocation id = ResourceLocation.tryParse(item);
        return id == null ? NEVER : new ItemPresent(id, false);
    }

    /** 便捷：取反。 */
    public static EntryCondition not(EntryCondition value) {
        return new Not(value == null ? ALWAYS : value);
    }

    /** 便捷：全部满足。 */
    public static EntryCondition all(EntryCondition... values) {
        return new All(List.of(values));
    }

    /** 便捷：满足任意一个。 */
    public static EntryCondition any(EntryCondition... values) {
        return new Any(List.of(values));
    }
}
