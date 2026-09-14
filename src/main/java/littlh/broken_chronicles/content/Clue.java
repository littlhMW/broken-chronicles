package littlh.broken_chronicles.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 线索：可点亮条目还没收录时，除了 ??? 之外给玩家一点方向感。整块可选，不写就是老行为。
 * <pre>
 * "clue": {
 *   "where": { "zh_cn": "村庄的箱子、要塞图书馆", "en_us": "Village chests, stronghold libraries" },
 *   "track": true
 * }
 * </pre>
 * <ul>
 *   <li>{@code where} —— 一行字，显示在 ??? 后面（没写 where 时退回用 hint 那行字）；</li>
 *   <li>{@code track} —— 为 true 时这条 ??? 可以点开「线索」界面，里面列出获取途径、还没满足的前置与门槛。默认 false。</li>
 * </ul>
 * 想让某些条目有线索、某些没有，就只在需要的那几条里写 clue。
 */
public record Clue(Localized where, boolean track) {

    public boolean isEmpty() {
        return (where == null || where.values().isEmpty()) && !track;
    }

    /** 解析 clue 字段；写法不对时记一条错误并当作没写。 */
    public static Clue parse(JsonElement element, String source, String entryId) {
        if (element == null || element.isJsonNull()) return null;
        if (!element.isJsonObject()) {
            EntryDiagnostics.error(source, entryId, "clue 必须是对象，例如 { \"where\": { \"zh_cn\": \"…\" }, \"track\": true }");
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        Localized where = Localized.fromJson(object.get("where"));
        boolean track = object.has("track") && object.get("track").isJsonPrimitive()
                && object.get("track").getAsBoolean();
        Clue clue = new Clue(where, track);
        return clue.isEmpty() ? null : clue;
    }
}