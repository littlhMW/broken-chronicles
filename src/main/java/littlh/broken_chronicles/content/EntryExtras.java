package littlh.broken_chronicles.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 条目的可选扩展字段：世界条目、分组、提示、加载条件、收录钩子。
 * <p>
 * 全部可选，缺省就是"个人条目 + 无分组 + 无条件 + 无钩子"。
 */
public record EntryExtras(EntryScope scope,
                          String group,
                          Localized groupTitle,
                          Localized hint,
                          Localized narrator,
                          Localized description,
                          Clue clue,
                          List<EntryGate> gates,
                          TagSources tagSources,
                          List<EntryCondition> conditions,
                          UnlockActions onUnlock,
                          Boolean autoPage,
                          List<ResourceLocation> requires,
                          boolean pinned) {

    /** 本模组支持的条目格式版本。 */
    public static final int FORMAT = 1;

    public static final EntryExtras DEFAULT = new EntryExtras(
            EntryScope.PLAYER, "", null, null, null, null, null, List.of(), TagSources.ANY,
            List.of(), UnlockActions.NONE, null, List.of(), false);

    /**
     * 故事链条：必须先收录这些条目，本条目才会出现（战利品表 / 物品自然生成 / 收集册的未收录项）。
     * <p>
     * 空列表 = 不设前置（老行为）。前置没满足时条目不会刷出来，收集册里也不显示（不是 ??? ，直接不存在）。
     */
    public List<ResourceLocation> requires() {
        return requires;
    }

    /** 前置是否全部满足；has 由调用方给出（服务端看玩家收录数据，客户端看本地缓存）。 */
    public boolean requiresMet(java.util.function.Predicate<ResourceLocation> has) {
        for (ResourceLocation required : requires) {
            if (!has.test(required)) return false;
        }
        return true;
    }

    /** 前置条目的可读文本（/broken_chronicles 指令与日志用）。 */
    public String requiresText() {
        if (requires.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ResourceLocation required : requires) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(required);
        }
        return sb.toString();
    }

    /**
     * 是否对这本书做自动分页：作者只写一段 {@code text} 时，按排版高度自动切成多页，
     * 不用自己数每页该放多少字。
     * <p>
     * 没写 {@code autopage} 时的默认值：book 条目用 {@code text}（而不是 {@code pages}）写正文时为 true，
     * 其余为 false（保持老行为：内容比一页高时滚动，不翻页）。
     */
    public boolean autoPageOrDefault(boolean cameFromTextField) {
        if (autoPage != null) return autoPage;
        return cameFromTextField;
    }

    /** 世界条目：任何人解锁，本存档所有玩家一起解锁。 */
    public boolean world() {
        return scope == EntryScope.WORLD;
    }

    /** 分组 id（编年史里按它分卷展示），空串表示不分组。 */
    public boolean hasGroup() {
        return group != null && !group.isEmpty();
    }

    public String groupOrEmpty() {
        return group == null ? "" : group;
    }

    /** 叙述者名字，没有就返回空串。 */
    public String narratorText(String language) {
        return narrator == null ? "" : narrator.resolve(language);
    }

    /** 运行时门槛是否全部满足（player 可能为空 = 没人在场，一律不放行）。 */
    public boolean gatesMet(net.minecraft.world.entity.player.Player player) {
        return EntryGate.allPass(gates, player);
    }

    /**
     * 置顶：收集册里排在所有条目之前。
     * <p>
     * 用来把"开场的那一篇"固定在列表顶部，不受其他条目的 order / id 影响。
     */
    public EntryExtras withPinned(boolean value) {
        if (pinned == value) return this;
        return new EntryExtras(scope, group, groupTitle, hint, narrator, description, clue, gates, tagSources,
                conditions, onUnlock, autoPage, requires, value);
    }

    /** 叙述者（作者）：收集册里跟在标题后面、按它筛选，阅读界面画在标题下面。 */
    public EntryExtras withNarrator(Localized value) {
        if (narrator == value) return this;
        return new EntryExtras(scope, group, groupTitle, hint, value, description, clue, gates, tagSources,
                conditions, onUnlock, autoPage, requires, pinned);
    }

    /** 描述：收集册悬浮提示与物品 tooltip 里的那行说明。 */
    public EntryExtras withDescription(Localized value) {
        if (description == value) return this;
        return new EntryExtras(scope, group, groupTitle, hint, narrator, value, clue, gates, tagSources,
                conditions, onUnlock, autoPage, requires, pinned);
    }

    /** 描述文本（条目悬浮提示 / 物品 tooltip 用），没有就返回空串。 */
    public String descriptionText(String language) {
        return description == null ? "" : description.resolve(language);
    }

    /** 条件是否全部满足。 */
    public boolean conditionsMet() {
        for (EntryCondition condition : conditions) {
            if (!condition.test()) return false;
        }
        return true;
    }

    /** 条件说明（日志 / validate 用）。 */
    public String conditionsText() {
        if (conditions.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (EntryCondition condition : conditions) {
            if (sb.length() > 0) sb.append(" && ");
            sb.append(condition.describe());
        }
        return sb.toString();
    }

    /** 解析 JSON 里的扩展字段。 */
    public static EntryExtras parse(JsonObject object, String source, ResourceLocation id) {
        String entryId = id == null ? "" : id.toString();

        if (object.has("format") && object.get("format").isJsonPrimitive()) {
            try {
                int format = object.get("format").getAsInt();
                if (format > FORMAT) {
                    EntryDiagnostics.warn(source, entryId,
                            "format=" + format + " 高于本模组支持的 " + FORMAT + "，部分字段可能不生效");
                }
            } catch (Exception e) {
                EntryDiagnostics.error(source, entryId, "format 不是整数");
            }
        }

        EntryScope scope = EntryScope.PLAYER;
        JsonElement scopeElement = object.get("scope");
        if (scopeElement != null && scopeElement.isJsonPrimitive()) {
            EntryScope parsed = EntryScope.fromString(scopeElement.getAsString());
            if (parsed == null) {
                EntryDiagnostics.error(source, entryId, "scope 只能是 player 或 world，收到：" + scopeElement.getAsString());
            } else {
                scope = parsed;
            }
        }

        String group = "";
        JsonElement groupElement = object.has("group") ? object.get("group") : object.get("series");
        if (groupElement != null && groupElement.isJsonPrimitive()) {
            group = groupElement.getAsString();
        }
        Localized groupTitle = Localized.fromJson(object.get("group_title"));

        Localized hint = Localized.fromJson(object.get("hint"));
        // 叙述者：写 narrator，或者用 author 当别名（和原版成书的 author 字段对齐）
        Localized narrator = Localized.fromJson(object.has("narrator") ? object.get("narrator") : object.get("author"));
        Localized description = Localized.fromJson(object.get("description"));
        Clue clue = Clue.parse(object.get("clue"), source, entryId);
        List<EntryGate> gates = EntryGate.parse(object.get("gates"), source, id);
        TagSources tagSources = TagSources.parse(object, source, entryId);

        List<EntryCondition> conditions = EntryCondition.parseList(object.get("conditions"), source, entryId);
        UnlockActions onUnlock = UnlockActions.parse(object.get("on_unlock"), source, entryId);

        Boolean autoPage = null;
        if (object.has("autopage")) {
            JsonElement autoPageElement = object.get("autopage");
            if (autoPageElement.isJsonPrimitive() && autoPageElement.getAsJsonPrimitive().isBoolean()) {
                autoPage = autoPageElement.getAsBoolean();
            } else if (autoPageElement.isJsonPrimitive()) {
                try {
                    autoPage = autoPageElement.getAsInt() != 0;
                } catch (Exception e) {
                    EntryDiagnostics.error(source, entryId, "autopage 只能是 true / false");
                }
            } else {
                EntryDiagnostics.error(source, entryId, "autopage 只能是 true / false");
            }
        }

        List<ResourceLocation> requires = new ArrayList<>();
        JsonElement requiresElement = object.get("requires");
        if (requiresElement != null && requiresElement.isJsonArray()) {
            for (JsonElement item : requiresElement.getAsJsonArray()) {
                ResourceLocation required = parseId(item, source, entryId, "requires");
                if (required != null) requires.add(required);
            }
        } else if (requiresElement != null) {
            ResourceLocation required = parseId(requiresElement, source, entryId, "requires");
            if (required != null) requires.add(required);
        }

        boolean pinned = net.minecraft.util.GsonHelper.getAsBoolean(object, "pinned", false);

        return new EntryExtras(scope, group, groupTitle, hint, narrator, description, clue, List.copyOf(gates),
                tagSources, conditions, onUnlock, autoPage, List.copyOf(requires), pinned);
    }

    /** requires 里的条目 id 解析 + 体检。 */
    private static ResourceLocation parseId(JsonElement element, String source, String entryId, String field) {
        if (element == null || !element.isJsonPrimitive()) {
            EntryDiagnostics.error(source, entryId, field + " 里不是条目 id 字符串");
            return null;
        }
        String raw = element.getAsString();
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed == null) {
            EntryDiagnostics.error(source, entryId, field + " 里的条目 id 不合法：" + raw);
        }
        return parsed;
    }
}
