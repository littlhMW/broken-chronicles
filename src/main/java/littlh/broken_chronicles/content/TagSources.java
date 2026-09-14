package littlh.broken_chronicles.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

/**
 * tag 条目的生成来源：文字挂在哪个物品上之外，还能限定"这个物品是怎么来的"。
 * <pre>
 * "item": "minecraft:rotten_flesh",
 * "chance": 5,
 * "entity": "minecraft:zombie",   // 只在僵尸死亡掉落时判定
 * "fishing": false,               // 钓上来的判定
 * "traded": false,                // 村民交易获得的判定
 * "crafted": false                // 合成产出的判定
 * </pre>
 * 规则：
 * <ul>
 *   <li>四项都没写（默认）——保持老行为：任何物品实体生成时按 chance 掷骰；</li>
 *   <li>写了任意一项——只在写了的来源判定，其它来源不再打这条文字。</li>
 * </ul>
 * 同一条物品上要"多种来源"的话，写多个 tag 条目（各自 chance），系统只会挑一条生效。
 */
public record TagSources(ResourceLocation entity, boolean fishing, boolean traded, boolean crafted) {

    public static final TagSources ANY = new TagSources(null, false, false, false);

    /** 是否限定了具体来源（限定后不再走"任意物品生成"）。 */
    public boolean specific() {
        return entity != null || fishing || traded || crafted;
    }

    /** 这条来源里含"生物掉落"，且生物 id 匹配（entity 为空表示任意生物掉落都不算）。 */
    public boolean matchesDrop(ResourceLocation killed) {
        return entity != null && killed != null && entity.equals(killed);
    }

    public static TagSources parse(JsonObject object, String source, String entryId) {
        ResourceLocation entity = null;
        JsonElement entityElement = object.get("entity");
        if (entityElement != null && entityElement.isJsonPrimitive()) {
            entity = ResourceLocation.tryParse(entityElement.getAsString());
            if (entity == null) {
                EntryDiagnostics.error(source, entryId, "entity 不是合法的生物 id：" + entityElement.getAsString());
            }
        }
        TagSources sources = new TagSources(entity, flag(object, "fishing"), flag(object, "traded"),
                flag(object, "crafted"));
        return sources;
    }

    private static boolean flag(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    /** 一行中文说明（指令 / 线索界面用）。 */
    public String describe() {
        if (!specific()) return "任意物品生成";
        StringBuilder sb = new StringBuilder();
        if (entity != null) sb.append("生物掉落(").append(entity).append(")");
        if (fishing) sb.append(sb.length() > 0 ? "、" : "").append("钓鱼");
        if (traded) sb.append(sb.length() > 0 ? "、" : "").append("交易");
        if (crafted) sb.append(sb.length() > 0 ? "、" : "").append("合成");
        return sb.toString();
    }
}