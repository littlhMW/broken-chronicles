package littlh.broken_chronicles.content;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 翻译覆盖层：{@code config/broken_chronicles/lang/&lt;语言&gt;.json}。
 * <p>
 * 一个文件对应一种语言（文件名就是语言代码，例如 {@code zh_cn.json} / {@code en_us.json}），内容是
 * "条目 id → { title, text, pages }"。加载时把它并进条目文本里：
 * <pre>
 * {
 *   "broken_chronicles:prologue": {
 *     "title": "序",
 *     "text": "……"
 *   },
 *   "external:my_diary": {
 *     "title": "Diary",
 *     "pages": ["page one", "page two"]
 *   }
 * }
 * </pre>
 * 规则：
 * <ul>
 *   <li>key 以 {@code _} 开头的字段会被忽略，可以拿 {@code _type} 之类的写备注。</li>
 *   <li>同一个语言在 lang 文件里写了就以文件为准（覆盖数据包里的原文）；留空的字段不动原文。</li>
 *   <li>条目本身只有一段纯文本（没有语言对象）时，它仍然作为兜底文本保留，写进去的语言优先。</li>
 *   <li>文件不存在就跳过，所以这个目录可以整个删掉。</li>
 * </ul>
 * 用 {@code /broken_chronicles export-lang <语言>} 生成模板，译者填完放回来即可。
 */
public final class LangOverrides {

    private static final Logger LOGGER = LoggerFactory.getLogger(LangOverrides.class);
    private static final Path DIR = FMLPaths.CONFIGDIR.get().resolve("broken_chronicles").resolve("lang");

    /** 一条覆盖：三个字段都可以缺，缺的表示不改。 */
    public record Override(String title, String text, List<String> pages) {
    }

    private LangOverrides() {
    }

    public static Path dir() {
        return DIR;
    }

    /** 读取整个 lang 目录：语言 -> 条目 id -> 覆盖。 */
    public static Map<String, Map<String, Override>> loadAll() {
        Map<String, Map<String, Override>> out = new LinkedHashMap<>();
        try {
            if (!Files.isDirectory(DIR)) return out;
            try (var stream = Files.list(DIR)) {
                for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                    String name = file.getFileName().toString();
                    String language = name.substring(0, name.length() - ".json".length());
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        JsonElement element = JsonParser.parseReader(reader);
                        if (!element.isJsonObject()) {
                            EntryDiagnostics.error("lang 覆盖", language, "文件内容不是 JSON 对象");
                            continue;
                        }
                        Map<String, Override> entries = new LinkedHashMap<>();
                        for (Map.Entry<String, JsonElement> item : element.getAsJsonObject().entrySet()) {
                            if (item.getKey().startsWith("_")) continue;
                            if (!item.getValue().isJsonObject()) {
                                EntryDiagnostics.error("lang 覆盖", language + " / " + item.getKey(),
                                        "值必须是对象，例如 { \"title\": \"…\", \"text\": \"…\" }");
                                continue;
                            }
                            JsonObject object = item.getValue().getAsJsonObject();
                            List<String> pages = new ArrayList<>();
                            JsonElement pagesElement = object.get("pages");
                            if (pagesElement != null && pagesElement.isJsonArray()) {
                                for (JsonElement page : pagesElement.getAsJsonArray()) {
                                    pages.add(page.isJsonPrimitive() ? page.getAsString() : "");
                                }
                            }
                            entries.put(item.getKey(), new Override(
                                    string(object, "title"), string(object, "text"), pages));
                        }
                        if (!entries.isEmpty()) out.put(language, entries);
                    } catch (Exception e) {
                        EntryDiagnostics.error("lang 覆盖", language, "读取失败：" + e);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("[破碎编年史] 读取 lang 目录失败 {}", DIR, e);
        }
        return out;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    /** 把一条条目的所有语言覆盖并进去（没有覆盖就原样返回）。 */
    public static ShardEntry apply(ShardEntry entry, Map<String, Override> byLanguage) {
        if (entry == null || byLanguage == null || byLanguage.isEmpty()) return entry;
        Localized title = entry.title();
        Localized text = entry.text();
        List<Localized> pages = entry.pages();
        boolean changed = false;
        for (Map.Entry<String, Override> item : byLanguage.entrySet()) {
            String language = item.getKey();
            Override override = item.getValue();
            if (override == null) continue;
            if (override.title() != null) {
                Localized base = title == null ? Localized.of("") : title;
                Localized merged = base.with(language, override.title());
                if (merged != base) {
                    title = merged;
                    changed = true;
                }
            }
            if (entry.type() == EntryType.BOOK) {
                if (!override.pages().isEmpty() && !pages.isEmpty()) {
                    List<Localized> merged = new ArrayList<>(pages);
                    for (int i = 0; i < merged.size() && i < override.pages().size(); i++) {
                        String value = override.pages().get(i);
                        if (value == null || value.isEmpty()) continue;
                        merged.set(i, merged.get(i).with(language, value));
                    }
                    pages = merged;
                    changed = true;
                }
            } else if (override.text() != null) {
                Localized base = text == null ? Localized.of("") : text;
                Localized merged = base.with(language, override.text());
                if (merged != base) {
                    text = merged;
                    changed = true;
                }
            }
        }
        return changed ? entry.withText(title, text, pages) : entry;
    }

    /** 这条文本是否缺某个语言（纯文本条目不算缺：它的原文写在数据包里）。 */
    public static boolean lacks(Localized value, String language) {
        if (value == null) return false;
        if (value.has(language)) return false;
        return !value.languages().contains("");
    }

    /** 生成某个语言的翻译模板 JSON 文本。 */
    public static String toJson(String language, boolean onlyMissing) {
        JsonObject root = new JsonObject();
        for (ShardEntry entry : ShardEntries.all()) {
            JsonObject item = new JsonObject();
            boolean missing = false;
            if (entry.title() != null) {
                if (lacks(entry.title(), language)) missing = true;
                item.addProperty("title", entry.title().resolve(language));
            }
            if (entry.type() == EntryType.BOOK) {
                JsonArray pages = new JsonArray();
                for (Localized page : entry.displayPages()) {
                    if (page == null) {
                        pages.add("");
                        continue;
                    }
                    if (lacks(page, language)) missing = true;
                    pages.add(page.resolve(language));
                }
                item.add("pages", pages);
            } else if (entry.text() != null) {
                if (lacks(entry.text(), language)) missing = true;
                item.addProperty("text", entry.text().resolve(language));
            }
            if (onlyMissing && !missing) continue;
            item.addProperty("_type", entry.type().id());
            root.add(entry.id().toString(), item);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
    }

    /** 导出模板到 lang 目录，返回写出的文件。 */
    public static Path export(String language, boolean onlyMissing) throws IOException {
        Files.createDirectories(DIR);
        Path file = DIR.resolve(language + ".json");
        Files.writeString(file, toJson(language, onlyMissing), StandardCharsets.UTF_8);
        return file;
    }

    /** 缺这个语言的条目数（lint 用）。 */
    public static int missingCount(String language) {
        int count = 0;
        for (ShardEntry entry : ShardEntries.all()) {
            boolean missing = lacks(entry.title(), language);
            if (entry.type() == EntryType.BOOK) {
                for (Localized page : entry.displayPages()) {
                    if (lacks(page, language)) missing = true;
                }
            } else if (lacks(entry.text(), language)) {
                missing = true;
            }
            if (missing) count++;
        }
        return count;
    }
}