package littlh.broken_chronicles.content;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 把翻译回填进条目 JSON（/broken_chronicles import-lang &lt;语言&gt; [原语言]）。
 * <p>
 * 配合 {@code export-lang}：译者填的是 config/broken_chronicles/lang/&lt;语言&gt;.json 这个"覆盖层"，
 * 本工具把它并进 config/broken_chronicles/entries/ 里对应的条目文件，让译文跟着条目走
 * （发布数据包时不用额外带 lang 文件）。
 * <p>
 * 能回填的只有 config/broken_chronicles/entries/ 里的条目（id 形如 {@code external:<文件名>}）。
 * 数据包 / 模组里的条目在 jar 或世界目录里，改不了，只能继续留在 lang 覆盖层里。
 * <p>
 * 原文是纯字符串的条目（没写成 {"zh_cn": …} 那种）必须给出原语言，否则不知道该把原文标成哪种语言：
 * 例如 {@code import-lang en_us zh_cn} 表示"这份译文是英文，条目里的原文是中文"。
 */
public final class LangImport {

    private static final Logger LOGGER = LoggerFactory.getLogger(LangImport.class);

    /** 结果报告。 */
    public record Result(int written, int skipped, List<String> notes) {
        public boolean anyWritten() {
            return written > 0;
        }
    }

    private LangImport() {
    }

    public static Path langDir() {
        return FMLPaths.CONFIGDIR.get().resolve("broken_chronicles").resolve("lang");
    }

    public static Path entriesDir() {
        return FMLPaths.CONFIGDIR.get().resolve("broken_chronicles").resolve("entries");
    }

