package littlh.broken_chronicles.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一段可按语言解析的文本。支持 {"zh_cn": "...", "en_us": "..."} 或纯字符串。
 * <p>
 * 网络传输时用 {@link #toJson()} / {@link #fromString(String)} 往返，这样语言变体不会在同步途中丢失。
 */
public record Localized(Map<String, String> values) {

    public static Localized of(String value) {
        return new Localized(Map.of("", value));
    }

    public static Localized fromJson(JsonElement element) {
        if (element == null) return null;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return of(element.getAsString());
        }
        if (element.isJsonObject()) {
            Map<String, String> map = new LinkedHashMap<>();
            JsonObject object = element.getAsJsonObject();
            for (String key : object.keySet()) {
                map.put(key, object.get(key).getAsString());
            }
            return new Localized(map);
        }
        return null;
    }

    /** 传输用：单语言文本直接存原串，多语言存成 JSON 对象。 */
    public String toJson() {
        if (values.size() == 1 && values.containsKey("")) return values.get("");
        return new com.google.gson.Gson().toJson(values);
    }

    /** {@link #toJson()} 的逆运算；不是 JSON 对象就当成纯文本。 */
    public static Localized fromString(String raw) {
        if (raw == null) return of("");
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            try {
                return fromJson(JsonParser.parseString(trimmed));
            } catch (Exception ignored) {
                // 不是合法 JSON，按纯文本处理
            }
        }
        return of(raw);
    }

    /** 是否存在这个语言的文本（空串也算写了）。 */
    public boolean has(String language) {
        return language != null && values.containsKey(language);
    }

    /** 已有的语言键（纯文本条目返回空集）。 */
    public java.util.Set<String> languages() {
        return java.util.Collections.unmodifiableSet(values.keySet());
    }

    /**
     * 覆盖/追加一个语言的文本（lang 覆盖文件用）。空值原样返回。
     * 原来没有语言的纯文本条目会保留原串作为兜底，新语言优先。
     */
    public Localized with(String language, String value) {
        if (language == null || language.isEmpty() || value == null || value.isEmpty()) return this;
        Map<String, String> copy = new LinkedHashMap<>(values);
        copy.put(language, value);
        return new Localized(copy);
    }

    public String resolve(String language) {
        if (language != null) {
            String value = values.get(language);
            if (value != null) return value;
        }
        String value = values.get("en_us");
        if (value != null) return value;
        value = values.get("en");
        if (value != null) return value;
        if (!values.isEmpty()) return values.values().iterator().next();
        return "";
    }
}
