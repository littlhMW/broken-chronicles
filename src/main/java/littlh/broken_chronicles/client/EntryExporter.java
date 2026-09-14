package littlh.broken_chronicles.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * 把游戏内写好的内容导出成条目 JSON，直接放进 config/broken_chronicles/entries/。
 * <p>
 * 整合包作者可以先用失传墨水在游戏里把文字和背景调好，再在「设置 → 本条条目」里配好
 * 可点亮 / 战利品表 / 前置 / 世界条目这些属性，最后一键导出。
 */
public final class EntryExporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntryExporter.class);

    private EntryExporter() {
    }

    /**
     * 写一个条目 JSON 到 config/broken_chronicles/entries/。
     *
     * @param draft 这条条目的属性（可点亮 / 战利品表 / 前置 / 分组……），可为 null
     * @return 写出的文件名（失败返回空串）
     */
    public static String export(String type, String title, String author, String description,
                                List<String> pages, List<ResourceLocation> textures, EntryDraft draft) {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("broken_chronicles").resolve("entries");
            Files.createDirectories(dir);
            String base = draft != null && !draft.id.isBlank() ? sanitize(draft.id) : idFrom(title);
            String id = uniqueId(dir, base);
            Path file = dir.resolve(id + ".json");
            Files.writeString(file, toJson(type, title, author, description, pages, textures, draft),
                    StandardCharsets.UTF_8);
            return id + ".json";
        } catch (Exception e) {
            LOGGER.error("[破碎编年史] 导出条目失败", e);
            return "";
        }
    }

    /** 生成条目 JSON 文本（不写文件，方便测试/复用）。 */
    public static String toJson(String type, String title, String author, String description,
                               List<String> pages, List<ResourceLocation> textures, EntryDraft draft) {
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        root.addProperty("type", type);
        root.addProperty("order", draft == null ? 100 : draft.order);
        root.addProperty("creative", draft == null || draft.creative);
        root.addProperty("reveal", draft == null || draft.reveal);
        root.addProperty("startUnlocked", draft != null && draft.startUnlocked);
        // 顺手把每条字段都写全，导出的 JSON 就是一份能直接用的数据包条目
        if (title != null && !title.isBlank()) root.addProperty("title", title.trim());
        // 作者（= 条目里的 narrator / author 字段）与描述：书写界面填了就一起导出
        if (author != null && !author.isBlank()) root.addProperty("narrator", author.trim());
        if (description != null && !description.isBlank()) root.addProperty("description", description.trim());

        if (draft != null) {
            if (draft.pinned) root.addProperty("pinned", true);
            if (draft.world) root.addProperty("scope", "world");
            if (!draft.group.isBlank()) root.addProperty("group", draft.group.trim());
            if (!draft.groupTitle.isBlank()) root.addProperty("group_title", draft.groupTitle.trim());
            if (!draft.hint.isBlank()) root.addProperty("hint", draft.hint.trim());
            if (!draft.clueWhere.isBlank() || draft.clueTrack) {
                JsonObject clue = new JsonObject();
                if (!draft.clueWhere.isBlank()) clue.addProperty("where", draft.clueWhere.trim());
                if (draft.clueTrack) clue.addProperty("track", true);
                root.add("clue", clue);
            }
            if (draft.autoPage != null) root.addProperty("autopage", draft.autoPage);
            if (!draft.lootTables.isEmpty()) {
                JsonArray loot = new JsonArray();
                for (String table : draft.lootTables) loot.add(table);
                root.add("loot_tables", loot);
                root.addProperty("loot_weight", draft.lootWeight);
            }
            if (!draft.requires.isEmpty()) {
                JsonArray requires = new JsonArray();
                for (String required : draft.requires) requires.add(required);
                root.add("requires", requires);
            }
            writeGates(root, draft.gates);
            writeConditions(root, draft.conditions);
            if (draft.hasUnlockActions()) {
                JsonObject unlock = new JsonObject();
                if (!draft.onUnlockFunction.isBlank()) unlock.addProperty("function", draft.onUnlockFunction.trim());
                if (!draft.onUnlockLootTable.isBlank()) unlock.addProperty("loot_table", draft.onUnlockLootTable.trim());
                if (!draft.onUnlockCommand.isBlank()) unlock.addProperty("command", draft.onUnlockCommand.trim());
                root.add("on_unlock", unlock);
            }
            // tag 条目：文字挂在哪个物品上、生成概率、限定来源
            if ("tag".equals(type)) {
                if (!draft.item.isBlank()) root.addProperty("item", draft.item.trim());
                if (draft.chance > 0) root.addProperty("chance", draft.chance);
                if (!draft.entity.isBlank()) root.addProperty("entity", draft.entity.trim());
                if (draft.fishing) root.addProperty("fishing", true);
                if (draft.traded) root.addProperty("traded", true);
                if (draft.crafted) root.addProperty("crafted", true);
            }
        }

        if ("book".equals(type)) {
            JsonArray array = new JsonArray();
            for (int i = 0; i < pages.size(); i++) {
                ResourceLocation texture = i < textures.size() ? textures.get(i) : null;
                if (texture == null) {
                    array.add(pages.get(i));
                } else {
                    JsonObject page = new JsonObject();
                    page.addProperty("text", pages.get(i));
                    page.addProperty("texture", texture.toString());
                    array.add(page);
                }
            }
            root.add("pages", array);
        } else {
            root.addProperty("text", String.join("\n\n", pages));
            if (textures != null && !textures.isEmpty() && textures.get(0) != null) {
                root.addProperty("texture", textures.get(0).toString());
            }
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
    }

    /**
     * 门槛：一行一条，写成 "类型:参数"，导出成 gates 数组。
     * <pre>
     * entry:你的数据包:前置条目
     * advancement:minecraft:story/enter_the_end
     * dimension:minecraft:the_end
     * item:minecraft:ender_pearl:2
     * scoreboard:quest_stage:3
     * </pre>
     * 解析不了的行会被跳过（写进 JSON 反而会让条目加载失败，不如丢掉）。
     */
    private static void writeGates(JsonObject root, List<String> lines) {
        if (lines.isEmpty()) return;
        JsonArray array = new JsonArray();
        for (String line : lines) {
            JsonObject gate = gateOf(line);
            if (gate != null) array.add(gate);
        }
        if (!array.isEmpty()) root.add("gates", array);
    }

    private static JsonObject gateOf(String line) {
        String text = line == null ? "" : line.trim();
        if (text.isEmpty()) return null;
        int colon = text.indexOf(':');
        if (colon <= 0) return null;
        String kind = text.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String rest = text.substring(colon + 1).trim();
        if (rest.isEmpty()) return null;
        JsonObject gate = new JsonObject();
        switch (kind) {
            case "entry", "collected", "advancement", "dimension" -> {
                gate.addProperty("type", kind.equals("collected") ? "entry" : kind);
                gate.addProperty("id", rest);
            }
            case "item" -> {
                String id = rest;
                int count = 1;
                int last = rest.lastIndexOf(':');
                if (last > 0 && rest.indexOf(':') != last) {
                    try {
                        count = Math.max(1, Integer.parseInt(rest.substring(last + 1).trim()));
                        id = rest.substring(0, last).trim();
                    } catch (NumberFormatException ignored) {
                    }
                }
                gate.addProperty("type", "item");
                gate.addProperty("id", id);
                if (count > 1) gate.addProperty("count", count);
            }
            case "scoreboard" -> {
                String objective = rest;
                Integer min = null;
                int last = rest.lastIndexOf(':');
                if (last > 0) {
                    try {
                        min = Integer.parseInt(rest.substring(last + 1).trim());
                        objective = rest.substring(0, last).trim();
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (objective.isEmpty()) return null;
                gate.addProperty("type", "scoreboard");
                gate.addProperty("objective", objective);
                if (min != null) gate.addProperty("min", min);
            }
            default -> {
                return null;
            }
        }
        return gate;
    }

    /**
     * 加载条件：一行一条，写成 "类型:参数"，导出成 conditions 数组。
     * <pre>
     * mod_loaded:create
     * mod_not_loaded:sodium
     * item_exists:minecraft:diamond
     * item_missing:minecraft:copper_ingot
     * </pre>
     */
    private static void writeConditions(JsonObject root, List<String> lines) {
        if (lines.isEmpty()) return;
        JsonArray array = new JsonArray();
        for (String line : lines) {
            JsonObject condition = conditionOf(line);
            if (condition != null) array.add(condition);
        }
        if (!array.isEmpty()) root.add("conditions", array);
    }

    private static JsonObject conditionOf(String line) {
        String text = line == null ? "" : line.trim();
        if (text.isEmpty()) return null;
        int colon = text.indexOf(':');
        if (colon <= 0) return null;
        String kind = text.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String rest = text.substring(colon + 1).trim();
        if (rest.isEmpty()) return null;
        JsonObject condition = new JsonObject();
        switch (kind) {
            case "mod_loaded", "mod_not_loaded" -> {
                condition.addProperty("type", kind);
                condition.addProperty("mod", rest);
            }
            case "item_exists", "item_missing" -> {
                condition.addProperty("type", kind);
                condition.addProperty("item", rest);
            }
            default -> {
                return null;
            }
        }
        return condition;
    }

    /** 文件名 = 条目 id，只能是 a-z0-9_-，中英混排的标题回退成 exported_entry。 */
    private static String idFrom(String title) {
        StringBuilder sb = new StringBuilder();
        if (title != null) {
            for (char c : title.toLowerCase(Locale.ROOT).toCharArray()) {
                if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) sb.append(c);
                else if (c == ' ' || c == '-' || c == '_') sb.append('_');
            }
        }
        String id = sb.toString().replaceAll("_+", "_").replaceAll("^_|_$", "");
        return id.isEmpty() ? "exported_entry" : id;
    }

    /** 玩家自己填的 id：允许写命名空间前缀，但文件名只留 a-z0-9_- 段。 */
    private static String sanitize(String raw) {
        String text = raw.trim().toLowerCase(Locale.ROOT);
        int colon = text.indexOf(':');
        if (colon >= 0) text = text.substring(colon + 1);
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') sb.append(c);
            else if (c == ' ' || c == '/' || c == '.') sb.append('_');
        }
        String id = sb.toString().replaceAll("_+", "_").replaceAll("^_|_$", "");
        return id.isEmpty() ? "exported_entry" : id;
    }

    private static String uniqueId(Path dir, String base) {
        if (!Files.exists(dir.resolve(base + ".json"))) return base;
        for (int i = 2; i < 1000; i++) {
            if (!Files.exists(dir.resolve(base + "_" + i + ".json"))) return base + "_" + i;
        }
        return base + "_" + System.currentTimeMillis();
    }
}