    /**
     * 回填。
     *
     * @param language       要回填进去的语言，例如 en_us
     * @param sourceLanguage 条目里现有纯文本是什么语言；可以不填（null）
     * @param dryRun         true 只检查不写文件
     */
    public static Result run(String language, String sourceLanguage, boolean dryRun) {
        List<String> notes = new ArrayList<>();
        Path file = langDir().resolve(language + ".json");
        if (!Files.isRegularFile(file)) {
            notes.add("找不到 " + file + "（先用 /broken_chronicles export-lang " + language + " 导出模板）");
            return new Result(0, 0, notes);
        }
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                notes.add(file + " 的内容不是 JSON 对象");
                return new Result(0, 0, notes);
            }
            root = element.getAsJsonObject();
        } catch (Exception e) {
            notes.add("读取 " + file + " 失败：" + e);
            return new Result(0, 0, notes);
        }

        int written = 0;
        int skipped = 0;
        for (var item : root.entrySet()) {
            String id = item.getKey();
            if (id.startsWith("_")) continue;
            if (!item.getValue().isJsonObject()) {
                if (item.getValue().isJsonPrimitive() && item.getValue().getAsJsonPrimitive().isString()) {
                    // 简写形式：值直接是一段字符串 = 正文
                    JsonObject shorthand = new JsonObject();
                    shorthand.addProperty("text", item.getValue().getAsString());
                    if (mergeEntry(language, sourceLanguage, id, shorthand, notes, dryRun)) {
                        written++;
                    } else {
                        skipped++;
                    }
                    continue;
                }
                skipped++;
                notes.add(id + "：值不是对象，跳过");
                continue;
            }
            if (mergeEntry(language, sourceLanguage, id, item.getValue().getAsJsonObject(), notes, dryRun)) {
                written++;
            } else {
                skipped++;
            }
        }
        notes.add((dryRun ? "（试运行，没有写文件）" : "") + "回填 " + written + " 条，跳过 " + skipped + " 条");
        return new Result(written, skipped, notes);
    }

    /** 单条条目：把覆盖写进它的 JSON 文件。 */
    private static boolean mergeEntry(String language, String sourceLanguage, String id, JsonObject override,
                                      List<String> notes, boolean dryRun) {
        String prefix = ExternalEntries.NAMESPACE + ":";
        if (!id.startsWith(prefix)) {
            notes.add(id + "：不是 config/broken_chronicles/entries/ 里的条目（数据包/模组条目改不了），"
                    + "译文继续留在 lang 覆盖层里生效");
            return false;
        }
        String name = id.substring(prefix.length());
        Path target = entriesDir().resolve(name + ".json");
        if (!Files.isRegularFile(target)) {
            notes.add(id + "：找不到文件 " + target);
            return false;
        }
        try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                notes.add(id + "：" + target.getFileName() + " 不是 JSON 对象");
                return false;
            }
            JsonObject entry = element.getAsJsonObject();
            boolean isBook = entry.has("type") && "book".equals(entry.get("type").getAsString());
            boolean changed = false;
            if (override.has("title") && !override.get("title").isJsonNull()) {
                changed |= mergeLocalized(entry, "title", override.get("title").getAsString(), language, sourceLanguage, notes, id);
            }
            if (isBook) {
                if (override.has("pages") && override.get("pages").isJsonArray()) {
                    changed |= mergePages(entry, override.getAsJsonArray("pages"), language, sourceLanguage, notes, id);
                }
            } else if (override.has("text") && !override.get("text").isJsonNull()) {
                changed |= mergeLocalized(entry, "text", override.get("text").getAsString(), language, sourceLanguage, notes, id);
            }
            if (!changed) {
                notes.add(id + "：这条已经是 " + language + " 的内容了，没改动");
                return false;
            }
            if (dryRun) {
                notes.add(id + "：会写入 " + target.getFileName());
                return true;
            }
            // 备份一次，文件被改坏还能拿回来
            Path backup = target.resolveSibling(target.getFileName() + ".bak");
            if (!Files.exists(backup)) Files.copy(target, backup);
            Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(entry) + "\n",
                    StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            notes.add(id + "：写入失败 " + e);
            LOGGER.error("[破碎编年史] 回填翻译失败 {}", target, e);
            return false;
        }
    }


    /** 把一个语言写进 title / text 这类单段字段。 */
    private static boolean mergeLocalized(JsonObject entry, String key, String value,
                                          String language, String sourceLanguage,
                                          List<String> notes, String id) {
        if (value == null || value.isEmpty()) return false;
        JsonElement existing = entry.get(key);
        JsonObject object;
        if (existing == null) {
            object = new JsonObject();
        } else if (existing.isJsonObject()) {
            object = existing.getAsJsonObject();
        } else if (existing.isJsonPrimitive()) {
            String original = existing.getAsString();
            if (sourceLanguage == null || sourceLanguage.isEmpty()) {
                notes.add(id + "：" + key + " 现在是纯文本，要回填得在指令里加一个原语言，"
                        + "例如 import-lang " + language + " zh_cn（表示原文是中文）");
                return false;
            }
            object = new JsonObject();
            object.addProperty(sourceLanguage, original);
        } else {
            notes.add(id + "：" + key + " 的类型不支持回填");
            return false;
        }
        if (value.equals(object.has(language) ? object.get(language).getAsString() : null)) return false;
        object.addProperty(language, value);
        entry.add(key, object);
        return true;
    }

    /** book 的 pages：按页码一一对应。 */
    private static boolean mergePages(JsonObject entry, JsonArray translations, String language,
                                      String sourceLanguage, List<String> notes, String id) {
        JsonElement existing = entry.get("pages");
        if (existing == null || !existing.isJsonArray()) {
            notes.add(id + "：pages 不是数组，跳过");
            return false;
        }
        JsonArray pages = existing.getAsJsonArray();
        boolean changed = false;
        for (int i = 0; i < pages.size() && i < translations.size(); i++) {
            String value = translations.get(i).isJsonNull() ? null : translations.get(i).getAsString();
            if (value == null || value.isEmpty()) continue;
            JsonElement page = pages.get(i);
            if (page.isJsonObject() && page.getAsJsonObject().has("text")) {
                JsonObject wrapper = page.getAsJsonObject();
                changed |= mergeLocalized(wrapper, "text", value, language, sourceLanguage, notes,
                        id + " 第 " + (i + 1) + " 页");
            } else {
                changed |= mergePageElement(pages, i, value, language, sourceLanguage, notes, id);
            }
        }
        return changed;
    }

    /** 单独一页：元素可能是纯字符串，也可能是 { "text": … }。 */
    private static boolean mergePageElement(JsonArray pages, int index, String value, String language,
                                            String sourceLanguage, List<String> notes, String id) {
        JsonElement page = pages.get(index);
        if (page.isJsonPrimitive()) {
            if (sourceLanguage == null || sourceLanguage.isEmpty()) {
                notes.add(id + "：第 " + (index + 1) + " 页是纯文本，要回填得加一个原语言参数");
                return false;
            }
            JsonObject object = new JsonObject();
            object.addProperty(sourceLanguage, page.getAsString());
            object.addProperty(language, value);
            pages.set(index, object);
            return true;
        }
        notes.add(id + "：第 " + (index + 1) + " 页的类型不支持回填");
        return false;
    }
}