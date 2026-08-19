package littlh.broken_chronicles.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一段可按语言解析的文本。支持 {"zh_cn": "...", "en_us": "..."} 或纯字符串。
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
